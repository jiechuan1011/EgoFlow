package com.egoflow.app.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 *
 * 支持：标题 / 粗体 / 斜体 / 行内代码 / 代码块 / 无序列表 / 分割线
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    try {
        MarkdownContent(markdown, modifier)
    } catch (e: Exception) {
        Log.e(TAG, "Markdown render failed", e)
        Text(
            text = markdown,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier
        )
    }
}

@Composable
private fun MarkdownContent(markdown: String, modifier: Modifier) {
    val lines = markdown.lines()
    var inCodeBlock = false
    val codeBlockLines = mutableListOf<String>()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
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
                i++
                continue
            }
            if (inCodeBlock) {
                codeBlockLines.add(line)
                i++
                continue
            }

            try {
                when {
                    trimmed.isEmpty() -> Spacer(Modifier.height(6.dp))
                    trimmed.matches(Regex("^-{3,}$")) -> Hr()
                    trimmed.startsWith("#### ") -> Heading(trimmed.removePrefix("#### ").trim(), 4)
                    trimmed.startsWith("### ") -> Heading(trimmed.removePrefix("### ").trim(), 3)
                    trimmed.startsWith("## ") -> Heading(trimmed.removePrefix("## ").trim(), 2)
                    trimmed.startsWith("# ") -> Heading(trimmed.removePrefix("# ").trim(), 1)
                    trimmed.matches(Regex("^[-*+]\\s.*")) -> BulletItem(trimmed.replaceFirst(Regex("^[-*+]\\s"), ""))
                    trimmed.matches(Regex("^\\d+\\.\\s.*")) -> BulletItem(trimmed.replaceFirst(Regex("^\\d+\\.\\s"), ""))
                    else -> Paragraph(trimmed)
                }
            } catch (_: Exception) {
                Paragraph(trimmed)
            }

            i++
        }
    }
}

@Composable
private fun Heading(text: String, level: Int) {
    val fontSize = when (level) {
        1 -> 20.sp; 2 -> 17.sp; 3 -> 15.sp; else -> 14.sp
    }
    Text(
        text = parseInline(text),
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun Paragraph(text: String) {
    if (text.isBlank()) return
    Text(
        text = parseInline(text),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun BulletItem(text: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text("  ∙  ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(
            text = parseInline(text),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun CodeBlock(code: String) {
    SelectionContainer {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun Hr() {
    Spacer(Modifier.height(4.dp))
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(4.dp))
}

/** 解析行内 **粗体** *斜体* `代码` */
private fun parseInline(text: String) = buildAnnotatedString {
    var remaining = text

    while (remaining.isNotEmpty()) {
        val b = remaining.indexOf("**")
        val i = remaining.indexOf('*')
        val c = remaining.indexOf('`')

        // 跳过与 ** 重叠的单个 *
        val italicIdx = if (i != -1 && i == b) remaining.indexOf('*', b + 2) else i

        // 找最近的有效标记
        val markers = mutableListOf<Pair<Int, String>>()
        if (b != -1) markers.add(b to "b")
        if (italicIdx != -1 && (b == -1 || italicIdx != b)) markers.add(italicIdx to "i")
        if (c != -1) markers.add(c to "c")

        if (markers.isEmpty()) { append(remaining); break }

        val (pos, type) = markers.minBy { it.first }
        if (pos > 0) { append(remaining.substring(0, pos)) }
        remaining = remaining.substring(pos)

        when (type) {
            "b" -> {
                val e = remaining.indexOf("**", 2)
                if (e != -1) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(remaining.substring(2, e)) }; remaining = remaining.substring(e + 2) }
                else { append(remaining); break }
            }
            "i" -> {
                val e = remaining.indexOf('*', 1)
                if (e != -1) { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(remaining.substring(1, e)) }; remaining = remaining.substring(e + 1) }
                else { append(remaining); break }
            }
            "c" -> {
                val e = remaining.indexOf('`', 1)
                if (e != -1) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)) { append(remaining.substring(1, e)) }; remaining = remaining.substring(e + 1) }
                else { append(remaining); break }
            }
        }
    }
}
