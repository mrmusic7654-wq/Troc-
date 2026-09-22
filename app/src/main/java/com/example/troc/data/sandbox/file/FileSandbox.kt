package com.example.troc.data.sandbox.file

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.troc.data.sandbox.security.SandboxSecurityManager
import com.example.troc.domain.model.FileAction
import com.example.troc.domain.model.FileMeta
import com.example.troc.domain.model.SandboxPayload
import com.example.troc.domain.model.SandboxResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Files sandbox: inspect and transform user-selected documents inside the
 * app's cache directory (scoped storage). No arbitrary filesystem access.
 */
@Singleton
class FileSandbox @Inject constructor(
    @ApplicationContext private val context: Context,
    private val security: SandboxSecurityManager
) {

    suspend fun execute(
        uriString: String?,
        fileName: String?,
        action: FileAction,
        pattern: String?,
        replacement: String,
        content: String?
    ): SandboxResult = withContext(Dispatchers.IO) {
        try {
            if (content != null) {
                inspectContent(fileName ?: "attachment.txt", content, action, pattern, replacement)
            } else if (uriString != null) {
                inspectUri(Uri.parse(uriString), action, pattern, replacement)
            } else {
                SandboxResult.Failure("No file provided — attach one with 📎 first.")
            }
        } catch (e: SecurityException) {
            SandboxResult.Failure("File access denied: ${e.message}")
        } catch (e: Exception) {
            SandboxResult.Failure("File processing failed: ${e.message}")
        }
    }

    suspend fun inspectUri(
        uri: Uri,
        action: FileAction,
        pattern: String? = null,
        replacement: String = ""
    ): SandboxResult = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val name = queryDisplayName(uri) ?: "file"
            val bytes = readAll(uri) ?: return@withContext SandboxResult.Failure("Could not read the file (it may have been moved or deleted).")
            security.validateFileSize(bytes.size.toLong())?.let { return@withContext SandboxResult.Failure(it) }
            process(name, bytes, resolver.getType(uri), action, pattern, replacement)
        } catch (e: Exception) {
            SandboxResult.Failure("File processing failed: ${e.message}")
        }
    }

    suspend fun inspectContent(
        fileName: String,
        content: String,
        action: FileAction,
        pattern: String? = null,
        replacement: String = ""
    ): SandboxResult = withContext(Dispatchers.IO) {
        security.validateInput(content)?.let { return@withContext SandboxResult.Failure(it) }
        process(fileName, content.toByteArray(), guessMime(fileName), action, pattern, replacement)
    }

    // ------------------------------------------------------------------ core

    private fun process(
        name: String,
        bytes: ByteArray,
        mime: String?,
        action: FileAction,
        pattern: String?,
        replacement: String
    ): SandboxResult {
        return when (action) {
            FileAction.METADATA -> metadataResult(name, bytes, mime)
            FileAction.READ_TEXT -> {
                if (!looksLikeText(bytes)) {
                    SandboxResult.Failure("“$name” doesn't look like a text file (${security.humanBytes(bytes.size.toLong())}). Try the Metadata action.")
                } else {
                    textResult(name, bytes, mime, action)
                }
            }
            FileAction.REGEX_REPLACE -> {
                val text = String(bytes)
                val regex = try {
                    Regex(pattern.orEmpty())
                } catch (e: Exception) {
                    return SandboxResult.Failure("Invalid regex: ${e.message}")
                }
                val replaced = regex.replace(text, replacement)
                val count = regex.findAll(text).count()
                val outFile = writeToCache(name, replaced.toByteArray())
                SandboxResult.Success(
                    output = "Replaced $count match(es) of `$pattern`. Saved to:\n${outFile.absolutePath}" +
                        "\n\nPreview:\n```text\n${replaced.take(2_000)}\n```",
                    durationMs = 0,
                    payload = SandboxPayload(fileMeta = FileMeta(outFile.name, outFile.length(), mime ?: "text/plain"))
                )
            }
            FileAction.LIST_ZIP, FileAction.EXTRACT_ZIP -> zipResult(name, bytes, action)
        }
    }

    private fun metadataResult(name: String, bytes: ByteArray, mime: String?): SandboxResult {
        val extras = mutableMapOf<String, String>()
        if ((mime?.startsWith("image") == true) || IMAGE_EXT.contains(name.substringAfterLast('.', "").lowercase())) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth > 0) {
                extras["dimensions"] = "${options.outWidth} × ${options.outHeight} px"
                extras["format"] = options.outMimeType ?: mime ?: "unknown"
            }
        }
        if (looksLikeText(bytes)) {
            val text = String(bytes)
            extras["lines"] = text.count { it == '\n' }.plus(1).toString()
            extras["characters"] = text.length.toString()
            extras["words"] = text.split(Regex("\\s+")).count { it.isNotBlank() }.toString()
        }
        val meta = FileMeta(name, bytes.size.toLong(), mime ?: guessMime(name), extras)
        val output = buildString {
            appendLine("📁 **$name**")
            appendLine("- Size: ${security.humanBytes(meta.sizeBytes)}")
            appendLine("- Type: ${meta.mimeType}")
            extras.forEach { (k, v) -> appendLine("- $k: $v") }
        }
        return SandboxResult.Success(output, 0, SandboxPayload(fileMeta = meta))
    }

    private fun textResult(name: String, bytes: ByteArray, mime: String?, action: FileAction): SandboxResult {
        val text = String(bytes)
        val meta = FileMeta(
            name = name,
            sizeBytes = bytes.size.toLong(),
            mimeType = mime ?: guessMime(name),
            preview = text.take(2_000)
        )
        return SandboxResult.Success(
            output = "```text\n${text.take(20_000)}${if (text.length > 20_000) "\n…[truncated]" else ""}\n```",
            durationMs = 0,
            payload = SandboxPayload(fileMeta = meta)
        )
    }

    private fun zipResult(name: String, bytes: ByteArray, action: FileAction): SandboxResult {
        val entries = mutableListOf<ZipEntry>()
        ZipInputStream(bytes.inputStream()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null && entries.size <= security.maxZipEntries) {
                entries += entry
                entry = zis.nextEntry
            }
        }
        val listing = entries.joinToString("\n") { "- ${it.name} (${security.humanBytes(it.size)})" }
        if (action == FileAction.LIST_ZIP) {
            return SandboxResult.Success(
                output = "Archive “$name” — ${entries.size} entries:\n$listing",
                durationMs = 0
            )
        }
        // Extract with zip-slip protection and size budget.
        val destDir = File(context.cacheDir, "sandbox-extracts/${System.currentTimeMillis()}").apply { mkdirs() }
        var totalBytes = 0L
        var count = 0
        ZipInputStream(bytes.inputStream()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null && count < security.maxZipEntries) {
                val outFile = File(destDir, entry.name).canonicalFile
                if (!outFile.path.startsWith(destDir.canonicalPath)) {
                    return SandboxResult.Failure("Blocked a path-traversal entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { fos ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = zis.read(buffer)
                            if (read <= 0) break
                            totalBytes += read
                            if (totalBytes > security.maxExtractedZipBytes) {
                                return SandboxResult.Failure("Archive too large to extract (max ${security.humanBytes(security.maxExtractedZipBytes)}).")
                            }
                            fos.write(buffer, 0, read)
                        }
                    }
                    count++
                }
                entry = zis.nextEntry
            }
        }
        return SandboxResult.Success(
            output = "Extracted $count file(s) (${security.humanBytes(totalBytes)}) to:\n${destDir.absolutePath}\n\n$listing",
            durationMs = 0
        )
    }

    // ------------------------------------------------------------------ zip creation (backup/export helper)

    fun zipDirectory(sourceDir: File, target: File) {
        ZipOutputStream(target.outputStream()).use { zos ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entry = ZipEntry(file.relativeTo(sourceDir).path)
                zos.putNextEntry(entry)
                FileInputStream(file).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun readAll(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var read: Int
            var total = 0
            while (input.read(chunk).also { read = it } > 0) {
                total += read
                if (total > security.maxFileSizeBytes + 64 * 1024) return null
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }
    }.getOrNull()

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

    private fun writeToCache(name: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "${System.currentTimeMillis()}_$safeName")
        file.writeBytes(bytes)
        return file
    }

    private fun looksLikeText(bytes: ByteArray): Boolean {
        val sample = bytes.take(4_096)
        var suspicious = 0
        for (b in sample) {
            if (b == 0.toByte()) return false
            if (b < 9 || (b in 14..31)) suspicious++
        }
        return suspicious * 20 < sample.size.coerceAtLeast(1)
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "csv" -> "text/csv"
            "json" -> "application/json"
            "txt", "md", "log" -> "text/plain"
            "xml", "html", "yaml", "yml" -> "text/plain"
            "zip" -> "application/zip"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    private companion object {
        val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    }
}
