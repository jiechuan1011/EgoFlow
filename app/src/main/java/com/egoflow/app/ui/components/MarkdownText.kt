package com.egoflow.app.ui.components

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val TAG = "MarkdownText"

/**
 * 简易 Markdown 渲染组件
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error

    // 在可组合上下文中捕获所有颜色，传递给纯函数
    try {
        MarkdownContent(markdown, modifier, onBg, primary)
    } catch (e: Exception) {
        Log.e(TAG, "Markdown render failed", e)
        Text(text = markdown, fontSize = 13.sp, color = error, modifier = modifier)
    }
}

@Composable
private fun MarkdownContent(markdown: String, modifier: Modifier, onBg: Color, primary: Color) {
    val lines = markdown.lines()
    var inCodeBlock = false
    val codeBlockLines = mutableListOf<String>()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    CodeBlock(codeBlockLines.joinToString("\n"))
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                i++; continue
            }
            if (inCodeBlock) {
                codeBlockLines.add(line)
                i++; continue
            }

            when {
                trimmed.isEmpty() -> Spacer(Modifier.height(6.dp))
                trimmed.matches(Regex("^-{3,}$")) -> Hr()
                trimmed.startsWith("#### ") -> H(trimmed.removePrefix("#### ").trim(), 4, onBg)
                trimmed.startsWith("### ") -> H(trimmed.removePrefix("### ").trim(), 3, onBg)
                trimmed.startsWith("## ") -> H(trimmed.removePrefix("## ").trim(), 2, onBg)
                trimmed.startsWith("# ") -> H(trimmed.removePrefix("# ").trim(), 1, onBg)
                trimmed.matches(Regex("^[-*+]\\s.*")) -> B(trimmed.replaceFirst(Regex("^[-*+]\\s"), ""), onBg, primary)
                trimmed.matches(Regex("^\\d+\\.\\s.*")) -> B(trimmed.replaceFirst(Regex("^\\d+\\.\\s"), ""), onBg, primary)
                else -> P(trimmed, onBg, primary)
            }
            i++
        }
    }
}

@Composable
private fun H(text: String, level: Int, onBg: Color) {
    Text(parseInline(text, onBg), fontSize = when (level) { 1 -> 20.sp; 2 -> 17.sp; 3 -> 15.sp; else -> 14.sp },
        fontWeight = FontWeight.Bold, color = onBg, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
private fun P(text: String, onBg: Color, primary: Color) {
    if (text.isBlank()) return
    Text(parseInline(text, onBg, primary), fontSize = 14.sp, lineHeight = 20.sp, color = onBg, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun B(text: String, onBg: Color, primary: Color) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text("  •  ", fontSize = 14.sp, color = onBg)
        Text(parseInline(text, onBg, primary), fontSize = 14.sp, lineHeight = 20.sp, color = onBg)
    }
}

@Composable
private fun CodeBlock(code: String) {
    SelectionContainer {
        Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp))
    }
}

@Composable
private fun Hr() {
    Spacer(Modifier.height(4.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(4.dp))
}

/** 非 Composable 内联解析：接收颜色参数而非直接调用 MaterialTheme */
private fun parseInline(text: String, onBg: Color, primary: Color = onBg) = buildAnnotatedString {
    var remaining = text
    while (remaining.isNotEmpty()) {
        val b = remaining.indexOf("**")
        val i = remaining.indexOf('*')
        val c = remaining.indexOf('`')

        val italicIdx = if (i != -1 && i == b) remaining.indexOf('*', b + 2) else i
        val markers = mutableListOf<Pair<Int, String>>()
        if (b != -1) markers.add(b to "b")
        if (italicIdx != -1 && (b == -1 || italicIdx != b)) markers.add(italicIdx to "i")
        if (c != -1) markers.add(c to "c")
        if (markers.isEmpty()) { append(remaining); break }

        val (pos, type) = markers.minBy { it.first }
        if (pos > 0) append(remaining.substring(0, pos))
        remaining = remaining.substring(pos)
        when (type) {
            "b" -> { val e = remaining.indexOf("**", 2)
                if (e != -1) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(remaining.substring(2, e)) }; remaining = remaining.substring(e + 2) }
                else { append(remaining); break } }
            "i" -> { val e = remaining.indexOf('*', 1)
                if (e != -1) { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(remaining.substring(1, e)) }; remaining = remaining.substring(e + 1) }
                else { append(remaining); break } }
            "c" -> { val e = remaining.indexOf('`', 1)
                if (e != -1) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = primary)) { append(remaining.substring(1, e)) }; remaining = remaining.substring(e + 1) }
                else { append(remaining); break } }
        }
    }
}
