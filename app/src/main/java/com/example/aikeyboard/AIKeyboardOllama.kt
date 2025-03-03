// AIKeyboardOllama.kt
package com.example.aikeyboard

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AIKeyboardOllama(private val baseUrl: String) {

    init {
        Log.d("AIKeyboard", "Ollama API initialized with base URL: $baseUrl")
    }

    private fun createConnection(): HttpURLConnection {
        val url = URL("$baseUrl/api/chat") // Ollama chat API endpoint
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        return connection
    }

    suspend fun askQuestion(question: String, model: String): String {
        val connection = createConnection()
        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", question)
                })
            })
            put("stream", false) // For non-streaming response
        }

        return makeRequest(connection, jsonBody)
    }

    private suspend fun makeRequest(connection: HttpURLConnection, jsonBody: JSONObject): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AIKeyboard", "Making Ollama API request to: ${connection.url}")
                Log.d("AIKeyboard", "Request body: ${jsonBody.toString().take(100)}...")

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(jsonBody.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                Log.d("AIKeyboard", "Response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        val response = reader.readText()
                        Log.d("AIKeyboard", "Ollama API Response: ${response.take(100)}...")
                        return@withContext parseResponse(response)
                    }
                } else {
                    val errorStream = connection.errorStream
                    val errorResponse = errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    Log.e("AIKeyboard", "Ollama API error: $errorResponse")
                    throw Exception("Ollama API request failed with code: $responseCode - $errorResponse")
                }
            } catch (e: Exception) {
                Log.e("AIKeyboard", "Ollama API request failed", e)
                throw Exception("Ollama API request failed: ${e.message}")
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseResponse(response: String): String {
        return try {
            val jsonResponse = JSONObject(response)
            jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getString("text") // Assuming Ollama's response structure is similar to OpenAI/DeepSeek
        } catch (e: Exception) {
            Log.e("AIKeyboard", "Error parsing Ollama API response", e)
            "Error parsing response"
        }
    }
}