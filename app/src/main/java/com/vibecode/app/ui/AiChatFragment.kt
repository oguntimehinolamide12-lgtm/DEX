package com.vibecode.app.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.vibecode.app.R
import com.vibecode.app.ai.AiAgent
import com.vibecode.app.ai.ChatMessage
import com.vibecode.app.databinding.FragmentAiBinding
import com.vibecode.app.util.CurrentFile
import com.vibecode.app.util.Prefs


class AiChatFragment : Fragment() {
    private var _binding: FragmentAiBinding? = null
    private val binding get() = _binding!!
    private val messages = mutableListOf<ChatMessage>()
    private val chat = mutableListOf<Pair<String, String>>()
    private lateinit var adapter: MsgAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = MsgAdapter(chat) { text -> applyToEditor(text) }
        binding.chatRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.chatRecycler.adapter = adapter

        if (chat.isEmpty()) {
            val logo = """  ____  _____  __  __
 |  _ \| ____| \ \/ /
 | | | |  _|    \  /
 | |_| | |___   /  \
 |____/|_____| /_/\_\
 
 Hi, I'm DEX — your coding buddy. Ask me anything!"""
            chat.add("assistant" to logo)
            adapter.notifyItemInserted(0)
        }

        binding.btnSend.setOnClickListener { send() }
        binding.btnClear.setOnClickListener { clearChat() }
        binding.inputMessage.setOnEditorActionListener { _, _, _ -> send(); true }
    }

    private var toolDepth = 0

    private fun send() {
        val text = binding.inputMessage.text.toString().trim()
        if (text.isEmpty()) return
        binding.inputMessage.setText("")
        messages.add(ChatMessage("user", text))
        chat.add("user" to text)
        adapter.notifyItemInserted(chat.size - 1)
        binding.chatRecycler.scrollToPosition(chat.size - 1)
        toolDepth = 0
        runChat()
    }

    private fun runChat() {
        val sys = buildSystem()
        val full = mutableListOf<ChatMessage>()
        if (sys.isNotBlank()) full.add(ChatMessage("system", sys))
        full.addAll(messages)

        chat.add("assistant" to "")
        val idx = chat.size - 1
        adapter.notifyItemInserted(idx)
        binding.chatRecycler.scrollToPosition(idx)

        val sb = StringBuilder()
        AiAgent.chat(full, { token ->
            activity?.runOnUiThread {
                sb.append(token)
                chat[idx] = "assistant" to sb.toString()
                adapter.notifyItemChanged(idx)
                binding.chatRecycler.scrollToPosition(idx)
            }
        }, {
            activity?.runOnUiThread {
                val reply = sb.toString()
                messages.add(ChatMessage("assistant", reply))
                val m = Regex("\\[\\[READ_FILE:(.+?)\\]\\]").find(reply)
                if (m != null && Prefs.fileAccess() && toolDepth < 3) {
                    toolDepth++
                    val path = m.groupValues[1].trim()
                    val content = try { java.io.File(path).readText().take(8000) } catch (e: Exception) { "Error reading file: ${e.message}" }
                    chat[idx] = "assistant" to "🔧 Read $path"
                    adapter.notifyItemChanged(idx)
                    messages.add(ChatMessage("user", "Tool result for $path:\n```\n$content\n```"))
                    runChat()
                } else {
                    chat[idx] = "assistant" to reply
                    adapter.notifyItemChanged(idx)
                }
            }
        }, { err ->
            activity?.runOnUiThread {
                chat[idx] = "assistant" to "Error: $err"
                adapter.notifyItemChanged(idx)
            }
        })
    }

    private fun buildSystem(): String {
        val cf = CurrentFile
        val base = if (cf.content.isBlank()) "You are DEX, a friendly, witty, and helpful coding assistant inside a mobile code editor. Answer concisely." else "You are DEX, a friendly, witty, and helpful coding assistant inside a mobile code editor. The user is editing file: ${cf.path}. Current file content:\n```\n${cf.content.take(8000)}\n```\nAnswer concisely and provide code when useful."
        if (!Prefs.fileAccess()) return base
        val folder = Prefs.lastFolder()
        if (folder.isBlank()) return base
        val files = try {
            val d = java.io.File(folder)
            if (d.isDirectory) d.listFiles()?.map { it.name }?.take(80) else null
        } catch (_: Exception) { null }
        if (files.isNullOrEmpty()) return base
        return base + "\n\nDevice file access is enabled. Current folder: $folder\nFiles: ${files.joinToString(", ")}\n\nTool: to read a file's contents, output exactly [[READ_FILE:<absolute path>]] on its own line. The app will return the file contents. Use only paths from the listed files or the currently open file."
    }

    private fun clearChat() {
        messages.clear()
        chat.clear()
        adapter.notifyDataSetChanged()
        val logo = """  ____  _____  __  __
 |  _ \| ____| \ \/ /
 | | | |  _|    \  /
 | |_| | |___   /  \
 |____/|_____| /_/\_\
 
 Hi, I'm DEX — your coding buddy. Ask me anything!"""
        chat.add("assistant" to logo)
        adapter.notifyItemInserted(0)
    }

    private fun applyToEditor(text: String) {
        val code = extractCode(text)
        (activity as? com.vibecode.app.MainActivity)?.applyToEditor(code)
        android.widget.Toast.makeText(requireContext(), "Applied to editor", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun extractCode(text: String): String {
        val fence = Regex("```[a-zA-Z0-9]*\\n([\\s\\S]*?)```")
        val m = fence.find(text)
        return m?.groupValues?.get(1)?.trim() ?: text
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class MsgAdapter(private val items: List<Pair<String, String>>, private val onApply: (String) -> Unit) :
        androidx.recyclerview.widget.RecyclerView.Adapter<MsgAdapter.VH>() {
        class VH(val root: android.view.View, val tv: android.widget.TextView, val btn: android.widget.Button) : androidx.recyclerview.widget.RecyclerView.ViewHolder(root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val root = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
            val tv = root.findViewById<TextView>(R.id.messageText)
            val btn = root.findViewById<android.widget.Button>(R.id.btnApply)
            return VH(root, tv, btn)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val (role, text) = items[position]
            holder.tv.text = text.ifEmpty { "…" }
            if (role == "user") {
                holder.tv.setBackgroundResource(R.drawable.bubble_user)
                holder.tv.gravity = Gravity.START
                (holder.root as? android.widget.LinearLayout)?.gravity = Gravity.END
                holder.btn.visibility = View.GONE
            } else {
                holder.tv.setBackgroundResource(R.drawable.bubble_assistant)
                holder.tv.gravity = Gravity.START
                (holder.root as? android.widget.LinearLayout)?.gravity = Gravity.START
                if (text.isNotBlank() && text != "…") {
                    holder.btn.visibility = View.VISIBLE
                    holder.btn.setOnClickListener { onApply(text) }
                } else holder.btn.visibility = View.GONE
            }
        }
    }
}
