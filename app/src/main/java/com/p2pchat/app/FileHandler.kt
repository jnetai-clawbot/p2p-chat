package com.p2pchat.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileHandler {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getFileInfo(uri: Uri): FileInfo? {
        val ctx = appContext ?: return null
        return try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else uri.lastPathSegment ?: "unknown"
                    val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
                    val mimeType = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
                    FileInfo(name, size, mimeType, uri)
                } else null
            }
        } catch (e: Exception) {
            ErrorLogger.e("FileHandler", "FH003", "Failed to get file info", e)
            null
        }
    }

    fun readFileBytes(uri: Uri): ByteArray? {
        val ctx = appContext ?: return null
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            ErrorLogger.e("FileHandler", "FH004", "Failed to read file bytes", e)
            null
        }
    }

    fun saveReceivedFile(fileName: String, data: ByteArray): File? {
        val ctx = appContext ?: return null
        val sanitizedName = sanitizeFileName(fileName)
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/P2PChat")
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { os ->
                        os.write(data)
                    }
                    ErrorLogger.i("FileHandler", "File saved via MediaStore: $sanitizedName")
                    File(sanitizedName) // Return dummy file object for path display
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val p2pDir = File(downloadsDir, "P2PChat")
                if (!p2pDir.exists()) p2pDir.mkdirs()
                
                var outFile = File(p2pDir, sanitizedName)
                var counter = 1
                val baseName = outFile.nameWithoutExtension
                val extension = outFile.extension
                while (outFile.exists()) {
                    outFile = File(p2pDir, "${baseName}_$counter.$extension")
                    counter++
                }
                
                FileOutputStream(outFile).use { it.write(data) }
                ErrorLogger.i("FileHandler", "File saved to legacy path: ${outFile.absolutePath}")
                outFile
            }
        } catch (e: Exception) {
            ErrorLogger.e("FileHandler", "FH006", "Failed to save file: $fileName", e)
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
