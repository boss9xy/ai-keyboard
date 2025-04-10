package com.example.aikeyboard

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
import java.util.*

class AIKeyboardService : InputMethodService(), TextToSpeech.OnInitListener,
    ClipboardManager.OnPrimaryClipChangedListener, KeyboardView.OnKeyboardActionListener {

    private var keyboard: View? = null
    private var btnPasteAndRead: Button? = null
    private var btnStopTts: Button? = null
    private var btnMic: ImageButton? = null
    private var smartbarScrollView: HorizontalScrollView? = null
    private var translateButton: Button? = null
    private var askButton: Button? = null
    private var gptTranslateButton: Button? = null
    private var gptAskButton: Button? = null
    private var gptContinueButton: Button? = null
    private var gptSuggestButton: Button? = null
    private var deepseekSuggestButton: Button? = null
    private var stopGenerationButton: Button? = null
    private var assistantsGptButton: Button? = null
    private var languageSpinner: Spinner? = null
    private var gptModelSpinner: Spinner? = null
    private var preferences: SharedPreferences? = null

    private var telexComposer = TelexComposer()
    private var vietnameseInputBuffer = StringBuilder()
    private var assistantsAPI: AssistantsAPI? = null

    private var currentLanguage = "Vietnamese"
    private val supportedLanguages = listOf(
        "Vietnamese", "English", "Chinese", "Japanese", "Korean",
        "French", "German", "Spanish", "Russian", "Italian"
    )

    private val gptModels = listOf(
        "gpt-3.5-turbo",
        "gpt-3.5-turbo-1106",
        "o3-mini-2025-01-31",
        "gpt-4o-2024-11-20",
        "gpt-4o-mini-2024-07-18",
        "o1-2024-12-17",
        "o1-preview-2024-09-12",
        "gpt-4.5-preview-2025-02-27"
    )

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var textToSpeech: TextToSpeech? = null
    private var lastDetectedLanguage = "vi"
    private var isVietnameseMode = true
    private val requestMutex = Mutex()

    private var gptAPI: GPTAPI? = null
    private var deepSeekAPI: DeepSeekAPI? = null
    private val clipboardManager: ClipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private val handler = Handler(Looper.getMainLooper())

    private var btnTinhToan: Button? = null
    private var calculatorKeyboard: View? = null
    private var calculatorResult: TextView? = null
    private var calculatorExpression = StringBuilder()
    private var lastCalculationResult: Double? = null
    private var calculatorPopup: PopupWindow? = null

    private val calculatorKeys = arrayOf(
        "7", "8", "9", "÷",
        "4", "5", "6", "×",
        "1", "2", "3", "-",
        "0", ".", "=", "+"
    )

    private lateinit var keyboardView: KeyboardView
    private lateinit var normalKeyboard: Keyboard
    private lateinit var symbolKeyboard1: Keyboard
    private lateinit var symbolKeyboard2: Keyboard
    private var currentKeyboard: Keyboard? = null

    private var shiftMode = 0 // 0: off, 1: on, 2: caps lock
    private var lastShiftClickTime = 0L
    private val DOUBLE_CLICK_THRESHOLD = 300L

    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false
    private val timeoutRunnable = Runnable { stopListening() }
    private var isSpeechRecognitionActive = false
    private var originalInputText = ""
    private var temporarySpeechText = ""
    private var lastRecognizedText: String? = null
    private var lastCursorPosition = 0 // Track cursor position

    private var isFromApp = false

    private var keyPopupWindow: PopupWindow? = null
    private var keyPopupView: View? = null

    private var currentThreadId: String? = null
    private var lastGptFunction: String? = null
    private var lastTranslateLanguage: String? = null
    private var thinkingTextLength = 0 // Track the length of "Thinking..." text

    // Thêm Job để quản lý tất cả các quá trình tạo nội dung
    private var generationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Logger.initialize(this)
        Logger.log("AIKeyboardService onCreate")
        initializeAPIs()
        preferences = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        textToSpeech = TextToSpeech(this, this)
        Log.d("AIKeyboard", "Service created")
        clipboardManager.addPrimaryClipChangedListener(this)
        setupCalculatorKeyboard()
        setupSpeechRecognition()
        setupKeyPopup()
        initializeKeyboards()
    }

    private fun initializeKeyboards() {
        normalKeyboard = Keyboard(this, R.xml.keyboard_normal)
        symbolKeyboard1 = Keyboard(this, R.xml.keyboard_symbols_1)
        symbolKeyboard2 = Keyboard(this, R.xml.keyboard_symbols_2)
        currentKeyboard = normalKeyboard
    }

    private fun setupSpeechRecognition() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                isSpeechRecognitionActive = true
                handler.postDelayed(timeoutRunnable, 2000)
            }

            override fun onBeginningOfSpeech() {
                handler.removeCallbacks(timeoutRunnable)
                handler.postDelayed(timeoutRunnable, 2000)
            }

            override fun onRmsChanged(rmsdB: Float) {}
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
                if (!matches.isNullOrEmpty()) {
                    Log.d("AIKeyboard", "Final speech result: ${matches[0]}")
                    currentInputConnection?.finishComposingText()
                } else {
                    Toast.makeText(this@AIKeyboardService, "No speech recognized.", Toast.LENGTH_SHORT).show()
                }
                stopListening()
                lastRecognizedText = null
                temporarySpeechText = ""
                lastCursorPosition = 0
            }

            override fun onPartialResults(partialResults: Bundle) {
                val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    val currentText = matches[0]
                    if (currentText != lastRecognizedText) {
                        val textBeforeCursorBeforeUpdate = currentInputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                        val userEditedText = lastRecognizedText != null && textBeforeCursorBeforeUpdate != (currentInputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: "")

                        Log.d("AIKeyboard", "Text before cursor: $textBeforeCursorBeforeUpdate")
                        Log.d("AIKeyboard", "Last recognized text: $lastRecognizedText")
                        Log.d("AIKeyboard", "User edited text: $userEditedText")

                        if (lastRecognizedText == null) {
                            Log.d("AIKeyboard", "First recognition, adding space and text: $currentText")
                            currentInputConnection?.commitText(" ", 1)
                            currentInputConnection?.commitText(currentText, 1)
                        } else if (!userEditedText) {
                            Log.d("AIKeyboard", "No manual edits, replacing: $temporarySpeechText with: $currentText")
                            if (temporarySpeechText.isNotEmpty()) {
                                currentInputConnection?.deleteSurroundingText(temporarySpeechText.length, 0)
                            }
                            currentInputConnection?.commitText(currentText, 1)
                        } else {
                            Log.d("AIKeyboard", "User added text, appending: $currentText")
                            currentInputConnection?.commitText(" $currentText", 1)
                            lastRecognizedText = textBeforeCursorBeforeUpdate + " $currentText"
                            temporarySpeechText = " $currentText"
                        }

                        lastRecognizedText = currentText
                        temporarySpeechText = currentText
                        lastCursorPosition = textBeforeCursorBeforeUpdate.length

                        Log.d("AIKeyboard", "Updated last cursor position: $lastCursorPosition")
                        Log.d("AIKeyboard", "Partial speech result: $currentText")
                    }
                }
                handler.removeCallbacks(timeoutRunnable)
                handler.postDelayed(timeoutRunnable, 2000)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListeningMic() {
        if (!isListening) {
            lastCursorPosition = currentInputConnection?.getTextBeforeCursor(1000, 0)?.length ?: 0
            clipboardManager.removePrimaryClipChangedListener(this)
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
            clipboardManager.addPrimaryClipChangedListener(this)
            isSpeechRecognitionActive = false
            btnMic?.setImageResource(R.drawable.ic_mic)
            btnMic?.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.key_special_background, theme))
            lastCursorPosition = 0
            Logger.log("Speech recognition stopped")
        }
    }

    private fun onMicButtonClick(view: View) {
        Logger.log("Checking microphone permission")
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Logger.log("Microphone permission not granted")
            showToast("Microphone permission required")
            return
        }

        if (!isListening) {
            Logger.log("Starting speech recognition")
            startListeningMic()
            btnMic?.setImageResource(R.drawable.ic_mic_active)
            btnMic?.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.mic_active_background, theme))
        } else {
            Logger.log("Stopping speech recognition")
            stopListening()
            btnMic?.setImageResource(R.drawable.ic_mic)
            btnMic?.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.key_special_background, theme))
        }
    }

    private fun initializeAPIs() {
        Logger.log("Initializing APIs")
        preferences = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)

        val gptApiKey = preferences?.getString("gpt_api_key", "") ?: ""
        val gptModel = preferences?.getString("selected_gpt_model", "gpt-3.5-turbo") ?: "gpt-3.5-turbo"
        val assistantId = preferences?.getString("gpt_assistants_id", "") ?: "" // Lấy từ key đúng

        if (gptApiKey.isNotEmpty()) {
            try {
                gptAPI = GPTAPI(gptApiKey, gptModel)
                Logger.log("GPT API initialized with model: $gptModel")
            } catch (e: Exception) {
                Logger.log("Failed to initialize GPT API: ${e.message}")
            }
        } else {
            Logger.log("GPT API key is empty")
        }

        val deepSeekApiKey = preferences?.getString("deepseek_api_key", "") ?: ""
        if (deepSeekApiKey.isNotEmpty()) {
            try {
                deepSeekAPI = DeepSeekAPI(deepSeekApiKey)
                Logger.log("DeepSeek API initialized")
            } catch (e: Exception) {
                Logger.log("Failed to initialize DeepSeek API: ${e.message}")
            }
        } else {
            Logger.log("DeepSeek API key is empty")
        }

        if (gptApiKey.isNotEmpty() && assistantId.isNotEmpty()) {
            try {
                assistantsAPI = AssistantsAPI(gptApiKey, assistantId)
                Logger.log("Assistants API initialized with assistantId: $assistantId")
            } catch (e: Exception) {
                Logger.log("Failed to initialize Assistants API: ${e.message}")
            }
        } else {
            Logger.log("Assistants API initialization skipped - missing keys")
        }
    }

    private fun setupSuggestionButtons() {
        gptSuggestButton?.setOnClickListener {
            val selectedText = getSelectedText()
            val clipboardText = getClipboardText() ?: ""
            val textToProcess = if (selectedText.isNotEmpty()) selectedText else clipboardText

            if (textToProcess.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboardManager.setPrimaryClip(ClipData.newPlainText("AI Keyboard Copy", textToProcess))
                addTextToClipboardHistory(textToProcess)

                val prompt = "Dựa trên nội dung này: '$textToProcess'\n\nTạo 3 đề xuất phản hồi khác nhau để tiếp tục câu chuyện theo style sau:\n1. Hài hước\n Thế hệ Z\n Trang trọng. Mỗi đề xuất cần trở nên độc đáo và phù hợp với ngữ cảnh, ngôn ngữ văn bản gợi ý giống với nội dung cuộc trò chuyện."

                currentInputConnection?.commitText("\n", 1)
                currentInputConnection?.commitText("Thinking...", 1)
                thinkingTextLength = "Thinking...".length
                stopGenerationButton?.visibility = View.VISIBLE

                generationJob?.cancel() // Hủy job cũ nếu có
                generationJob = CoroutineScope(Dispatchers.Main).launch {
                    try {
                        var fullResponse = StringBuilder()
                        lastGptFunction = "suggest"

                        val ic = currentInputConnection
                        gptAPI?.streamChatCompletion(prompt, false, currentInputConnection)?.collect { response ->
                            if (thinkingTextLength > 0) {
                                ic?.deleteSurroundingText(thinkingTextLength, 0)
                                thinkingTextLength = 0
                                ic?.commitText("\n", 1)
                            }
                            currentInputConnection?.commitText(response, 1)
                            fullResponse.append(response)
                        }
                        currentInputConnection?.commitText("\n", 1)

                        if (gptAPI?.getLastFinishReason() == "length") {
                            gptContinueButton?.visibility = View.VISIBLE
                        } else {
                            gptContinueButton?.visibility = View.GONE
                        }
                        captureGPTResponse(fullResponse.toString())
                    } catch (e: Exception) {
                        Logger.log("Error in GPT Suggest: ${e.message}")
                        if (thinkingTextLength > 0) {
                            currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                            thinkingTextLength = 0
                        }
                        if (e !is CancellationException) {
                            showToast("Error: ${e.message}")
                        }
                    } finally {
                        stopGenerationButton?.visibility = View.GONE
                    }
                }
            }
        }

        deepseekSuggestButton?.setOnClickListener {
            val selectedText = getSelectedText()
            val clipboardText = getClipboardText() ?: ""
            val textToProcess = if (selectedText.isNotEmpty()) selectedText else clipboardText

            if (textToProcess.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboardManager.setPrimaryClip(ClipData.newPlainText("AI Keyboard Copy", textToProcess))
                addTextToClipboardHistory(textToProcess)

                val prompt = "Dựa trên cuộc trò chuyện này: '$textToProcess'\n\nTạo 3 đề xuất phản hồi khác nhau cho kiểu sau:\n1. Hài hước mix với styles Thế hệ Z\nLàm cho mỗi đề xuất trở nên độc đáo và phù hợp với ngữ cảnh, ngôn ngữ văn bản gợi ý giống với nội dung cuộc trò chuyện."

                currentInputConnection?.commitText("\n", 1)
                currentInputConnection?.commitText("Thinking...", 1)
                thinkingTextLength = "Thinking...".length
                stopGenerationButton?.visibility = View.VISIBLE

                generationJob?.cancel() // Hủy job cũ nếu có
                generationJob = CoroutineScope(Dispatchers.Main).launch {
                    try {
                        var fullResponse = StringBuilder()

                        deepSeekAPI?.streamTranslate(prompt, "Vietnamese", currentInputConnection)?.collect { response ->
                            if (thinkingTextLength > 0) {
                                currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                                thinkingTextLength = 0
                                currentInputConnection?.commitText("\n", 1)
                            }
                            currentInputConnection?.commitText(response, 1)
                            fullResponse.append(response)
                        }
                        currentInputConnection?.commitText("\n", 1)

                        captureGPTResponse(fullResponse.toString())
                    } catch (e: Exception) {
                        Logger.log("Error in DeepSeek Suggest: ${e.message}")
                        if (thinkingTextLength > 0) {
                            currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                            thinkingTextLength = 0
                        }
                        if (e !is CancellationException) {
                            showToast("Error: ${e.message}")
                        }
                    } finally {
                        stopGenerationButton?.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        keyboard = layoutInflater.inflate(R.layout.keyboard_layout, null)
        keyboardView = keyboard?.findViewById(R.id.keyboard) as KeyboardView
        keyboardView.keyboard = currentKeyboard
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.isPreviewEnabled = false

        initializeViews()
        setupSmartbar()

        gptModelSpinner = keyboard?.findViewById(R.id.gptModelSpinner)
        gptModelSpinner?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, gptModels)
        gptModelSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedModel = gptModels[position]
                preferences?.edit()?.putString("selected_gpt_model", selectedModel)?.apply()
                val apiKey = preferences?.getString("gpt_api_key", "") ?: ""
                val assistantsId = preferences?.getString("gpt_assistants_id", "") ?: ""
                if (apiKey.isNotEmpty()) {
                    gptAPI = GPTAPI(apiKey, selectedModel)
                    if (assistantsId.isNotEmpty()) {
                        assistantsAPI = AssistantsAPI(apiKey, assistantsId)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        gptContinueButton = keyboard?.findViewById(R.id.gptContinueButton)
        gptContinueButton?.setOnClickListener {
            Logger.log("GPT Continue button clicked")
            handleGptContinueGenerating()
        }

        return keyboard as View
    }

    private fun initializeViews() {
        translateButton = keyboard?.findViewById(R.id.translateButton)
        askButton = keyboard?.findViewById(R.id.askButton)
        gptTranslateButton = keyboard?.findViewById(R.id.gptTranslateButton)
        gptAskButton = keyboard?.findViewById(R.id.gptAskButton)
        gptContinueButton = keyboard?.findViewById(R.id.gptContinueButton)
        gptSuggestButton = keyboard?.findViewById(R.id.gptSuggestButton)
        deepseekSuggestButton = keyboard?.findViewById(R.id.deepseekSuggestButton)
        stopGenerationButton = keyboard?.findViewById(R.id.stopGenerationButton)
        assistantsGptButton = keyboard?.findViewById(R.id.assistantsGptButton)

        setupSuggestionButtons()
        btnPasteAndRead = keyboard?.findViewById(R.id.btnPasteAndRead)
        btnStopTts = keyboard?.findViewById(R.id.btnStopTts)
        btnMic = keyboard?.findViewById(R.id.btnMic)
    }

    private fun setupSmartbar() {
        setupLanguageSpinner()
        setupSmartbarButtons()
        setupTTSButtons()
        setupLanguageToggleButton()
        setupClipboardHistorySpinner()
        setupCalculatorButton()
        setupSwitchKeyboardButton()
    }

    private fun setupSmartbarButtons() {
        translateButton?.setOnClickListener { handleDeepSeekTranslate() }
        askButton?.setOnClickListener { handleDeepSeekAsk() }
        gptTranslateButton?.setOnClickListener { handleGptTranslate() }
        gptAskButton?.setOnClickListener { processGPTAsk() }
        gptContinueButton?.setOnClickListener {
            Logger.log("GPT Continue button clicked")
            handleGptContinueGenerating()
        }
        stopGenerationButton?.setOnClickListener {
            Logger.log("Stop Generation button clicked")
            handleStopGeneration()
        }
        assistantsGptButton?.setOnClickListener {
            Logger.log("Assistants GPT button clicked")
            handleAssistantsGpt()
        }
        btnMic?.setOnClickListener {
            Logger.log("Mic button clicked")
            onMicButtonClick(it)
        }
    }

    private fun handleAssistantsGpt() {
        Logger.log("handleAssistantsGpt called")
    
        val prefs = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)
        val gptApiKey = prefs.getString("gpt_api_key", "") ?: ""
        val assistantId = prefs.getString("gpt_assistants_id", "") ?: ""
    
        if (gptApiKey.isEmpty() || assistantId.isEmpty()) {
            Logger.log("GPT API key or Assistant ID is empty")
            showToast("Please set your GPT API key and Assistant ID in settings")
            return
        }
    
        // Luôn khởi tạo lại để đảm bảo sử dụng key và ID mới nhất
        try {
            assistantsAPI = AssistantsAPI(gptApiKey, assistantId)
            Logger.log("Assistants API initialized with assistantId: $assistantId")
        } catch (e: Exception) {
            Logger.log("Failed to initialize Assistants API", e)
            showToast("Error initializing Assistants API: ${e.message}")
            return
        }
    
        val clipboardText = getClipboardText()
        if (clipboardText.isNullOrEmpty()) {
            showToast("Please copy text to ask assistant")
            return
        }
    
        lastGptFunction = "assistants"
        currentInputConnection?.commitText("\nThinking...", 1)
        thinkingTextLength = "\nThinking...".length
        stopGenerationButton?.visibility = View.VISIBLE
    
        generationJob?.cancel() // Hủy job cũ nếu có
        generationJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                Logger.log("Starting Assistants GPT request with text: ${clipboardText.take(100)}...")
                var fullResponse = StringBuilder()
    
                assistantsAPI?.sendMessage(clipboardText)?.collect { chunk ->
                    if (thinkingTextLength > 0) {
                        currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                        thinkingTextLength = 0
                        currentInputConnection?.commitText("\n", 1)
                    }
                    if (chunk.startsWith("Error: ")) {
                        // Hiển thị lỗi riêng biệt
                        currentInputConnection?.commitText("\n$chunk\n", 1)
                    } else {
                        currentInputConnection?.commitText(chunk, 1)
                        fullResponse.append(chunk)
                    }
                }
                currentInputConnection?.commitText("\n", 1)
    
                val lastFinishReason = assistantsAPI?.getLastFinishReason()
                Logger.log("Last finish reason: $lastFinishReason")
                when (lastFinishReason) {
                    "completed" -> {
                        gptContinueButton?.visibility = View.GONE
                        captureGPTResponse(fullResponse.toString())
                    }
                    "failed", "expired" -> {
                        gptContinueButton?.visibility = View.GONE
                        assistantsAPI?.clearConversation() // Làm mới thread nếu thất bại hoặc hết hạn
                    }
                    else -> {
                        gptContinueButton?.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Logger.log("Assistants GPT error: ${e.message}")
                if (thinkingTextLength > 0) {
                    currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                    thinkingTextLength = 0
                }
                if (e !is CancellationException) {
                    currentInputConnection?.commitText("\nAssistants error: ${e.message ?: "Unknown error"}\n", 1)
                }
                gptContinueButton?.visibility = View.GONE
            } finally {
                stopGenerationButton?.visibility = View.GONE
            }
        }
    }

    private fun setupSwitchKeyboardButton() {
        val btnSwitchKeyboard = keyboard?.findViewById<ImageButton>(R.id.btnSwitchKeyboard)
        btnSwitchKeyboard?.setOnClickListener {
            Logger.log("Switch Keyboard button clicked")
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
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

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupTTSButtons() {
        btnPasteAndRead?.setOnClickListener {
            Logger.log("Paste and Read button clicked")
            pasteAndReadText()
        }
        btnStopTts?.setOnClickListener {
            Logger.log("Stop TTS button clicked")
            stopTts()
        }
    }

    private fun setupLanguageToggleButton() {
        val languageButton = keyboard?.findViewById<Button>(R.id.languageButton)
        languageButton?.setOnClickListener { toggleLanguage() }
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

    private fun setupClipboardHistorySpinner() {
        val clipboardHistorySpinner = keyboard?.findViewById<Spinner>(R.id.clipboardHistorySpinner) ?: return
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, clipboardHistory)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        clipboardHistorySpinner.adapter = adapter

        var isUserInitiatedSelection = false
        clipboardHistorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isUserInitiatedSelection && position >= 0 && position < clipboardHistory.size) {
                    val selectedText = clipboardHistory[position]
                    currentInputConnection?.commitText(selectedText, 1)
                    showToast("Inserted: ${selectedText.take(20)}...")
                }
                isUserInitiatedSelection = false
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        clipboardHistorySpinner.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                isUserInitiatedSelection = true
            }
            false
        }

        clipboardHistorySpinner.setOnLongClickListener {
            val textToCopy = getSelectedText().ifBlank { getClipboardText() ?: "" }
            if (textToCopy.isNotBlank()) {
                addTextToClipboardHistory(textToCopy)
            }
            true
        }
    }

    private fun setupCalculatorButton() {
        btnTinhToan = keyboard?.findViewById(R.id.btnTinhToan)
        btnTinhToan?.setOnClickListener {
            if (calculatorPopup?.isShowing == true) {
                calculatorPopup?.dismiss()
            } else {
                calculatorExpression.clear()
                calculatorResult?.text = "0"
                calculatorPopup?.showAtLocation(keyboard, Gravity.BOTTOM, 0, 0)
            }
        }
    }

    private fun setupCalculatorKeyboard() {
        calculatorKeyboard = layoutInflater.inflate(R.layout.calculator_keyboard, null)
        calculatorResult = calculatorKeyboard?.findViewById(R.id.calculatorResult)
        val keyboardContainer = calculatorKeyboard?.findViewById<LinearLayout>(R.id.calculatorKeyboardContainer)

        calculatorPopup = PopupWindow(calculatorKeyboard, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            isOutsideTouchable = true
            isFocusable = true
        }

        val btnQuayLai = calculatorKeyboard?.findViewById<Button>(R.id.btnQuayLai)
        btnQuayLai?.setOnClickListener { calculatorPopup?.dismiss() }

        val btnInVanBan = calculatorKeyboard?.findViewById<Button>(R.id.btnInVanBan)
        btnInVanBan?.setOnClickListener {
            val resultText = calculatorResult?.text.toString()
            currentInputConnection?.commitText(resultText, 1)
            calculatorPopup?.dismiss()
        }

        val rows = 4
        val cols = 4
        for (row in 0 until rows) {
            val rowLayout = LinearLayout(this)
            rowLayout.orientation = LinearLayout.HORIZONTAL
            rowLayout.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

            for (col in 0 until cols) {
                val index = row * cols + col
                val button = Button(this)
                button.text = calculatorKeys[index]
                button.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                button.setOnClickListener { handleCalculatorButtonClick(calculatorKeys[index]) }
                rowLayout.addView(button)
            }
            keyboardContainer?.addView(rowLayout)
        }

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
                    calculatorResult?.text = "${calculatorExpression} = ${formatResult(result)}"
                    calculatorExpression.append(" = $result")
                } catch (e: Exception) {
                    calculatorResult?.text = "Error"
                }
            }
            "C" -> {
                if (calculatorExpression.isNotEmpty()) {
                    calculatorExpression.deleteCharAt(calculatorExpression.length - 1)
                    calculatorResult?.text = calculatorExpression.toString().ifEmpty { "0" }
                }
            }
            in listOf("+", "-", "×", "÷") -> {
                if (lastCalculationResult != null) {
                    calculatorExpression.clear()
                    calculatorExpression.append(lastCalculationResult)
                    lastCalculationResult = null
                }
                calculatorExpression.append(when (key) { "×" -> "*"; "÷" -> "/"; else -> key })
                calculatorResult?.text = calculatorExpression.toString()
            }
            else -> {
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
            val cleanedExpression = expression.replace("×", "*").replace("÷", "/")
            val result = object : Any() {
                var pos = -1
                var ch = 0

                fun nextChar() {
                    ch = if (++pos < cleanedExpression.length) cleanedExpression[pos].toInt() else -1
                }

                fun eat(charToEat: Char): Boolean {
                    while (ch == ' '.toInt()) nextChar()
                    if (ch == charToEat.toInt()) {
                        nextChar()
                        return true
                    }
                    return false
                }

                fun parse(): Double {
                    nextChar()
                    val x = parseExpression()
                    if (pos < cleanedExpression.length) throw RuntimeException("Unexpected: ${ch.toChar()}")
                    return x
                }

                fun parseExpression(): Double {
                    var x = parseTerm()
                    while (true) {
                        when {
                            eat('+') -> x += parseTerm()
                            eat('-') -> x -= parseTerm()
                            else -> return x
                        }
                    }
                }

                fun parseTerm(): Double {
                    var x = parseFactor()
                    while (true) {
                        when {
                            eat('*') -> x *= parseFactor()
                            eat('/') -> x /= parseFactor()
                            else -> return x
                        }
                    }
                }

                fun parseFactor(): Double {
                    if (eat('+')) return parseFactor()
                    if (eat('-')) return -parseFactor()

                    var x: Double
                    val startPos = pos
                    if (eat('(')) {
                        x = parseExpression()
                        eat(')')
                    } else if ((ch in '0'.toInt()..'9'.toInt()) || ch == '.'.toInt()) {
                        while ((ch in '0'.toInt()..'9'.toInt()) || ch == '.'.toInt()) nextChar()
                        x = cleanedExpression.substring(startPos, pos).toDouble()
                    } else {
                        throw RuntimeException("Unexpected: ${ch.toChar()}")
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
        return if (result % 1.0 == 0.0) result.toLong().toString() else "%.2f".format(result)
    }

    private var isSpeaking = false
    private fun pasteAndReadText() {
        Logger.log("pasteAndReadText called")
        if (isSpeaking) {
            Logger.log("TTS is already speaking, ignoring request")
            return
        }
        try {
            val clipboardText = getClipboardText()
            Logger.log("Clipboard text: ${clipboardText?.take(100)}...")
            if (!clipboardText.isNullOrEmpty()) {
                GlobalScope.launch(Dispatchers.Main) {
                    try {
                        speakText(clipboardText)
                    } catch (e: Exception) {
                        Logger.log("Error in speakText: ${e.message}")
                        showToast("Error speaking text: ${e.message}")
                    }
                }
            } else {
                Logger.log("Clipboard is empty")
                showToast("No text to read")
            }
        } catch (e: Exception) {
            Logger.log("Error in pasteAndReadText: ${e.message}")
            showToast("Error accessing clipboard")
        }
    }

    private val MAX_TTS_LENGTH = 4000
    private suspend fun speakText(text: String) {
        if (!isTtsInitialized) {
            Logger.log("TTS not initialized")
            showToast("Text-to-Speech not initialized")
            return
        }

        Logger.log("Starting to speak text")
        isSpeaking = true
        try {
            val segments = mutableListOf<String>()
            var remainingText = text
            while (remainingText.isNotEmpty()) {
                if (remainingText.length <= MAX_TTS_LENGTH) {
                    segments.add(remainingText)
                    break
                } else {
                    var cutIndex = remainingText.lastIndexOf(' ', MAX_TTS_LENGTH - 1)
                    if (cutIndex == -1) cutIndex = MAX_TTS_LENGTH
                    segments.add(remainingText.substring(0, cutIndex))
                    remainingText = remainingText.substring(cutIndex).trim()
                }
            }

            Logger.log("Split text into ${segments.size} segments")
            for (segment in segments) {
                if (segment.isNotEmpty()) {
                    Logger.log("Speaking segment: ${segment.take(100)}...")
                    val speakJob = CoroutineScope(Dispatchers.IO).async {
                        val detectedLanguage = withContext(Dispatchers.IO) {
                            detectLanguage(segment)
                        }
                        Logger.log("Detected language: $detectedLanguage")
                        val locale = withContext(Dispatchers.IO) {
                            getLocaleForLanguage(detectedLanguage)
                        }
                        val speakCompleted = CompletableDeferred<Unit>()

                        withContext(Dispatchers.Main) {
                            tts?.language = locale
                            tts?.setOnUtteranceCompletedListener {
                                Logger.log("Utterance completed")
                                speakCompleted.complete(Unit)
                            }
                            tts?.speak(
                                segment,
                                TextToSpeech.QUEUE_FLUSH,
                                Bundle().apply {
                                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "segment")
                                },
                                "segment"
                            )
                        }
                        speakCompleted.await()
                    }
                    speakJob.await()
                }
            }
            Logger.log("Finished speaking all segments")
        } catch (e: Exception) {
            Logger.log("Error in speakText: ${e.message}")
            showToast("Error speaking text")
        } finally {
            isSpeaking = false
        }
    }

    private fun stopTts() {
        Logger.log("Stopping TTS")
        try {
            textToSpeech?.stop()
            tts?.stop()
            isSpeaking = false
            tts?.shutdown()
            textToSpeech?.shutdown()
            tts = TextToSpeech(this, this)
            textToSpeech = TextToSpeech(this, this)
            Logger.log("TTS stopped and reinitialized")
        } catch (e: Exception) {
            Logger.log("Error stopping TTS: ${e.message}")
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
        lastTranslateLanguage = targetLanguage
        lastGptFunction = "translate"

        currentInputConnection?.commitText("\nThinking...", 1)
        thinkingTextLength = "\nThinking...".length
        stopGenerationButton?.visibility = View.VISIBLE

        generationJob?.cancel() // Hủy job cũ nếu có
        generationJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                Logger.log("Starting GPT translation request")
                currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                var fullResponse = StringBuilder()

                val ic = currentInputConnection
                gptAPI?.streamTranslate(clipboardText, targetLanguage, currentInputConnection)?.collect { chunk ->
                    ic?.commitText(chunk, 1)
                    fullResponse.append(chunk)
                }
                ic?.commitText("\n", 1)

                if (gptAPI?.getLastFinishReason() == "length") {
                    gptContinueButton?.visibility = View.VISIBLE
                } else {
                    gptContinueButton?.visibility = View.GONE
                }
                captureGPTResponse(fullResponse.toString())
            } catch (e: Exception) {
                Logger.log("GPT translation error", e)
                currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                if (e !is CancellationException) {
                    currentInputConnection?.commitText("\nTranslation error: ${e.message}\n", 1)
                }
                gptContinueButton?.visibility = View.GONE
            } finally {
                stopGenerationButton?.visibility = View.GONE
            }
        }
    }

    private fun processGPTAsk() {
        Logger.log("processGPTAsk called")
        val clipboardText = getClipboardText()
        if (clipboardText.isNullOrEmpty()) {
            showToast("Please copy text to ask about")
            return
        }

        val gptApiKey = preferences?.getString("gpt_api_key", "") ?: ""
        Logger.log("GPT API key length: ${gptApiKey.length}")

        if (gptApiKey.isEmpty()) {
            showToast("Please set your GPT API key in settings")
            return
        }

        if (gptAPI == null) {
            try {
                val model = preferences?.getString("selected_gpt_model", "gpt-3.5-turbo") ?: "gpt-3.5-turbo"
                gptAPI = GPTAPI(gptApiKey, model)
                Logger.log("Created new GPT API instance with model: $model")
            } catch (e: Exception) {
                Logger.log("Failed to initialize GPT API: ${e.message}")
                showToast("Error initializing GPT API")
                return
            }
        }

        currentInputConnection?.commitText("\nThinking...", 1)
        thinkingTextLength = "\nThinking...".length
        stopGenerationButton?.visibility = View.VISIBLE

        generationJob?.cancel() // Hủy job cũ nếu có
        generationJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                Logger.log("Starting GPT request with text: ${clipboardText.take(100)}...")
                currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                var fullResponse = StringBuilder()
                lastGptFunction = "ask"

                val ic = currentInputConnection
                gptAPI?.streamAskQuestion(clipboardText, currentInputConnection)?.collect { chunk ->
                    ic?.commitText(chunk, 1)
                    fullResponse.append(chunk)
                }
                ic?.commitText("\n", 1)

                if (gptAPI?.getLastFinishReason() == "length") {
                    Logger.log("Response truncated, showing continue button")
                    gptContinueButton?.visibility = View.VISIBLE
                } else {
                    Logger.log("Response complete, hiding continue button")
                    gptContinueButton?.visibility = View.GONE
                }
                captureGPTResponse(fullResponse.toString())
            } catch (e: Exception) {
                Logger.log("GPT ask error: ${e.message}")
                currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                if (e !is CancellationException) {
                    currentInputConnection?.commitText("\nError: ${e.message}\n", 1)
                }
                gptContinueButton?.visibility = View.GONE
            } finally {
                stopGenerationButton?.visibility = View.GONE
            }
        }
    }

    private fun handleStopGeneration() {
        generationJob?.cancel() // Hủy tất cả các quá trình tạo nội dung
        gptAPI?.clearConversation()
        deepSeekAPI?.clearConversation()
        assistantsAPI?.clearConversation()
        currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
        currentInputConnection?.commitText("\nGeneration stopped.", 1)
        stopGenerationButton?.visibility = View.GONE
        gptContinueButton?.visibility = View.GONE
        thinkingTextLength = 0 // Reset độ dài văn bản "Thinking..."
    }

    private fun handleGptContinueGenerating() {
        currentInputConnection?.commitText("\nThinking...", 1)
        thinkingTextLength = "\nThinking...".length
        stopGenerationButton?.visibility = View.VISIBLE

        generationJob?.cancel() // Hủy job cũ nếu có
        generationJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                var fullResponse = StringBuilder()
                gptAPI?.streamContinueGeneration(currentInputConnection)?.collect { response ->
                    currentInputConnection?.commitText(response, 1)
                    fullResponse.append(response)
                }
                currentInputConnection?.commitText("\n", 1)
                captureGPTResponse(fullResponse.toString())
            } catch (e: Exception) {
                Logger.log("GPT Continue error", e)
                currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                if (e !is CancellationException) {
                    currentInputConnection?.commitText("\nError continuing generation: ${e.message}\n", 1)
                }
                gptContinueButton?.visibility = View.GONE
            } finally {
                stopGenerationButton?.visibility = View.GONE
            }
        }
    }

    private fun captureGPTResponse(response: String) {
        if (response.isNotBlank()) {
            isFromApp = true
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("GPT Response", response))
            addTextToClipboardHistory(response)
        }
    }

    fun getSelectedText(): String {
        val ic = currentInputConnection ?: return ""
        var selectedText = ic.getSelectedText(0)?.toString()
        if (selectedText.isNullOrEmpty()) {
            val beforeLength = 10000
            val afterLength = 10000
            val textBeforeCursor = ic.getTextBeforeCursor(beforeLength, 0)?.toString() ?: ""
            val textAfterCursor = ic.getTextAfterCursor(afterLength, 0)?.toString() ?: ""
            selectedText = textBeforeCursor + textAfterCursor
        }
        return selectedText ?: ""
    }

    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        Log.e("AIKeyboard", "Error: $message")
        currentInputConnection?.commitText("\nError: $message\n", 1)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "AIKeyboard Error: $message", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (restarting) {
            currentThreadId = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        generationJob?.cancel() // Hủy bất kỳ job nào đang chạy
        stopTts()
        speechRecognizer.destroy()
        clipboardManager.removePrimaryClipChangedListener(this)
        Logger.log("AIKeyboardService destroyed")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            Log.d("AIKeyboard", "TTS initialized successfully")
        } else {
            Log.e("AIKeyboard", "TTS initialization failed with status: $status")
        }
    }

    private val clipboardHistory = mutableListOf<String>()

    override fun onPrimaryClipChanged() {
        if (isFromApp) {
            isFromApp = false
            return
        }

        val clipText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
        if (clipText != null) {
            addTextToClipboardHistory(clipText, false)
        }
    }

    private fun addTextToClipboardHistory(text: String, showToast: Boolean = true) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank() || clipboardHistory.contains(trimmedText)) return

        if (clipboardHistory.size >= 10) clipboardHistory.removeAt(0)
        clipboardHistory.add(trimmedText)
        (keyboard?.findViewById<Spinner>(R.id.clipboardHistorySpinner)?.adapter as? ArrayAdapter<*>)?.notifyDataSetChanged()

        if (showToast) showToast("Text added to clipboard history")
    }

    fun getClipboardText(): String? {
        return clipboardManager.primaryClip?.let { clip ->
            if (clip.itemCount > 0) clip.getItemAt(0).text?.toString()?.trim() else null
        }
    }

    override fun onPress(primaryCode: Int) {
        keyboardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        showKeyPreview(primaryCode)
    }

    override fun onRelease(primaryCode: Int) {
        hideKeyPreview()
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        when (primaryCode) {
            -2 -> {
                when (currentKeyboard) {
                    normalKeyboard -> {
                        currentKeyboard = symbolKeyboard1
                        keyboardView.keyboard = symbolKeyboard1
                    }
                    symbolKeyboard1, symbolKeyboard2 -> {
                        currentKeyboard = normalKeyboard
                        keyboardView.keyboard = normalKeyboard
                    }
                }
            }
            -3 -> {
                when (currentKeyboard) {
                    symbolKeyboard1 -> {
                        currentKeyboard = symbolKeyboard2
                        keyboardView.keyboard = symbolKeyboard2
                    }
                    symbolKeyboard2 -> {
                        currentKeyboard = symbolKeyboard1
                        keyboardView.keyboard = symbolKeyboard1
                    }
                }
            }
            -1 -> {
                val now = System.currentTimeMillis()
                if (now - lastShiftClickTime < DOUBLE_CLICK_THRESHOLD) {
                    shiftMode = if (shiftMode == 2) 0 else 2
                } else {
                    shiftMode = if (shiftMode == 0) 1 else 0
                }
                lastShiftClickTime = now
                updateShiftState()
            }
            else -> {
                val ic = currentInputConnection
                if (ic != null) {
                    when (primaryCode) {
                        -5 -> {
                            val selectedText = ic.getSelectedText(0)
                            if (selectedText.isNullOrEmpty()) {
                                ic.deleteSurroundingText(1, 0)
                            } else {
                                ic.commitText("", 1)
                            }
                        }
                        -4 -> {
                            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        }
                        else -> {
                            if (isVietnameseMode) {
                                processVietnameseInput(primaryCode.toChar())
                            } else {
                                val code = primaryCode.toChar()
                                val text = when {
                                    shiftMode == 2 -> code.uppercase()
                                    shiftMode == 1 -> {
                                        shiftMode = 0
                                        updateShiftState()
                                        code.uppercase()
                                    }
                                    else -> code.toString()
                                }
                                ic.commitText(text, 1)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onText(text: CharSequence?) {
        val ic = currentInputConnection
        if (ic != null && text != null) {
            ic.commitText(text, 1)
        }
    }

    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

    fun updateShiftState() {
        keyboardView.isShifted = shiftMode > 0
        keyboardView.invalidateAllKeys()
    }

    private fun processVietnameseInput(char: Char) {
        val precedingText = currentInputConnection?.getTextBeforeCursor(10, 0)?.toString() ?: ""
        val (deleteCount, newText) = telexComposer.getActions(precedingText, char.lowercaseChar().toString())
        if (deleteCount > 0) currentInputConnection?.deleteSurroundingText(deleteCount, 0)

        val finalText = when {
            shiftMode == 2 -> newText.uppercase()
            shiftMode == 1 -> newText.replaceFirstChar { it.uppercase() }
            else -> newText
        }

        currentInputConnection?.commitText(finalText, 1)

        if (shiftMode == 1) {
            shiftMode = 0
            updateShiftState()
        }
    }

    suspend fun detectLanguage(text: String): String = withContext(Dispatchers.IO) {
        if (text.isEmpty()) return@withContext lastDetectedLanguage

        var hasVietnamese = false
        var hasChinese = false
        var hasJapanese = false
        var hasKorean = false
        var hasLatin = false
        var hasMalay = false
        var hasThai = false
        var hasHindi = false
        var hasId = false
        var hasArabic = false
        var hasRussian = false
        var hasItalian = false
        var hasGerman = false
        var hasPortuguese = false
        var hasFrench = false
        var hasSpanish = false

        for (char in text) {
            when {
                char in '\u0300'..'\u036F' || char in '\u1EA0'..'\u1EF9' -> hasVietnamese = true
                char in '\u4E00'..'\u9FFF' -> hasChinese = true
                char in '\u3040'..'\u309F' || char in '\u30A0'..'\u30FF' -> hasJapanese = true
                char in '\uAC00'..'\uD7AF' -> hasKorean = true
                char in 'A'..'Z' || char in 'a'..'z' -> hasLatin = true
                char in '\u0600'..'\u06FF' -> hasArabic = true
                char in '\u0900'..'\u097F' -> hasHindi = true
                char in '\u0E00'..'\u0E7F' -> hasThai = true
                char in '\u0100'..'\u017F' -> hasId = true
                char in '\u0400'..'\u04FF' -> hasRussian = true
                char in '\u0100'..'\u017F' -> hasItalian = true
                char in '\u00C0'..'\u00FF' -> hasGerman = true
                char in '\u0100'..'\u017F' -> hasPortuguese = true
                char in '\u00C0'..'\u00FF' -> hasFrench = true
                char in '\u00C0'..'\u00FF' -> hasSpanish = true
                char in '\u0100'..'\u017F' -> hasMalay = true
            }
        }

        return@withContext when {
            hasVietnamese -> "vi"
            hasChinese -> "zh"
            hasJapanese -> "ja"
            hasKorean -> "ko"
            hasLatin -> "en"
            hasMalay -> "ms"
            hasThai -> "th"
            hasHindi -> "hi"
            hasId -> "id"
            hasArabic -> "ar"
            hasRussian -> "ru"
            hasItalian -> "it"
            hasGerman -> "de"
            hasPortuguese -> "pt"
            hasFrench -> "fr"
            hasSpanish -> "es"
            else -> "vi"
        }
    }

    suspend fun getLocaleForLanguage(lang: String): Locale = withContext(Dispatchers.IO) {
        return@withContext when (lang) {
            "vi" -> Locale("vi", "VN")
            "zh" -> Locale.CHINESE
            "ja" -> Locale.JAPANESE
            "ko" -> Locale.KOREAN
            "en" -> Locale.US
            "fr" -> Locale.FRANCE
            "es" -> Locale("es", "ES")
            "it" -> Locale.ITALIAN
            "de" -> Locale.GERMAN
            "pt" -> Locale("pt", "PT")
            "ru" -> Locale("ru", "RU")
            "ar" -> Locale("ar", "SA")
            "hi" -> Locale("hi", "IN")
            "th" -> Locale("th", "TH")
            "id" -> Locale("id", "ID")
            "ms" -> Locale("ms", "MY")
            else -> Locale.getDefault()
        }
    }

    private fun setupKeyPopup() {
        keyPopupView = layoutInflater.inflate(R.layout.custom_key_popup, null)
        keyPopupWindow = PopupWindow(
            keyPopupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isClippingEnabled = false
            isTouchable = false
            isOutsideTouchable = false
        }
    }

    private fun showKeyPreview(primaryCode: Int) {
        val key = currentKeyboard?.keys?.find { it.codes.contains(primaryCode) } ?: return
        val popupText = keyPopupView?.findViewById<TextView>(R.id.popupText)
        popupText?.text = key.label ?: key.codes[0].toChar().toString()

        popupText?.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val keyboardLocation = IntArray(2)
        keyboardView.getLocationInWindow(keyboardLocation)

        val x = keyboardLocation[0] + key.x + (key.width - popupText?.measuredWidth!!) / 2
        val y = keyboardLocation[1] + key.y - popupText.measuredHeight - 2

        keyPopupWindow?.dismiss()
        keyPopupWindow?.showAtLocation(keyboardView, Gravity.NO_GRAVITY, x, y)
    }

    private fun hideKeyPreview() {
        keyPopupWindow?.dismiss()
    }

    private fun handleDeepSeekTranslate() {
        Logger.log("handleDeepSeekTranslate called")
        val clipboardText = getClipboardText()
        if (clipboardText.isNullOrEmpty()) {
            showToast("Please copy text to translate")
            return
        }

        val deepSeekApiKey = preferences?.getString("deepseek_api_key", "") ?: ""
        if (deepSeekApiKey.isEmpty()) {
            showToast("Please set your DeepSeek API key in settings")
            return
        }

        if (deepSeekAPI == null) {
            try {
                deepSeekAPI = DeepSeekAPI(deepSeekApiKey)
            } catch (e: Exception) {
                Logger.log("Failed to initialize DeepSeek API: ${e.message}")
                showToast("Error initializing DeepSeek API")
                return
            }
        }

        val targetLanguage = languageSpinner?.selectedItem?.toString() ?: "English"
        Logger.log("Target language: $targetLanguage")

        currentInputConnection?.commitText("\nThinking...", 1)
        thinkingTextLength = "\nThinking...".length
        stopGenerationButton?.visibility = View.VISIBLE

        generationJob?.cancel() // Hủy job cũ nếu có
        generationJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                Logger.log("Starting DeepSeek translation request")
                currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                var fullResponse = StringBuilder()

                deepSeekAPI?.streamTranslate(clipboardText, targetLanguage, currentInputConnection)?.collect { chunk ->
                    currentInputConnection?.commitText(chunk, 1)
                    fullResponse.append(chunk)
                }
                currentInputConnection?.commitText("\n", 1)

                captureGPTResponse(fullResponse.toString())
            } catch (e: Exception) {
                Logger.log("DeepSeek translation error", e)
                currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                if (e !is CancellationException) {
                    currentInputConnection?.commitText("\nTranslation error: ${e.message}\n", 1)
                }
            } finally {
                stopGenerationButton?.visibility = View.GONE
            }
        }
    }

    private fun handleDeepSeekAsk() {
        Logger.log("handleDeepSeekAsk called")
        val clipboardText = getClipboardText()
        if (clipboardText.isNullOrEmpty()) {
            showToast("Please copy text to ask about")
            return
        }

        val deepSeekApiKey = preferences?.getString("deepseek_api_key", "") ?: ""
        if (deepSeekApiKey.isEmpty()) {
            showToast("Please set your DeepSeek API key in settings")
            return
        }

        if (deepSeekAPI == null) {
            try {
                deepSeekAPI = DeepSeekAPI(deepSeekApiKey)
            } catch (e: Exception) {
                Logger.log("Failed to initialize DeepSeek API: ${e.message}")
                showToast("Error initializing DeepSeek API")
                return
            }
        }

        currentInputConnection?.commitText("\nThinking...", 1)
        thinkingTextLength = "\nThinking...".length
        stopGenerationButton?.visibility = View.VISIBLE

        generationJob?.cancel() // Hủy job cũ nếu có
        generationJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                Logger.log("Starting DeepSeek request with text: ${clipboardText.take(100)}...")
                currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                var fullResponse = StringBuilder()

                deepSeekAPI?.streamAskQuestion(clipboardText, currentInputConnection!!)?.collect { chunk ->
                    currentInputConnection?.commitText(chunk, 1)
                    fullResponse.append(chunk)
                }
                currentInputConnection?.commitText("\n", 1)

                captureGPTResponse(fullResponse.toString())
            } catch (e: Exception) {
                Logger.log("DeepSeek ask error: ${e.message}")
                currentInputConnection?.deleteSurroundingText(thinkingTextLength, 0)
                if (e !is CancellationException) {
                    currentInputConnection?.commitText("\nError: ${e.message}\n", 1)
                }
            } finally {
                stopGenerationButton?.visibility = View.GONE
            }
        }
    }
}