// WelcomeActivity.kt
package com.example.aikeyboard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val btnOllamaChat = findViewById<Button>(R.id.btnOllamaChat)
        val btnSettings = findViewById<Button>(R.id.btnSettings)

        btnOllamaChat.setOnClickListener {
            startActivity(Intent(this, OllamaChatActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}