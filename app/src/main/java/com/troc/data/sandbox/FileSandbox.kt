package com.troc.data.sandbox

import android.content.Context
import com.troc.domain.model.SandboxResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileSandbox @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(operation: String, input: String): SandboxResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val result = when (operation.lowercase()) {
                "regex_replace" -> {
                    // input format: pattern::replacement::text
                    val parts = input.split("::", limit = 3)
                    if (parts.size < 3) throw IllegalArgumentException("Format: pattern::replacement::text")
                    val regex = Regex(parts[0])
                    regex.replace(parts[2], parts[1])
                }
                "uppercase" -> input.uppercase()
                "lowercase" -> input.lowercase()
                "trim" -> input.trim()
                "word_count" -> "Words: ${input.split(Regex("\\s+")).size}, Chars: ${input.length}, Lines: ${input.lines().size}"
                "zip" -> {
                    // input is file path in cacheDir to zip
                    val file = File(context.cacheDir, input)
                    if (!file.exists()) throw IllegalArgumentException("File not found in cache: $input")
                    val zipFile = File(context.cacheDir, "${file.name}.zip")
                    ZipOutputStream(zipFile.outputStream()).use { zos ->
                        zos.putNextEntry(ZipEntry(file.name))
                        file.inputStream().copyTo(zos)
                        zos.closeEntry()
                    }
                    "Zipped to ${zipFile.absolutePath}"
                }
                "unzip" -> {
                    val file = File(context.cacheDir, input)
                    if (!file.exists()) throw IllegalArgumentException("Zip not found: $input")
                    val outDir = File(context.cacheDir, "unzipped_${System.currentTimeMillis()}")
                    outDir.mkdirs()
                    ZipInputStream(file.inputStream()).use { zis ->
                        var entry: ZipEntry?
                        while (zis.nextEntry.also { entry = it } != null) {
                            val outFile = File(outDir, entry!!.name)
                            outFile.outputStream().use { zis.copyTo(it) }
                        }
                    }
                    "Unzipped to ${outDir.absolutePath}"
                }
                "metadata" -> {
                    val file = File(context.cacheDir, input)
                    if (!file.exists()) "File not found"
                    else "Name: ${file.name}, Size: ${file.length()} bytes, LastModified: ${file.lastModified()}, Path: ${file.absolutePath}"
                }
                else -> throw IllegalArgumentException("Unknown operation: $operation")
            }
            SandboxResult.Success(result, System.currentTimeMillis() - start)
        } catch (e: Exception) {
            SandboxResult.Error(e.message ?: "File operation failed", e.stackTraceToString(), System.currentTimeMillis() - start)
        }
    }

    fun listCacheFiles(): List<File> {
        return context.cacheDir.listFiles()?.toList() ?: emptyList()
    }
}
