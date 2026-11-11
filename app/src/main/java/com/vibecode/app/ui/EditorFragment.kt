package com.vibecode.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.vibecode.app.MainActivity
import com.vibecode.app.databinding.FragmentEditorBinding
import com.vibecode.app.fs.FileManager
import com.vibecode.app.util.CurrentFile
import com.vibecode.app.util.Prefs
import java.io.File

class EditorFragment : Fragment() {
    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!
    private var currentFile: File? = null
    private var dirty = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnSave.setOnClickListener { save() }
        binding.editor.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(s: android.text.Editable?) {
                if (binding.editor.isApplyingProgrammaticChange()) return
                CurrentFile.content = s?.toString() ?: ""
                markDirty(true)
                updateStatus()
            }
        })
    }

    fun currentName(): String = currentFile?.name ?: "no file"

    fun openFile(file: File) {
        currentFile = file
        binding.fileTitle.text = file.name
        val content = FileManager.readText(file)
        binding.editor.setLanguage(FileManager.languageFor(file))
        binding.editor.setText(content)
        CurrentFile.path = file.absolutePath
        CurrentFile.content = content
        Prefs.addRecentFile(file.absolutePath)
        markDirty(false)
        updateStatus()
        (activity as? MainActivity)?.setContextLabel(file.name)
    }

    fun applyText(text: String) {
        binding.editor.setProgrammaticChange(true)
        binding.editor.setText(text)
        binding.editor.setProgrammaticChange(false)
        CurrentFile.content = text
        markDirty(true)
        updateStatus()
    }

    private fun save() {
        val file = currentFile ?: return
        val text = binding.editor.text?.toString() ?: ""
        val ok = FileManager.writeText(file, text)
        CurrentFile.content = text
        if (ok) markDirty(false)
        Toast.makeText(requireContext(), if (ok) "saved" else "write failed", Toast.LENGTH_SHORT).show()
    }

    private fun markDirty(value: Boolean) {
        dirty = value
        binding.dirtyDot.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun updateStatus() {
        val editor = binding.editor
        val text = editor.text?.toString() ?: ""
        val pos = editor.selectionStart.coerceAtLeast(0)
        val before = text.take(pos)
        val line = before.count { it == '\n' } + 1
        val col = pos - (before.lastIndexOf('\n') + 1) + 1
        val language = FileManager.languageFor(currentFile ?: File("")).uppercase()
        binding.statusBar.text = "$language · UTF-8 · TAB ${Prefs.tabSize()}" + if (dirty) " · UNSAVED" else ""
        binding.cursorInfo.text = "LN $line COL $col"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
