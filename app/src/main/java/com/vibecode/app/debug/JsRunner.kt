package com.vibecode.app.debug

import android.content.Context
import android.webkit.WebView

object JsRunner {
    fun run(context: Context, code: String, onResult: (String) -> Unit) {
        val wv = WebView(context)
        val sb = StringBuilder()
        wv.settings.javaScriptEnabled = true
        wv.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun log(msg: String) { sb.append(msg).append("\n") }
        }, "DEX")
        // inject console.log override
        val wrapped = "try { console.log = function(){ DEX.log(Array.prototype.slice.call(arguments).join(' ')); }; $code } catch(e){ DEX.log('Error: '+e); }"
        wv.evaluateJavascript(wrapped) { _ ->
            onResult(sb.toString().ifBlank { "(no output)" })
        }
    }
}