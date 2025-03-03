package com.example.aikeyboard

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.app.ActivityCompat
import com.example.aikeyboard.text.TelexComposer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max

class AIKeyboardService : InputMethodService(), TextToSpeech.OnInitListener {
    private var keyboard: View? = null
    private var keyPopupText: TextView? = null
    private var keyPopup: PopupWindow? = null
    private var btnPasteAndRead: Button? = null
    private var btnStopTts: Button? = null
    private var btnSmartbarToggle: Button? = null
    private var smartbarScrollView: HorizontalScrollView? = null
    private var translateButton: Button? = null
    private var askButton: Button? = null
    private var languageSpinner: Spinner? = null
    private var preferences: SharedPreferences? = null

    private var telexComposer = TelexComposer()
    private var vietnameseInputBuffer = StringBuilder()

    private var currentLanguage = "Vietnamese"
    private val supportedLanguages = listOf(
        "Vietnamese", "English", "Chinese", "Japanese", "Korean",
        "French", "German", "Spanish", "Russian", "Italian"
    )

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var textToSpeech: TextToSpeech? = null
    private var lastDetectedLanguage = "vi"
    private var isShiftEnabled = false
    private var isSymbolMode = false
    private var isVietnameseMode = true
    private val requestMutex = Mutex()
    private val ttsScope = CoroutineScope(Dispatchers.Main)

    private var deepSeekAPI: DeepSeekAPI? = null
    private var gptAPI: GPTAPI? = null
    private val clipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    private val deleteHandler = Handler(Looper.getMainLooper())
    private val deleteRunnable = object : Runnable {
        override fun run() {
            handleBackspace()
            deleteHandler.postDelayed(this, 400)
        }
    }

    private var thinkingTextLength = 0

    private val handler = Handler(Looper.getMainLooper())
    private var popupHideRunnable: Runnable? = null

    private val backspaceHandler = Handler(Looper.getMainLooper())
    private val BACKSPACE_INITIAL_DELAY = 400L  // Độ trễ ban đầu
    private val BACKSPACE_INITIAL_INTERVAL = 100L  // Tốc độ xóa ban đầu
    private val BACKSPACE_MIN_INTERVAL = 20L  // Tốc độ xóa tối đa
    private val BACKSPACE_ACCELERATION = 10L  // Mức giảm interval sau mỗi lần xóa
    private var currentBackspaceInterval = BACKSPACE_INITIAL_INTERVAL

    private var shiftableButtons: List<Button> = emptyList()

    // Calculator related variables
    private var btnTinhToan: Button? = null
    private var calculatorKeyboard: View? = null
    private var calculatorResult: TextView? = null
    private var calculatorExpression = StringBuilder()
    private var lastCalculationResult: Double? = null
    private var calculatorPopup: PopupWindow? = null

    // Calculator keyboard keys
    private val calculatorKeys = arrayOf(
        "7", "8", "9", "÷",
        "4", "5", "6", "×",
        "1", "2", "3", "-",
        "0", ".", "=", "+"
    )

    private val normalKeys = arrayOf(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
        "a", "s", "d", "f", "g", "h", "j", "k", "l",
        "⇧", "z", "x", "c", "v", "b", "n", "m", "⌫",
        "123", " ", "↵"
    )

    private val symbolKeys = arrayOf(
        "!", "@", "#", "$", "%", "^", "&", "*", "(", ")",
        "+", "=", "{", "}", "[", "]", "|", "\\", "/", "?",
        "~", "`", "<", ">", ":", ";", "\"", "'", "-",
        "⇧", "_", ",", ".", "€", "£", "¥", "§", "⌫",
        "ABC", " ", "↵"
    )

    // --- Inner Classes for API (Non-Streaming) ---
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

        suspend fun translate(text: String, targetLang: String): String {
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
                put("stream", false) // Make sure stream is false
            }

            val response = makeRequest(connection, jsonBody)
            return parseResponse(response) // Use a helper function to extract content
        }

        suspend fun askQuestion(question: String): String {
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
                put("stream", false) // Make sure stream is false
            }

            val response = makeRequest(connection, jsonBody)
            return parseResponse(response) // Use a helper function to extract content
        }
        private fun parseResponse(response: String): String {
            return try {
                val jsonResponse = JSONObject(response)
                jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } catch (e: Exception) {
                Log.e("AIKeyboard", "Error parsing API response", e)
                "Error parsing response"
            }
        }
    }

    class GPTAPI(private val apiKey: String, private val model: String = "gpt-3.5-turbo") {
        private val baseUrl = "https://api.openai.com/v1"

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

        suspend fun translate(text: String, targetLanguage: String): String {
            val connection = createConnection()
            val prompt = "Translate the following text to $targetLanguage. Only respond with the translation, no explanations: $text"

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
                put("max_tokens", 2048)
                put("stream", false) // Ensure streaming is off
            }

            return parseResponse(makeRequest(connection, jsonBody))
        }

        suspend fun askQuestion(question: String): String {
            val connection = createConnection()
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
                put("max_tokens", 4096)
                put("stream", false)  // Ensure streaming is off

            }

            return parseResponse(makeRequest(connection, jsonBody))
        }

        private fun parseResponse(response: String): String {
            return try {
                val jsonResponse = JSONObject(response)
                jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } catch (e: Exception) {
                Log.e("AIKeyboard", "Error parsing API response", e)
                "Error parsing response"
            }
        }
    }


    override fun onCreate() {
        super.onCreate()
        Logger.initialize(this)
        Logger.log("AIKeyboardService onCreate")
        initializeAPIs()
        preferences = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        textToSpeech = TextToSpeech(this, this)
        Log.d("AIKeyboard", "Service created")

        // Register clipboard listener
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)

        setupCalculatorKeyboard()

        // Initialize speech recognizer
        setupSpeechRecognition()

    }

    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false
    private val timeoutRunnable = Runnable { stopListening() }
    private fun setupSpeechRecognition() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            private var lastRecognizedText: String? = null

            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                // Bắt đầu đếm thởi gian chờ
                handler.postDelayed(timeoutRunnable, 5000) // 5 giây timeout
            }

            override fun onBeginningOfSpeech() {
                // Reset timeout khi bắt đầu nói
                handler.removeCallbacks(timeoutRunnable)
                handler.postDelayed(timeoutRunnable, 5000)
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Có thể sử dụng để hiển thị mức độ âm thanh
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                stopListening()
            }

            override fun onError(error: Int) {
                stopListening()
                Toast.makeText(this@AIKeyboardService, "Speech recognition error: $error", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches == null || matches.isEmpty()) {
                    stopListening() // Stop listening even if no final results to avoid hanging
                    Toast.makeText(this@AIKeyboardService, "No speech recognized.", Toast.LENGTH_SHORT).show()
                }
                // Stop listening and reset
                stopListening()
                lastRecognizedText = null
            }

            override fun onPartialResults(partialResults: Bundle) {
                val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    // Get the last (most complete) recognized text
                    val currentText = matches[matches.size - 1]

                    // Only update if the text is different from the last recognized text
                    if (currentText != lastRecognizedText) {
                        // Update temporary speech text
                        temporarySpeechText = currentText

                        // Restore original text with new speech text
                        currentInputConnection?.deleteSurroundingText(Integer.MAX_VALUE, Integer.MAX_VALUE)
                        currentInputConnection?.commitText(originalInputText + temporarySpeechText, 1)

                        // Update last recognized text
                        lastRecognizedText = currentText
                    }

                    // Clear any composing text
                    currentInputConnection?.finishComposingText()
                }
                // Reset timeout
                handler.removeCallbacks(timeoutRunnable)
                handler.postDelayed(timeoutRunnable, 5000)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListeningMic() {
        if (!isListening) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer.startListening(intent)
        }
    }

    private fun stopListening() {
        if (isListening) {
            isListening = false
            speechRecognizer.stopListening()
            handler.removeCallbacks(timeoutRunnable)
        }
    }

    private fun clearComposingText() {
        // Explicitly clear any composing text state
        currentInputConnection?.let {
            it.finishComposingText()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                // Check if there's a recognized text that needs to be preserved
                try {
                    val recognitionListenerField = speechRecognizer.javaClass.getDeclaredField("mRecognitionListener")
                    recognitionListenerField.isAccessible = true
                    val recognitionListener = recognitionListenerField.get(speechRecognizer)

                    // Use reflection to access the lastRecognizedText
                    val lastTextField = recognitionListener.javaClass.getDeclaredField("lastRecognizedText")
                    lastTextField.isAccessible = true
                    val lastText = lastTextField.get(recognitionListener) as? String

                    if (lastText != null) {
                        // If there's a last recognized text, add space without deleting
                        currentInputConnection?.commitText(" ", 1)
                        true
                    } else {
                        // Normal space behavior if no recognized text
                        super.onKeyDown(keyCode, event)
                    }
                } catch (e: Exception) {
                    // Fallback to default space handling
                    super.onKeyDown(keyCode, event)
                }
            }
            // ... rest of the existing onKeyDown code remains the same
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun initializeAPIs() {
        Logger.log("Initializing APIs")
        val prefs = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)

        val deepseekApiKey = prefs.getString("deepseek_api_key", "") ?: ""
        val gptApiKey = prefs.getString("gpt_api_key", "") ?: ""
        val gptModel = prefs.getString("gpt_model", "gpt-3.5-turbo") ?: "gpt-3.5-turbo"

        Log.e("AIKeyboard", "DEBUGGING: SharedPreferences GPT Model: $gptModel")
        Log.e("AIKeyboard", "DEBUGGING: All SharedPreferences contents: ${prefs.all}")

        Logger.log("DeepSeek API key length: ${deepseekApiKey.length}")
        Logger.log("GPT API key length: ${gptApiKey.length}")
        Logger.log("GPT model: $gptModel")

        try {
            if (deepseekApiKey.isNotEmpty()) {
                deepSeekAPI = DeepSeekAPI(deepseekApiKey)
                Logger.log("DeepSeek API initialized successfully")
            } else {
                Logger.log("DeepSeek API key is empty")
            }

            if (gptApiKey.isNotEmpty()) {
                gptAPI = GPTAPI(gptApiKey, gptModel)
                Logger.log("GPT API initialized successfully with model: $gptModel")
            } else {
                Logger.log("GPT API key is empty")
            }
        } catch (e: Exception) {
            Logger.log("Error initializing APIs", e)
        }
    }

    private fun getApiKey(): String? {
        val key = preferences?.getString("api_key", "")?.takeIf { it.isNotEmpty() }
        Log.d("AIKeyboard", "Retrieved API key: ${if (key != null) "Present" else "Empty"}")
        return key
    }

    private fun loadApiKeys() {
        val prefs = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)
        val deepseekKey = prefs.getString("deepseek_api_key", "") ?: ""
        val gptKey = prefs.getString("gpt_api_key", "") ?: ""
        deepSeekAPI = DeepSeekAPI(deepseekKey)
        gptAPI = GPTAPI(gptKey)
    }

    private fun initializeViews() {
        Log.d("AIKeyboard", "Initializing views")
        btnPasteAndRead = keyboard?.findViewById(R.id.btnPasteAndRead)
        btnStopTts = keyboard?.findViewById(R.id.btnStopTts)
        translateButton = keyboard?.findViewById(R.id.translateButton)
        askButton = keyboard?.findViewById(R.id.askButton)
        languageSpinner = keyboard?.findViewById(R.id.languageSpinner)
        btnSmartbarToggle = keyboard?.findViewById(R.id.btnSmartbarToggle)
        smartbarScrollView = keyboard?.findViewById(R.id.smartbarScrollView)

        smartbarScrollView?.visibility = View.VISIBLE
        Log.d("AIKeyboard", "Views initialized, smartbar visibility: ${smartbarScrollView?.visibility}")
    }

    private fun setupLanguageSpinner() {
        languageSpinner = keyboard?.findViewById(R.id.languageSpinner)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, supportedLanguages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner?.adapter = adapter

        languageSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentLanguage = supportedLanguages[position]
                (view as? TextView)?.text = currentLanguage
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun setupAIButtons() {
        translateButton?.setOnClickListener { handleDeepSeekTranslate() }
        askButton?.setOnClickListener { handleDeepSeekAsk() }
    }

    private fun setupSmartbarButtons() {
        Log.d("AIKeyboard", "Setting up smartbar buttons")
        val gptTranslateButton = keyboard?.findViewById<Button>(R.id.gptTranslateButton)
        val gptAskButton = keyboard?.findViewById<Button>(R.id.gptAskButton)

        Log.d("AIKeyboard", "GPT Translate button found: ${gptTranslateButton != null}")
        Log.d("AIKeyboard", "GPT Ask button found: ${gptAskButton != null}")

        // Setup GPT Model Spinner
        setupGptModelSpinner()

        gptTranslateButton?.setOnClickListener {
            Logger.log("GPT Translate button clicked")
            handleGptTranslate()
        }
        gptAskButton?.setOnClickListener {
            Logger.log("GPT Ask button clicked")
            val clipboardText = getClipboardText() ?: ""
            processGPTAsk(clipboardText)
        }
    }

    // GPT Model Spinner
    private var _gptModelSpinner: Spinner? = null
    private val gptModelSpinner: Spinner
        get() = _gptModelSpinner ?: throw IllegalStateException("GPT Model Spinner not initialized")

    private fun setupGptModelSpinner() {
        // Ensure keyboard view is available
        val keyboardView = keyboard ?: run {
            Log.e("AIKeyboard", "Keyboard view not initialized for GPT Model Spinner")
            return
        }

        // Find the spinner in the smartbar layout
        val spinner = keyboardView.findViewById<Spinner>(R.id.gptModelSpinner)
        if (spinner == null) {
            Log.e("AIKeyboard", "GPT Model Spinner not found in smartbar layout")
            return
        }

        // Store reference to the spinner
        _gptModelSpinner = spinner

        // Available GPT models
        val gptModels = arrayOf(
            "gpt-3.5-turbo-1106",
            "o3-mini-2025-01-31",
            "gpt-4o-2024-11-20",
            "gpt-4o-mini-2024-07-18",
            "o1-2024-12-17",
            "o1-preview-2024-09-12"
        )

        // Create and set adapter
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            gptModels
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Retrieve SharedPreferences
        val prefs = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)

        // Load saved model, default to first model if not found
        val savedModel = prefs.getString("gpt_model", gptModels[0]) ?: gptModels[0]
        val modelPosition = gptModels.indexOf(savedModel)

        // Set spinner to saved model position
        if (modelPosition != -1) {
            spinner.setSelection(modelPosition)
        }

        // Set item selection listener
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedModel = gptModels[position]

                // Save selected model to preferences
                prefs.edit().apply {
                    putString("gpt_model", selectedModel)
                    apply()
                }

                // Reinitialize GPTAPI if API key is present
                val gptApiKey = prefs.getString("gpt_api_key", "") ?: ""
                if (gptApiKey.isNotEmpty()) {
                    gptAPI = GPTAPI(gptApiKey, selectedModel)
                    Logger.log("GPT API reinitialized with model: $selectedModel")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun setupCalculatorKeyboard() {
        calculatorKeyboard = layoutInflater.inflate(R.layout.calculator_keyboard, null)
        calculatorResult = calculatorKeyboard?.findViewById(R.id.calculatorResult)
        val keyboardContainer = calculatorKeyboard?.findViewById<LinearLayout>(R.id.calculatorKeyboardContainer)

        // Create the popup window for the calculator
        calculatorPopup = PopupWindow(
            calculatorKeyboard,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
        }

        // Go Back Button
        val btnQuayLai = calculatorKeyboard?.findViewById<Button>(R.id.btnQuayLai)
        btnQuayLai?.setOnClickListener {
            // Dismiss the calculator popup and return to the main keyboard
            calculatorPopup?.dismiss()
        }

        // Print Text Button
        val btnInVanBan = calculatorKeyboard?.findViewById<Button>(R.id.btnInVanBan)
        btnInVanBan?.setOnClickListener {
            // Get the current result text
            val resultText = calculatorResult?.text.toString()

            // Commit the text to the current input connection
            currentInputConnection?.commitText(resultText, 1)

            // Dismiss the calculator popup
            calculatorPopup?.dismiss()
        }

        // Create calculator buttons dynamically
        val rows = 4
        val cols = 4
        for (row in 0 until rows) {
            val rowLayout = LinearLayout(this)
            rowLayout.orientation = LinearLayout.HORIZONTAL
            rowLayout.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            for (col in 0 until cols) {
                val index = row * cols + col
                val button = Button(this)
                button.text = calculatorKeys[index]
                button.layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                button.setOnClickListener { handleCalculatorButtonClick(calculatorKeys[index]) }
                rowLayout.addView(button)
            }

            keyboardContainer?.addView(rowLayout)
        }

        // Clear button
        val clearButton = calculatorKeyboard?.findViewById<Button>(R.id.btnClear)
        clearButton?.setOnClickListener {
            if (calculatorExpression.isNotEmpty()) {
                calculatorExpression.deleteCharAt(calculatorExpression.length - 1)
                calculatorResult?.text = calculatorExpression.toString().ifEmpty { "0" }
            } else {
                calculatorResult?.text = "0"
            }
        }
    }

    private fun handleCalculatorButtonClick(key: String) {
        when (key) {
            "=" -> {
                try {
                    val result = evaluateExpression(calculatorExpression.toString())
                    lastCalculationResult = result
                    // Display the full expression with the result
                    calculatorResult?.text = "${calculatorExpression} = ${formatResult(result)}"
                    // Append the result to the expression for further calculations
                    calculatorExpression.append(" = $result")
                } catch (e: Exception) {
                    calculatorResult?.text = "Error"
                }
            }
            "C" -> {
                // Remove the last character from the current expression
                if (calculatorExpression.isNotEmpty()) {
                    calculatorExpression.deleteCharAt(calculatorExpression.length - 1)
                    calculatorResult?.text = calculatorExpression.toString().ifEmpty { "0" }
                }
            }
            in listOf("+", "-", "×", "÷") -> {
                // If last result was used, start a new expression
                if (lastCalculationResult != null) {
                    calculatorExpression.clear()
                    calculatorExpression.append(lastCalculationResult)
                    lastCalculationResult = null
                }
                calculatorExpression.append(when (key) {
                    "×" -> "*"
                    "÷" -> "/"
                    else -> key
                })
                calculatorResult?.text = calculatorExpression.toString()
            }
            else -> {
                // Reset if previous result was used
                if (lastCalculationResult != null) {
                    calculatorExpression.clear()
                    lastCalculationResult = null
                }
                calculatorExpression.append(key)
                calculatorResult?.text = calculatorExpression.toString()
            }
        }
    }

    private fun evaluateExpression(expression: String): Double {
        return try {
            val cleanedExpression = expression
                .replace("×", "*")
                .replace("÷", "/")

            // Use Kotlin's built-in expression evaluation
            val result = object : Any() {
                var pos = -1
                var ch = 0

                fun nextChar() {
                    ch = if (++pos < cleanedExpression.length) cleanedExpression[pos].toInt() else -1
                }

                fun eat(charToEat: Int): Boolean {
                    while (ch == ' '.toInt()) nextChar()
                    if (ch == charToEat) {
                        nextChar()
                        return true
                    }
                    return false
                }

                fun parse(): Double {
                    nextChar()
                    val x = parseExpression()
                    if (pos < cleanedExpression.length) throw RuntimeException("Unexpected: " + ch.toChar())
                    return x
                }

                // Grammar:
                // expression = term | expression '+' term | expression '-' term
                // term = factor | term '*' factor | term '/' factor
                // factor = '+' factor | '-' factor | '(' expression ')' | number
                //        | functionName '(' expression ')'

                fun parseExpression(): Double {
                    var x = parseTerm()
                    while (true) {
                        when {
                            eat('+'.toInt()) -> x += parseTerm() // addition
                            eat('-'.toInt()) -> x -= parseTerm() // subtraction
                            else -> return x
                        }
                    }
                }

                fun parseTerm(): Double {
                    var x = parseFactor()
                    while (true) {
                        when {
                            eat('*'.toInt()) -> x *= parseFactor() // multiplication
                            eat('/'.toInt()) -> {
                                val divisor = parseFactor()
                                x /= divisor
                            }
                            else -> return x
                        }
                    }
                }

                fun parseFactor(): Double {
                    if (eat('+'.toInt())) return parseFactor() // unary plus
                    if (eat('-'.toInt())) return -parseFactor() // unary minus

                    var x: Double
                    val startPos = pos
                    if (eat('('.toInt())) { // parentheses
                        x = parseExpression()
                        eat(')'.toInt())
                    } else if ((ch in '0'.toInt()..'9'.toInt()) || ch == '.'.toInt()) {
                        while ((ch in '0'.toInt()..'9'.toInt()) || ch == '.'.toInt()) nextChar()
                        x = cleanedExpression.substring(startPos, pos).toDouble()
                    } else {
                        throw RuntimeException("Unexpected: " + ch.toChar())
                    }

                    return x
                }
            }.parse()
            result
        } catch (e: Exception) {
            throw RuntimeException("Invalid expression: ${e.message}")
        }
    }

    private fun formatResult(result: Double): String {
        return if (result % 1.0 == 0.0) {
            result.toLong().toString()
        } else {
            "%.2f".format(result)
        }
    }

        private fun setupKeyboardButtons() {
        this.keyboard?.let { keyboardView ->
            val numberIds = listOf(
                R.id.button_1, R.id.button_2, R.id.button_3, R.id.button_4, R.id.button_5,
                R.id.button_6, R.id.button_7, R.id.button_8, R.id.button_9, R.id.button_0
            )
            for (i in numberIds.indices) {
                keyboardView.findViewById<Button>(numberIds[i])?.let { button ->
                    val key = if (isSymbolMode) symbolKeys[i] else normalKeys[i]
                    button.setBackgroundResource(R.drawable.rounded_button)
                    button.setTextColor(Color.WHITE)
                    setupCharacterButton(button, key)
                }
            }

            val firstRowIds = listOf(
                R.id.button_q, R.id.button_w, R.id.button_e, R.id.button_r, R.id.button_t,
                R.id.button_y, R.id.button_u, R.id.button_i, R.id.button_o, R.id.button_p
            )
            for (i in firstRowIds.indices) {
                keyboardView.findViewById<Button>(firstRowIds[i])?.let { button ->
                    val key = if (isSymbolMode) symbolKeys[i + 10] else normalKeys[i + 10]
                    setupCharacterButton(button, key)
                }
            }

            val secondRowIds = listOf(
                R.id.button_a, R.id.button_s, R.id.button_d, R.id.button_f, R.id.button_g,
                R.id.button_h, R.id.button_j, R.id.button_k, R.id.button_l
            )
            for (i in secondRowIds.indices) {
                keyboardView.findViewById<Button>(secondRowIds[i])?.let { button ->
                    val key = if (isSymbolMode) symbolKeys[i + 20] else normalKeys[i + 20]
                    setupCharacterButton(button, key)
                }
            }

            val thirdRowIds = listOf(
                R.id.button_shift, R.id.button_z, R.id.button_x, R.id.button_c, R.id.button_v,
                R.id.button_b, R.id.button_n, R.id.button_m, R.id.button_backspace
            )
            for (i in thirdRowIds.indices) {
                keyboardView.findViewById<Button>(thirdRowIds[i])?.let { button ->
                    when (thirdRowIds[i]) {
                        R.id.button_shift -> setupShiftButton(button)
                        R.id.button_backspace -> setupBackspaceButton(button)
                        else -> {
                            val key = if (isSymbolMode) symbolKeys[i + 29] else normalKeys[i + 29]
                            setupCharacterButton(button, key)
                            if (key.length == 1 && key[0].isLetter()) {
                                shiftableButtons = shiftableButtons + button
                            }
                        }
                    }
                }
            }

            val letterButtonIds = listOf(
                R.id.button_q, R.id.button_w, R.id.button_e, R.id.button_r, R.id.button_t,
                R.id.button_y, R.id.button_u, R.id.button_i, R.id.button_o, R.id.button_p,
                R.id.button_a, R.id.button_s, R.id.button_d, R.id.button_f, R.id.button_g,
                R.id.button_h, R.id.button_j, R.id.button_k, R.id.button_l,
                R.id.button_z, R.id.button_x, R.id.button_c, R.id.button_v, R.id.button_b,
                R.id.button_n, R.id.button_m
            )
            for (id in letterButtonIds) {
                keyboardView.findViewById<Button>(id)?.let { shiftableButtons = shiftableButtons + it }
            }

            keyboardView.findViewById<Button>(R.id.button_comma)?.let { setupCharacterButton(it, ",") }
            keyboardView.findViewById<Button>(R.id.button_space)?.let { setupSpaceButton(it) }
            keyboardView.findViewById<Button>(R.id.button_period)?.let { setupCharacterButton(it, ".") }
            keyboardView.findViewById<Button>(R.id.button_symbols)?.let { setupSymbolButton(it) }
            keyboardView.findViewById<Button>(R.id.button_enter)?.let { setupEnterButton(it) }
        }
    }

    private fun setupCharacterButton(button: Button, key: String) {
        val displayText = when (shiftMode) {
            2 -> key.uppercase() // Caps Lock
            1 -> if (key.length == 1 && key[0].isLetter()) key.uppercase() else key  // Single Shift (chỉ chữ cái)
            else -> key // Normal
        }
        button.text = displayText

        if (key.matches(Regex("[0-9]"))) {
            button.setBackgroundResource(R.drawable.rounded_button)
            button.setTextColor(Color.WHITE)
        }

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.setBackgroundResource(R.drawable.keyboard_button_active_background)
                    showKeyPopup(v as Button)

                    if (isVietnameseMode && key.length == 1 && key[0].isLetter()) {
                        // Telex input:  Xử lý trong processVietnameseInput
                        // Luôn truyền key gốc, KHÔNG viết hoa ở đây.
                        processVietnameseInput(key[0])
                         // Quan trọng: KHÔNG return ở đây.
                    } else {
                        // Non-Telex input
                        val textToCommit = when (shiftMode) {
                            2 -> key.uppercase()
                            1 -> if (key.length == 1 && key[0].isLetter()) key.uppercase() else key
                            else -> key
                        }
                        commitText(textToCommit)
                        // Đặt lại shiftMode về 0 NẾU đang ở Single-Shift.
                          if (shiftMode == 1) {
                            shiftMode = 0
                            updateShiftState()
                            keyboard?.findViewById<Button>(R.id.button_shift)?.setBackgroundResource(R.drawable.rounded_button)
                        }
                    }

                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val isFirstRow = normalKeys.take(10).contains(key) || symbolKeys.take(10).contains(key)
                    if (isFirstRow) {
                        v.setBackgroundResource(R.drawable.rounded_button)
                    } else {
                        v.setBackgroundResource(R.drawable.keyboard_button_background)
                    }
                    hideKeyPopup()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSpaceButton(button: Button) {
        button.text = "Space"
        button.setBackgroundResource(R.drawable.rounded_button)
        button.setTextColor(Color.WHITE)

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.setBackgroundResource(R.drawable.keyboard_button_active_background)
                    showKeyPopup(button)
                    commitText(" ")
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.setBackgroundResource(R.drawable.rounded_button)
                    hideKeyPopup()
                    true
                }
                else -> false
            }
        }
    }
    private fun processVietnameseInput(char: Char) {
        if (!isVietnameseMode || isSymbolMode) {
            commitText(char.toString())
            return
        }

        val precedingText = currentInputConnection?.getTextBeforeCursor(Integer.MAX_VALUE, 0)?.toString() ?: ""

        // Xác định xem có nên viết hoa hay không (luôn viết hoa nếu shiftMode = 1)
        val isUppercase = shiftMode == 2 || shiftMode == 1
        //val inputChar = if (isUppercase) char.uppercaseChar() else char.lowercaseChar() // Removed line

        Log.d("AIKeyboard", "processVietnameseInput: shiftMode=$shiftMode, precedingText='$precedingText', char=$char, isUppercase=$isUppercase, inputChar=$char") // Debugging: inputChar is now char

        val (deleteCount, newText) = telexComposer.getActions(precedingText, char.lowercaseChar().toString()) // Always use char.lowercaseChar()

        if (deleteCount > 0) {
            currentInputConnection?.deleteSurroundingText(deleteCount, 0)
        }

        val finalText: String = if (isUppercase && newText.isNotEmpty()) { // Check if newText is not empty
            newText.substring(0, 1).uppercase(Locale.getDefault()) + newText.substring(1) // Capitalize first letter, keep rest
        } else {
            newText // If not uppercase or newText is as is
        }

        Log.d("AIKeyboard", "processVietnameseInput: newText=$newText, finalText=$finalText")

        currentInputConnection?.commitText(finalText, 1)

        // Đặt lại shiftMode về 0 NẾU đang ở Single-Shift.
        if (shiftMode == 1) {
            shiftMode = 0
            updateShiftState()
            keyboard?.findViewById<Button>(R.id.button_shift)?.setBackgroundResource(R.drawable.rounded_button)
        }
    }

    private fun showKeyPopup(button: Button) {
        try {
            if (keyPopupText == null) {
                keyPopupText = TextView(this).apply {
                    setBackgroundResource(R.drawable.popup_background)
                    setPadding(30, 15, 30, 15)
                    setTextColor(Color.BLACK)
                    textSize = 50f
                    minWidth = 80
                    minHeight = 80
                    gravity = Gravity.CENTER
                }
                keyPopup = PopupWindow(
                    keyPopupText,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    isOutsideTouchable = true
                    isFocusable = false
                }
            }

            keyPopupText?.text = button.text
            if (keyPopup?.isShowing == true) {
                keyPopup?.update(button, 0, -button.height*2, -1, -1)
            } else {
                keyPopup?.showAsDropDown(button, 0, -button.height*2)
            }
        } catch (e: Exception) {
            Log.e("AIKeyboard", "Error showing popup: ${e.message}")
        }
    }

    private fun hideKeyPopup() {
        try {
            keyPopup?.dismiss()
        } catch (e: Exception) {
            Log.e("AIKeyboard", "Error hiding popup: ${e.message}")
        }
    }

    private fun handleBackspace() {
        if (vietnameseInputBuffer.isNotEmpty()) {
            vietnameseInputBuffer.deleteCharAt(vietnameseInputBuffer.length - 1)
            if (vietnameseInputBuffer.isNotEmpty()) {
                currentInputConnection?.setComposingText(vietnameseInputBuffer.toString(), 1)
            } else {
                currentInputConnection?.finishComposingText()
            }
        } else {
            val selectedText = currentInputConnection?.getSelectedText(0)
            if (selectedText != null && selectedText.isNotEmpty()) {
                currentInputConnection?.commitText("", 1)
            } else {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
        }
    }

    private fun commitText(text: String) {
        if (vietnameseInputBuffer.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            vietnameseInputBuffer.setLength(0)
        }
        currentInputConnection?.commitText(text, 1)
    }

    private fun setupShiftButton(button: Button) {
        button.setBackgroundResource(R.drawable.rounded_button)
        button.setTextColor(Color.WHITE)
        button.text = "⇧"

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.setBackgroundResource(R.drawable.keyboard_button_active_background)
                    showKeyPopup(button)
                    val currentTime = SystemClock.elapsedRealtime()
                    if (currentTime - lastShiftClickTime < 200) { // Double tap
                        shiftMode = if (shiftMode == 2) 0 else 2 // Toggle Caps Lock
                    } else {
                        // Single tap: Luôn chuyển sang Single-Shift (shiftMode = 1)
                        shiftMode = 1
                    }
                    lastShiftClickTime = currentTime

                    updateShiftState()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.setBackgroundResource(R.drawable.rounded_button)
                    hideKeyPopup()
                    true
                }
                else -> false
            }
        }
    }

    private var shiftMode = 0 // 0: normal, 1: single-shift, 2: caps lock
    private var lastShiftClickTime: Long = 0

    private fun updateShiftState() {
        keyboard?.let { keyboardView ->
            val buttonIds = listOf(
                R.id.button_q, R.id.button_w, R.id.button_e, R.id.button_r, R.id.button_t,
                R.id.button_y, R.id.button_u, R.id.button_i, R.id.button_o, R.id.button_p,
                R.id.button_a, R.id.button_s, R.id.button_d, R.id.button_f, R.id.button_g,
                R.id.button_h, R.id.button_j, R.id.button_k, R.id.button_l,
                R.id.button_z, R.id.button_x, R.id.button_c, R.id.button_v, R.id.button_b,
                R.id.button_n, R.id.button_m
            )

            buttonIds.forEach { id ->
                keyboardView.findViewById<Button>(id)?.let { button ->
                    val key = button.text.toString().lowercase(Locale.getDefault()) // Lấy key gốc ở dạng lowercase
                    button.text = when (shiftMode) {
                        2 -> key.uppercase(Locale.getDefault()) // Caps Lock: Viết hoa toàn bộ
                        1 -> key.uppercase(Locale.getDefault()) // Single Shift: Viết hoa
                        else -> key // Normal: giữ nguyên
                    }
                }
            }

            keyboardView.findViewById<Button>(R.id.button_shift)?.let { shiftButton ->
                shiftButton.setBackgroundResource(R.drawable.rounded_button)
                shiftButton.text = when (shiftMode) {
                    2 -> "⇧⇧" // Caps Lock indicator
                    1 -> "⇧" // Single Shift indicator
                    else -> "⇧" // Normal indicator
                }
            }
        }
    }

    private fun setupSymbolButton(button: Button) {
        button.text = if (isSymbolMode) "ABC" else "123"
        button.setBackgroundResource(R.drawable.rounded_button)
        button.setOnClickListener {
            isSymbolMode = !isSymbolMode
            button.text = if (isSymbolMode) "ABC" else "123"
            setupKeyboardButtons()
        }
    }

    private fun setupBackspaceButton(button: Button) {
        button.setBackgroundResource(R.drawable.rounded_button)
        button.setTextColor(Color.WHITE)
        button.text = "⌫"

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.setBackgroundResource(R.drawable.keyboard_button_active_background)
                    showKeyPopup(button)
                    handleBackspace()
                    startBackspaceRepeat()
                    v.performClick()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.setBackgroundResource(R.drawable.rounded_button)
                    hideKeyPopup()
                    stopBackspaceRepeat()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupEnterButton(button: Button) {
        button.setBackgroundResource(R.drawable.rounded_button)
        button.setTextColor(Color.WHITE)
        button.text = "⏎"

        button.setOnClickListener {
            val inputConnection = currentInputConnection ?: return@setOnClickListener
            val editorInfo = currentInputEditorInfo

            // Terminate current Vietnamese word processing
            if (vietnameseInputBuffer.isNotEmpty()) {
                // Finalize the current composed text without additional processing
                inputConnection.finishComposingText()
                vietnameseInputBuffer.clear()
            }

            val isMultiLine = (editorInfo?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0

            if (isMultiLine) {
                inputConnection.commitText("\n", 1)
            } else {
                when (editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_SEND,
                    EditorInfo.IME_ACTION_DONE,
                    EditorInfo.IME_ACTION_NEXT -> {
                        inputConnection.performEditorAction(editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION)
                    }
                    else -> {
                        inputConnection.commitText("\n", 1)
                    }
                }
            }
        }

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.setBackgroundResource(R.drawable.keyboard_button_active_background)
                    showKeyPopup(button)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.setBackgroundResource(R.drawable.rounded_button)
                    hideKeyPopup()
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSmartbar() {
        btnSmartbarToggle?.setOnClickListener {
            smartbarScrollView?.visibility = if (smartbarScrollView?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        setupLanguageSpinner()
        setupAIButtons()
        setupSmartbarButtons()
        setupTTSButtons()
        setupLanguageToggleButton()
        setupClipboardHistorySpinner()
    }

    private fun setupLanguageToggleButton() {
        val languageButton = keyboard?.findViewById<Button>(R.id.languageButton)
        languageButton?.setOnClickListener {
            toggleLanguage()
        }
    }

    private fun toggleLanguage() {
        val languageButton = keyboard?.findViewById<Button>(R.id.languageButton)
        if (languageButton?.text.toString() == "VN") {
            languageButton?.text = "EN"
            isVietnameseMode = false
        } else {
            languageButton?.text = "VN"
            isVietnameseMode = true
        }
    }

    private fun pasteAndReadText() {
        try {
            val clipboardText = getClipboardText()
            if (!clipboardText.isNullOrEmpty()) {
                speakText(clipboardText)
            }
        } catch (e: Exception) {
            Log.e("AIKeyboard", "Error in pasteAndReadText: ${e.message}")
        }
    }

    private fun setupTTSButtons() {
        btnPasteAndRead?.setOnClickListener { pasteAndReadText() }
        btnStopTts?.setOnClickListener { stopTts() }
    }

    private var chunksToSpeak: List<String> = emptyList()
    private var currentChunkIndex = 0

    private val utteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Log.d("TTS_CALLBACK", "onStart: $utteranceId, index: $currentChunkIndex")
        }

        override fun onDone(utteranceId: String?) {
            Log.d("TTS_CALLBACK", "onDone: $utteranceId, index: $currentChunkIndex")
            if (utteranceId?.startsWith("chunk_") == true) { // Check if utteranceId is for a chunk
                ttsScope.launch(Dispatchers.Main) {
                    currentChunkIndex++
                    if (currentChunkIndex < chunksToSpeak.size) { // Check if there are more chunks
                        val nextChunk = chunksToSpeak[currentChunkIndex]
                        val nextUtteranceId = "chunk_${currentChunkIndex}"
                        Log.d("TTS_DEBUG", "Queueing chunk ${currentChunkIndex + 1}/${chunksToSpeak.size}, mode: ADD, utteranceId: $nextUtteranceId")
                        tts?.speak(nextChunk, TextToSpeech.QUEUE_ADD, null, nextUtteranceId)
                    } else {
                        Log.d("TTS_DEBUG", "All chunks read sequentially.")
                        currentChunkIndex = 0
                        chunksToSpeak = emptyList() // Clear chunks after reading is complete
                    }
                }
            }
        }

        override fun onError(utteranceId: String?) {
            Log.e("TTS_CALLBACK", "onError: $utteranceId, index: $currentChunkIndex")
            ttsScope.launch(Dispatchers.Main) {
                currentChunkIndex = 0
                chunksToSpeak = emptyList() // Clear chunks on error
                showToast("Error speaking text chunk.")
            }
        }
    }


    private fun speakText(text: String) {
        ttsScope.launch {
            try {
                if (!isTtsInitialized) {
                    Log.e("AIKeyboard", "TTS not initialized")
                    showToast("Text-to-Speech not initialized")
                    return@launch
                }

                val detectedLanguage = detectLanguage(text)
                val locale = when (detectedLanguage) {
                    "vi" -> Locale("vi", "VN")
                    "zh" -> Locale.CHINESE
                    "ja" -> Locale.JAPANESE
                    "ko" -> Locale.KOREAN
                    else -> Locale("vi", "VN") // Default to Vietnamese
                }

                tts?.language = locale
                tts?.setOnUtteranceProgressListener(utteranceProgressListener)

                val chunkSize = 4000
                chunksToSpeak = chunkText(text, chunkSize) // Split text into chunks BEFORE starting to speak
                currentChunkIndex = 0 // Reset chunk index for new text

                if (chunksToSpeak.isNotEmpty()) {
                    val firstChunk = chunksToSpeak[0]
                    val firstUtteranceId = "chunk_0"
                    Log.d("TTS_DEBUG", "Queueing first chunk 1/${chunksToSpeak.size}, mode: FLUSH, utteranceId: $firstUtteranceId")
                    tts?.speak(firstChunk, TextToSpeech.QUEUE_FLUSH, null, firstUtteranceId) // Speak first chunk with QUEUE_FLUSH
                } else {
                    Log.d("TTS_DEBUG", "No text to speak after chunking.")
                }


            } catch (e: Exception) {
                Log.e("AIKeyboard", "Error in speakText: ${e.message}")
                showToast("Error speaking text")
            }
        }
    }


    private fun stopTts() {
        try {
            textToSpeech?.stop()
            tts?.stop()
            ttsScope.coroutineContext.cancelChildren()
            currentChunkIndex = 0
            chunksToSpeak = emptyList()
        } catch (e: Exception) {
            Log.e("AIKeyboard", "Error stopping TTS: ${e.message}")
        }
    }

    private fun handleDeepSeekTranslate() {
        Logger.log("handleDeepSeekTranslate called")

        val prefs = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)
        val deepseekApiKey = prefs.getString("deepseek_api_key", "") ?: ""

        if (deepseekApiKey.isEmpty()) {
            Logger.log("DeepSeek API key is empty")
            showToast("Please set your DeepSeek API key in settings")
            return
        }

        if (deepSeekAPI == null) {
            try {
                deepSeekAPI = DeepSeekAPI(deepseekApiKey)
                Logger.log("Re-initialized DeepSeek API")
            } catch (e: Exception) {
                Logger.log("Failed to initialize DeepSeek API", e)
                showToast("Error initializing DeepSeek API")
                return
            }
        }

        val clipboardText = getClipboardText()
        if (clipboardText.isNullOrEmpty()) {
            showToast("Please copy text to translate")
            return
        }

        val targetLanguage = languageSpinner?.selectedItem?.toString() ?: "English"
        Logger.log("Target language: $targetLanguage")

        currentInputConnection?.commitText("Thinking...", 1)
        thinkingTextLength = "Thinking...".length

        GlobalScope.launch(Dispatchers.Main) {
            try {
                Logger.log("Starting DeepSeek translation request")
                val translatedText = withContext(Dispatchers.IO) {
                    deepSeekAPI?.translate(clipboardText, targetLanguage)
                }

                for (i in 0 until thinkingTextLength) {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
                currentInputConnection?.commitText("\n$translatedText", 1)
            } catch (e: Exception) {
                Logger.log("DeepSeek translation error", e)
                for (i in 0 until thinkingTextLength) {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
                currentInputConnection?.commitText("\nTranslation error: ${e.message}\n", 1)
            }
        }
    }

    private fun handleDeepSeekAsk() {
        Logger.log("handleDeepSeekAsk called")

        val prefs = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)
        val deepseekApiKey = prefs.getString("deepseek_api_key", "") ?: ""

        if (deepseekApiKey.isEmpty()) {
            Logger.log("DeepSeek API key is empty")
            showToast("Please set your DeepSeek API key in settings")
            return
        }

        if (deepSeekAPI == null) {
            try {
                deepSeekAPI = DeepSeekAPI(deepseekApiKey)
                Logger.log("Re-initialized DeepSeek API")
            } catch (e: Exception) {
                Logger.log("Failed to initialize DeepSeek API", e)
                showToast("Error initializing DeepSeek API")
                return
            }
        }

        val clipboardText = getClipboardText()
        if (clipboardText.isNullOrEmpty()) {
            showToast("Please copy text to ask about")
            return
        }

        currentInputConnection?.commitText("Thinking...", 1)
        thinkingTextLength = "Thinking...".length

        GlobalScope.launch(Dispatchers.Main) {
            try {
                Logger.log("Starting DeepSeek ask request")
                val answer = withContext(Dispatchers.IO) {
                    deepSeekAPI?.askQuestion(clipboardText)
                }

                for (i in 0 until thinkingTextLength) {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
                currentInputConnection?.commitText("\n$answer", 1)
            } catch (e: Exception) {
                Logger.log("DeepSeek ask error", e)
                for (i in 0 until thinkingTextLength) {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
                currentInputConnection?.commitText("\nError: ${e.message}\n", 1)
            }
        }
    }

    private fun handleGptTranslate() {
        Logger.log("handleGptTranslate called")

        val prefs = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)
        val gptApiKey = prefs.getString("gpt_api_key", "") ?: ""

        if (gptApiKey.isEmpty()) {
            Logger.log("GPT API key is empty")
            showToast("Please set your GPT API key in settings")
            return
        }

        if (gptAPI == null) {
            try {
                gptAPI = GPTAPI(gptApiKey)
                Logger.log("Re-initialized GPT API")
            } catch (e: Exception) {
                Logger.log("Failed to initialize GPT API", e)
                showToast("Error initializing GPT API")
                return
            }
        }

        val clipboardText = getClipboardText()
        if (clipboardText.isNullOrEmpty()) {
            showToast("Please copy text to translate")
            return
        }

        val targetLanguage = languageSpinner?.selectedItem?.toString() ?: "English"
        Logger.log("Target language: $targetLanguage")

        currentInputConnection?.commitText("Thinking...", 1)
        thinkingTextLength = "Thinking...".length

        GlobalScope.launch(Dispatchers.Main) {
            try {
                Logger.log("Starting GPT translation request")
                val translatedText = withContext(Dispatchers.IO) {
                    gptAPI?.translate(clipboardText, targetLanguage)
                }

                for (i in 0 until thinkingTextLength) {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
                currentInputConnection?.commitText("\n$translatedText", 1)
            } catch (e: Exception) {
                Logger.log("GPT translation error", e)
                for (i in 0 until thinkingTextLength) {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
                currentInputConnection?.commitText("\nTranslation error: ${e.message}\n", 1)
            }
        }
    }

    private fun processGPTAsk(clipboardText: String) {
        if (clipboardText.isBlank()) {
            showToast("Please copy text to ask about")
            return
        }

        // Ensure GPT API is initialized
        val prefs = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)
        val gptApiKey = prefs.getString("gpt_api_key", "") ?: ""

        if (gptApiKey.isEmpty()) {
            showToast("Please set your GPT API key in settings")
            return
        }

        // Initialize GPTAPI if not already initialized
        if (gptAPI == null) {
            try {
                gptAPI = GPTAPI(gptApiKey)
            } catch (e: Exception) {
                Logger.log("Failed to initialize GPT API: ${e.message}")
                showToast("Error initializing GPT API")
                return
            }
        }

        // Show thinking indicator
        currentInputConnection?.commitText("Thinking...", 1)
        thinkingTextLength = "Thinking...".length

        // Use GlobalScope for long-running operations
        GlobalScope.launch(Dispatchers.Main) {
            try {
                // Switch to IO dispatcher for network call
                val answer = withContext(Dispatchers.IO) {
                    gptAPI?.askQuestion(clipboardText) ?: ""
                }

                // Remove thinking indicator
                for (i in 0 until thinkingTextLength) {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }

                // Commit response with newline
                currentInputConnection?.commitText("\n$answer", 1)

                // Add to clipboard history
                captureGPTResponse(answer)

            } catch (e: Exception) {
                // Remove thinking indicator and show error
                for (i in 0 until thinkingTextLength) {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
                currentInputConnection?.commitText("\nError: ${e.message}\n", 1)
                Logger.log("GPT ask error: ${e.message}")
            }
        }
    }

    private fun getSelectedText(): String {
        Logger.log("Getting selected text")
        val ic = currentInputConnection
        if (ic == null) {
            Logger.log("InputConnection is null")
            return ""
        }

        var selectedText = ic.getSelectedText(0)?.toString()
        Logger.log("Selected text from getSelectedText: $selectedText")

        if (selectedText.isNullOrEmpty()) {
            Logger.log("No selected text, trying to get all text")
            val beforeLength = 10000
            val afterLength = 10000

            val textBeforeCursor = ic.getTextBeforeCursor(beforeLength, 0)?.toString() ?: ""
            Logger.log("Text before cursor (${textBeforeCursor.length} chars): $textBeforeCursor")

            val textAfterCursor = ic.getTextAfterCursor(afterLength, 0)?.toString() ?: ""
            Logger.log("Text after cursor (${textAfterCursor.length} chars): $textAfterCursor")

            selectedText = textBeforeCursor + textAfterCursor
            Logger.log("Combined text (${selectedText.length} chars): $selectedText")
        }

        return selectedText ?: ""
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        Log.e("AIKeyboard", "Error: $message")
        currentInputConnection?.commitText("\nError: $message\n", 1)

        // Optional: Show a toast message for more visibility
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "AIKeyboard Error: $message", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentInputConnection?.let {}
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer.destroy() // Add this line
        ttsScope.cancel() // Hủy coroutines TTS

        Log.d("AIKeyboard", "Service destroyed")

        // Unregister clipboard listener
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            Log.d("AIKeyboard", "TTS initialized successfully")
            tts?.setOnUtteranceProgressListener(utteranceProgressListener) // Set callback listener here, after TTS is initialized
        } else {
            Log.e("AIKeyboard", "TTS initialization failed")
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return super.onKeyUp(keyCode, event)
    }

    override fun onCreateInputView(): View {
        Log.d("AIKeyboard", "Creating input view")
        keyboard = layoutInflater.inflate(R.layout.keyboard_layout, null)

        initializeViews()
        setupSmartbar()
        setupSmartbarButtons()
        setupKeyboardButtons()
        setupQuaCauButton()

        keyPopupText = TextView(this).apply {
            setBackgroundResource(R.drawable.popup_background)
            setPadding(30, 15, 30, 15)
            setTextColor(Color.BLACK)
            textSize = 32f
            minWidth = 80
            minHeight = 80
            gravity = Gravity.CENTER
        }
        keyPopup = PopupWindow(keyPopupText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            isOutsideTouchable = true
            isFocusable = false
        }

        // Setup calculator keyboard
        setupCalculatorKeyboard()

        // Add calculator button setup
        btnTinhToan = keyboard?.findViewById(R.id.btnTinhToan)
        btnTinhToan?.setOnClickListener {
            // Show calculator keyboard
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(keyboard?.windowToken, 0)

            // Show the popup at the bottom of the screen
            calculatorPopup?.showAtLocation(keyboard, Gravity.BOTTOM, 0, 0)
        }

        // Add microphone button setup
        val btnMic = keyboard?.findViewById<Button>(R.id.btnMic)
        btnMic?.setOnClickListener {
            onMicButtonClick(it)
        }

        Log.d("AIKeyboard", "Input view setup completed")
        return keyboard!!
    }

    private fun setupQuaCauButton() {
        val btnQuaCau = keyboard?.findViewById<Button>(R.id.btnQuaCau)
        btnQuaCau?.setOnClickListener {
            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            } catch (e: Exception) {
                Log.e("AIKeyboard", "Error showing input method picker", e)
                Toast.makeText(this, "Không thể chuyển bàn phím", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Clipboard History Management
    private val clipboardHistory = mutableListOf<String>()
    private var _clipboardHistorySpinner: Spinner? = null
    private val clipboardHistorySpinner: Spinner
        get() = _clipboardHistorySpinner ?: throw IllegalStateException("Clipboard History Spinner not initialized")

        // Clipboard change listener
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        // Get the current clipboard text
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.primaryClip?.let { clip ->
            if (clip.itemCount > 0) {
                val copiedText = clip.getItemAt(0).text?.toString()?.trim()

                // Add non-empty text to clipboard history WITHOUT inserting
                if (!copiedText.isNullOrBlank()) {
                    addTextToClipboardHistory(copiedText, false)
                }
            }
        }
    }

    // Initialize clipboard listener
    private fun initializeClipboardListener() {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
    }

    // Method to add text to clipboard history
    private fun addTextToClipboardHistory(text: String, showToast: Boolean = true) {
        // Prevent duplicates and empty strings
        val trimmedText = text.trim()
        if (trimmedText.isBlank() || clipboardHistory.contains(trimmedText)) return

        // Limit clipboard history to last 10 items
        if (clipboardHistory.size >= 10) {
            clipboardHistory.removeAt(0)
        }

        // Add new text to history
        clipboardHistory.add(trimmedText)

        // Update spinner adapter on main thread
        runOnMainThread {
            _clipboardHistorySpinner?.let { spinner ->
                val adapter = spinner.adapter as? ArrayAdapter<String>
                adapter?.notifyDataSetChanged()

                // Optional: Set spinner to show the newly added item
                spinner.setSelection(clipboardHistory.size - 1)
            }
        }

        // Optional: Show toast if requested
        if (showToast) {
            showToast("Text added to clipboard history")
        }

        // Optional: Log the action
        Logger.log("Added to clipboard history: $trimmedText")
    }

    // Helper method to run on main thread
    private fun runOnMainThread(action: () -> Unit) {
        Handler(Looper.getMainLooper()).post(action)
    }

    // Setup method for clipboard history spinner
    private fun setupClipboardHistorySpinner() {
        // Ensure keyboard view is available
        val keyboardView = keyboard ?: run {
            Log.e("AIKeyboard", "Keyboard view not initialized for Clipboard History Spinner")
            return
        }

        // Find the spinner in the smartbar layout
        val spinner = keyboardView.findViewById<Spinner>(R.id.clipboardHistorySpinner)
        if (spinner == null) {
            Log.e("AIKeyboard", "Clipboard History Spinner not found in smartbar layout")
            return
        }

        // Store reference to the spinner
        _clipboardHistorySpinner = spinner

        // Create and set adapter for clipboard history
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            clipboardHistory
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter

        // Flag to track user interaction
        var isUserInitiatedSelection = false

        // Set item selection listener
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Only insert text if it's a user-initiated selection
                if (isUserInitiatedSelection && position in clipboardHistory.indices) {
                    val selectedText = clipboardHistory[position]

                    // Commit the selected text to the input connection
                    currentInputConnection?.let { ic ->
                        ic.commitText(selectedText, 1)

                        // Show a toast with the inserted text
                        showToast("Inserted: ${selectedText.take(20)}${if (selectedText.length > 20) "..." else ""}")
                    }

                    // Log the action
                    Logger.log("Inserted text from clipboard history: $selectedText")
                }

                // Reset the user interaction flag
                isUserInitiatedSelection = false
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }

        // Intercept touch events to enable user-initiated selection
        spinner.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Set flag to allow text insertion on next selection
                isUserInitiatedSelection = true
            }
            false
        }

        // Add long press listener to capture text
        spinner.setOnLongClickListener {
            // Get currently selected text
            val selectedText = getSelectedText()

            // If no text selected, get clipboard text
            val textToCopy = if (selectedText.isNotBlank()) {
                selectedText
            } else {
                getClipboardText() ?: ""
            }

            // Add to clipboard history if not blank
            if (textToCopy.isNotBlank()) {
                addTextToClipboardHistory(textToCopy)
                showToast("Text captured to clipboard history")
            } else {
                showToast("No text to capture")
            }
            true
        }

        // Initialize clipboard listener
        initializeClipboardListener()
    }

    // Method to get clipboard text
    private fun getClipboardText(): String? {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return clipboardManager?.primaryClip?.let { clip ->
            if (clip.itemCount > 0) clip.getItemAt(0).text?.toString()?.trim() else null
        }
    }

    private fun startBackspaceRepeat() {
        backspaceHandler.removeCallbacksAndMessages(null)
        currentBackspaceInterval = BACKSPACE_INITIAL_INTERVAL

        backspaceHandler.postDelayed(object : Runnable {
            override fun run() {
                if (vietnameseInputBuffer.isNotEmpty()) {
                    handleBackspace()
                } else {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }

                currentBackspaceInterval = maxOf(
                    BACKSPACE_MIN_INTERVAL,
                    currentBackspaceInterval - BACKSPACE_ACCELERATION
                )

                backspaceHandler.postDelayed(this, currentBackspaceInterval)
            }
        }, BACKSPACE_INITIAL_DELAY)
    }

    private fun stopBackspaceRepeat() {
        backspaceHandler.removeCallbacksAndMessages(null)
        currentBackspaceInterval = BACKSPACE_INITIAL_INTERVAL
    }

    private fun detectLanguage(text: String): String {
        if (text.isEmpty()) return lastDetectedLanguage
        var hasVietnamese = false
        var hasChinese = false
        var hasJapanese = false
        var hasKorean = false
        var hasLatin = false

        text.forEach { char ->
            when {
                char in '\u0300'..'\u036F' || char in '\u1EA0'..'\u1EF9' -> hasVietnamese = true
                char in '\u4E00'..'\u9FFF' -> hasChinese = true
                char in '\u3040'..'\u309F' || char in '\u30A0'..'\u30FF' -> hasJapanese = true
                char in '\uAC00'..'\uD7AF' -> hasKorean = true
                char in 'A'..'Z' || char in 'a'..'z' -> hasLatin = true
            }
        }

        lastDetectedLanguage = when {
            hasVietnamese -> "vi"
            hasChinese -> "zh"
            hasJapanese -> "ja"
            hasKorean -> "ko"
            hasLatin -> "en"
            else -> "vi"
        }
        return lastDetectedLanguage
    }

    // Additional method to capture text from GPT responses
    private fun captureGPTResponse(response: String) {
        // Add GPT response to clipboard history
        if (response.isNotBlank()) {
            addTextToClipboardHistory(response)
        }
    }

    private var temporarySpeechText: String = ""
    private var originalInputText: String = ""

    private fun preserveOriginalText() {
        // Store the current text in the input connection before speech recognition
        originalInputText = currentInputConnection?.getTextBeforeCursor(Integer.MAX_VALUE, 0)?.toString() ?: ""
    }

    private fun restoreOriginalText() {
        // Restore the original text
        currentInputConnection?.deleteSurroundingText(Integer.MAX_VALUE, Integer.MAX_VALUE)
        currentInputConnection?.commitText(originalInputText + temporarySpeechText, 1)
    }

    private fun onMicButtonClick(view: View) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Show a dialog or Toast explaining why the permission is needed
            Toast.makeText(this, "Microphone permission is required for voice input.", Toast.LENGTH_SHORT).show()
        } else {
            // Permission granted, start listening
            preserveOriginalText()
            startListeningMic() // Changed to call the modified function
        }
    }

    // Hàm chia văn bản thành các đoạn
    private fun chunkText(text: String, chunkSize: Int): List<String> {
        return text.chunked(chunkSize)
    }
}