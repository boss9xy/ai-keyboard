package com.example.aikeyboard

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private const val TAG = "AIKeyboard"
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5MB
    private const val MAX_LOG_FILES = 5 // Giới hạn số file log
    private var logFile: File? = null
    private var context: Context? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Khởi tạo Logger với context
     */
    fun initialize(context: Context) {
        this.context = context
        try {
            val logDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            setupLogFile(logDir)
            log("Logger initialized. Log file: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing logger: ${e.message}", e)
        }
    }

    /**
     * Thiết lập file log mới hoặc sử dụng file hiện có
     */
    private fun setupLogFile(logDir: File) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val newLogFile = File(logDir, "aikeyboard_$timestamp.log")

            // Kiểm tra kích thước file hiện tại
            if (logFile != null && logFile!!.exists() && logFile!!.length() >= MAX_FILE_SIZE) {
                logFile = newLogFile
                cleanupOldLogs(logDir)
            } else if (logFile == null || !logFile!!.exists()) {
                logFile = newLogFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up log file: ${e.message}", e)
        }
    }

    /**
     * Xóa các file log cũ nếu vượt quá giới hạn
     */
    private fun cleanupOldLogs(logDir: File) {
        try {
            val logFiles = logDir.listFiles { file -> file.name.startsWith("aikeyboard_") && file.name.endsWith(".log") }
                ?.sortedBy { it.lastModified() } // Sắp xếp theo thời gian sửa đổi

            if (logFiles != null && logFiles.size >= MAX_LOG_FILES) {
                val filesToDelete = logFiles.take(logFiles.size - MAX_LOG_FILES + 1)
                filesToDelete.forEach { file ->
                    if (file.delete()) {
                        Log.d(TAG, "Deleted old log file: ${file.absolutePath}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old logs: ${e.message}", e)
        }
    }

    /**
     * Ghi log với thông điệp và tùy chọn throwable
     */
    @Synchronized
    fun log(message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] $message"

        // Ghi vào Logcat
        if (throwable != null) {
            Log.e(TAG, logMessage, throwable)
        } else {
            Log.d(TAG, logMessage)
        }

        // Ghi vào file
        try {
            val currentLogFile = logFile
            if (currentLogFile == null || context == null) {
                Log.e(TAG, "Logger not initialized or context missing")
                return
            }

            // Kiểm tra và xoay file nếu cần
            if (currentLogFile.length() >= MAX_FILE_SIZE) {
                setupLogFile(File(context!!.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "logs"))
            }

            BufferedWriter(FileWriter(currentLogFile, true)).use { writer ->
                writer.append(logMessage)
                if (throwable != null) {
                    writer.newLine()
                    writer.append("Exception: ${throwable.message}")
                    throwable.stackTrace.forEach { element ->
                        writer.newLine()
                        writer.append("    at $element")
                    }
                }
                writer.newLine()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to log file: ${e.message}", e)
        }
    }

    /**
     * Lấy đường dẫn file log hiện tại
     */
    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }

    /**
     * Xóa tất cả file log (dùng khi cần reset)
     */
    fun clearLogs() {
        try {
            val logDir = File(context?.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "logs")
            logDir.listFiles { file -> file.name.endsWith(".log") }?.forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "Deleted log file: ${file.absolutePath}")
                }
            }
            logFile = null
            setupLogFile(logDir)
            log("All logs cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing logs: ${e.message}", e)
        }
    }
}