package com.vibecode.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.vibecode.app.databinding.ActivityMainBinding
import com.vibecode.app.fs.FileManager
import com.vibecode.app.ui.AiChatFragment
import com.vibecode.app.ui.EditorFragment
import com.vibecode.app.util.Prefs
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val files = mutableListOf<File>()
    private lateinit var fileAdapter: FileAdapter
    private var currentDir: File? = null

    private val openFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) { }
            Prefs.setLastFolder(uri.toString())
            Toast.makeText(this, "Opened: $uri", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Prefs.init(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.open() }

        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = if (pos == 0) "Editor" else "AI Agent"
        }.attach()

        fileAdapter = FileAdapter(files) { file ->
            binding.drawerLayout.closeDrawers()
            if (file.isDirectory) loadDir(file) else (adapter.getFragment(0) as? EditorFragment)?.openFile(file)
        }
        binding.fileRecycler.layoutManager = LinearLayoutManager(this)
        binding.fileRecycler.adapter = fileAdapter

        binding.btnOpenFolder.setOnClickListener { openFolderLauncher.launch(null) }
        binding.btnNewFile.setOnClickListener { newFile() }

        val last = Prefs.lastFolder()
        if (last.isEmpty()) {
            try { loadDir(Environment.getExternalStorageDirectory()) } catch (_: Exception) {
                try { loadDir(filesDir) } catch (_: Exception) { }
            }
        }

        requestStoragePermissionIfNeeded()
    }

    private fun loadDir(dir: File) {
        currentDir = dir
        files.clear()
        if (dir.parentFile != null) files.add(dir.parentFile!!)
        files.addAll(FileManager.listDir(dir))
        fileAdapter.notifyDataSetChanged()
    }
    private fun newFile() {
        val dir = currentDir ?: Environment.getExternalStorageDirectory()
        val f = File(dir, "untitled.txt")
        f.writeText("")
        loadDir(dir)
        ((binding.viewPager.adapter as ViewPagerAdapter).getFragment(0) as? EditorFragment)?.openFile(f)
    }

    private fun requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (_: Exception) { }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Settings")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    inner class ViewPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        private val fragments = listOf(EditorFragment(), AiChatFragment())
        override fun getItemCount() = fragments.size
        override fun createFragment(position: Int): Fragment = fragments[position]
        fun getFragment(position: Int): Fragment? = fragments.getOrNull(position)
    }

    class FileAdapter(private val items: List<File>, private val onClick: (File) -> Unit) :
        androidx.recyclerview.widget.RecyclerView.Adapter<FileAdapter.VH>() {
        class VH(val tv: android.widget.TextView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(tv)
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val tv = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false) as android.widget.TextView
            return VH(tv)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = items[position]
            holder.tv.text = if (f.isDirectory) "\uD83D\uDCC1 ${f.name}" else "\uD83D\uDCC4 ${f.name}"
            holder.tv.setOnClickListener { onClick(f) }
        }
    }
}

