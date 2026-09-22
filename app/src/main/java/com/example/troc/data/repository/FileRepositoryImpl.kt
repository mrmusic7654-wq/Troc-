package com.example.troc.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.troc.data.sandbox.file.FileSandbox
import com.example.troc.domain.model.FileAction
import com.example.troc.domain.model.FileAttachment
import com.example.troc.domain.model.SandboxResult
import com.example.troc.domain.repository.FileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class FileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileSandbox: FileSandbox
) : FileRepository {

    override suspend fun readAttachment(uriString: String): FileAttachment? =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val resolver = context.contentResolver
                val name = queryName(uri) ?: "attachment"
                val size = querySize(uri) ?: -1L
                val mime = resolver.getType(uri) ?: guessMime(name)
                val content = if (isTextLike(name, mime)) {
                    readTextLimited(uri, maxBytes = 512 * 1024)
                } else {
                    null
                }
                FileAttachment(
                    name = name,
                    sizeBytes = size,
                    mimeType = mime,
                    content = content,
                    uri = uriString
                )
            }.getOrNull()
        }

    override suspend fun inspectFile(
        uriString: String,
        action: FileAction,
        pattern: String?,
        replacement: String
    ): SandboxResult =
        fileSandbox.inspectUri(Uri.parse(uriString), action, pattern, replacement)

    override suspend fun inspectContent(
        fileName: String,
        content: String,
        action: FileAction,
        pattern: String?,
        replacement: String
    ): SandboxResult =
        fileSandbox.inspectContent(fileName, content, action, pattern, replacement)

    // ------------------------------------------------------------------ helpers

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    private fun querySize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && c.moveToFirst()) c.getLong(idx) else null
        }
    }.getOrNull()

    private fun readTextLimited(uri: Uri, maxBytes: Int): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(maxBytes + 1)
            var offset = 0
            while (offset < buffer.size) {
                val read = input.read(buffer, offset, buffer.size - offset)
                if (read <= 0) break
                offset += read
            }
            if (offset > maxBytes) {
                String(buffer, 0, maxBytes, Charsets.UTF_8) + "\n…[truncated]"
            } else {
                String(buffer, 0, offset, Charsets.UTF_8)
            }
        }
    }.getOrNull()

    private fun isTextLike(name: String, mime: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        val textExts = setOf("csv", "tsv", "json", "txt", "md", "log", "xml", "yaml", "yml", "html", "js", "kt", "py", "sql", "ini", "toml")
        return mime.startsWith("text/") || ext in textExts || mime.contains("json") || mime.contains("xml")
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "csv" -> "text/csv"
            "json" -> "application/json"
            "zip" -> "application/zip"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }
}
