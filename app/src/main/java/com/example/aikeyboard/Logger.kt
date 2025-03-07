package com.example.aikeyboard

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private const val TAG = "AIKeyboard"
    private var logFile: File? = null

    fun initialize(context: Context) {
        try {
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            logFile = File(logDir, "aikeyboard_$timestamp.log")
            
            log("Logger initialized. Log file: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing logger", e)
        }
    }

    fun log(message: String, throwable: Throwable? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message"
        
        // Log to Android's logcat
        if (throwable != null) {
            Log.e(TAG, logMessage, throwable)
        } else {
            Log.d(TAG, logMessage)
        }

        // Log to file
        try {
            logFile?.let { file ->
                FileWriter(file, true).use { writer ->
                    writer.append(logMessage)
                    if (throwable != null) {
                        writer.append("\nException: ${throwable.message}")
                        throwable.stackTrace.forEach { element ->
                            writer.append("\n    at $element")
                        }
                    }
                    writer.append("\n")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to log file", e)
        }
    }

    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }
}
