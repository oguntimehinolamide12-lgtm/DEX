package com.vibecode.app.editor

import android.content.Context
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.vibecode.app.R

object SyntaxHighlighter {
    private val keywordSets = mapOf(
        "kotlin" to setOf("fun","val","var","class","object","interface","if","else","when","for","while","do","return","package","import","as","is","in","this","super","null","true","false","try","catch","finally","throw","break","continue","constructor","init","companion","data","sealed","enum","annotation","suspend","inline","reified","lateinit","by","where","typealias","const","operator","override","open","abstract","final","private","protected","public","internal"),
        "java" to setOf("class","interface","enum","extends","implements","public","private","protected","static","final","abstract","void","int","long","double","float","boolean","char","byte","short","if","else","for","while","do","switch","case","default","break","continue","return","new","this","super","null","true","false","try","catch","finally","throw","throws","import","package","synchronized","volatile","transient"),
        "python" to setOf("def","class","if","elif","else","for","while","try","except","finally","with","as","import","from","return","yield","pass","break","continue","lambda","global","nonlocal","None","True","False","and","or","not","in","is","raise","assert","del"),
        "javascript" to setOf("function","var","let","const","if","else","for","while","do","switch","case","default","break","continue","return","new","this","class","extends","import","export","from","async","await","try","catch","finally","throw","null","true","false","typeof","instanceof"),
        "typescript" to setOf("function","var","let","const","if","else","for","while","do","switch","case","default","break","continue","return","new","this","class","extends","import","export","from","async","await","try","catch","finally","throw","null","true","false","typeof","instanceof","interface","type","enum","public","private","protected","readonly"),
        "cpp" to setOf("int","float","double","char","void","bool","long","short","unsigned","signed","if","else","for","while","do","switch","case","default","break","continue","return","new","delete","class","struct","public","private","protected","virtual","override","const","static","namespace","using","template","typename","include","define","nullptr","true","false"),
        "csharp" to setOf("class","struct","interface","enum","public","private","protected","internal","static","void","int","string","bool","double","float","if","else","for","foreach","while","do","switch","case","default","break","continue","return","new","this","base","null","true","false","try","catch","finally","throw","using","namespace","async","await","var"),
        "go" to setOf("func","package","import","var","const","type","struct","interface","if","else","for","range","switch","case","default","break","continue","return","defer","go","select","chan","map","make","new","nil","true","false"),
        "rust" to setOf("fn","let","mut","const","static","struct","enum","trait","impl","if","else","for","while","loop","match","return","break","continue","use","mod","pub","crate","self","super","true","false","None","Some","Ok","Err"),
        "bash" to setOf("if","then","else","elif","fi","for","while","do","done","case","esac","function","return","export","local","readonly","source","echo","exit"),
        "sql" to setOf("SELECT","FROM","WHERE","INSERT","INTO","VALUES","UPDATE","SET","DELETE","CREATE","TABLE","DROP","ALTER","JOIN","LEFT","RIGHT","INNER","OUTER","ON","GROUP","BY","ORDER","HAVING","LIMIT","OFFSET","AND","OR","NOT","NULL","AS","DISTINCT","COUNT","SUM","AVG","MAX","MIN"),
        "groovy" to setOf("def","class","interface","if","else","for","while","switch","case","default","break","continue","return","new","this","super","null","true","false","try","catch","finally","throw","import","package","public","private","protected","static","final","abstract","void"),
    )

    private val typeKeywords = setOf("Int","Long","Double","Float","Boolean","Char","String","List","Map","Set","Array","Any","Unit","Nothing","Object","Class","Throwable","Exception")

    fun forLanguage(lang: String): (Editable, Context) -> Unit {
        val kw = keywordSets[lang] ?: emptySet()
        val ci = lang == "sql"
        return { editable, ctx -> highlight(editable, ctx, kw, ci, lang) }
    }

    private fun highlight(editable: Editable, ctx: Context, keywords: Set<String>, caseInsensitive: Boolean, lang: String) {
        val old = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        for (s in old) editable.removeSpan(s)

        val str = editable.toString()
        val keywordColor = ctx.getColor(R.color.keyword)
        val stringColor = ctx.getColor(R.color.string)
        val commentColor = ctx.getColor(R.color.comment)
        val numberColor = ctx.getColor(R.color.number)
        val typeColor = ctx.getColor(R.color.type)

        val commentRegex = when (lang) {
            "python","bash" -> Regex("#[^\\n]*")
            "sql" -> Regex("--[^\\n]*")
            else -> Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/")
        }
        applyRegex(editable, str, commentRegex, commentColor)
        applyRegex(editable, str, Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'"), stringColor)
        applyRegex(editable, str, Regex("\\b\\d+(\\.\\d+)?\\b"), numberColor)

        if (keywords.isNotEmpty()) {
            val pattern = "\\b(" + keywords.joinToString("|") { Regex.escape(it) } + ")\\b"
            val r = if (caseInsensitive) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
            applyRegex(editable, str, r, keywordColor)
        }
        applyRegex(editable, str, Regex("\\b(" + typeKeywords.joinToString("|") + ")\\b"), typeColor)
    }

    private fun applyRegex(editable: Editable, str: String, regex: Regex, color: Int) {
        for (m in regex.findAll(str)) {
            editable.setSpan(ForegroundColorSpan(color), m.range.first, m.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
