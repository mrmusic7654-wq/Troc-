package com.troc.util

import java.util.regex.Pattern

object MarkdownRenderer {
    // Lightweight helpers; actual rendering uses compose-richtext
    fun containsCodeBlock(text: String): Boolean = text.contains("```")
    fun extractCodeBlocks(text: String): List<String> {
        val pattern = Pattern.compile("```(?:\\w+)?\\n(.*?)\\n```", Pattern.DOTALL)
        val matcher = pattern.matcher(text)
        val list = mutableListOf<String>()
        while (matcher.find()) {
            list.add(matcher.group(1) ?: "")
        }
        return list
    }

    fun stripMarkdown(text: String): String {
        return text
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            .replace(Regex("`([^`]+)`"), "$1")
    }
}
