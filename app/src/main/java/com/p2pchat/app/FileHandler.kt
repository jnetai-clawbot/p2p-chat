package com.p2pchat.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream

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

    fun saveReceivedFile(fileName: String, data: ByteArray, subFolder: String = "P2PChat"): File? {
        val ctx = appContext ?: return null
        val sanitizedName = sanitizeFileName(fileName)
        val extension = File(sanitizedName).extension
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        val folderPath = subFolder.trim().let { if (it.isEmpty()) "" else it }
        val relativePath = if (folderPath.isEmpty()) Environment.DIRECTORY_DOWNLOADS else Environment.DIRECTORY_DOWNLOADS + "/" + folderPath
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = ctx.contentResolver
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val uri = resolver.insert(collection, contentValues)
                
                uri?.let {
                    resolver.openOutputStream(it)?.use { os ->
                        os.write(data)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                    
                    ErrorLogger.i("FileHandler", "File saved via MediaStore: $sanitizedName in $folderPath")
                    val relativeFolder = if (folderPath.isEmpty()) "" else "/$folderPath"
                    File("/sdcard/Download$relativeFolder/$sanitizedName")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val saveDir = if (folderPath.isEmpty()) downloadsDir else File(downloadsDir, folderPath)
                if (folderPath.isNotEmpty() && !saveDir.exists()) saveDir.mkdirs()
                
                var outFile = File(saveDir, sanitizedName)
                var counter = 1
                val baseName = outFile.nameWithoutExtension
                val ext = outFile.extension
                while (outFile.exists()) {
                    outFile = File(p2pDir, "${baseName}_$counter.$ext")
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
