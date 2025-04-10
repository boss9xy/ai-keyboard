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
    private lateinit var gptAssistantsIdEditText: EditText
    private lateinit var webUrlEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var enableKeyboardButton: Button
    private lateinit var selectKeyboardButton: Button

    companion object {
        private const val STORAGE_PERMISSION_CODE = 100
        private const val MICROPHONE_PERMISSION_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        Logger.initialize(this)
        Logger.log("SettingsActivity onCreate")

        checkStoragePermission()
        checkMicrophonePermission()

        deepseekApiKeyEditText = findViewById(R.id.deepseekApiKeyEditText)
        gptApiKeyEditText = findViewById(R.id.gptApiKeyEditText)
        gptAssistantsIdEditText = findViewById(R.id.gptAssistantsIdEditText)
        webUrlEditText = findViewById(R.id.webUrlEditText)
        saveButton = findViewById(R.id.saveButton)
        enableKeyboardButton = findViewById(R.id.enableKeyboardButton)
        selectKeyboardButton = findViewById(R.id.selectKeyboardButton)

        val prefs = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)
        deepseekApiKeyEditText.setText(prefs.getString("deepseek_api_key", ""))
        gptApiKeyEditText.setText(prefs.getString("gpt_api_key", ""))
        gptAssistantsIdEditText.setText(prefs.getString("gpt_assistants_id", ""))
        webUrlEditText.setText(prefs.getString("web_url", "https://real-time-gpt-translator-45.lovable.app"))

        saveButton.setOnClickListener {
            val deepseekApiKey = deepseekApiKeyEditText.text.toString().trim()
            val gptApiKey = gptApiKeyEditText.text.toString().trim()
            val gptAssistantsId = gptAssistantsIdEditText.text.toString().trim()
            val webUrl = webUrlEditText.text.toString().trim()
            Logger.log("Saving settings - DeepSeek key length: ${deepseekApiKey.length}, GPT key length: ${gptApiKey.length}")
            Logger.log("Saving settings - Assistants ID: $gptAssistantsId")
            Logger.log("Saving settings - Web URL: $webUrl")

            prefs.edit().apply {
                putString("deepseek_api_key", deepseekApiKey)
                putString("gpt_api_key", gptApiKey)
                putString("gpt_assistants_id", gptAssistantsId)
                putString("web_url", webUrl)
                apply()
            }

            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            Logger.log("Settings saved successfully")
        }

        enableKeyboardButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

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
            Toast.makeText(this, "Microphone permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Logger.log("Storage permission granted")
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