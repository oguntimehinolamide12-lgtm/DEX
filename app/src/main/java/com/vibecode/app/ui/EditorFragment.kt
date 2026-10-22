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
    }

    fun openFile(file: File) {
        currentFile = file
        binding.fileTitle.text = file.name
        val content = FileManager.readText(file)
        binding.editor.setText(content)
        binding.editor.setLanguage(FileManager.languageFor(file))
        com.vibecode.app.util.CurrentFile.path = file.absolutePath
        com.vibecode.app.util.CurrentFile.content = content
    }

    private fun save() {
        val f = currentFile ?: return
        val text = binding.editor.text?.toString() ?: ""
        val ok = FileManager.writeText(f, text)
        com.vibecode.app.util.CurrentFile.content = text
        Toast.makeText(requireContext(), if (ok) "Saved" else "Error saving", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
