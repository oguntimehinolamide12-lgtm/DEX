package com.vibecode.app.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vibecode.app.R
import com.vibecode.app.util.CurrentFile

class DebugConsoleFragment : Fragment() {
    private val lines = mutableListOf<String>()
    private lateinit var adapter: LogAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_debug, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.logRecycler)
        adapter = LogAdapter(lines)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val spin = view.findViewById<android.widget.Spinner>(R.id.spinLang)
        spin.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, arrayOf("JS", "Shell", "Python")).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        DebugLog.addListener { l ->
            activity?.runOnUiThread {
                lines.add(l); adapter.notifyItemInserted(lines.size - 1)
                recycler.scrollToPosition(lines.size - 1)
            }
        }
        // load existing
        lines.addAll(DebugLog.snapshot())
        adapter.notifyDataSetChanged()

        view.findViewById<android.widget.Button>(R.id.btnClear).setOnClickListener { lines.clear(); DebugLog.clear(); adapter.notifyDataSetChanged() }
        view.findViewById<android.widget.Button>(R.id.btnRun).setOnClickListener {
            val lang = spin.selectedItem.toString()
            val code = CurrentFile.content
            if (code.isBlank()) { DebugLog.log("DEBUG", "No file content to run"); return@setOnClickListener }
            DebugLog.log("DEBUG", "Running as $lang...")
            when (lang) {
                "JS" -> JsRunner.run(requireContext(), code) { out -> DebugLog.log("JS", out) }
                "Shell" -> Thread { DebugLog.log("SHELL", ShellRunner.run(code)) }.start()
                "Python" -> Thread { DebugLog.log("PY", PythonRunner.run(code)) }.start()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        DebugLog.removeListener { }
    }

    class LogAdapter(private val items: List<String>) : RecyclerView.Adapter<LogAdapter.VH>() {
        class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply { textSize = 11f; setPadding(4,2,4,2); setTextColor(0xFFD4D4D4.toInt()) }
            return VH(tv)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) { holder.tv.text = items[position] }
    }
}