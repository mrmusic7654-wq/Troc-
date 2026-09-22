package com.example.troc.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.troc.domain.model.Chat
import com.example.troc.domain.model.Role
import com.example.troc.domain.model.SandboxSessionRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ShareUtils {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun formatTimestamp(timestamp: Long): String = timeFormat.format(Date(timestamp))

    fun chatToMarkdown(chat: Chat): String = buildString {
        appendLine("# ${chat.title}")
        appendLine()
        appendLine("_Exported from Troc — ${formatTimestamp(chat.timestamp)}${if (chat.isAgentMode) " (Agent Mode)" else ""}_")
        appendLine()
        chat.messages.filter { it.role != Role.SYSTEM }.forEach { message ->
            when (message.role) {
                Role.USER -> appendLine("## 🧑 You")
                Role.ASSISTANT -> appendLine("## 🤖 Troc")
                Role.TOOL -> appendLine("## 🛠️ ${message.toolLabel ?: "Tool"}")
                Role.SYSTEM -> Unit
            }
            appendLine()
            appendLine(message.content)
            appendLine()
        }
    }

    fun sessionsToCsv(sessions: List<SandboxSessionRecord>): String = buildString {
        appendLine("timestamp,tool,language,success,duration_ms,input,output")
        sessions.forEach { s ->
            appendLine(
                listOf(
                    formatTimestamp(s.timestamp),
                    s.tool,
                    s.language.orEmpty(),
                    s.success.toString(),
                    s.durationMs.toString(),
                    escape(s.input.take(500)),
                    escape(s.output.take(500))
                ).joinToString(",")
            )
        }
    }

    private fun escape(value: String): String =
        "\"" + value.replace("\"", "\"\"").replace("\n", "\\n") + "\""

    fun shareText(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share file").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Zips chats (markdown + JSON) and sandbox sessions into the app cache and
     * returns the file, ready to be shared via [shareFile].
     */
    fun exportBackup(
        context: Context,
        chats: List<Chat>,
        sessions: List<SandboxSessionRecord>,
        json: Json
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val workDir = File(dir, "troc-backup-${System.currentTimeMillis()}").apply { mkdirs() }

        File(workDir, "chats.json").writeText(
            json.encodeToString(ListSerializer(Chat.serializer()), chats)
        )
        File(workDir, "chats.md").writeText(
            chats.joinToString("\n\n---\n\n") { chatToMarkdown(it) }
        )
        File(workDir, "sandbox-sessions.csv").writeText(sessionsToCsv(sessions))

        val zip = File(dir, workDir.name + ".zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { zos ->
            workDir.walkTopDown().filter { it.isFile }.forEach { f ->
                zos.putNextEntry(java.util.zip.ZipEntry(f.relativeTo(workDir).path))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        workDir.deleteRecursively()
        return zip
    }
}
