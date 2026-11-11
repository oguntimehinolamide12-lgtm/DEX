package com.vibecode.app

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vibecode.app.ai.AgentHost
import com.vibecode.app.browser.BrowserConsole
import com.vibecode.app.brain.BrainPack
import com.vibecode.app.browser.BrowserFragment
import com.vibecode.app.cloud.CloudFragment
import com.vibecode.app.databinding.ActivityMainBinding
import com.vibecode.app.fs.FileManager
import com.vibecode.app.linux.LinuxEnv
import com.vibecode.app.linux.TerminalFragment
import com.vibecode.app.ui.AiChatFragment
import com.vibecode.app.ui.EditorFragment
import com.vibecode.app.util.Prefs
import java.io.File

class MainActivity : AppCompatActivity(), AgentHost {

    companion object {
        private const val TAG_EDITOR = "editor"
        private const val TAG_BROWSER = "browser"
        private const val TAG_AGENT = "agent"
        private const val TAG_TERMINAL = "terminal"
        private const val TAG_CLOUD = "cloud"
    }

    enum class Pane { EDITOR, BROWSER, TERMINAL, CLOUD }

    private lateinit var binding: ActivityMainBinding
    private lateinit var editorFragment: EditorFragment
    private lateinit var browserFragment: BrowserFragment
    private lateinit var terminalFragment: TerminalFragment
    private lateinit var cloudFragment: CloudFragment
    private lateinit var agentFragment: AiChatFragment

    private var pane = Pane.EDITOR
    private var agentBusy = false
    private var pulseAnimator: ObjectAnimator? = null

    private val files = mutableListOf<File>()
    private val allFiles = mutableListOf<File>()
    private lateinit var fileAdapter: FileAdapter
    private var currentDir: File? = null

    private val openFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
            Toast.makeText(this, "Opened: $uri", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        BrainPack.init(this)
        try { com.chaquo.python.Python.start(com.chaquo.python.android.AndroidPlatform(this)) } catch (_: Exception) { }
        setTheme(themeRes())
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFragments()
        setupRail()
        setupSidebar()
        setupSplitter()

        showPane(if (Prefs.lastPane() == "browser") Pane.BROWSER else Pane.EDITOR)
        if (Prefs.lastPane() == "terminal") showPane(Pane.TERMINAL)
        if (Prefs.lastPane() == "cloud") showPane(Pane.CLOUD)
        setAgentBusy(false)

        val last = Prefs.lastFolder()
        val lastDir = if (last.isNotBlank()) File(last) else null
        if (lastDir != null && lastDir.isDirectory) {
            loadDir(lastDir)
        } else {
            try { loadDir(Environment.getExternalStorageDirectory()) } catch (_: Exception) {
                try { loadDir(filesDir) } catch (_: Exception) { }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pane == Pane.BROWSER && browserFragment.canGoBack()) {
                    browserFragment.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        requestStoragePermissionIfNeeded()
    }

    private fun themeRes(): Int = when (Prefs.theme()) {
        "acid" -> R.style.Theme_Dex_Acid
        "paper" -> R.style.Theme_Dex_Paper
        else -> R.style.Theme_Dex
    }

    // ------------------------------------------------------------- fragments

    private fun setupFragments() {
        val fm = supportFragmentManager
        editorFragment = fm.findFragmentByTag(TAG_EDITOR) as? EditorFragment ?: EditorFragment()
        browserFragment = fm.findFragmentByTag(TAG_BROWSER) as? BrowserFragment ?: BrowserFragment()
        terminalFragment = fm.findFragmentByTag(TAG_TERMINAL) as? TerminalFragment ?: TerminalFragment()
        cloudFragment = fm.findFragmentByTag(TAG_CLOUD) as? CloudFragment ?: CloudFragment()
        agentFragment = fm.findFragmentByTag(TAG_AGENT) as? AiChatFragment ?: AiChatFragment()
        if (!editorFragment.isAdded || !browserFragment.isAdded || !terminalFragment.isAdded ||
            !cloudFragment.isAdded || !agentFragment.isAdded
        ) {
            fm.beginTransaction().apply {
                if (!editorFragment.isAdded) add(R.id.editorContainer, editorFragment, TAG_EDITOR)
                if (!browserFragment.isAdded) add(R.id.browserContainer, browserFragment, TAG_BROWSER)
                if (!terminalFragment.isAdded) add(R.id.terminalContainer, terminalFragment, TAG_TERMINAL)
                if (!cloudFragment.isAdded) add(R.id.cloudContainer, cloudFragment, TAG_CLOUD)
                if (!agentFragment.isAdded) add(R.id.dexContainer, agentFragment, TAG_AGENT)
            }.commit()
        }
        if (!Prefs.agentVisible()) {
            binding.dexContainer.visibility = View.GONE
            binding.splitter.visibility = View.GONE
        }
    }

    private fun setupRail() {
        binding.railFiles.setOnClickListener { toggleFiles() }
        binding.railEditor.setOnClickListener { showPane(Pane.EDITOR) }
        binding.railBrowser.setOnClickListener { showPane(Pane.BROWSER) }
        binding.railTerminal.setOnClickListener { showPane(Pane.TERMINAL) }
        binding.railCloud.setOnClickListener { showPane(Pane.CLOUD) }
        binding.railAgent.setOnClickListener { toggleAgent() }
        binding.btnMenu.setOnClickListener { showMenu(it) }
    }

    fun showPane(target: Pane) {
        pane = target
        Prefs.setLastPane(target.name.lowercase())
        binding.editorContainer.visibility = if (target == Pane.EDITOR) View.VISIBLE else View.GONE
        binding.browserContainer.visibility = if (target == Pane.BROWSER) View.VISIBLE else View.GONE
        binding.terminalContainer.visibility = if (target == Pane.TERMINAL) View.VISIBLE else View.GONE
        binding.cloudContainer.visibility = if (target == Pane.CLOUD) View.VISIBLE else View.GONE
        styleRail()
        if (target != Pane.EDITOR) {
            setContextLabel(
                when (target) {
                    Pane.BROWSER -> browserFragment.currentUrl().ifBlank { "browser" }
                    Pane.TERMINAL -> "linux shell"
                    Pane.CLOUD -> "cloud workspace"
                    else -> ""
                }
            )
        }
    }

    private fun styleRail() {
        val accent = attrColor(R.attr.dexAccent, 0xFFFF5A1F.toInt())
        val muted = attrColor(R.attr.dexMuted, 0xFF8B7C92.toInt())

        fun style(view: TextView, active: Boolean) {
            view.setTextColor(if (active) accent else muted)
            view.setBackgroundResource(if (active) R.drawable.bg_rail_active else R.drawable.bg_rail_item)
        }

        style(binding.railEditor, pane == Pane.EDITOR)
        style(binding.railBrowser, pane == Pane.BROWSER)
        style(binding.railTerminal, pane == Pane.TERMINAL)
        style(binding.railCloud, pane == Pane.CLOUD)
        style(binding.railFiles, binding.sidebar.visibility == View.VISIBLE)
        style(binding.railAgent, binding.dexContainer.visibility == View.VISIBLE)

        if (pane == Pane.EDITOR) setContextLabel(editorFragment.currentName())
    }

    private fun toggleFiles() {
        val show = binding.sidebar.visibility != View.VISIBLE
        binding.sidebar.visibility = if (show) View.VISIBLE else View.GONE
        binding.sidebarRule.visibility = if (show) View.VISIBLE else View.GONE
        styleRail()
    }

    private fun toggleAgent() {
        val show = binding.dexContainer.visibility != View.VISIBLE
        binding.dexContainer.visibility = if (show) View.VISIBLE else View.GONE
        binding.splitter.visibility = if (show) View.VISIBLE else View.GONE
        Prefs.setAgentVisible(show)
        styleRail()
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Settings")
        popup.menu.add(0, 2, 1, "Files panel")
        popup.menu.add(0, 3, 2, "Agent panel")
        popup.menu.add(0, 4, 3, "Restart Linux shell")
        popup.menu.add(0, 5, 4, "About DEX")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, SettingsActivity::class.java))
                2 -> toggleFiles()
                3 -> toggleAgent()
                4 -> terminalFragment.restartShell()
                5 -> about()
            }
            true
        }
        popup.show()
    }

    private fun about() {
        val installed = LinuxEnv.isInstalled(this)
        val target = LinuxEnv.target()
        AlertDialog.Builder(this)
            .setTitle("DEX")
            .setMessage(
                buildString {
                    appendLine("agentic android ide")
                    appendLine("version " + (try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }))
                    appendLine()
                    appendLine("linux: " + if (installed) "alpine ${target?.alpineArch ?: ""} via proot" else "not provisioned")
                    appendLine("device abi: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}")
                }
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------------------------------------------------------------- status

    fun setContextLabel(label: String) {
        binding.tvContext.text = if (label.isBlank()) "no file" else label
    }

    fun setAgentBusy(busy: Boolean) {
        agentBusy = busy
        binding.tvAgentState.text = if (busy) "THINKING" else "IDLE"
        binding.tvAgentState.setTextColor(
            if (busy) attrColor(R.attr.dexAccent, 0xFFFF5A1F.toInt())
            else attrColor(R.attr.dexMuted, 0xFF8B7C92.toInt())
        )
        pulseAnimator?.cancel()
        pulseAnimator = null
        if (busy) {
            pulseAnimator = ObjectAnimator.ofFloat(binding.pulseDot, "alpha", 1f, 0.15f).apply {
                duration = 620
                repeatMode = ObjectAnimator.REVERSE
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        } else {
            binding.pulseDot.alpha = 0.3f
        }
    }

    private fun attrColor(attr: Int, fallback: Int): Int {
        val ta = theme.obtainStyledAttributes(intArrayOf(attr))
        return try { ta.getColor(0, fallback) } finally { ta.recycle() }
    }

    // ---------------------------------------------------------------- files

    private fun setupSidebar() {
        fileAdapter = FileAdapter(files) { file ->
            if (file.isDirectory) loadDir(file) else openFileInEditor(file)
        }
        binding.fileRecycler.layoutManager = LinearLayoutManager(this)
        binding.fileRecycler.adapter = fileAdapter
        binding.etFileSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFileFilter() }
            override fun afterTextChanged(s: android.text.Editable?) { }
        })
        binding.btnOpenFolder.setOnClickListener { openFolderLauncher.launch(null) }
        binding.btnNewFile.setOnClickListener { newFile() }
        binding.btnRecent.setOnClickListener { showRecent() }
    }

    private fun setupSplitter() {
        val splitter = binding.splitter
        var initialX = 0f
        var initialDexWidth = 0
        splitter.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = event.rawX
                    initialDexWidth = binding.dexContainer.width
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val delta = (initialX - event.rawX).toInt()
                    val newWidth = (initialDexWidth + delta).coerceIn(200, binding.root.width - 240)
                    val lp = binding.dexContainer.layoutParams as LinearLayout.LayoutParams
                    lp.width = newWidth
                    lp.weight = 0f
                    binding.dexContainer.layoutParams = lp
                    true
                }
                else -> false
            }
        }
    }

    private fun loadDir(dir: File) {
        currentDir = dir
        Prefs.setLastFolder(dir.absolutePath)
        allFiles.clear()
        dir.parentFile?.let { allFiles.add(it) }
        allFiles.addAll(FileManager.listDir(dir))
        applyFileFilter()
    }

    private fun applyFileFilter() {
        val q = binding.etFileSearch.text?.toString()?.trim()?.lowercase() ?: ""
        files.clear()
        for (f in allFiles) {
            if (q.isEmpty() || f.name.lowercase().contains(q)) files.add(f)
        }
        fileAdapter.notifyDataSetChanged()
    }

    private fun newFile() {
        val dir = currentDir ?: Environment.getExternalStorageDirectory()
        var f = File(dir, "untitled.txt")
        var i = 1
        while (f.exists()) f = File(dir, "untitled${i++}.txt")
        try {
            f.writeText("")
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot create file here: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }
        loadDir(dir)
        openFileInEditor(f)
    }

    private fun showRecent() {
        allFiles.clear()
        for (p in Prefs.recentFiles()) allFiles.add(File(p))
        applyFileFilter()
    }

    private fun requestStoragePermissionIfNeeded() {
        val needsLegacy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            applicationInfo.targetSdkVersion >= Build.VERSION_CODES.R
        if (needsLegacy) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (_: Exception) { }
                return
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                100
            )
        }
    }

    // ------------------------------------------------------------- AgentHost

    override fun openInEditor(path: String) {
        runOnUiThread {
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(this, "Not found: $path", Toast.LENGTH_SHORT).show()
                return@runOnUiThread
            }
            openFileInEditor(file)
        }
    }

    override fun applyToEditor(text: String) {
        runOnUiThread {
            showPane(Pane.EDITOR)
            editorFragment.applyText(text)
        }
    }

    override fun openBrowser(url: String) {
        runOnUiThread {
            showPane(Pane.BROWSER)
            browserFragment.loadUrlSmart(url)
        }
    }

    override fun browserConsoleLog(): String {
        val lines = BrowserConsole.snapshot()
        if (lines.isEmpty()) return "(browser console is empty)"
        return lines.takeLast(80).joinToString("\n")
    }

    fun openFileInEditor(file: File) {
        showPane(Pane.EDITOR)
        editorFragment.openFile(file)
    }

    class FileAdapter(private val items: List<File>, private val onClick: (File) -> Unit) :
        RecyclerView.Adapter<FileAdapter.VH>() {

        class VH(val root: View, val chip: TextView, val name: TextView, val meta: TextView) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val root = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
            return VH(
                root,
                root.findViewById(R.id.fileChip),
                root.findViewById(R.id.fileName),
                root.findViewById(R.id.fileMeta)
            )
        }

        override fun getItemCount() = items.size

        private fun chipFor(f: File): Pair<String, Int> {
            if (f.isDirectory) return "DIR" to 0xFFFFB86B.toInt()
            val ext = f.extension.lowercase()
            val label = when (ext) {
                "kt", "kts" -> "KT"
                "java" -> "JV"
                "py" -> "PY"
                "js", "jsx", "mjs" -> "JS"
                "ts", "tsx" -> "TS"
                "html", "htm" -> "HT"
                "css", "scss" -> "CS"
                "json" -> "JN"
                "xml" -> "XM"
                "md" -> "MD"
                "sh", "bash" -> "SH"
                "c", "h" -> "C"
                "cpp", "cc", "hpp" -> "C+"
                "go" -> "GO"
                "rs" -> "RS"
                "gradle" -> "GR"
                "yml", "yaml" -> "YM"
                "png", "jpg", "jpeg", "webp" -> "IM"
                else -> if (ext.isBlank()) "··" else ext.take(2).uppercase()
            }
            val colour = when (ext) {
                "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "go", "rs", "c", "h", "cpp", "cc", "hpp" -> 0xFFFF7A45.toInt()
                "html", "htm", "css", "scss", "xml", "svg", "md" -> 0xFFFFC24B.toInt()
                "json", "yml", "yaml", "toml", "gradle" -> 0xFF8FB6FF.toInt()
                else -> 0xFF8B7C92.toInt()
            }
            return label to colour
        }

        private fun humanSize(bytes: Long): String = when {
            bytes >= 1024 * 1024 -> "%.1fM".format(bytes / 1024.0 / 1024.0)
            bytes >= 1024 -> "%.0fK".format(bytes / 1024.0)
            else -> "${bytes}B"
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = items[position]
            val (label, colour) = chipFor(f)
            holder.chip.text = label
            holder.chip.setTextColor(colour)
            holder.name.text = f.name
            holder.meta.text = if (f.isDirectory) "dir" else humanSize(f.length())
            holder.root.setOnClickListener { onClick(f) }
        }
    }
}
