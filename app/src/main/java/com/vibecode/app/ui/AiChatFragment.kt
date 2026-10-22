package com.vibecode.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.vibecode.app.ai.AiAgent
import com.vibecode.app.ai.ChatMessage
import com.vibecode.app.databinding.FragmentAiBinding
import com.vibecode.app.util.CurrentFile

class AiChatFragment : Fragment() {
    private var _binding: FragmentAiBinding? = null
    private val binding get() = _binding!!
    private val messages = mutableListOf<ChatMessage>()
    private val display = mutableListOf<String>()
    private lateinit var adapter: MsgAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = MsgAdapter(display)
        binding.chatRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.chatRecycler.adapter = adapter

        binding.btnSend.setOnClickListener {
            val text = binding.inputMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            binding.inputMessage.setText("")
            messages.add(ChatMessage("user", text))
            display.add("You: $text")
            adapter.notifyItemInserted(display.size - 1)
            binding.chatRecycler.scrollToPosition(display.size - 1)

            // build full message list with system context
            val sys = buildSystem()
            val full = mutableListOf<ChatMessage>()
            if (sys.isNotBlank()) full.add(ChatMessage("system", sys))
            full.addAll(messages)

            display.add("Assistant: ")
            val idx = display.size - 1
            adapter.notifyItemInserted(idx)

            val sb = StringBuilder()
            AiAgent.chat(full, { token ->
                activity?.runOnUiThread {
                    sb.append(token)
                    display[idx] = "Assistant: $sb"
                    adapter.notifyItemChanged(idx)
                    binding.chatRecycler.scrollToPosition(idx)
                }
            }, {
                activity?.runOnUiThread {
                    messages.add(ChatMessage("assistant", sb.toString()))
                }
            }, { err ->
                activity?.runOnUiThread {
                    display[idx] = "Error: $err"
                    adapter.notifyItemChanged(idx)
                }
            })
        }
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

    class MsgAdapter(private val items: List<String>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<MsgAdapter.VH>() {
        class VH(val tv: android.widget.TextView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(tv)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = LayoutInflater.from(parent.context).inflate(com.vibecode.app.R.layout.item_message, parent, false) as android.widget.TextView
            return VH(tv)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.tv.text = items[position]
        }
    }
}
