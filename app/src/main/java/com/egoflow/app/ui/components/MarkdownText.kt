package com.egoflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 简易 Markdown 渲染组件
 *
 * 支持：标题 / 粗体 / 斜体 / 行内代码 / 代码块 / 无序列表 / 有序列表 / 分割线
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
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

            // 代码块
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // End code block
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

            when {
                // 空行
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // 分割线
                trimmed.matches(Regex("^-{3,}$")) -> {
                    HorizontalDivider()
                }
                // 标题
                trimmed.startsWith("##### ") -> {
                    Heading(trimmed.removePrefix("##### ").trim(), 5)
                }
                trimmed.startsWith("#### ") -> {
                    Heading(trimmed.removePrefix("#### ").trim(), 4)
                }
                trimmed.startsWith("### ") -> {
                    Heading(trimmed.removePrefix("### ").trim(), 3)
                }
                trimmed.startsWith("## ") -> {
                    Heading(trimmed.removePrefix("## ").trim(), 2)
                }
                trimmed.startsWith("# ") -> {
                    Heading(trimmed.removePrefix("# ").trim(), 1)
                }
                // 无序列表
                trimmed.matches(Regex("^[-*+]\\s.*")) -> {
                    val content = trimmed.replaceFirst(Regex("^[-*+]\\s"), "")
                    BulletItem(content)
                }
                // 有序列表
                trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val content = trimmed.replaceFirst(Regex("^\\d+\\.\\s"), "")
                    BulletItem(content)
                }
                // 普通段落
                else -> {
                    Paragraph(trimmed)
                }
            }
            i++
        }
    }
}

@Composable
private fun Heading(text: String, level: Int) {
    val fontSize = when (level) {
        1 -> 22.sp
        2 -> 18.sp
        3 -> 16.sp
        4 -> 14.sp
        else -> 13.sp
    }
    Text(
        text = parseInline(text),
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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
        Text(
            text = "  •  ",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
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
private fun HorizontalDivider() {
    Spacer(modifier = Modifier.height(4.dp))
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
}

/**
 * 解析行内标记：**粗体** *斜体* `行内代码`
 */
@Composable
private fun parseInline(text: String) = buildAnnotatedString {
    var remaining = text

    while (remaining.isNotEmpty()) {
        val boldStart = remaining.indexOf("**")
        val italicStart = remaining.indexOf("*")
        val codeStart = remaining.indexOf("`")

        // Find the earliest marker
        val candidates = listOfNotNull(
            boldStart to "bold",
            if (italicStart != -1 && italicStart != boldStart) italicStart to "italic" else null,
            if (codeStart != -1 && codeStart != boldStart && codeStart != italicStart) codeStart to "code" else null
        )

        if (candidates.isEmpty()) {
            append(remaining)
            break
        }

        val (firstPos, type) = candidates.minBy { it.first }

        // Append text before marker
        if (firstPos > 0) {
            append(remaining.substring(0, firstPos))
        }
        remaining = remaining.substring(firstPos)

        when (type) {
            "bold" -> {
                val end = remaining.indexOf("**", 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(remaining.substring(2, end))
                    }
                    remaining = remaining.substring(end + 2)
                } else {
                    append(remaining)
                    break
                }
            }
            "italic" -> {
                val end = remaining.indexOf("*", 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(remaining.substring(1, end))
                    }
                    remaining = remaining.substring(end + 1)
                } else {
                    append(remaining)
                    break
                }
            }
            "code" -> {
                val end = remaining.indexOf("`", 1)
                if (end != -1) {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )) {
                        append(remaining.substring(1, end))
                    }
                    remaining = remaining.substring(end + 1)
                } else {
                    append(remaining)
                    break
                }
            }
        }
    }
}
