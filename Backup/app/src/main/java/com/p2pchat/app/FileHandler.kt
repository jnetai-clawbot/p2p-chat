package com.p2pchat.app

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream

object FileHandler {
    private var appContext: Context? = null
    private var transferDir: File? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            transferDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "transfers")
            if (transferDir?.exists() != true) {
                transferDir?.mkdirs()
            }
            ErrorLogger.i("FileHandler", "Initialized", mapOf(
                "path" to (transferDir?.absolutePath ?: "null"),
                "exists" to (transferDir?.exists()?.toString() ?: "false")
            ))
        } catch (e: Exception) {
            ErrorLogger.e("FileHandler", "FH001", "Failed to initialize FileHandler", e)
        }
    }

    fun getFileInfo(uri: Uri): FileInfo? {
        val ctx = appContext ?: run {
            ErrorLogger.e("FileHandler", "FH002", "Context is null in getFileInfo")
            return null
        }

        return try {
            val cursor: Cursor? = ctx.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else uri.lastPathSegment ?: "unknown"
                    val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else -1L
                    val mimeType = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
                    FileInfo(name, size, mimeType, uri)
                } else null
            }
        } catch (e: Exception) {
            ErrorLogger.e("FileHandler", "FH003", "Failed to get file info for URI: $uri", e)
            null
        }
    }

    fun readFileBytes(uri: Uri): ByteArray? {
        val ctx = appContext ?: return null
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            ErrorLogger.e("FileHandler", "FH004", "Failed to read file bytes from URI: $uri", e)
            null
        }
    }

    fun saveReceivedFile(fileName: String, data: ByteArray): File? {
        val dir = transferDir ?: run {
            ErrorLogger.e("FileHandler", "FH005", "Transfer directory is null")
            return null
        }

        return try {
            if (!dir.exists()) dir.mkdirs()
            var outputName = sanitizeFileName(fileName)
            var outFile = File(dir, outputName)

            var counter = 1
            val dotIndex = outputName.lastIndexOf('.')
            val baseName = if (dotIndex > 0) outputName.substring(0, dotIndex) else outputName
            val ext = if (dotIndex > 0) outputName.substring(dotIndex) else ""
            while (outFile.exists()) {
                outputName = "${baseName}_$counter$ext"
                outFile = File(dir, outputName)
                counter++
            }

            FileOutputStream(outFile).use { it.write(data) }
            ErrorLogger.i("FileHandler", "File saved", mapOf(
                "name" to outputName, "size" to data.size.toString(), "path" to outFile.absolutePath
            ))
            outFile
        } catch (e: Exception) {
            ErrorLogger.e("FileHandler", "FH006", "Failed to save received file: $fileName", e)
            null
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[<>:"/\\|?*\x00-\x1f]"""), "_").trim()
    }

    data class FileInfo(
        val name: String,
        val size: Long,
        val mimeType: String,
        val uri: Uri
    )
}
