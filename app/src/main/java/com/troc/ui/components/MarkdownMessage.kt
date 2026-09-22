package com.troc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troc.ui.theme.CodeSurface
import com.troc.ui.theme.CodeText

@Composable
fun MarkdownMessage(
    text: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Simple detection of code blocks for custom rendering with copy button
        // Fallback to simple Text rendering if richtext not available
        if (text.contains("```")) {
            val parts = splitByCodeBlocks(text)
            parts.forEach { part ->
                if (part.isCodeBlock) {
                    CodeBlockWithCopy(code = part.content, language = part.language)
                } else {
                    // Simple markdown-like rendering: handle bold, italics, lists via Text
                    SimpleMarkdownText(part.content)
                }
            }
        } else {
            SimpleMarkdownText(text.ifEmpty { " " })
        }

        if (isStreaming) {
            TypingIndicator(modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun SimpleMarkdownText(text: String) {
    // Very basic markdown handling without external lib
    // Handles **bold**, *italics*, `code`, - lists, # headers
    Column {
        text.lines().forEach { line ->
            when {
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# ").trim(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## ").trim(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)) {
                        Text("• ", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = line.drop(2).trim().replaceBold().replaceCode(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                line.trim().isEmpty() -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                else -> {
                    Text(
                        text = line.replaceBold().replaceCode(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

// Helper to strip markdown for simple display
private fun String.replaceBold(): String {
    return this.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("\\*(.*?)\\*"), "$1")
}

private fun String.replaceCode(): String {
    return this.replace(Regex("`([^`]+)`"), "$1")
}

private data class TextPart(
    val content: String,
    val isCodeBlock: Boolean,
    val language: String = ""
)

private fun splitByCodeBlocks(text: String): List<TextPart> {
    val regex = Regex("```(\\w+)?\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
    val result = mutableListOf<TextPart>()
    var lastIndex = 0
    regex.findAll(text).forEach { match ->
        val start = match.range.first
        if (start > lastIndex) {
            result.add(TextPart(text.substring(lastIndex, start), false))
        }
        val lang = match.groupValues[1]
        val code = match.groupValues[2]
        result.add(TextPart(code, true, lang))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        result.add(TextPart(text.substring(lastIndex), false))
    }
    if (result.isEmpty()) result.add(TextPart(text, false))
    return result
}

@Composable
fun CodeBlockWithCopy(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CodeSurface)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CodeSurface.copy(alpha = 0.8f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifEmpty { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = CodeText.copy(alpha = 0.7f)
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = CodeText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = CodeText
                )
            }
        }

        if (copied) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = "Copied!",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
