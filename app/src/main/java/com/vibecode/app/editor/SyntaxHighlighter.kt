package com.vibecode.app.editor

import android.content.Context
import android.text.Editable
import android.text.style.ForegroundColorSpan
import com.vibecode.app.R

object SyntaxHighlighter {
    private val keywordSets = mapOf(
        "kotlin" to setOf("fun","val","var","class","object","interface","if","else","when","for","while","do","return","package","import","as","is","in","this","super","null","true","false","try","catch","finally","throw","break","continue","constructor","init","companion","data","sealed","enum","annotation","suspend","inline","reified","lateinit","by","where","typealias","const","operator","override","open","abstract","final","private","protected","public","internal"),
        "java" to setOf("class","interface","enum","extends","implements","public","private","protected","static","final","abstract","void","int","long","double","float","boolean","char","byte","short","if","else","for","while","do","switch","case","default","break","continue","return","new","this","super","null","true","false","try","catch","finally","throw","throws","import","package","synchronized","volatile","transient","record","var","yield"),
        "python" to setOf("def","class","if","elif","else","for","while","try","except","finally","with","as","import","from","return","yield","pass","break","continue","lambda","global","nonlocal","None","True","False","and","or","not","in","is","raise","assert","del","async","await","match","case"),
        "javascript" to setOf("function","var","let","const","if","else","for","while","do","switch","case","default","break","continue","return","new","this","class","extends","import","export","from","async","await","try","catch","finally","throw","null","true","false","typeof","instanceof","yield","of","in"),
        "typescript" to setOf("function","var","let","const","if","else","for","while","do","switch","case","default","break","continue","return","new","this","class","extends","import","export","from","async","await","try","catch","finally","throw","null","true","false","typeof","instanceof","interface","type","enum","public","private","protected","readonly","implements","namespace","abstract","as","is","keyof","infer"),
        "cpp" to setOf("int","float","double","char","void","bool","long","short","unsigned","signed","if","else","for","while","do","switch","case","default","break","continue","return","new","delete","class","struct","public","private","protected","virtual","override","const","static","namespace","using","template","typename","include","define","nullptr","true","false","auto","constexpr","noexcept","friend","operator","this"),
        "c" to setOf("int","float","double","char","void","if","else","for","while","do","switch","case","default","break","continue","return","struct","union","enum","typedef","const","static","extern","volatile","sizeof","NULL","true","false","include","define"),
        "csharp" to setOf("class","struct","interface","enum","public","private","protected","internal","static","void","int","string","bool","double","float","if","else","for","foreach","while","do","switch","case","default","break","continue","return","new","this","base","null","true","false","try","catch","finally","throw","using","namespace","async","await","var","record","get","set","value","partial","sealed","abstract","override","virtual"),
        "go" to setOf("func","package","import","var","const","type","struct","interface","if","else","for","range","switch","case","default","break","continue","return","defer","go","select","chan","map","make","new","nil","true","false","fallthrough","goto"),
        "rust" to setOf("fn","let","mut","const","static","struct","enum","trait","impl","if","else","for","while","loop","match","return","break","continue","use","mod","pub","crate","self","super","true","false","None","Some","Ok","Err","async","await","where","move","ref","unsafe","extern","dyn"),
        "bash" to setOf("if","then","else","elif","fi","for","while","do","done","case","esac","function","return","export","local","readonly","source","echo","exit","in","select","until","time"),
        "sql" to setOf("SELECT","FROM","WHERE","INSERT","INTO","VALUES","UPDATE","SET","DELETE","CREATE","TABLE","DROP","ALTER","JOIN","LEFT","RIGHT","INNER","OUTER","ON","GROUP","BY","ORDER","HAVING","LIMIT","OFFSET","AND","OR","NOT","NULL","AS","DISTINCT","COUNT","SUM","AVG","MAX","MIN","CASE","WHEN","THEN","ELSE","END","UNION","ALL","EXISTS","BETWEEN","LIKE","IN","IS","PRIMARY","KEY","FOREIGN","REFERENCES","INDEX","VIEW"),
        "groovy" to setOf("def","class","interface","if","else","for","while","switch","case","default","break","continue","return","new","this","super","null","true","false","try","catch","finally","throw","import","package","public","private","protected","static","final","abstract","void","trait","enum"),
        "ruby" to setOf("def","class","module","if","elsif","else","unless","for","while","until","do","end","begin","rescue","ensure","raise","return","yield","super","self","nil","true","false","and","or","not","in","require","include","attr_accessor","attr_reader","attr_writer","lambda","proc"),
        "php" to setOf("function","class","interface","trait","extends","implements","public","private","protected","static","final","abstract","if","else","elseif","for","foreach","while","do","switch","case","default","break","continue","return","new","this","null","true","false","use","namespace","as","try","catch","finally","throw","echo","print","array","isset","unset","empty","require","include","var"),
        "swift" to setOf("func","class","struct","enum","protocol","extension","if","else","for","while","repeat","switch","case","default","break","continue","return","guard","let","var","const","import","nil","true","false","self","super","init","deinit","throws","try","catch","defer","in","where","as","is","typealias","associatedtype","public","private","internal","fileprivate","open","static","mutating","nonmutating","lazy","weak","unowned","optional","required","override","convenience"),
        "dart" to setOf("void","class","enum","extends","implements","with","mixin","if","else","for","while","do","switch","case","default","break","continue","return","new","this","super","null","true","false","var","final","const","late","dynamic","async","await","yield","import","export","library","part","typedef","get","set","factory","static","abstract","external","operator","is","as","in","covariant"),
        "scala" to setOf("class","object","trait","extends","with","def","val","var","if","else","for","while","do","match","case","return","new","this","super","null","true","false","import","package","try","catch","finally","throw","yield","forSome","type","implicit","lazy","sealed","abstract","final","private","protected","override","macro"),
        "haskell" to setOf("module","import","where","let","in","if","then","else","case","of","data","type","newtype","class","instance","deriving","do","return","lambda","forall","infix","infixl","infixr","default","foreign"),
        "lua" to setOf("function","local","if","then","elseif","else","end","for","while","do","repeat","until","in","return","break","and","or","not","nil","true","false","goto"),
        "perl" to setOf("my","our","use","package","sub","if","elsif","else","unless","for","foreach","while","until","do","return","last","next","redo","goto","and","or","not","xor","eq","ne","lt","gt","le","ge","cmp","print","say","chomp","defined","undef","scalar","ref","bless","new"),
        "r" to setOf("function","if","else","for","while","repeat","in","next","break","return","TRUE","FALSE","NULL","NA","NaN","Inf","library","require","source","c","list","data.frame","matrix","array","factor","as.numeric","as.character","as.logical","print","cat","paste","sprintf"),
        "julia" to setOf("function","end","if","elseif","else","for","while","try","catch","finally","return","break","continue","local","global","const","struct","mutable","abstract","primitive","type","module","import","using","export","macro","quote","begin","do","let","where","true","false","nothing","missing"),
        "elixir" to setOf("def","defp","defmodule","defmacro","defstruct","do","end","if","else","unless","case","cond","for","with","fn","->","when","try","rescue","catch","after","raise","import","require","alias","use","true","false","nil","and","or","not","in"),
        "clojure" to setOf("def","defn","defmacro","let","if","when","cond","case","do","loop","recur","fn","quote","ns","require","use","import","true","false","nil","and","or","not"),
        "objc" to setOf("interface","implementation","end","protocol","optional","required","property","synthesize","dynamic","import","include","if","else","for","while","do","switch","case","default","break","continue","return","nil","NULL","YES","NO","true","false","self","super","class","struct","enum","typedef","void","int","float","double","char","id","BOOL"),
        "vb" to setOf("Sub","Function","Class","Module","If","Then","Else","ElseIf","End","For","Next","While","Wend","Do","Loop","Select","Case","Return","Dim","As","New","Public","Private","Protected","Friend","Shared","Static","Const","Try","Catch","Finally","Throw","Imports","Namespace","Module","Property","Get","Set","ByVal","ByRef","Optional","True","False","Nothing","Me"),
        "powershell" to setOf("function","param","if","elseif","else","foreach","for","while","do","until","switch","break","continue","return","try","catch","finally","throw","trap","begin","process","end","filter","in","true","false","null"),
        "markup" to setOf("html","head","body","div","span","p","a","img","ul","ol","li","table","tr","td","th","form","input","button","script","style","link","meta","title","h1","h2","h3","h4","h5","h6","section","article","nav","header","footer","main","aside","canvas","svg"),
        "css" to setOf("color","background","margin","padding","border","display","flex","grid","position","top","left","right","bottom","width","height","font","size","weight","family","text","align","justify","content","items","gap","overflow","opacity","transition","transform","animation","z-index","cursor","hidden","auto","none","block","inline","relative","absolute","fixed","sticky"),
        "json" to emptySet(),
        "yaml" to emptySet(),
        "toml" to emptySet(),
        "ini" to emptySet(),
        "markdown" to emptySet(),
        "text" to emptySet()
    )

    private val typeKeywords = setOf("Int","Long","Double","Float","Boolean","Char","String","List","Map","Set","Array","Any","Unit","Nothing","Object","Class","Throwable","Exception","var","let","const","auto","void","bool","byte","short","long","float","double","char","int","string","boolean","dynamic")

    fun forLanguage(lang: String): (Editable, Context) -> Unit {
        val kw = keywordSets[lang] ?: emptySet()
        val ci = lang in setOf("sql")
        return { editable, ctx -> highlight(editable, ctx, kw, ci, lang) }
    }

    private fun commentRegex(lang: String): Regex = when (lang) {
        "python","bash","ruby","perl","r","powershell","toml","ini","yaml","dockerfile","makefile","elixir","clojure" -> Regex("#[^\\n]*")
        "sql","haskell","lua" -> Regex("--[^\\n]*")
        "html","markup","xml" -> Regex("<!--[\\s\\S]*?-->")
        "css" -> Regex("/\\*[\\s\\S]*?\\*/")
        else -> Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/")
    }

    private fun stringRegex(): Regex =
        Regex("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|`[^`]*`|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")

    private fun highlight(editable: Editable, ctx: Context, keywords: Set<String>, caseInsensitive: Boolean, lang: String) {
        val old = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        for (s in old) editable.removeSpan(s)

        val str = editable.toString()
        val keywordColor = ctx.getColor(R.color.keyword)
        val stringColor = ctx.getColor(R.color.string)
        val commentColor = ctx.getColor(R.color.comment)
        val numberColor = ctx.getColor(R.color.number)
        val typeColor = ctx.getColor(R.color.type)

        applyRegex(editable, str, commentRegex(lang), commentColor)
        applyRegex(editable, str, stringRegex(), stringColor)
        applyRegex(editable, str, Regex("\\b\\d+(\\.\\d+)?\\b"), numberColor)

        if (keywords.isNotEmpty()) {
            val pattern = "\\b(" + keywords.joinToString("|") { Regex.escape(it) } + ")\\b"
            val r = if (caseInsensitive) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
            applyRegex(editable, str, r, keywordColor)
        }
        applyRegex(editable, str, Regex("\\b(" + typeKeywords.joinToString("|") { Regex.escape(it) } + ")\\b"), typeColor)
    }

    private fun applyRegex(editable: Editable, str: String, regex: Regex, color: Int) {
        for (m in regex.findAll(str)) {
            editable.setSpan(ForegroundColorSpan(color), m.range.first, m.range.last + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
