package com.vibecode.app.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText
import com.vibecode.app.R
import com.vibecode.app.util.Prefs

class CodeEditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.line_number)
        textSize = 12f * resources.displayMetrics.density
        typeface = Typeface.MONOSPACE
    }
    private val gutterWidth = (44f * resources.displayMetrics.density).toInt()

    private var highlighter: ((android.text.Editable, android.content.Context) -> Unit)? = null
    private var language = "text"
    private var updating = false

    init {
        typeface = Typeface.MONOSPACE
        textSize = Prefs.fontSize().toFloat()
        setHorizontallyScrolling(true)
        isVerticalScrollBarEnabled = true
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(gutterWidth + (8 * resources.displayMetrics.density).toInt(), paddingTop, paddingRight, paddingBottom)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating || s == null) return
                applyHighlight(s)
            }
        })
    }

    fun setLanguage(lang: String) {
        language = lang
        highlighter = SyntaxHighlighter.forLanguage(lang)
        if (text != null) applyHighlight(text!!)
    }

    private fun applyHighlight(editable: android.text.Editable) {
        updating = true
        try {
            highlighter?.invoke(editable, context)
        } finally {
            updating = false
        }
    }

    override fun onDraw(canvas: Canvas) {
        // draw line numbers
        val layout = layout ?: run { super.onDraw(canvas); return }
        val firstLine = (scrollY / lineHeight).coerceAtLeast(0)
        val lastLine = ((scrollY + height) / lineHeight + 1).coerceAtMost(lineCount)
        var i = firstLine
        while (i < lastLine) {
            val baseline = layout.getLineBaseline(i)
            if (baseline > 0) {
                val lineNum = (i + 1).toString()
                val x = gutterWidth - linePaint.measureText(lineNum) - (6 * resources.displayMetrics.density)
                canvas.drawText(lineNum, x, baseline.toFloat(), linePaint)
            }
            i++
        }
        super.onDraw(canvas)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            val start = selectionStart
            val end = selectionEnd
            text?.replace(start, end, " ".repeat(Prefs.tabSize()))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
