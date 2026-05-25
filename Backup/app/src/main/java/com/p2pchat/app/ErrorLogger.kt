package com.p2pchat.app

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ErrorLogger {
    private const val LOG_FILE = "p2pchat_errors.log"
    private var logFile: File? = null
    private var errorCount = 0

    fun init(context: Context) {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir
            logFile = File(dir, LOG_FILE)
            i("ErrorLogger", "Initialized", mapOf("path" to (logFile?.absolutePath ?: "null")))
        } catch (e: Exception) {
            android.util.Log.e("P2PChat:ErrorLogger", "E001: Failed to init logger", e)
        }
    }

    @JvmStatic
    fun e(tag: String, code: String, message: String, throwable: Throwable? = null) {
        errorCount++
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val throwableStr = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        } ?: ""

        val entry = buildString {
            appendLine("========================================")
            appendLine("ERROR #$errorCount [$code]")
            appendLine("Timestamp: $timestamp")
            appendLine("Tag: $tag")
            appendLine("Message: $message")
            if (throwableStr.isNotEmpty()) {
                appendLine("Stacktrace:")
                appendLine(throwableStr)
            }
            appendLine("========================================")
        }

        android.util.Log.e("P2PChat:$tag", "[$code] $message", throwable)

        logFile?.let { file ->
            try {
                FileWriter(file, true).use { it.append(entry) }
            } catch (ioe: Exception) {
                android.util.Log.e("P2PChat:ErrorLogger", "E002: Failed to write to log file", ioe)
            }
        }
    }

    @JvmStatic
    fun w(tag: String, code: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = "[WARN] [$code] $timestamp | $tag: $message"
        android.util.Log.w("P2PChat:$tag", "[$code] $message")

        logFile?.let { file ->
            try {
                FileWriter(file, true).use { it.appendLine(entry) }
            } catch (_: Exception) {}
        }
    }

    @JvmStatic
    fun i(tag: String, message: String, data: Map<String, String> = emptyMap()) {
        val dataStr = if (data.isNotEmpty()) {
            data.entries.joinToString(", ") { "${it.key}=${it.value}" }
        } else ""
        android.util.Log.i("P2PChat:$tag", if (dataStr.isNotEmpty()) "$message [$dataStr]" else message)
    }

    @JvmStatic
    fun d(tag: String, message: String) {
        android.util.Log.d("P2PChat:$tag", message)
    }

    fun getErrorCount(): Int = errorCount
}
