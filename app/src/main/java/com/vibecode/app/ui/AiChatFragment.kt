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

class AiChatFragment : Fragment() {
    private var _binding: FragmentAiBinding? = null
    private val binding get() = _binding!!
    private val messages = mutableListOf<ChatMessage>()
    private val chat = mutableListOf<Pair<String, String>>() // role, text
    private lateinit var adapter: MsgAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = MsgAdapter(chat)
        binding.chatRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.chatRecycler.adapter = adapter

        binding.btnSend.setOnClickListener { send() }
        binding.inputMessage.setOnEditorActionListener { _, _, _ -> send(); true }
    }

    private fun send() {
        val text = binding.inputMessage.text.toString().trim()
        if (text.isEmpty()) return
        binding.inputMessage.setText("")
        messages.add(ChatMessage("user", text))
        chat.add("user" to text)
        adapter.notifyItemInserted(chat.size - 1)
        binding.chatRecycler.scrollToPosition(chat.size - 1)

        val sys = buildSystem()
        val full = mutableListOf<ChatMessage>()
        if (sys.isNotBlank()) full.add(ChatMessage("system", sys))
        full.addAll(messages)

        chat.add("assistant" to "")
        val idx = chat.size - 1
        adapter.notifyItemInserted(idx)

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
                messages.add(ChatMessage("assistant", sb.toString()))
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
        if (cf.content.isBlank()) return "You are a helpful coding assistant inside a mobile code editor. Answer concisely."
        return "You are a helpful coding assistant inside a mobile code editor. The user is editing file: ${cf.path}. Current file content:\n```\n${cf.content.take(8000)}\n```\nAnswer concisely and provide code when useful."
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class MsgAdapter(private val items: List<Pair<String, String>>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<MsgAdapter.VH>() {
        class VH(val root: android.view.View, val tv: android.widget.TextView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val root = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
            val tv = root.findViewById<TextView>(R.id.messageText)
            return VH(root, tv)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val (role, text) = items[position]
            holder.tv.text = text.ifEmpty { "…" }
            val lp = holder.tv.layoutParams as? android.widget.LinearLayout.LayoutParams
            if (role == "user") {
                holder.tv.setBackgroundResource(R.drawable.bubble_user)
                holder.tv.gravity = Gravity.START
                (holder.root as? android.widget.LinearLayout)?.gravity = Gravity.END
            } else {
                holder.tv.setBackgroundResource(R.drawable.bubble_assistant)
                holder.tv.gravity = Gravity.START
                (holder.root as? android.widget.LinearLayout)?.gravity = Gravity.START
            }
        }
    }
}
