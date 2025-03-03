// OllamaChatActivity.kt
package com.example.aikeyboard

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OllamaChatActivity : AppCompatActivity() {

    private lateinit var etChatInput: EditText
    private lateinit var btnSendMessage: Button
    private lateinit var tvChat: TextView
    private lateinit var btnOllamaSettings: Button
    private lateinit var ollamaAPI: AIKeyboardOllama
    private var chatText = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ollama_chat)

        etChatInput = findViewById(R.id.etChatInput)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        tvChat = findViewById(R.id.tvChat)
        btnOllamaSettings = findViewById(R.id.btnOllamaSettings)

        // Initialize Ollama API with default base URL, you can get this from SharedPreferences later
        val ollamaBaseUrl = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)
            .getString("ollama_base_url", "http://192.168.0.1:11434") ?: "http://192.168.0.1:11434"
        ollamaAPI = AIKeyboardOllama(ollamaBaseUrl)

        btnSendMessage.setOnClickListener {
            sendMessage()
        }

        btnOllamaSettings.setOnClickListener {
            // Navigate to settings, you can extend SettingsActivity to include Ollama settings
            // For now, let's just go to the general settings
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
    }

    private fun sendMessage() {
        val message = etChatInput.text.toString()
        if (message.isNotBlank()) {
            appendMessage("You: $message")
            etChatInput.text.clear()
            getOllamaResponse(message)
        }
    }

    private fun getOllamaResponse(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val ollamaModel = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)
                    .getString("ollama_model", "llama2") ?: "llama2" // Default model
                val response = withContext(Dispatchers.IO) {
                    ollamaAPI.askQuestion(message, ollamaModel)
                }
                appendMessage("Ollama: $response")
            } catch (e: Exception) {
                Log.e("OllamaChat", "Error getting Ollama response", e)
                appendMessage("Error: ${e.message}")
            }
        }
    }

    private fun appendMessage(newMessage: String) {
        chatText.append("\n").append(newMessage)
        tvChat.text = chatText
    }
}