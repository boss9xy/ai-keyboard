package com.example.aikeyboard

import android.util.Log
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GPTAPI(private val apiKey: String, private val model: String = "gpt-3.5-turbo") {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Mapping of models to their maximum context window sizes
    private val modelContextWindows = mapOf(
        "gpt-3.5-turbo" to 4096,
        "gpt-3.5-turbo-1106" to 16_385,
        "o3-mini-2025-01-31" to 200_000,
        "gpt-4o-2024-11-20" to 128_000,
        "gpt-4o-mini-2024-07-18" to 128_000,
        "o1-2024-12-17" to 200_000,
        "o1-preview-2024-09-12" to 128_000,
        "gpt-4.5-preview-2025-02-27" to 128_000
    )

    // Store conversation history
    private val conversationHistory = mutableListOf<JSONObject>()
    private var lastFinishReasonValue: String? = null // Store last finish reason

    private fun calculateMaxTokens(model: String): Int {
        val contextWindow = modelContextWindows.getOrDefault(model, 4096)
        // Reserve about 25% of tokens for the response, leaving 75% for input
        val maxTokens = (contextWindow * 0.75).toInt()
        return minOf(maxTokens, 4096) // Ensure it does not exceed the model's max limit
    }

    init {
        Log.d("AIKeyboard", "GPT API initialized with key length: ${apiKey.length}, model: $model")
        // Add initial system message
        addSystemMessage("You are a helpful assistant. Please provide concise and accurate responses.")
    }

    private fun addSystemMessage(content: String) {
        val message = JSONObject().apply {
            put("role", "system")
            put("content", content)
        }
        conversationHistory.add(message)
    }

    private fun addUserMessage(content: String) {
        val message = JSONObject().apply {
            put("role", "user")
            put("content", content)
        }
        conversationHistory.add(message)
    }

    private fun addAssistantMessage(content: String) {
        val message = JSONObject().apply {
            put("role", "assistant")
            put("content", content)
        }
        conversationHistory.add(message)
    }

    fun getLastFinishReason(): String? = lastFinishReasonValue // Corrected to return the stored value

    fun clearConversation() {
        conversationHistory.clear()
        addSystemMessage("You are a helpful assistant. Please provide concise and accurate responses.")
        lastFinishReasonValue = null // Reset finish reason on conversation clear
    }

    // Stream chat completion
    fun streamChatCompletion(prompt: String, isContinue: Boolean = false, ic: InputConnection): Flow<String> = callbackFlow {
        // Add a new line and "Thinking..." text
        ic.commitText("\n", 1)
        val thinkingText = "Thinking..."
        ic.commitText(thinkingText, 1)
        // Delete the entire "Thinking..." text at once
        ic.deleteSurroundingText(thinkingText.length, 0)

        if (!isContinue) {
            addUserMessage(prompt)
        }

        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray(conversationHistory))
            put("stream", true)
            put("max_tokens", calculateMaxTokens(model))
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)
        var responseContent = StringBuilder()

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GPTAPI", "Chat completion failed", e)
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.body?.use { responseBody ->
                        val reader = responseBody.charStream()
                        reader.forEachLine { line ->
                            if (line.startsWith("data: ") && line != "data: [DONE]") {
                                val jsonData = JSONObject(line.substring(6))
                                val choices = jsonData.getJSONArray("choices")
                                if (choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    val delta = choice.optJSONObject("delta")
                                    delta?.optString("content", "")?.let { content ->
                                        if (content.isNotEmpty()) {
                                            responseContent.append(content)
                                            trySend(content)
                                        }
                                    }
                                    // Check finish_reason and store it
                                    choice.optString("finish_reason")?.let { reason ->
                                        if (reason.isNotEmpty()) {
                                            lastFinishReasonValue = reason // Store the finish reason
                                        }
                                    }
                                }
                            }
                        }
                        // Add assistant's complete response to history
                        if (responseContent.isNotEmpty()) {
                            addAssistantMessage(responseContent.toString())
                        }
                        close()
                    }
                } catch (e: Exception) {
                    Log.e("GPTAPI", "Chat completion response error", e)
                    close(e)
                }
            }
        })

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    // Function for translation
    suspend fun streamTranslate(text: String, targetLanguage: String, ic: InputConnection): Flow<String> {
        val prompt = "Translate the following text to $targetLanguage: $text"
        return streamChatCompletion(prompt, ic = ic)
    }

    // Function for asking questions
    suspend fun streamAskQuestion(question: String, ic: InputConnection): Flow<String> {
        return streamChatCompletion(question, ic = ic)
    }

    // Function to continue generation
    suspend fun streamContinueGeneration(ic: InputConnection): Flow<String> {
        return streamChatCompletion("Please continue.", isContinue = true, ic = ic)
    }
}