package com.troc.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troc.ui.theme.CodeBorder
import com.troc.ui.theme.CodeSurface
import com.troc.ui.theme.CodeText
import com.troc.ui.theme.PrimaryIndigo
import com.troc.ui.theme.TertiaryEmerald
import kotlinx.coroutines.delay

@Composable
fun MarkdownMessage(
    text: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (text.contains("```")) {
            val parts = splitByCodeBlocks(text)
            parts.forEach { part ->
                if (part.isCodeBlock) {
                    CodeBlockWithCopy(code = part.content.trim(), language = part.language)
                } else if (part.content.isNotBlank()) {
                    SimpleMarkdownText(part.content)
                }
            }
        } else {
            SimpleMarkdownText(text.ifEmpty { " " })
        }

        if (isStreaming) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypingIndicator()
            }
        }
    }
}

@Composable
fun SimpleMarkdownText(text: String) {
    val lines = text.lines()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### ").trim(),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## ").trim(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# ").trim(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> {
                    val bulletContent = line.drop(2).trim()
                    Row(
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = PrimaryIndigo,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(bulletContent),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                        )
                    }
                }
                line.matches(Regex("""^\d+\.\s.*""")) -> {
                    val num = line.substringBefore(".").trim()
                    val rest = line.substringAfter(".").trim()
                    Row(
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "$num.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = PrimaryIndigo,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(rest),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                        )
                    }
                }
                line.isBlank() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(line),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )
                    )
                }
            }
        }
    }
}

private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("""(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`)""")
        val matches = regex.findAll(text).toList()

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > cursor) {
                append(text.substring(cursor, start))
            }
            val matchText = match.value
            when {
                matchText.startsWith("**") && matchText.endsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(matchText.removeSurrounding("**"))
                    }
                }
                matchText.startsWith("*") && matchText.endsWith("*") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(matchText.removeSurrounding("*"))
                    }
                }
                matchText.startsWith("`") && matchText.endsWith("`") -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x336366F1),
                            color = Color(0xFF818CF8),
                            fontSize = 13.sp
                        )
                    ) {
                        append(" ${matchText.removeSurrounding("`")} ")
                    }
                }
                else -> append(matchText)
            }
            cursor = end
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
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
            delay(1500)
            copied = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CodeSurface)
            .border(1.dp, CodeBorder, RoundedCornerShape(14.dp))
    ) {
        Column {
            // Code header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = PrimaryIndigo.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = language.ifEmpty { "code" }.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFA5B4FC)
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(code))
                            copied = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        AnimatedContent(
                            targetState = copied,
                            label = "copyAnimation"
                        ) { isCopied ->
                            if (isCopied) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = TertiaryEmerald,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Copied",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TertiaryEmerald
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Copy",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = CodeText
                )
            }
        }
    }
}
