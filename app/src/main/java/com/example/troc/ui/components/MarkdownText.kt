package com.example.troc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.troc.domain.agent.AgentToolCallParser
import com.example.troc.ui.theme.isTrocDarkTheme
import com.example.troc.domain.model.AppSettings
import com.example.troc.util.MdBlock
import com.example.troc.util.MdInline
import com.example.troc.util.MdSpanType
import com.example.troc.util.SyntaxHighlighter
import com.example.troc.util.SyntaxRole

/**
 * Renders parsed Markdown (from Mistral or the agent) inside chat bubbles:
 * headings, paragraphs, lists, tables, quotes, rules and highlighted code blocks.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val cleaned = remember(markdown) { AgentToolCallParser().summarizeForDisplay(markdown) }
    val blocks = remember(cleaned) { MarkdownParser.parseBlocks(cleaned) }
    Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = inlineToAnnotatedString(block.inline),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                )
                is MdBlock.Paragraph -> Text(
                    text = inlineToAnnotatedString(block.inline),
                    style = MaterialTheme.typography.bodyMedium
                )
                is MdBlock.Quote -> Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(Modifier.padding(8.dp)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(inlineToAnnotatedString(block.inline), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is MdBlock.BulletList -> Column {
                    block.items.forEach { item ->
                        Row {
                            Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Text(inlineToAnnotatedString(item), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is MdBlock.OrderedList -> Column {
                    block.items.forEachIndexed { index, item ->
                        Row {
                            Text("${index + 1}.  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Text(inlineToAnnotatedString(item), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is MdBlock.CodeBlock -> CodeBlock(
                    code = block.code,
                    language = block.language
                )
                is MdBlock.Table -> DataTable(
                    columns = block.header,
                    rows = block.rows
                )
                MdBlock.HorizontalRule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
fun inlineToAnnotatedString(inline: MdInline): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeForeground = MaterialTheme.colorScheme.onSurfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(inline.text)
        inline.spans.forEach { span ->
            when (span.type) {
                MdSpanType.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), span.start, span.end)
                MdSpanType.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), span.start, span.end)
                MdSpanType.CODE -> addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = codeForeground),
                    span.start, span.end
                )
                MdSpanType.STRIKETHROUGH -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), span.start, span.end)
                MdSpanType.LINK -> addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium),
                    span.start, span.end
                )
            }
        }
    }
}

/** Fenced code block with syntax highlighting and a copy button. */
@Composable
fun CodeBlock(code: String, language: String?, modifier: Modifier = Modifier) {
    val dark = isTrocDarkTheme(com.example.troc.domain.model.AppSettings())
    val colors = syntaxColors(dark)
    val background = if (dark) com.example.troc.ui.theme.CodeBackgroundDark else com.example.troc.ui.theme.CodeBackgroundLight
    val clipboard = LocalClipboardManager.current
    val highlighted = remember(code, language) {
        buildAnnotatedString {
            append(code)
            SyntaxHighlighter.highlight(code, language).forEach { span ->
                val role = when (span.type) {
                    SyntaxHighlighter.TokenType.COMMENT -> SyntaxRole.COMMENT
                    SyntaxHighlighter.TokenType.STRING -> SyntaxRole.STRING
                    SyntaxHighlighter.TokenType.NUMBER -> SyntaxRole.NUMBER
                    SyntaxHighlighter.TokenType.KEYWORD -> SyntaxRole.KEYWORD
                    SyntaxHighlighter.TokenType.ANNOTATION -> SyntaxRole.ANNOTATION
                }
                addStyle(
                    SpanStyle(color = colors[role] ?: MaterialTheme.colorScheme.onSurface),
                    span.start, span.end
                )
            }
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = background,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp)
            ) {
                Text(
                    text = language ?: "text",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors[SyntaxRole.COMMENT],
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy code",
                        tint = colors[SyntaxRole.COMMENT]
                    )
                }
            }
            Box(Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Text(text = highlighted, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            }
        }
    }
}
