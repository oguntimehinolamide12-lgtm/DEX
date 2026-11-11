package com.vibecode.app.linux

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Character-cell terminal renderer with an ANSI/VT parser: SGR colours (16, 256 and
 * truecolour), cursor addressing, erase, scrollback, resize and touch scrolling.
 * Deliberately custom-drawn so it belongs to the ember design language instead of
 * looking like a generic text box.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val FLAG_BOLD = 1
        private const val FLAG_DIM = 2
        private const val FLAG_UNDERLINE = 4
        private const val FLAG_INVERSE = 8
        private const val SCROLLBACK_MAX = 1200
        private const val DEFAULT = -1
        private const val STATE_NORMAL = 0
        private const val STATE_ESC = 1
        private const val STATE_CSI = 2
        private const val STATE_OSC = 3
    }

    class Cell(var ch: Char = ' ', var fg: Int = DEFAULT, var bg: Int = DEFAULT, var flags: Int = 0)

    var onResize: ((cols: Int, rows: Int) -> Unit)? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        isSubpixelText = true
    }
    private val boxPaint = Paint()

    private val defaultFg = themeColor(com.vibecode.app.R.attr.dexText, 0xFFF3EADF.toInt())
    private val defaultBg = themeColor(com.vibecode.app.R.attr.dexTermBg, 0xFF08060B.toInt())
    private val accent = themeColor(com.vibecode.app.R.attr.dexAccent, 0xFFFF5A1F.toInt())
    private val selectionLine = themeColor(com.vibecode.app.R.attr.dexLine, 0xFF2B2135.toInt())

    private var cellW = 8f
    private var cellH = 16f
    private var cols = 80
    private var rows = 24
    private var grid: Array<Array<Cell>> = Array(rows) { emptyRow(cols) }
    private val history = ArrayList<Array<Cell>>()

    private var cx = 0
    private var cy = 0
    private var fg = DEFAULT
    private var bg = DEFAULT
    private var flags = 0
    private var cursorVisible = true
    private var blinkOn = true
    private var scrollOffset = 0

    private var state = STATE_NORMAL
    private val csi = StringBuilder()
    private var savedCx = 0
    private var savedCy = 0
    private var lastTouchY = 0f
    private var touchStart = 0L

    private val palette: IntArray by lazy { buildPalette() }
    private val palette256: IntArray by lazy { build256() }

    init {
        textPaint.textSize = 12f * resources.displayMetrics.scaledDensity
        isFocusable = true
        isClickable = true
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private val blink = object : Runnable {
        override fun run() {
            blinkOn = !blinkOn
            if (cursorVisible) invalidate()
            postDelayed(this, 520)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(blink, 520)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blink)
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------- geometry

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellW = textPaint.measureText("M").coerceAtLeast(1f)
        cellH = textPaint.fontSpacing
        val newCols = ((w - paddingLeft - paddingRight) / cellW).toInt().coerceIn(20, 400)
        val newRows = ((h - paddingTop - paddingBottom) / cellH).toInt().coerceIn(4, 200)
        resize(newCols, newRows)
    }

    private fun resize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows && grid.isNotEmpty()) return
        val old = grid
        val next = Array(newRows) { Array(newCols) { Cell() } }
        if (old.isNotEmpty()) {
            val copyRows = min(newRows, old.size)
            for (r in 0 until copyRows) {
                val src = old[old.size - copyRows + r]
                val dst = next[newRows - copyRows + r]
                for (c in 0 until min(newCols, src.size)) dst[c] = src[c]
            }
        }
        grid = next
        cols = newCols
        rows = newRows
        cx = cx.coerceIn(0, cols - 1)
        cy = cy.coerceIn(0, rows - 1)
        onResize?.invoke(cols, rows)
        invalidate()
    }

    // -------------------------------------------------------------- feeding

    fun feed(text: String) {
        for (ch in text) parse(ch)
        invalidate()
    }

    fun clear() {
        grid = Array(rows) { emptyRow(cols) }
        history.clear()
        cx = 0; cy = 0
        scrollOffset = 0
        fg = DEFAULT; bg = DEFAULT; flags = 0
        state = STATE_NORMAL
        csi.setLength(0)
        invalidate()
    }

    fun scrollToBottom() {
        scrollOffset = 0
        invalidate()
    }

    private fun parse(ch: Char) {
        when (state) {
            STATE_NORMAL -> when (ch) {
                '\u001b' -> state = STATE_ESC
                '\r' -> cx = 0
                '\n' -> lineFeed()
                '\b' -> if (cx > 0) cx--
                '\t' -> cx = min(cols - 1, ((cx / 8) + 1) * 8)
                '\u0007' -> { }
                else -> if (ch.code >= 32) put(ch)
            }
            STATE_ESC -> when (ch) {
                '[' -> { csi.setLength(0); state = STATE_CSI }
                ']' -> state = STATE_OSC
                '7' -> { savedCx = cx; savedCy = cy; state = STATE_NORMAL }
                '8' -> { cx = savedCx; cy = savedCy; state = STATE_NORMAL }
                'M' -> { if (cy == 0) scrollDown() else cy--; state = STATE_NORMAL }
                'D' -> { lineFeed(); state = STATE_NORMAL }
                'E' -> { cx = 0; lineFeed(); state = STATE_NORMAL }
                '(' -> state = STATE_ESC
                else -> state = STATE_NORMAL
            }
            STATE_CSI -> {
                if (ch in '0'..'?') csi.append(ch)
                else { dispatchCsi(ch); state = STATE_NORMAL }
            }
            STATE_OSC -> if (ch == '\u0007' || ch == '\u001b') state = STATE_NORMAL
        }
    }

    private fun put(ch: Char) {
        if (cx >= cols) { cx = 0; lineFeed() }
        val cell = grid[cy][cx]
        cell.ch = ch
        cell.fg = fg
        cell.bg = bg
        cell.flags = flags
        cx++
    }

    private fun lineFeed() {
        if (cy == rows - 1) scrollUp() else cy++
    }

    private fun scrollUp() {
        history.add(grid[0])
        while (history.size > SCROLLBACK_MAX) history.removeAt(0)
        for (r in 1 until rows) grid[r - 1] = grid[r]
        grid[rows - 1] = emptyRow(cols)
        if (scrollOffset > 0) scrollOffset = min(scrollOffset + 1, history.size)
    }

    private fun scrollDown() {
        for (r in rows - 1 downTo 1) grid[r] = grid[r - 1]
        grid[0] = emptyRow(cols)
    }

    private fun dispatchCsi(final: Char) {
        val raw = csi.toString()
        val privateMode = raw.startsWith("?")
        val body = if (privateMode) raw.substring(1) else raw
        val parts = if (body.isEmpty()) emptyList() else body.split(';')
        fun num(i: Int, fallback: Int = 1): Int =
            parts.getOrNull(i)?.toIntOrNull()?.takeIf { it > 0 } ?: fallback

        when (final) {
            'm' -> sgr(parts)
            'H', 'f' -> {
                cy = (num(0) - 1).coerceIn(0, rows - 1)
                cx = (num(1) - 1).coerceIn(0, cols - 1)
            }
            'A' -> cy = (cy - num(0)).coerceAtLeast(0)
            'B' -> cy = (cy + num(0)).coerceAtMost(rows - 1)
            'C' -> cx = (cx + num(0)).coerceAtMost(cols - 1)
            'D' -> cx = (cx - num(0)).coerceAtLeast(0)
            'G' -> cx = (num(0) - 1).coerceIn(0, cols - 1)
            'd' -> cy = (num(0) - 1).coerceIn(0, rows - 1)
            'J' -> eraseDisplay(parts.getOrNull(0)?.toIntOrNull() ?: 0)
            'K' -> eraseLine(parts.getOrNull(0)?.toIntOrNull() ?: 0)
            's' -> { savedCx = cx; savedCy = cy }
            'u' -> { cx = savedCx; cy = savedCy }
            'h', 'l' -> if (privateMode && body.contains("25")) cursorVisible = final == 'h'
            'P' -> deleteChars(num(0))
            '@' -> insertChars(num(0))
            'L' -> insertLines(num(0))
            'M' -> deleteLines(num(0))
            else -> { }
        }
    }

    private fun sgr(parts: List<String>) {
        if (parts.isEmpty() || (parts.size == 1 && parts[0].isEmpty())) {
            fg = DEFAULT; bg = DEFAULT; flags = 0
            return
        }
        var i = 0
        while (i < parts.size) {
            val code = parts[i].toIntOrNull() ?: 0
            when {
                code == 0 -> { fg = DEFAULT; bg = DEFAULT; flags = 0 }
                code == 1 -> flags = flags or FLAG_BOLD
                code == 2 -> flags = flags or FLAG_DIM
                code == 4 -> flags = flags or FLAG_UNDERLINE
                code == 7 -> flags = flags or FLAG_INVERSE
                code == 22 -> flags = flags and (FLAG_BOLD or FLAG_DIM).inv()
                code == 24 -> flags = flags and FLAG_UNDERLINE.inv()
                code == 27 -> flags = flags and FLAG_INVERSE.inv()
                code == 39 -> fg = DEFAULT
                code == 49 -> bg = DEFAULT
                code in 30..37 -> fg = palette[code - 30]
                code in 90..97 -> fg = palette[code - 90 + 8]
                code in 40..47 -> bg = palette[code - 40]
                code in 100..107 -> bg = palette[code - 100 + 8]
                code == 38 || code == 48 -> {
                    val isBg = code == 48
                    when {
                        parts.getOrNull(i + 1) == "5" -> {
                            val colour = palette256[(parts.getOrNull(i + 2)?.toIntOrNull() ?: 0).coerceIn(0, 255)]
                            if (isBg) bg = colour else fg = colour
                            i += 2
                        }
                        parts.getOrNull(i + 1) == "2" -> {
                            val r = (parts.getOrNull(i + 2)?.toIntOrNull() ?: 0).coerceIn(0, 255)
                            val g = (parts.getOrNull(i + 3)?.toIntOrNull() ?: 0).coerceIn(0, 255)
                            val b = (parts.getOrNull(i + 4)?.toIntOrNull() ?: 0).coerceIn(0, 255)
                            val colour = Color.rgb(r, g, b)
                            if (isBg) bg = colour else fg = colour
                            i += 4
                        }
                    }
                }
            }
            i++
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(0)
                for (r in cy + 1 until rows) grid[r] = emptyRow(cols)
            }
            1 -> {
                for (r in 0 until cy) grid[r] = emptyRow(cols)
                eraseLine(1)
            }
            else -> {
                grid = Array(rows) { emptyRow(cols) }
                history.clear()
                scrollOffset = 0
            }
        }
    }

    private fun eraseLine(mode: Int) {
        val row = grid[cy]
        val from = if (mode == 1 || mode == 2) 0 else cx
        val to = if (mode == 0) cols - 1 else cx
        for (c in from..min(to, cols - 1)) row[c] = Cell()
    }

    private fun deleteChars(count: Int) {
        val row = grid[cy]
        for (c in cx until cols - count) row[c] = row[c + count]
        for (c in max(0, cols - count) until cols) row[c] = Cell()
    }

    private fun insertChars(count: Int) {
        val row = grid[cy]
        for (c in cols - 1 downTo cx + count) row[c] = row[c - count]
        for (c in cx until min(cols, cx + count)) row[c] = Cell()
    }

    private fun insertLines(count: Int) {
        for (r in rows - 1 downTo cy + count) grid[r] = grid[r - count]
        for (r in cy until min(rows, cy + count)) grid[r] = emptyRow(cols)
    }

    private fun deleteLines(count: Int) {
        for (r in cy until rows - count) grid[r] = grid[r + count]
        for (r in max(cy, rows - count) until rows) grid[r] = emptyRow(cols)
    }

    // ------------------------------------------------------------- rendering

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(if (defaultBg == Color.TRANSPARENT) Color.BLACK else defaultBg)

        val offset = scrollOffset.coerceIn(0, history.size)
        val total = history.size + rows
        val first = (total - rows - offset).coerceAtLeast(0)

        textPaint.color = defaultFg
        boxPaint.style = Paint.Style.FILL

        for (r in 0 until rows) {
            val logical = first + r
            if (logical >= total) break
            val line = if (logical < history.size) history[logical] else grid[logical - history.size]
            drawRow(canvas, line, r)
        }

        if (cursorVisible && blinkOn && offset == 0) {
            val left = paddingLeft + cx * cellW
            val top = paddingTop + cy * cellH
            boxPaint.color = accent
            canvas.drawRect(left, top, left + cellW, top + cellH, boxPaint)
            val cell = grid.getOrNull(cy)?.getOrNull(cx)
            if (cell != null && cell.ch != ' ') {
                textPaint.color = defaultBg
                canvas.drawText(cell.ch.toString(), left, top + cellH - textPaint.descent() - 1f, textPaint)
            }
        }

        // CRT scanlines: a whisper of texture, not a gimmick
        boxPaint.color = Color.argb(10, 0, 0, 0)
        var y = paddingTop.toFloat()
        while (y < height) {
            canvas.drawRect(0f, y, width.toFloat(), y + 1f, boxPaint)
            y += 3f
        }
    }

    private fun drawRow(canvas: Canvas, line: Array<Cell>, row: Int) {
        val top = paddingTop + row * cellH
        val baseline = top + cellH - textPaint.descent() - 1f

        var c = 0
        while (c < cols) {
            val cell = line.getOrNull(c)
            if (cell == null) break
            val bgColour = resolveBg(cell)
            if (bgColour != defaultBg) {
                var end = c + 1
                while (end < cols) {
                    val next = line.getOrNull(end) ?: break
                    if (resolveBg(next) != bgColour) break
                    end++
                }
                boxPaint.color = bgColour
                canvas.drawRect(paddingLeft + c * cellW, top, paddingLeft + end * cellW, top + cellH, boxPaint)
                c = end
                continue
            }
            c++
        }

        for (c in 0 until min(cols, line.size)) {
            val cell = line[c]
            if (cell.ch == ' ') continue
            textPaint.color = resolveFg(cell)
            textPaint.isFakeBoldText = (cell.flags and FLAG_BOLD) != 0
            val x = paddingLeft + c * cellW
            canvas.drawText(cell.ch.toString(), x, baseline, textPaint)
            if ((cell.flags and FLAG_UNDERLINE) != 0) {
                boxPaint.color = textPaint.color
                canvas.drawRect(x, baseline + 2f, x + cellW, baseline + 3f, boxPaint)
            }
        }
        textPaint.isFakeBoldText = false
    }

    private fun resolveFg(cell: Cell): Int {
        val colour = if (cell.fg == DEFAULT) defaultFg else cell.fg
        return if ((cell.flags and FLAG_INVERSE) != 0) {
            if (cell.bg == DEFAULT) defaultBg else cell.bg
        } else colour
    }

    private fun resolveBg(cell: Cell): Int {
        val colour = if (cell.bg == DEFAULT) defaultBg else cell.bg
        return if ((cell.flags and FLAG_INVERSE) != 0) {
            if (cell.fg == DEFAULT) defaultFg else cell.fg
        } else colour
    }

    // ----------------------------------------------------------------- touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                touchStart = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastTouchY - event.y
                if (kotlin.math.abs(dy) > cellH / 2) {
                    val steps = (dy / cellH).toInt()
                    if (steps != 0) {
                        scrollOffset = (scrollOffset + steps).coerceIn(0, history.size)
                        lastTouchY -= steps * cellH
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (System.currentTimeMillis() - touchStart < 220 && kotlin.math.abs(event.y - lastTouchY) < cellH) {
                    performClick()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // --------------------------------------------------------------- helpers

    private fun emptyRow(size: Int) = Array(size) { Cell() }

    private fun themeColor(attr: Int, fallback: Int): Int {
        val ta = context.obtainStyledAttributes(intArrayOf(attr))
        return try { ta.getColor(0, fallback) } finally { ta.recycle() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildPalette(): IntArray = intArrayOf(
        0xFF1B1620.toInt(), 0xFFFF5A5A.toInt(), 0xFF7BE38B.toInt(), 0xFFFFC24B.toInt(),
        0xFF7FB2FF.toInt(), 0xFFFF6EC7.toInt(), 0xFF5CE1E6.toInt(), 0xFFD9CDBF.toInt(),
        0xFF6E6273.toInt(), 0xFFFF8A6B.toInt(), 0xFFA6F0A0.toInt(), 0xFFFFE08A.toInt(),
        0xFF9FC6FF.toInt(), 0xFFFF9AD5.toInt(), 0xFF9BF3F0.toInt(), 0xFFF3EADF.toInt()
    )

    private fun build256(): IntArray {
        val table = IntArray(256)
        for (i in 0 until 16) table[i] = palette[i]
        var index = 16
        val steps = intArrayOf(0, 95, 135, 175, 215, 255)
        for (r in 0..5) for (g in 0..5) for (b in 0..5) {
            table[index++] = Color.rgb(steps[r], steps[g], steps[b])
        }
        for (i in 0 until 24) {
            val v = 8 + i * 10
            table[index++] = Color.rgb(v, v, v)
        }
        return table
    }
}
