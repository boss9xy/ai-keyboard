package com.example.aikeyboard

import android.util.Log
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AssistantsAPI(private val apiKey: String, private val assistantId: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain: Interceptor.Chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .header("OpenAI-Beta", "assistants=v2")
                .build()
            Log.d("AssistantsAPI", "Request headers: ${newRequest.headers}")
            chain.proceed(newRequest)
        }
        .build()

    private var threadId: String? = null
    private var lastRunStatus: String? = null

    fun getLastFinishReason(): String? = lastRunStatus

    fun clearConversation() {
        threadId = null
        lastRunStatus = null
        Log.d("AssistantsAPI", "Conversation cleared")
    }

    private suspend fun ensureThreadExists(): String {
        if (threadId == null) {
            val requestBody = JSONObject().toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.openai.com/v1/threads")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    throw IOException("Thread creation failed: ${response.code} - $errorBody")
                }
                val jsonResponse = JSONObject(response.body?.string() ?: "{}")
                threadId = jsonResponse.getString("id")
                Log.d("AssistantsAPI", "Thread created: $threadId")
            }
        }
        return threadId!!
    }

    private fun sendMessageToThread(content: String) {
        val modifiedContent = content
        val messageBody = JSONObject().apply {
            put("role", "user")
            put("content", modifiedContent)
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/threads/$threadId/messages")
            .addHeader("Content-Type", "application/json")
            .post(messageBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw IOException("Message sending failed: ${response.code} - $errorBody")
            }
            Log.d("AssistantsAPI", "Message sent: $modifiedContent")
        }
    }

    private fun runAssistant(): String {
        val runBody = JSONObject().apply {
            put("assistant_id", assistantId)
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/threads/$threadId/runs")
            .addHeader("Content-Type", "application/json")
            .post(runBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw IOException("Run failed: ${response.code} - $errorBody")
            }
            val jsonResponse = JSONObject(response.body?.string() ?: "{}")
            val runId = jsonResponse.getString("id")
            Log.d("AssistantsAPI", "Run started with ID: $runId")
            return runId
        }
    }

    private suspend fun getResponse(runId: String): String? {
        val request = Request.Builder()
            .url("https://api.openai.com/v1/threads/$threadId/runs/$runId")
            .build()

        while (true) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    throw IOException("Run check failed: ${response.code} - $errorBody")
                }
                val jsonResponse = JSONObject(response.body?.string() ?: "{}")
                val status = jsonResponse.getString("status")
                Log.d("AssistantsAPI", "Run status: $status")
                if (status == "completed") {
                    val messagesRequest = Request.Builder()
                        .url("https://api.openai.com/v1/threads/$threadId/messages")
                        .build()
                    client.newCall(messagesRequest).execute().use { msgResponse ->
                        if (!msgResponse.isSuccessful) {
                            val errorBody = msgResponse.body?.string() ?: "Unknown error"
                            throw IOException("Messages fetch failed: ${msgResponse.code} - $errorBody")
                        }
                        val messagesJson = JSONObject(msgResponse.body?.string() ?: "{}")
                        val messages = messagesJson.getJSONArray("data")
                        for (i in 0 until messages.length()) {
                            val message = messages.getJSONObject(i)
                            if (message.getString("role") == "assistant" && message.getJSONArray("content").length() > 0) {
                                val contentArray = message.getJSONArray("content")
                                val contentObj = contentArray.getJSONObject(0)
                                if (contentObj.getString("type") == "text") {
                                    val responseText = contentObj.getJSONObject("text").getString("value")
                                    Log.d("AssistantsAPI", "Received response: $responseText")
                                    try {
                                        val responseJson = JSONObject(responseText)
                                        return responseJson.getString("response")
                                    } catch (e: Exception) {
                                        return responseText
                                    }
                                }
                            }
                        }
                    }
                } else if (status in listOf("failed", "expired")) {
                    lastRunStatus = status
                    throw IOException("Run $status")
                }
            }
            delay(1000)
        }
    }

    fun sendMessage(content: String): Flow<String> = callbackFlow {

        try {
            ensureThreadExists() // Đảm bảo thread tồn tại, tái sử dụng nếu đã có
            sendMessageToThread(content)
            val runId = runAssistant()
            val response = getResponse(runId)
            if (response != null) {
                trySend(response)
            }
            lastRunStatus = "completed"
            close()
        } catch (e: Exception) {
            Log.e("AssistantsAPI", "Error in sendMessage", e)
            trySend("Error: ${e.message}")
            close(e)
        }
    }.flowOn(Dispatchers.IO)
}