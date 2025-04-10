package com.example.aikeyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.ContextCompat

class ClipboardHelper(private val context: Context) {

    private val clipboardManager: ClipboardManager by lazy {
        ContextCompat.getSystemService(context, ClipboardManager::class.java)!!
    }

    // Maximum number of clipboard history items to store
    private val MAX_CLIPBOARD_HISTORY = 100

    // Persistent clipboard history storage
    private val clipboardHistory = mutableListOf<String>()

    /**
     * Copy text to clipboard and add to clipboard history
     */
    fun copyToClipboard(text: String) {
        val clipData = ClipData.newPlainText("AI Keyboard Copy", text)
        clipboardManager.setPrimaryClip(clipData)
        
        // Add to clipboard history, avoiding duplicates
        if (!clipboardHistory.contains(text)) {
            clipboardHistory.add(0, text)
            
            // Trim history if it exceeds maximum size
            if (clipboardHistory.size > MAX_CLIPBOARD_HISTORY) {
                clipboardHistory.removeAt(clipboardHistory.lastIndex)
            }
        }
    }

    /**
     * Get the current clipboard text
     */
    fun getCurrentClipboardText(): String? {
        return try {
            val primaryClip = clipboardManager.primaryClip
            val item = primaryClip?.getItemAt(0)
            item?.text?.toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get clipboard history
     */
    fun getClipboardHistory(): List<String> {
        return clipboardHistory.toList()
    }

    /**
     * Clear clipboard history
     */
    fun clearClipboardHistory() {
        clipboardHistory.clear()
    }

    /**
     * Paste text from clipboard
     */
    fun pasteFromClipboard(): String? {
        return getCurrentClipboardText()
    }

    /**
     * Check if clipboard contains text
     */
    fun hasClipboardText(): Boolean {
        return getCurrentClipboardText() != null
    }

    /**
     * Add listener for clipboard changes
     */
    fun addClipboardListener(listener: ClipboardManager.OnPrimaryClipChangedListener) {
        clipboardManager.addPrimaryClipChangedListener(listener)
    }

    /**
     * Remove clipboard change listener
     */
    fun removeClipboardListener(listener: ClipboardManager.OnPrimaryClipChangedListener) {
        clipboardManager.removePrimaryClipChangedListener(listener)
    }

    companion object {
        /**
         * Create a singleton instance of ClipboardHelper
         */
        @Volatile
        private var instance: ClipboardHelper? = null

        fun getInstance(context: Context): ClipboardHelper {
            return instance ?: synchronized(this) {
                instance ?: ClipboardHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}