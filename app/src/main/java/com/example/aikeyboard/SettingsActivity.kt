// SettingsActivity.kt
package com.example.aikeyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {
    private lateinit var deepseekApiKeyEditText: EditText
    private lateinit var gptApiKeyEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var enableKeyboardButton: Button
    private lateinit var selectKeyboardButton: Button
    private lateinit var ollamaBaseUrlEditText: EditText // New
    private lateinit var ollamaModelEditText: EditText // New


    companion object {
        private const val STORAGE_PERMISSION_CODE = 100
        private const val MICROPHONE_PERMISSION_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Khởi tạo logger
        Logger.initialize(this)
        Logger.log("SettingsActivity onCreate")

        // Kiểm tra và yêu cầu quyền truy cập bộ nhớ
        checkStoragePermission()

        // Kiểm tra và yêu cầu quyền truy cập microphone
        checkMicrophonePermission()

        // Khởi tạo views
        deepseekApiKeyEditText = findViewById(R.id.deepseekApiKeyEditText)
        gptApiKeyEditText = findViewById(R.id.gptApiKeyEditText)
        saveButton = findViewById(R.id.saveButton)
        enableKeyboardButton = findViewById(R.id.enableKeyboardButton)
        selectKeyboardButton = findViewById(R.id.selectKeyboardButton)
        ollamaBaseUrlEditText = findViewById(R.id.ollamaBaseUrlEditText) // New
        ollamaModelEditText = findViewById(R.id.ollamaModelEditText) // New


        // Load saved values
        val prefs = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)
        deepseekApiKeyEditText.setText(prefs.getString("deepseek_api_key", ""))
        gptApiKeyEditText.setText(prefs.getString("gpt_api_key", ""))
        ollamaBaseUrlEditText.setText(prefs.getString("ollama_base_url", "http://192.168.0.1:11434")) // Default URL
        ollamaModelEditText.setText(prefs.getString("ollama_model", "llama2")) // Default model


        // Save button click listener
        saveButton.setOnClickListener {
            val deepseekApiKey = deepseekApiKeyEditText.text.toString().trim()
            val gptApiKey = gptApiKeyEditText.text.toString().trim()
            val ollamaBaseUrl = ollamaBaseUrlEditText.text.toString().trim() // New
            val ollamaModel = ollamaModelEditText.text.toString().trim() // New


            Logger.log("Saving settings - DeepSeek key length: ${deepseekApiKey.length}, GPT key length: ${gptApiKey.length}, Ollama URL: $ollamaBaseUrl, Ollama Model: $ollamaModel")

            // Validate API keys (you can add validation for Ollama URL if needed)

            // Save to SharedPreferences
            prefs.edit().apply {
                putString("deepseek_api_key", deepseekApiKey)
                putString("gpt_api_key", gptApiKey)
                putString("ollama_base_url", ollamaBaseUrl) // New
                putString("ollama_model", ollamaModel)     // New
                apply()
            }

            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            Logger.log("Settings saved successfully")
        }

        // Enable keyboard button click listener
        enableKeyboardButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        // Select keyboard button click listener
        selectKeyboardButton.setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
    }

    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                STORAGE_PERMISSION_CODE
            )
        }
    }

    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_PERMISSION_CODE)
        } else {
            // Permission already granted
            Toast.makeText(this, "Microphone permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Logger.log("Storage permission granted")
                // Khởi tạo lại logger sau khi được cấp quyền
                Logger.initialize(this)
            } else {
                Logger.log("Storage permission denied")
                Toast.makeText(this, "Storage permission is required for logging", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == MICROPHONE_PERMISSION_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Microphone permission denied. Please enable it in settings.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
