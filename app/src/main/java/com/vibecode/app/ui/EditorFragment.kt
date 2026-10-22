package com.vibecode.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.vibecode.app.databinding.FragmentEditorBinding
import com.vibecode.app.editor.CodeEditorView
import com.vibecode.app.fs.FileManager
import com.vibecode.app.util.Prefs
import java.io.File


class EditorFragment : Fragment() {
    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!
    private var currentFile: File? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnSave.setOnClickListener { save() }
        binding.btnFind.setOnClickListener { findNext() }
        binding.btnReplace.setOnClickListener { replaceOne() }
        binding.btnGoto.setOnClickListener { gotoLine() }
        binding.editor.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updateStatus() }
        })
    }

    fun openFile(file: File) {
        currentFile = file
        binding.fileTitle.text = file.name
        val content = FileManager.readText(file)
        binding.editor.setText(content)
        binding.editor.setLanguage(FileManager.languageFor(file))
        com.vibecode.app.util.CurrentFile.path = file.absolutePath
        com.vibecode.app.util.CurrentFile.content = content
        Prefs.addRecentFile(file.absolutePath)
        updateStatus()
    }

    fun applyText(text: String) {
        binding.editor.setText(text)
        com.vibecode.app.util.CurrentFile.content = text
    }

    private fun save() {
        val f = currentFile ?: return
        val text = binding.editor.text?.toString() ?: ""
        val ok = FileManager.writeText(f, text)
        com.vibecode.app.util.CurrentFile.content = text
        Toast.makeText(requireContext(), if (ok) "Saved" else "Error saving", Toast.LENGTH_SHORT).show()
    }

    private fun findNext() {
        val q = binding.etSearch.text.toString()
        if (q.isEmpty()) return
        val ed = binding.editor
        val text = ed.text?.toString() ?: return
        val start = (ed.selectionEnd).coerceAtLeast(0)
        val idx = text.indexOf(q, start)
        val found = if (idx >= 0) idx else text.indexOf(q)
        if (found >= 0) {
            ed.setSelection(found, found + q.length)
            ed.requestFocus()
        } else {
            Toast.makeText(requireContext(), "Not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun replaceOne() {
        val q = binding.etSearch.text.toString()
        val r = binding.etReplace.text.toString()
        if (q.isEmpty()) return
        val ed = binding.editor
        val s = ed.selectionStart
        val e = ed.selectionEnd
        if (s >= 0 && e > s && ed.text?.substring(s, e) == q) {
            ed.text?.replace(s, e, r)
        } else {
            findNext()
            val s2 = ed.selectionStart
            val e2 = ed.selectionEnd
            if (s2 >= 0 && e2 > s2 && ed.text?.substring(s2, e2) == q) {
                ed.text?.replace(s2, e2, r)
            }
        }
    }

    private fun gotoLine() {
        val n = binding.etGoto.text.toString().toIntOrNull() ?: return
        val ed = binding.editor
        val text = ed.text?.toString() ?: return
        val lines = text.split('\n')
        if (n < 1 || n > lines.size) return
        var pos = 0
        for (i in 0 until n - 1) pos += lines[i].length + 1
        ed.setSelection(pos)
        ed.requestFocus()
    }

    private fun updateStatus() {
        val ed = binding.editor
        val text = ed.text?.toString() ?: ""
        val pos = ed.selectionStart.coerceAtLeast(0)
        val before = text.take(pos)
        val line = before.count { it == '\n' } + 1
        val col = pos - (before.lastIndexOf('\n') + 1) + 1
        binding.statusBar.text = "${FileManager.languageFor(currentFile ?: java.io.File(""))}  |  Ln $line, Col $col  |  Tab ${Prefs.tabSize()}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
