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

class AIKeyboardService : InputMethodService(), TextToSpeech.OnInitListener, ClipboardManager.OnPrimaryClipChangedListener {
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
    private var shiftButton: Button? = null // Store a direct reference to the Shift button

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

    // --- Keyboard Layout Definitions ---
    private val normalKeys = arrayOf(
        "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
        "a", "s", "d", "f", "g", "h", "j", "k", "l",
        "⇧", "z", "x", "c", "v", "b", "n", "m", "⌫",
        "123", ",", " ", ".", "↵"
    )

    private val symbolKeys1 = arrayOf(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "@", "#", "$", "%", "&", "*", "-", "+", "(", ")",
        "=\\<", "!", "\"", "'", ":", ";", "/", "?", "⌫",  // Note:  "=\\<"  is the toggle
        "ABC", ",", " ", ".", "↵"
    )

    private val symbolKeys2 = arrayOf(
        "~", "`", "|", "•", "√", "π", "÷", "×", "{", "}",
        "€", "£", "¥", "^", "°", "_", "=", "[", "]",
        "123", "¡", "¿", "<", ">", "¢", "\\", "⌫",
        "ABC", ",", " ", "…", "↵"
    )

    private var currentKeyboardLayout = normalKeys  // Keep track of the current layout


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

        // Register clipboard listener using 'this' since the service implements the interface
        clipboardManager.addPrimaryClipChangedListener(this)

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
        return super.onKeyDown(keyCode, event)
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

        // Available GPT models - Removed irrelevant models
        val gptModels = arrayOf(
            "gpt-3.5-turbo-1106",
            "gpt-4o-2024-11-20",
            "gpt-4o-mini-2024-07-18",
            "o1-2024-12-17",
            "o1-preview-2024-09-12",
            "gpt-4.5-preview-2025-02-27"
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
            // ... (rest of evaluateExpression function remains the same)
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
        keyboard?.let { keyboardView ->
            shiftableButtons = emptyList()
            shiftButton = null

            val keyboardContainer = keyboardView.findViewById<LinearLayout>(R.id.keyboardContainer)
            keyboardContainer.removeAllViews()
            keyboardContainer.gravity = Gravity.CENTER_HORIZONTAL

            fun getKeysForRow(row: Int): Array<String> {
                return when (currentKeyboardLayout) {
                    normalKeys -> when (row) {
                        0 -> normalKeys.sliceArray(0 until 10)
                        1 -> normalKeys.sliceArray(10 until 19)
                        2 -> normalKeys.sliceArray(19 until 28)
                        3 -> normalKeys.sliceArray(28 until normalKeys.size)
                        else -> emptyArray()
                    }
                    symbolKeys1 -> when (row) {
                        0 -> symbolKeys1.sliceArray(0 until 10)
                        1 -> symbolKeys1.sliceArray(10 until 20)
                        2 -> symbolKeys1.sliceArray(20 until 29)
                        3 -> symbolKeys1.sliceArray(29 until symbolKeys1.size)
                        else -> emptyArray()
                    }
                    symbolKeys2 -> when (row) {
                        0 -> symbolKeys2.sliceArray(0 until 10)
                        1 -> symbolKeys2.sliceArray(10 until 19)
                        2 -> symbolKeys2.sliceArray(19 until 27)
                        3 -> symbolKeys2.sliceArray(27 until symbolKeys2.size)
                        else -> emptyArray()
                    }
                    else -> emptyArray()
                }
            }

            val rows = when (currentKeyboardLayout) {
                normalKeys -> 4
                symbolKeys1, symbolKeys2 -> 4
                else -> 4
            }

            for (row in 0 until rows) {
                val rowKeys = getKeysForRow(row)
                val rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(
                        resources.getDimensionPixelSize(R.dimen.key_margin),
                        0,
                        resources.getDimensionPixelSize(R.dimen.key_margin),
                        0
                    )
                }

                for (key in rowKeys) {
                    val button = Button(this).apply {
                        text = key
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            resources.getDimensionPixelSize(R.dimen.key_height),
                            1f // Default weight
                        ).apply {
                            val margin = resources.getDimensionPixelSize(R.dimen.key_margin)
                            setMargins(margin, 0, margin, 0)
                        }
                        setTextAppearance(R.style.KeyboardButtonStyle)
                        setBackgroundResource(R.drawable.keyboard_button_background)


                        // Apply number style consistently here
                        if (key.matches(Regex("[0-9]"))) {
                            setBackgroundResource(R.drawable.rounded_button)
                            setTextColor(Color.WHITE)
                        }

                        when (key) {
                            "⇧" -> {
                                setupShiftButton(this)
                                shiftButton = this
                                (layoutParams as LinearLayout.LayoutParams).weight = 1.5f // Shift key weight
                            }
                            "⌫" -> {
                                setupBackspaceButton(this)
                                (layoutParams as LinearLayout.LayoutParams).weight = 1.5f // Backspace key weight
                            }
                            " " -> {
                                setupSpaceButton(this)
                                (layoutParams as LinearLayout.LayoutParams).weight = 3f   // Space key weight
                            }
                            "123" -> {
                                setupSymbolButton(this, key)
                                if (currentKeyboardLayout == normalKeys) {
                                    (layoutParams as LinearLayout.LayoutParams).weight = 1.5f // Increased weight for "123" in normalKeys
                                } else if (currentKeyboardLayout == symbolKeys2) {
                                    (layoutParams as LinearLayout.LayoutParams).weight = 1.5f // Match shift size in symbolKeys2
                                }
                            }
                            "=\\<" -> {
                                setupSymbolButton(this, key)
                                if (currentKeyboardLayout == symbolKeys1) {
                                    (layoutParams as LinearLayout.LayoutParams).weight = 1.5f // Match shift size in symbolKeys1
                                }
                            }
                            "ABC" -> {
                                setupSymbolButton(this, key)
                                if (currentKeyboardLayout == symbolKeys1 || currentKeyboardLayout == symbolKeys2) {
                                    (layoutParams as LinearLayout.LayoutParams).weight = 1.5f // Match "123" size in symbolKeys
                                }
                            }
                            "↵" -> {
                                setupEnterButton(this)
                                if (currentKeyboardLayout == normalKeys || currentKeyboardLayout == symbolKeys1 || currentKeyboardLayout == symbolKeys2) {
                                    (layoutParams as LinearLayout.LayoutParams).weight = 1.5f // Increased weight for "↵" in all layouts
                                }
                            }
                            else -> setupCharacterButton(this, key)
                        }

                        // Apply margins based on row and key
                        if (row == 1) {
                            if (currentKeyboardLayout == normalKeys) {
                                if (key == "a") {
                                    (layoutParams as LinearLayout.LayoutParams).marginStart = resources.getDimensionPixelSize(R.dimen.key_margin_row2_side)
                                } else if (key == "l") {
                                    (layoutParams as LinearLayout.LayoutParams).marginEnd = resources.getDimensionPixelSize(R.dimen.key_margin_row2_side)
                                }
                            } else if (currentKeyboardLayout == symbolKeys2) {
                                if (key == "€") {
                                    (layoutParams as LinearLayout.LayoutParams).marginStart = resources.getDimensionPixelSize(R.dimen.key_margin_row2_side)
                                } else if (key == "]") {
                                    (layoutParams as LinearLayout.LayoutParams).marginEnd = resources.getDimensionPixelSize(R.dimen.key_margin_row2_side)
                                }
                            }
                        }
                    }
                    rowLayout.addView(button)
                     // Thêm nút chữ cái vào shiftableButtons (CHỈ nút chữ cái, không phải nút chức năng)
                    if (key.length == 1 && key[0].isLetter()) {
                        shiftableButtons = shiftableButtons + button // Thêm button vào danh sách
                    }
                }
                keyboardContainer.addView(rowLayout)
            }
            updateShiftState()
             // After assigning shiftableButtons, add this log line:
            Log.d("AIKeyboard", "shiftableButtons size: ${shiftableButtons.size}")
            if (shiftableButtons.isNotEmpty()) {
                Log.d("AIKeyboard", "First shiftable button text: ${shiftableButtons[0].text}")
            }
        }
    }

    private fun setupCharacterButton(button: Button, key: String) {
        val displayText = when (shiftMode) {
            2 -> key.uppercase()
            1 -> if (key.length == 1 && key[0].isLetter()) key.uppercase() else key
            else -> key // Normal
        }
        button.text = displayText

        val isNumberKey = key.matches(Regex("[0-9]")) // Helper variable

        if (isNumberKey) {
            button.setBackgroundResource(R.drawable.rounded_button)
            button.setTextColor(Color.BLACK)
        } else {
            button.setBackgroundResource(R.drawable.maunenphim)
            button.setTextColor(Color.BLACK)
        }

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.setBackgroundResource(R.drawable.keyboard_button_active_background)
                    showKeyPopup(v as Button)

                    if (isVietnameseMode && key.length == 1 && key[0].isLetter()) {
                        processVietnameseInput(key[0])
                    } else {
                        val textToCommit = when (shiftMode) {
                            2 -> key.uppercase()
                            1 -> if (key.length == 1 && key[0].isLetter()) key.uppercase() else key
                            else -> key
                        }
                        commitText(textToCommit)
                        if (shiftMode == 1) {
                            shiftMode = 0
                            updateShiftState()
                            shiftButton?.setBackgroundResource(R.drawable.rounded_button)
                        }
                    }

                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Restore the ORIGINAL background based on key type
                    if (isNumberKey) {
                        v.setBackgroundResource(R.drawable.rounded_button)
                    } else {
                        v.setBackgroundResource(R.drawable.maunenphim)
                    }

                    hideKeyPopup()

                    if (v is Button && !isNumberKey) {
                        (v as Button).setTextColor(Color.BLACK)
                    }

                    return@setOnTouchListener true
                }
                else -> return@setOnTouchListener false
            }
        }
    }

    private fun setupSpaceButton(button: Button) {
       // ... (rest of setupSpaceButton function remains the same)
        button.text = "Space"
        button.setBackgroundResource(R.drawable.rounded_button)
        button.setTextColor(Color.WHITE)

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.setBackgroundResource(R.drawable.keyboard_button_active_background)
                    showKeyPopup(button)
                    commitText(" ")
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.setBackgroundResource(R.drawable.rounded_button)
                    hideKeyPopup()
                    return@setOnTouchListener true
                }
                else -> return@setOnTouchListener false
            }
        }
    }

    private fun processVietnameseInput(char: Char) {
        // ... (rest of processVietnameseInput function remains the same)
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
            newText // If not uppercase or newText is empty, keep as is
        }

        Log.d("AIKeyboard", "processVietnameseInput: newText=$newText, finalText=$finalText")

        currentInputConnection?.commitText(finalText, 1)

        // Đặt lại shiftMode về 0 NẾU đang ở Single-Shift.
        if (shiftMode == 1) {
            shiftMode = 0
            updateShiftState()
            //No longer using findViewById
            //keyboard?.findViewById<Button>(R.id.button_shift)?.setBackgroundResource(R.drawable.rounded_button)
            shiftButton?.setBackgroundResource(R.drawable.rounded_button)
        }
    }

    private fun showKeyPopup(button: Button) {
        // ... (rest of showKeyPopup function remains the same)
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
        // ... (rest of hideKeyPopup function remains the same)
        try {
            keyPopup?.dismiss()
        } catch (e: Exception) {
            Log.e("AIKeyboard", "Error hiding popup: ${e.message}")
        }
    }

     fun handleBackspace() {
        // ... (rest of handleBackspace function remains the same)
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

     fun commitText(text: String) {
        // ... (rest of commitText function remains the same)
        if (vietnameseInputBuffer.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            vietnameseInputBuffer.setLength(0)
        }
        currentInputConnection?.commitText(text, 1)
    }

    private fun setupShiftButton(button: Button) {
        // ... (rest of setupShiftButton function remains the same)
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

                    Log.d("AIKeyboard", "Shift button pressed, shiftMode set to: $shiftMode") // Log when Shift button is pressed
                    updateShiftState() // Call updateShiftState()
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.setBackgroundResource(R.drawable.rounded_button)
                    hideKeyPopup()
                    return@setOnTouchListener true
                }
                else -> return@setOnTouchListener false
            }
        }
    }

    private var shiftMode = 0 // 0: normal, 1: single-shift, 2: caps lock
    private var lastShiftClickTime: Long = 0

     fun updateShiftState() {
        // ... (rest of updateShiftState function remains the same)
        // Update letter keys
        for (button in shiftableButtons) {
            val key = button.text.toString().lowercase(Locale.getDefault())
            button.text = when (shiftMode) {
                2 -> key.uppercase(Locale.getDefault())
                1 -> key.uppercase(Locale.getDefault())
                else -> key
            }
        }

        // Update Shift key indicator using the stored reference
        shiftButton?.let { shiftButton ->
            shiftButton.setBackgroundResource(R.drawable.rounded_button) // Reset background
            shiftButton.text = when (shiftMode) {
                2 -> "⇧⇧" // Caps Lock
                1 -> "⇧"  // Single Shift
                else -> "⇧" // Normal
            }
        }
    }


    private fun setupSymbolButton(button: Button, key: String) {
        // ... (rest of setupSymbolButton function remains the same)
      button.text = key
      button.setBackgroundResource(R.drawable.rounded_button)
      button.setOnClickListener {
          currentKeyboardLayout = when (key) {
              "123" -> {
                  if (currentKeyboardLayout == symbolKeys1) {
                      symbolKeys2
                  } else {
                      symbolKeys1
                  }
              }
              "=\\<" -> symbolKeys2
              "ABC" -> normalKeys
              else -> normalKeys // Default case
          }
          setupKeyboardButtons() // Rebuild the keyboard
      }
  }


    private fun setupBackspaceButton(button: Button) {
        // ... (rest of setupBackspaceButton function remains the same)
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
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.setBackgroundResource(R.drawable.rounded_button)
                    hideKeyPopup()
                    stopBackspaceRepeat()
                    return@setOnTouchListener true
                }
                else -> return@setOnTouchListener false
            }
        }
    }

    private fun setupEnterButton(button: Button) {
        // ... (rest of setupEnterButton function remains the same)
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
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.setBackgroundResource(R.drawable.rounded_button)
                    hideKeyPopup()
                    v.performClick()
                    return@setOnTouchListener true
                }
                else -> return@setOnTouchListener false
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

     fun toggleLanguage() {
        // ... (rest of toggleLanguage function remains the same)
        val languageButton = keyboard?.findViewById<Button>(R.id.languageButton)
        if (languageButton?.text.toString() == "VN") {
            languageButton?.text = "EN"
            isVietnameseMode = false
        } else {
            languageButton?.text = "VN"
            isVietnameseMode = true
        }
    }

     fun setupTTSButtons() {
        btnPasteAndRead?.setOnClickListener { pasteAndReadText() }
        btnStopTts?.setOnClickListener { stopTts() }
    }

     fun pasteAndReadText() {
        // ... (rest of pasteAndReadText function remains the same)
        try {
            val clipboardText = getClipboardText()
            if (!clipboardText.isNullOrEmpty()) {
                speakText(clipboardText)
            }
        } catch (e: Exception) {
            Log.e("AIKeyboard", "Error in pasteAndReadText: ${e.message}")
        }
    }

    private fun speakText(text: String) {
        // ... (rest of speakText function remains the same)
        try {
            if (!isTtsInitialized) {
                Log.e("AIKeyboard", "TTS not initialized")
                showToast("Text-to-Speech not initialized")
                return
            }

            val detectedLanguage = detectLanguage(text)
            val locale = when (detectedLanguage) {
                "vi" -> Locale("vi", "VN")
                "zh" -> Locale.CHINESE
                "ja" -> Locale.JAPANESE
                "ko" -> Locale.KOREAN
                else -> Locale.US
            }

            tts?.language = locale
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            Log.d("AIKeyboard", "Speaking text in ${locale.language}: $text")
        } catch (e: Exception) {
            Log.e("AIKeyboard", "Error in speakText: ${e.message}")
            showToast("Error speaking text")
        }
    }

     fun stopTts() {
        // ... (rest of stopTts function remains the same)
        try {
            textToSpeech?.stop()
            tts?.stop()
        } catch (e: Exception) {
            Log.e("AIKeyboard", "Error stopping TTS: ${e.message}")
        }
    }

    private fun handleDeepSeekTranslate() {
        // ... (rest of handleDeepSeekTranslate function remains the same)
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
                    deepSeekAPI?.translate(clipboardText, targetLanguage) ?: "No translation found" // Handle null response
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
        // ... (rest of handleDeepSeekAsk function remains the same)
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
                    deepSeekAPI?.askQuestion(clipboardText) ?: "No answer found" // Handle null response
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
        // ... (rest of handleGptTranslate function remains the same)
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
                    gptAPI?.translate(clipboardText, targetLanguage) ?: "No translation found" // Handle null response
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
        // ... (rest of processGPTAsk function remains the same)
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
                    gptAPI?.askQuestion(clipboardText) ?: "No answer found" // Handle null response
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

     fun getSelectedText(): String {
        // ... (rest of getSelectedText function remains the same)
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

     fun showToast(message: String) {
        // ... (rest of showToast function remains the same)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        // ... (rest of showError function remains the same)
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
        // ... (rest of onDestroy function remains the same)
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer.destroy() // Add this line
        Log.d("AIKeyboard", "Service destroyed")

        // Unregister clipboard listener
        clipboardManager.removePrimaryClipChangedListener(this) // Unregister here
    }

    override fun onInit(status: Int) {
        // ... (rest of onInit function remains the same)
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            Log.d("AIKeyboard", "TTS initialized successfully")
        } else {
            Log.e("AIKeyboard", "TTS initialization failed")
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return super.onKeyUp(keyCode, event)
    }

    override fun onCreateInputView(): View {
        // ... (rest of onCreateInputView function remains the same)
        Log.d("AIKeyboard", "Creating input view")
        keyboard = layoutInflater.inflate(R.layout.keyboard_layout, null)

        initializeViews()
        setupSmartbar()
        setupSmartbarButtons()
        setupKeyboardButtons() // Now uses dynamic layout
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
        // ... (rest of setupQuaCauButton function remains the same)
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
    override fun onPrimaryClipChanged() {
        // ... (rest of onPrimaryClipChanged function remains the same)
        // Get the current clipboard text
        clipboardManager.primaryClip?.let { clip ->
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
        // ... (rest of initializeClipboardListener function remains the same)
        // Use 'this' as the listener since AIKeyboardService now implements the interface
        clipboardManager.addPrimaryClipChangedListener(this)
    }

    // Method to add text to clipboard history
     fun addTextToClipboardHistory(text: String, showToast: Boolean = true) {
        // ... (rest of addTextToClipboardHistory function remains the same)
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
     fun runOnMainThread(action: () -> Unit) {
        // ... (rest of runOnMainThread function remains the same)
        Handler(Looper.getMainLooper()).post(action)
    }

    // Setup method for clipboard history spinner
    private fun setupClipboardHistorySpinner() {
        // ... (rest of setupClipboardHistorySpinner function remains the same)
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

        // Initialize clipboard listener - VERY IMPORTANT
        initializeClipboardListener()
    }

    // Method to get clipboard text
     fun getClipboardText(): String? {
        // ... (rest of getClipboardText function remains the same)
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return clipboardManager?.primaryClip?.let { clip ->
            if (clip.itemCount > 0) clip.getItemAt(0).text?.toString()?.trim() else null
        }
    }

    private fun startBackspaceRepeat() {
        // ... (rest of startBackspaceRepeat function remains the same)
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
        // ... (rest of stopBackspaceRepeat function remains the same)
        backspaceHandler.removeCallbacksAndMessages(null)
        currentBackspaceInterval = BACKSPACE_INITIAL_INTERVAL
    }

    private fun detectLanguage(text: String): String {
        // ... (rest of detectLanguage function remains the same)
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
            else -> lastDetectedLanguage
        }
        return lastDetectedLanguage
    }

    // Additional method to capture text from GPT responses
    private fun captureGPTResponse(response: String) {
        // ... (rest of captureGPTResponse function remains the same)
        // Add GPT response to clipboard history
        if (response.isNotBlank()) {
            addTextToClipboardHistory(response)
        }
    }

    private var temporarySpeechText: String = ""
    private var originalInputText: String = ""

    private fun preserveOriginalText() {
        // ... (rest of preserveOriginalText function remains the same)
        // Store the current text in the input connection before speech recognition
        originalInputText = currentInputConnection?.getTextBeforeCursor(Integer.MAX_VALUE, 0)?.toString() ?: ""
    }

    private fun restoreOriginalText() {
        // ... (rest of restoreOriginalText function remains the same)
        // Restore the original text
        currentInputConnection?.deleteSurroundingText(Integer.MAX_VALUE, Integer.MAX_VALUE)
        currentInputConnection?.commitText(originalInputText + temporarySpeechText, 1)
    }

    private fun onMicButtonClick(view: View) {
        // ... (rest of onMicButtonClick function remains the same)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Show a dialog or Toast explaining why the permission is needed
            Toast.makeText(this, "Microphone permission is required for voice input.", Toast.LENGTH_SHORT).show()
        } else {
            // Permission granted, start listening
            preserveOriginalText()
            startListeningMic() // Changed to call the modified function
        }
    }
}