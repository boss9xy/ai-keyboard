// DeepSeekAPI.kt

package com.example.aikeyboard

import android.util.Log
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.sync.Mutex
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class DeepSeekAPI(private val apiKey: String) {
    private val baseUrl = "https://api.deepseek.com"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

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

    private suspend fun makeRequest(connection: HttpURLConnection, jsonBody: JSONObject): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AIKeyboard", "Making API request to: ${connection.url}")
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

    private var isRequestPending = false
    private val requestMutex = Mutex()

    fun clearConversation() {
        Log.d("DeepSeekAPI", "Conversation cleared")
    }

    fun streamTranslate(text: String, targetLanguage: String, ic: InputConnection): Flow<String> = callbackFlow {
        // Add a new line and "Thinking..." text
        ic.commitText("\n", 1)
        val thinkingText = "Thinking..."
        ic.commitText(thinkingText, 1)
        // Delete the entire "Thinking..." text at once
        ic.deleteSurroundingText(thinkingText.length, 0)

        val prompt = "Translate the following text to $targetLanguage: $text"
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", "You are a professional translator. Translate the text directly without any explanations or additional context.")
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val jsonBody = JSONObject().apply {
            put("model", "deepseek-chat")
            put("messages", messages)
            put("stream", true)
            put("temperature", 0.7)
        }

        val request = Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.body?.use { responseBody ->
                        val reader = responseBody.charStream()
                        reader.forEachLine { line ->
                            if (line.startsWith("data: ") && line != "data: [DONE]") {
                                val jsonString = line.substring(6)
                                try {
                                    val jsonResponse = JSONObject(jsonString)
                                    val choices = jsonResponse.getJSONArray("choices")
                                    if (choices.length() > 0) {
                                        val choice = choices.getJSONObject(0)
                                        val delta = choice.getJSONObject("delta")
                                        if (delta.has("content")) {
                                            val content = delta.getString("content")
                                            trySend(content)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("DeepSeekAPI", "Error parsing JSON: $e")
                                }
                            }
                        }
                        close()
                    }
                } catch (e: Exception) {
                    close(e)
                }
            }
        })

        awaitClose {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    fun streamAskQuestion(question: String, ic: InputConnection): Flow<String> = callbackFlow {
        // Add a new line and "Thinking..." text
        ic.commitText("\n", 1)
        val thinkingText = "Thinking..."
        ic.commitText(thinkingText, 1)
        // Delete the entire "Thinking..." text at once
        ic.deleteSurroundingText(thinkingText.length, 0)

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", "You are a text formatter. Convert the provided text to 𝒃𝒐𝒍𝒅 𝒊𝒕𝒂𝒍𝒊𝒄 font style. Only output the converted text without any additional explanation or context.")
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", question)
            })
        }

        val jsonBody = JSONObject().apply {
            put("model", "deepseek-chat")
            put("messages", messages)
            put("stream", true)
            put("temperature", 0.7)
        }

        val request = Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.body?.use { responseBody ->
                        val reader = responseBody.charStream()
                        reader.forEachLine { line ->
                            if (line.startsWith("data: ") && line != "data: [DONE]") {
                                val jsonString = line.substring(6)
                                try {
                                    val jsonResponse = JSONObject(jsonString)
                                    val choices = jsonResponse.getJSONArray("choices")
                                    if (choices.length() > 0) {
                                        val choice = choices.getJSONObject(0)
                                        val delta = choice.getJSONObject("delta")
                                        if (delta.has("content")) {
                                            val content = delta.getString("content")
                                            trySend(content)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("DeepSeekAPI", "Error parsing JSON: $e")
                                }
                            }
                        }
                        close()
                    }
                } catch (e: Exception) {
                    close(e)
                }
            }
        })

        awaitClose {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun translate(text: String, targetLang: String, ic: InputConnection): String {
        if (isRequestPending) {
            Log.w("AIKeyboard", "Translate request ignored: Another request is already pending.")
            return "Request Ignored"
        }

        try {
            requestMutex.lock()
            isRequestPending = true

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

            return makeRequest(connection, jsonBody).also { response ->
                processResponse(response, text, ic)
            }
        } catch (e: Exception) {
            return "Error: ${e.message}".also {
                processResponse(it, text, ic)
            }
        } finally {
            requestMutex.unlock()
            isRequestPending = false
        }
    }

    suspend fun askQuestion(question: String, ic: InputConnection): String {
        if (isRequestPending) {
            Log.w("AIKeyboard", "AskQuestion request ignored: Another request is already pending.")
            return "Request Ignored"
        }

        try {
            requestMutex.lock()
            isRequestPending = true

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

            return makeRequest(connection, jsonBody).also { response ->
                processResponse(response, question, ic)
            }
        } catch (e: Exception) {
            return "Error: ${e.message}".also {
                processResponse(it, question, ic)
            }
        } finally {
            requestMutex.unlock()
            isRequestPending = false
        }
    }

    private fun processResponse(response: String, currentText: String, ic: InputConnection) {
        val thinkingText = "Thinking..."
        for (i in 0 until thinkingText.length) {
            ic.deleteSurroundingText(1, 0)
        }

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
            Log.e("AIKeyboard", "Full API response: $response", e)
            Logger.log("Error parsing API response. Full response: $response", e)
        }
    }
}