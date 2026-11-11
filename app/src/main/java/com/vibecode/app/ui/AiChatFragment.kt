package com.vibecode.app.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vibecode.app.R
import com.vibecode.app.ai.AgentHost
import com.vibecode.app.ai.AgentPrompt
import com.vibecode.app.ai.AgentSession
import com.vibecode.app.ai.ChatMessage
import com.vibecode.app.databinding.FragmentAiBinding

class AiChatFragment : Fragment() {

    companion object {
        private val LOGO = """
              ____  _____  __  __
             |  _ \| ____| \ \/ /
             | | | |  _|    \  /
             | |_| | |___   /  \
             |____/|_____| /_/\_\

            Hi, I'm DEX — your coding buddy.
            I can read, edit and run your files, and drive the browser.
        """.trimIndent()
    }

    private var _binding: FragmentAiBinding? = null
    private val binding get() = _binding!!
    private val messages = mutableListOf<ChatMessage>()
    private val chat = mutableListOf<Pair<String, String>>()
    private lateinit var adapter: MsgAdapter
    private var activeIndex = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = MsgAdapter(chat) { text -> applyToEditor(text) }
        binding.chatRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.chatRecycler.adapter = adapter

        if (chat.isEmpty()) {
            chat.add("assistant" to LOGO)
            adapter.notifyItemInserted(0)
        }

        binding.btnSend.setOnClickListener { send() }
        binding.btnClear.setOnClickListener { clearChat() }
        binding.btnStop.setOnClickListener { AgentSession.cancel() }
        binding.inputMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                send(); true
            } else false
        }
        syncRunningState()
    }

    private fun syncRunningState() {
        val running = AgentSession.isRunning()
        binding.btnSend.isEnabled = !running
        binding.btnStop.visibility = if (running) View.VISIBLE else View.GONE
        binding.inputMessage.isEnabled = true
    }

    private fun send() {
        if (AgentSession.isRunning()) {
            toast("DEX is still working — stop it first")
            return
        }
        val text = binding.inputMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        binding.inputMessage.setText("")
        messages.add(ChatMessage("user", text))
        chat.add("user" to text)
        adapter.notifyItemInserted(chat.size - 1)
        scrollToEnd()
        activeIndex = -1
        runAgent()
    }

    private fun runAgent() {
        val host = activity as? AgentHost
        val appContext = context?.applicationContext
        binding.btnSend.isEnabled = false
        binding.btnStop.visibility = View.VISIBLE
        (activity as? com.vibecode.app.MainActivity)?.setAgentBusy(true)

        AgentSession.start(messages, AgentPrompt.build(), host, appContext, object : AgentSession.Listener {
            override fun onDelta(text: String) = post { showStreaming(text) }

            override fun onAssistantDone(text: String) = post { finalizeAssistant(text) }

            override fun onToolStart(name: String, summary: String) = post {
                val detail = if (summary.isBlank()) name else "$name  $summary"
                addLine("tool", "⚙  $detail")
            }

            override fun onToolResult(name: String, summary: String, ok: Boolean, output: String) = post {
                if (ok) {
                    addLine("tool", "   ↳ ok")
                } else {
                    val first = output.lineSequence().firstOrNull { it.isNotBlank() } ?: "failed"
                    addLine("error", "✗  $name: ${first.take(160)}")
                }
            }

            override fun onError(message: String) = post {
                activeIndex = -1
                addLine("error", "✗  $message")
                (activity as? com.vibecode.app.MainActivity)?.setAgentBusy(false)
                syncRunningState()
            }

            override fun onFinished(stopped: Boolean) = post {
                activeIndex = -1
                if (stopped) addLine("tool", "⏹  stopped")
                (activity as? com.vibecode.app.MainActivity)?.setAgentBusy(false)
                syncRunningState()
            }
        })
    }

    private fun showStreaming(raw: String) {
        val text = visibleText(raw)
        if (text.isBlank() && activeIndex < 0) return
        if (activeIndex < 0) {
            chat.add("assistant" to text)
            activeIndex = chat.size - 1
            adapter.notifyItemInserted(activeIndex)
        } else {
            chat[activeIndex] = "assistant" to text
            adapter.notifyItemChanged(activeIndex)
        }
        scrollToEnd()
    }

    private fun finalizeAssistant(raw: String) {
        val text = visibleText(raw)
        if (text.isBlank()) {
            if (activeIndex >= 0) {
                chat.removeAt(activeIndex)
                adapter.notifyItemRemoved(activeIndex)
                activeIndex = -1
            }
            return
        }
        if (activeIndex < 0) {
            chat.add("assistant" to text)
            activeIndex = chat.size - 1
            adapter.notifyItemInserted(activeIndex)
        } else {
            chat[activeIndex] = "assistant" to text
            adapter.notifyItemChanged(activeIndex)
        }
        activeIndex = -1
        scrollToEnd()
    }

    private fun addLine(role: String, text: String) {
        chat.add(role to text)
        adapter.notifyItemInserted(chat.size - 1)
        scrollToEnd()
    }

    private fun scrollToEnd() {
        if (chat.isNotEmpty()) binding.chatRecycler.scrollToPosition(chat.size - 1)
    }

    /** Tool JSON is plumbing, not conversation: hide it from the transcript. */
    private fun visibleText(raw: String): String =
        raw.replace(Regex("<tool>[\\s\\S]*?</tool>", RegexOption.IGNORE_CASE), "").trim()

    private fun clearChat() {
        AgentSession.cancel()
        messages.clear()
        chat.clear()
        adapter.notifyDataSetChanged()
        activeIndex = -1
        chat.add("assistant" to LOGO)
        adapter.notifyItemInserted(0)
        syncRunningState()
    }

    private fun applyToEditor(text: String) {
        val code = extractCode(text)
        val host = activity as? AgentHost
        if (host == null) {
            toast("Editor is not available")
            return
        }
        host.applyToEditor(code)
        toast("Applied to editor")
    }

    private fun extractCode(text: String): String {
        val fence = Regex("```[a-zA-Z0-9+#-]*\\n([\\s\\S]*?)```")
        return fence.find(text)?.groupValues?.get(1)?.trimEnd() ?: text
    }

    private fun post(block: () -> Unit) {
        val act = activity ?: return
        act.runOnUiThread {
            if (isAdded && _binding != null) block()
        }
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class MsgAdapter(private val items: List<Pair<String, String>>, private val onApply: (String) -> Unit) :
        RecyclerView.Adapter<MsgAdapter.VH>() {
        class VH(val root: View, val tv: TextView, val btn: Button) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val root = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
            val tv = root.findViewById<TextView>(R.id.messageText)
            val btn = root.findViewById<Button>(R.id.btnApply)
            return VH(root, tv, btn)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (role, text) = items[position]
            val context = holder.root.context
            holder.tv.text = text.ifEmpty { "…" }
            holder.tv.textSize = if (role == "tool" || role == "error") 11f else 14f
            val layout = holder.root as? LinearLayout
            when (role) {
                "user" -> {
                    holder.tv.setBackgroundResource(R.drawable.bg_user)
                    holder.tv.setTextColor(ContextCompat.getColor(context, R.color.bone))
                    holder.tv.gravity = Gravity.START
                    layout?.gravity = Gravity.END
                    holder.btn.visibility = View.GONE
                }
                "tool" -> {
                    holder.tv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    holder.tv.setTextColor(ContextCompat.getColor(context, R.color.line_number))
                    holder.tv.gravity = Gravity.START
                    layout?.gravity = Gravity.START
                    holder.btn.visibility = View.GONE
                }
                "error" -> {
                    holder.tv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    holder.tv.setTextColor(ContextCompat.getColor(context, R.color.magma))
                    holder.tv.gravity = Gravity.START
                    layout?.gravity = Gravity.START
                    holder.btn.visibility = View.GONE
                }
                else -> {
                    holder.tv.setBackgroundResource(R.drawable.bg_assistant)
                    holder.tv.setTextColor(ContextCompat.getColor(context, R.color.bone))
                    holder.tv.gravity = Gravity.START
                    layout?.gravity = Gravity.START
                    val hasCode = text.contains("```")
                    holder.btn.visibility = if (hasCode) View.VISIBLE else View.GONE
                    if (hasCode) holder.btn.setOnClickListener { onApply(text) }
                }
            }
        }
    }
}
