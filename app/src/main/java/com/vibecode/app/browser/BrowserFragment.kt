package com.vibecode.app.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.vibecode.app.databinding.FragmentBrowserBinding
import com.vibecode.app.debug.DebugLog
import com.vibecode.app.util.Prefs
import java.net.URLEncoder

/**
 * Embedded browser pane. Loads arbitrary http(s) URLs, local `file://` previews of
 * projects written by the agent, and pipes `console.*` output into [BrowserConsole]
 * so DEX can debug the pages it builds.
 */
class BrowserFragment : Fragment() {

    companion object {
        const val DEFAULT_HOME = "https://developer.mozilla.org"
    }

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!
    private var webView: WebView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val wv = binding.webView
        webView = wv

        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
        }
        wv.setBackgroundColor(0xFF1E1E1E.toInt())

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleExternal(request.url)

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBar.progress = 0
                if (url != null) {
                    binding.etUrl.setText(url)
                    (activity as? com.vibecode.app.MainActivity)?.setContextLabel(url)
                }
                syncNavButtons()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
                if (url != null && !binding.etUrl.hasFocus()) binding.etUrl.setText(url)
                if (url != null) (activity as? com.vibecode.app.MainActivity)?.setContextLabel(url)
                syncNavButtons()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    val desc = error?.description?.toString() ?: "load error"
                    BrowserConsole.log("[ERROR] $desc (${request.url})")
                    binding.progressBar.visibility = View.GONE
                    binding.etUrl.setText(request.url?.toString() ?: "")
                }
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                if (newProgress >= 100) binding.progressBar.visibility = View.GONE
            }

            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                val line = "[${msg.messageLevel()}] ${msg.message()}  (${msg.sourceId()}:${msg.lineNumber()})"
                BrowserConsole.log(line)
                DebugLog.log("WEB", line)
                return true
            }
        }

        binding.btnBack.setOnClickListener { if (wv.canGoBack()) wv.goBack() }
        binding.btnForward.setOnClickListener { if (wv.canGoForward()) wv.goForward() }
        binding.btnReload.setOnClickListener { wv.reload() }
        binding.btnHome.setOnClickListener { loadUrlSmart(Prefs.browserHome().ifBlank { DEFAULT_HOME }) }
        binding.btnStop.setOnClickListener { wv.stopLoading(); binding.progressBar.visibility = View.GONE }

        binding.etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                loadUrlSmart(binding.etUrl.text?.toString() ?: ""); true
            } else false
        }

        if (wv.url == null) wv.loadUrl(Prefs.browserHome().ifBlank { DEFAULT_HOME })
        syncNavButtons()
    }

    /** Converts whatever the user (or the agent) typed into a loadable URL. */
    fun normalize(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return Prefs.browserHome().ifBlank { DEFAULT_HOME }
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("file://") || lower.startsWith("about:") ||
            lower.startsWith("data:") || lower.startsWith("javascript:")
        ) return text
        if (text.startsWith("/")) return "file://$text"
        if (!text.contains(" ") && text.contains(".")) return "https://$text"
        return "https://duckduckgo.com/?q=" + URLEncoder.encode(text, "UTF-8")
    }

    fun loadUrlSmart(input: String) {
        val wv = webView ?: return
        val url = normalize(input)
        BrowserConsole.log("[NAV] $url")
        binding.etUrl.setText(url)
        binding.etUrl.clearFocus()
        wv.loadUrl(url)
    }

    fun canGoBack(): Boolean = webView?.canGoBack() == true
    fun goBack() { webView?.goBack() }
    fun currentUrl(): String = webView?.url ?: ""

    /** Chrome custom tabs / intents for schemes the WebView cannot render. */
    private fun handleExternal(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: ""
        if (scheme.isEmpty() || scheme == "http" || scheme == "https" || scheme == "file" ||
            scheme == "about" || scheme == "data" || scheme == "javascript"
        ) return false
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            true
        }
    }

    private fun syncNavButtons() {
        val wv = webView ?: return
        binding.btnBack.isEnabled = wv.canGoBack()
        binding.btnForward.isEnabled = wv.canGoForward()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView?.let {
            it.stopLoading()
            it.destroy()
        }
        webView = null
        _binding = null
    }
}
