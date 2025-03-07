package com.example.aikeyboard

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GPTAPI(private val apiKey: String, private val model: String = "gpt-3.5-turbo-1106") {
    private val baseUrl = "https://api.openai.com/v1"

    // Mapping of models to their maximum context window sizes
    private val modelContextWindows = mapOf(
        "gpt-3.5-turbo-1106" to 16_385,
        "o3-mini-2025-01-31" to 200_000,
        "gpt-4o-2024-11-20" to 128_000,
        "gpt-4o-mini-2024-07-18" to 128_000,
        "o1-2024-12-17" to 200_000,
        "o1-preview-2024-09-12" to 128_000,
        "gpt-4.5-preview-2025-02-27" to 128_000
    )

    // Function to calculate max tokens based on the model's context window
    private fun calculateMaxTokens(model: String): Int {
        val contextWindow = modelContextWindows.getOrDefault(model, 4096)
        // Reserve about 25% of tokens for the response, leaving 75% for input
        return (contextWindow * 0.75).toInt()
    }

    init {
        Log.d("AIKeyboard", "GPT API initialized with key length: ${apiKey.length}, model: $model")
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

    // Hàm gọi API thông thường (không streaming)
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
                    Log.e("AIKeyboard", "GPT API error: $errorResponse")
                    throw Exception("GPT API request failed with code: $responseCode - $errorResponse")
                }
            } catch (e: Exception) {
                Log.e("AIKeyboard", "GPT API request failed", e)
                throw Exception("GPT API request failed: ${e.message}")
            } finally {
                connection.disconnect()
            }
        }
    }

    private var isRequestPending = false // Biến cờ
    private val requestMutex = Mutex() // Mutex để đồng bộ

    // Hàm dịch (Không streaming)
    suspend fun translate(text: String, targetLanguage: String): String {
        if (isRequestPending) {
            Log.w("AIKeyboard", "GPT Translate request ignored: Another request is already pending.")
            return "Request Ignored (Another request pending)" // Return a message indicating the request was ignored
        }

        return withContext(Dispatchers.Main) {
            try {
                requestMutex.lock()
                isRequestPending = true

                val connection = createConnection()
                val prompt = "Translate the following text to $targetLanguage. Only respond with the translation, no explanations: $text"

                val maxTokens = calculateMaxTokens(model)
                Log.d("AIKeyboard", "Using max_tokens: $maxTokens for model: $model")

                val jsonBody = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are a helpful translator. Only respond with the translated text, no explanations.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.7)
                    put("max_tokens", maxTokens)
                }

                parseResponse(makeRequest(connection, jsonBody))
            } catch (e: Exception) {
                Log.e("AIKeyboard", "GPT Translate error", e)
                Logger.log("GPT Translate error: ${e.message}", e)  // Log the error
                "Error: ${e.message}"
            } finally {
                requestMutex.unlock()
                isRequestPending = false
            }
        }
    }

    // Hàm hỏi đáp (Không streaming)
    suspend fun askQuestion(question: String): String {
        if (isRequestPending) {
            Log.w("AIKeyboard", "GPT AskQuestion request ignored: Another request is already pending.")
            return "Request Ignored (Another request pending)" // Return a message indicating the request was ignored
        }

        return withContext(Dispatchers.Main) {
            try {
                requestMutex.lock()
                isRequestPending = true

                val connection = createConnection()
                val maxTokens = calculateMaxTokens(model)
                Log.d("AIKeyboard", "Using max_tokens: $maxTokens for model: $model")

                val jsonBody = JSONObject().apply {
                    put("model", model)
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
                    put("max_tokens", maxTokens)
                }

                val response = parseResponse(makeRequest(connection, jsonBody))
                
                // Simulate the DeepSeek "thinking..." deletion mechanism and add newline
                "\n" + response.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.lastOrNull() ?: response
            } catch (e: Exception) {
                Log.e("AIKeyboard", "GPT Ask error", e)
                Logger.log("GPT Ask error: ${e.message}", e)  // Log the error
                "Error: ${e.message}"
            } finally {
                requestMutex.unlock()
                isRequestPending = false
            }
        }
    }

    // Hàm parse JSON response để lấy nội dung
    private fun parseResponse(response: String): String {
        return try {
            val jsonResponse = JSONObject(response)
            val content = jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            Log.d("AIKeyboard", "GPT API Content: $content")
            Logger.log("GPT API Content: $content")

            content
        } catch (e: Exception) {
            Log.e("AIKeyboard", "Error parsing GPT API response", e)
            Log.e("AIKeyboard", "Full GPT API response: $response", e)
            Logger.log("Error parsing GPT API response. Full response: $response", e)
            "Error parsing response"
        }
    }
}