package com.example.aikeyboard

import android.util.Log
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.sync.Mutex

class DeepSeekAPI(private val apiKey: String) {
    private val baseUrl = "https://api.deepseek.com"

    init {
        Log.d("AIKeyboard", "API initialized with key length: ${apiKey.length}")
    }

    private fun createConnection(): HttpURLConnection {
        val url = URL("$baseUrl/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.doOutput = true
        return connection
    }

    // Hàm gọi API thông thường (Không streaming)
    private suspend fun makeRequest(connection: HttpURLConnection, jsonBody: JSONObject): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AIKeyboard", "Making API request to: ${connection.url}")
                Log.d("AIKeyboard", "Request body: ${jsonBody.toString().take(100)}...")

                // Ghi dữ liệu vào request body
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(jsonBody.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                Log.d("AIKeyboard", "Response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        val response = reader.readText()
                        Log.d("AIKeyboard", "API Response: ${response.take(100)}...")
                        return@withContext response
                    }
                } else {
                    val errorStream = connection.errorStream
                    val errorResponse = errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    Log.e("AIKeyboard", "API error: $errorResponse")
                    throw Exception("API request failed with code: $responseCode - $errorResponse")
                }
            } catch (e: Exception) {
                Log.e("AIKeyboard", "API request failed", e)
                throw Exception("API request failed: ${e.message}")
            } finally {
                connection.disconnect()
            }
        }
    }

    private var isRequestPending = false // Biến cờ
    private val requestMutex = Mutex() // Mutex để đồng bộ

    // Hàm dịch (Không streaming)
    suspend fun translate(text: String, targetLang: String, ic: InputConnection) {
        if (isRequestPending) {
            Log.w("AIKeyboard", "Translate request ignored: Another request is already pending.")
            return // Bỏ qua yêu cầu nếu đang chờ
        }

        try {
            requestMutex.lock()
            isRequestPending = true
            ic.commitText("Thinking...", 1)

            val connection = createConnection()
            val prompt = "Translate the following text to $targetLang: $text"
            val jsonBody = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.7)
                put("max_tokens", 8000)
                put("presence_penalty", 0.1)
                put("frequency_penalty", 0.1)
            }

            val response = makeRequest(connection, jsonBody)
            withContext(Dispatchers.Main) {
                processResponse(response, text, ic)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                processResponse("Error: ${e.message}", text, ic)
            }
        } finally {
            requestMutex.unlock()
            isRequestPending = false
        }
    }

    // Hàm hỏi đáp (Không streaming)
    suspend fun askQuestion(question: String, ic: InputConnection) {
        if (isRequestPending) {
            Log.w("AIKeyboard", "AskQuestion request ignored: Another request is already pending.")
            return // Bỏ qua yêu cầu nếu đang chờ
        }

        try {
            requestMutex.lock()
            isRequestPending = true
            ic.commitText("Thinking...", 1)

            val connection = createConnection()
            val jsonBody = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are a helpful assistant.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", question)
                    })
                })
                put("temperature", 0.7)
                put("max_tokens", 8000)
            }

            val response = makeRequest(connection, jsonBody)
            withContext(Dispatchers.Main) {
                processResponse(response, question, ic)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                processResponse("Error: ${e.message}", question, ic)
            }
        } finally {
            requestMutex.unlock()
            isRequestPending = false
        }
    }

    // Xử lý response
    private fun processResponse(response: String, currentText: String, ic: InputConnection) {
        // Xóa "Thinking..."
        val thinkingText = "Thinking..."
        for (i in 0 until thinkingText.length) {
            ic.deleteSurroundingText(1, 0)
        }

        // Parse nội dung từ JSON
        try {
            val jsonResponse = JSONObject(response)
            val content = jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            ic.commitText("\n$content", 1)
        } catch (e: Exception) {
            ic.commitText("\nError parsing response", 1)
            Log.e("AIKeyboard", "Error parsing API response", e)
            Log.e("AIKeyboard", "Full API response: $response", e)  // Log the full response
            Logger.log("Error parsing API response. Full response: $response", e) // Use Logger to log to file
        }
    }
}