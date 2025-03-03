```
+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
|                                                                                                      AIKeyboard (All-in-One Diagram)                                                                                                      |
+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
|                                                                                                 +------------------------+                                                                                                |
|                                                                                                 |   AndroidManifest.xml  |  (Khai báo ứng dụng, quyền, các thành phần: SettingsActivity, WelcomeActivity, AIKeyboardService)                                                                                                |
|                                                                                                 +------------------------+                                                                                                |
|                                                                                                                 |                                                                                                                 |
|                                                                                                                 V                                                                                                                 |
|  +---------------------+                                    +-----------------------------------------------------------------------+                                   +-----------------------+                                                                        |
|  |   SettingsActivity  |  <--------------------------->  |                       AIKeyboardService (InputMethodService)                      |  <----------------->  |  External Applications  |  (Các ứng dụng khác nhận text input)                                                                       |
|  +---------------------+                                    +-----------------------------------------------------------------------+                                   +-----------------------+                                                                        |
|  (Quản lý cài đặt: API Keys, Bật/Tắt bàn phím)                    |  onCreate(): Khởi tạo Service, Logger, ClipboardManager, SharedPreferences  |                                      ▲                                                                        |
|      ▲                                                                |    ├── initializeAPIs()                                                  |                                      │  InputConnection (Giao tiếp)                                                                        |
|      │  Intent (Start Activity)                                       |    │   ├── DeepSeekAPI(apiKey)                                          |                                      │                                                                        |
|      │                                                                |    │   └── GPTAPI(apiKey, model)                                        |                                      │                                                                        |
|      │                                                                |    ├── initializeViews()                                                 |                                      │                                                                        |
|      └───────────────────────────────────────────>  |    │   ├── inflate(R.layout.keyboard_layout)                              |                                      │                                                                        |
|                                                                |    │   ├── setupKeyboardButtons()                                        |                                      │                                                                        |
|                                                                |    │   │   ├── setOnClickListener (cho từng nút) -> commitText(), processVietnameseInput(), handleBackspace(), ... |                                      │                                                                        |
|                                                                |    │   ├── setupSmartbar()                                                 |                                      │                                                                        |
|                                                                |    │   │   ├── setupAIButtons()                                             |                                      │                                                                        |
|                                                                |    │   │   │   ├── translateButton.setOnClickListener -> handleDeepSeekTranslate() / handleGptTranslate()     |                                      │                                                                        |
|                                                                |    │   │   │   └── askButton.setOnClickListener -> handleDeepSeekAsk() / processGPTAsk()              |                                      │                                                                        |
|                                                                |    │   │   └── ...                                                         |                                      │                                                                        |
|                                                                |    │   └── ...                                                             |                                      │                                                                        |
|                                                                |    └── initializeClipboardListener()                                    |                                      │                                                                        |
|                                                                |  onCreateInputView(): View  (Trả về layout bàn phím)                       |                                      │                                                                        |
|                                                                |  onStartInputView(...)  (Bắt đầu hiển thị, cập nhật giao diện)          |                                      │                                                                        |
|                                                                |  onKeyDown(keyCode, event): Boolean                                     |                                      │                                                                        |
|                                                                |    ├── if (keyCode == KeyCode.KEYCODE_DEL)                               |                                      │                                                                        |
|                                                                |    │   └── handleBackspace()  (Xử lý xóa ký tự)                             |                                      │                                                                        |
|                                                                |    │       └── currentInputConnection.deleteSurroundingText(1, 0)           |                                      │                                                                        |
|                                                                |    ├── else if (isVietnameseMode && isLetter(keyCode))                   |                                      │                                                                        |
|                                                                |    │   └── processVietnameseInput(char)  (Xử lý Telex)                   |                                      │                                                                        |
|                                                                |    │       ├── vietnameseInputBuffer.append(char)                        |                                      │                                                                        |
|                                                                |    │       ├── val (action, text) = telexComposer.getActions(...)          |                                      │                                                                        |
|                                                                |    │       ├── if (action == Composer.ACTION_REPLACE)                        |                                      │                                                                         |
|                                                                |    │       │   ├── currentInputConnection.setComposingText(text, 1)         |                                      │                                                                        |
|                                                                |    │       │   └── vietnameseInputBuffer.setLength(0)                      |                                      │                                                                        |
|                                                                |    │       ├── ...                                                         |                                      │                                                                        |
|                                                                |    │       └── commitText(text)                                            |                                      │                                                                        |
|                                                                |    ├── else                                                               |                                      │                                                                        |
|                                                                |    │   └── commitText(char.toString())                                   |                                      │                                                                        |
|                                                                |    └── return true                                                        |                                      │                                                                        |
|                                                                |  onKeyUp(keyCode, event): Boolean (Tương tự onKeyDown)                   |                                      │                                                                        |
|                                                                |  onUpdateSelection(...) (Cập nhật khi selection thay đổi)                 |                                      │                                                                        |
|                                                                |  commitText(text)                                                         |                                      │                                                                        |
|                                                                |    ├── currentInputConnection.commitText(text, 1)  (Gửi text)             |  <--------------------------------------+                                                                        |
|                                                                |    └── vietnameseInputBuffer.setLength(0)                                 |                                                                                                                  |
|                                                                |  handleDeepSeekTranslate() / handleGptTranslate()                        |                                                                                                                  |
|                                                                |    ├── val text = getSelectedText() ?: getClipboardText()                 |                                                                                                                  |
|                                                                |    ├── serviceScope.launch {                                              |                                                                                                                  |
|                                                                |    │   ├── val translatedText = deepSeekAPI.translate(...) / gptAPI.translate(...) |                                                                                                                  |
|                                                                |    │   └── commitText(translatedText)                                     |                                                                                                                  |
|                                                                |    │   }                                                                  |                                                                                                                  |
|                                                                |  handleDeepSeekAsk() / processGPTAsk()                                    |                                                                                                                  |
|                                                                |    ├── val question = getSelectedText() ?: getClipboardText()               |                                                                                                                  |
|                                                                |    ├── serviceScope.launch {                                              |                                                                                                                  |
|                                                                |    │    ├── val answer = deepSeekAPI.askQuestion(...) / gptAPI.askQuestion(...)   |                                                                                                                  |
|    │    └── commitText(answer)                                           |                                                                                                                  |
|  │    }                                                               |                                                                                                                  |
|  └── onDestroy(): Giải phóng tài nguyên                                       |                                                                                                                  |
|      ├── serviceJob.cancel()                                                  |                                                                                                                  |
|      ├── tts?.shutdown()                                                     |                                                                                                                  |
|      └── clipboardManager.removePrimaryClipChangedListener(clipboardListener)  |                                                                                                                  |
|                                                                +-----------------------------------------------------------------------+                                                                                                                  |
|                                                                |                                   ▲                                   |                                                                                                                  |
|                                                                |                                   │  CoroutineScope (Xử lý bất đồng bộ)   |                                                                                                                  |
|                                                                |                                   │                                   |                                                                                                                  |
|  +---------------------------+        +-------------------------+    |   +---------------------------+      +-------------------------+                                                                                                                  |
|  |       DeepSeekAPI         |        |         GPTAPI          |    |   |       TelexComposer       |      |      TextProcessor      |                                                                                                                  |
|  +---------------------------+        +-------------------------+    |   +---------------------------+      +-------------------------+                                                                                                                  |
|  | makeRequest(...)          |        | makeRequest(...)          |    └──>| getActions(...)           |  <───| processText(char)      |                                                                                                                  |
|  | translate(...)            |        | translate(...)            |        |  (Xử lý Telex)            |      |  (Gọi TelexComposer)   |                                                                                                                  |
|  | askQuestion(...)          |        | askQuestion(...)          |        +---------------------------+      | commitText(), reset() ...|                                                                                                                  |
|  +---------------------------+        +-------------------------+                                           +-------------------------+                                                                                                                  |
+-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+

```

**Giải thích bảng mã:**

*   **Trung tâm:** `AIKeyboardService` là lớp chính, kế thừa từ `InputMethodService` của Android. Nó xử lý tất cả các sự kiện và tương tác chính.
*   **`AndroidManifest.xml`**: Khai báo các thành phần của ứng dụng (như `AIKeyboardService`, `SettingsActivity`, quyền truy cập...).
*   **`SettingsActivity`**: Cho phép người dùng cấu hình ứng dụng (ví dụ: nhập API key).
*   **Luồng nhập liệu:**
    *   Khi người dùng gõ phím, sự kiện `onKeyDown` và `onKeyUp` được gọi.
    *   Nếu là chế độ tiếng Việt và là chữ cái, `processVietnameseInput` được gọi.
    *   `processVietnameseInput` sử dụng `TelexComposer` để xử lý gõ Telex.
    *   Kết quả cuối cùng được gửi đến ứng dụng thông qua `commitText`.
*   **Smartbar:**
    *   Các nút trên Smartbar (dịch, hỏi) được gắn các hàm xử lý sự kiện click.
    *   Các hàm này (ví dụ: `handleDeepSeekTranslate`, `handleGptTranslate`) sẽ:
        *   Lấy văn bản cần xử lý (từ vùng chọn hoặc clipboard).
        *   Gọi các hàm tương ứng trong `DeepSeekAPI` hoặc `GPTAPI` (chạy trong `CoroutineScope` để không chặn luồng UI).
        *   Gửi kết quả đến ứng dụng bằng `commitText`.
*   **`DeepSeekAPI` và `GPTAPI`**:  Cung cấp các hàm để giao tiếp với API của DeepSeek và OpenAI. Hàm `makeRequest` là hàm chung để thực hiện các request HTTP.
*    **`TelexComposer`**: Hàm `getActions` là trung tâm của bộ gõ Telex.
*   **`TextProcessor`**:  Lớp trung gian, gọi `TelexComposer` và xử lý các hành động trả về (thêm, thay thế ký tự).
* **Mũi tên:**
    *   Mũi tên liền (`--->`, `<---`):  Biểu thị lời gọi hàm (function call).
    *   Mũi tên đứt đoạn (`----->`, `<-----`): Biểu thị luồng dữ liệu hoặc sự kiện.
    *   Mũi tên dọc (`▲`, `▼`): Biểu thị mối quan hệ kế thừa hoặc tương tác giữa các thành phần lớn.
* **`External Applications`**: Đại diện cho các ứng dụng bên ngoài mà bàn phím sẽ gửi text input vào. Bàn phím tương tác với các ứng dụng này thông qua `InputConnection`.

**Lưu ý quan trọng:**

*   Đây là sơ đồ *tổng quát* và *chi tiết*, nhưng vẫn *chưa thể hiện hết 100%* code (ví dụ: các xử lý lỗi, các hàm tiện ích nhỏ, các biến thành viên...). Mục đích là để bạn có cái nhìn *trực quan* và *dễ hiểu* về luồng hoạt động chính của ứng dụng.
*   Sơ đồ này tập trung vào các *luồng chính*: nhập liệu, xử lý Telex, gọi API.
*   Các chi tiết về UI (layout, drawable...) được đơn giản hóa để tập trung vào logic.

Sơ đồ này cung cấp một cái nhìn toàn diện về cách các phần khác nhau của ứng dụng bàn phím AI tương tác với nhau. Nó bao gồm luồng nhập liệu, xử lý Telex, gọi API, và tương tác với các ứng dụng bên ngoài.
```
AIKeyboard (Root)
|__ AndroidManifest.xml
|   |__ android:versionCode="1", android:versionName="1.0"
|   |__ Permissions:
|   |   |__ android.permission.INTERNET: For API calls (DeepSeek, GPT)
|   |   |__ android.permission.READ_CLIPBOARD: Access clipboard for AI features
|   |   |__ android.permission.WRITE_CLIPBOARD: Write to clipboard (copy actions, AI responses)
|   |   |__ android.permission.WRITE_EXTERNAL_STORAGE: Logging to file
|   |   |__ android.permission.READ_EXTERNAL_STORAGE: Logging to file
|   |__ Application:
|   |   |__ Activities:
|   |   |   |__ SettingsActivity.kt: (com.example.aikeyboard.SettingsActivity)
|   |   |   |   |__ android:exported="true": Launchable from system settings, other apps
|   |   |   |   |__ Intent Filter: MAIN, LAUNCHER (App entry point in launcher)
|   |   |   |   |__ Functionality:
|   |   |   |   |   |__ API Key Configuration: Input fields for DeepSeek & GPT API keys (EditTexts: deepseekApiKeyEditText, gptApiKeyEditText)
|   |   |   |   |   |__ GPT Model Selection: Dropdown to choose GPT model (Spinner: gptModelSpinner)
|   |   |   |   |   |__ Save API Keys: Button (saveButton) to store API keys & model in SharedPreferences
|   |   |   |   |   |__ Enable Keyboard: Button (enableKeyboardButton) to open system input method settings
|   |   |   |   |   |__ Select Keyboard: Button (selectKeyboardButton) to open system input method picker
|   |   |   |__ WelcomeActivity.kt: (com.example.aikeyboard.WelcomeActivity)
|   |   |   |   |__ android:exported="true": Launchable by other apps
|   |   |   |   |__ android:theme="@style/Theme.AppCompat.Light.DarkActionBar": Sets Activity theme
|   |   |   |   |__ Functionality: Welcome screen, initial setup, tutorial (likely)
|   |   |__ Services:
|   |   |   |__ AIKeyboardService.kt: (com.example.aikeyboard.AIKeyboardService)
|   |   |   |   |__ android:label="AI Keyboard": Display name in input method list
|   |   |   |   |__ android:permission="android.permission.BIND_INPUT_METHOD": System permission for input method
|   |   |   |   |__ android:windowSoftInputMode="adjustResize": Handles window resizing for keyboard
|   |   |   |   |__ android:exported="true": Service accessible by system, other apps
|   |   |   |   |__ Intent Filter: android.view.InputMethod (Identifies as input method service)
|   |   |   |   |__ Meta-data: android.view.im (Points to method.xml for config)
|   |   |   |   |__ Implements: InputMethodService, CoroutineScope, TextToSpeech.OnInitListener, ClipboardManager.OnPrimaryClipChangedListener
|   |   |   |   |__ Member Variables:
|   |   |   |   |   |__ UI Elements: keyboard, keyPopupText, keyPopup, btnPasteAndRead, btnStopTts, btnSmartbarToggle, smartbarScrollView, translateButton, askButton, languageSpinner, gptModelSpinner, clipboardHistorySpinner, calculatorKeyboard, calculatorResult, btnTinhToan, btnQuaCau
|   |   |   |   |   |__ API Clients: de seepSeekAPI, gptAPI
|   |   |   |   |   |__ Text Processing: textProcessor, telexComposer, vietnameseInputBuffer
|   |   |   |   |   |__ TTS: tts, textToSpeech, isTtsInitialized, lastDetectedLanguage
|   |   |   |   |   |__ State Flags: isShiftEnabled, isSymbolMode, isVietnameseMode
|   |   |   |   |   |__ Coroutines & Handlers: serviceJob, serviceScope, requestMutex, deleteHandler, deleteRunnable, handler, popupHideRunnable, backspaceHandler
|   |   |   |   |   |__ Keyboard Keys: normalKeys, symbolKeys, calculatorKeys, shiftableButtons
|   |   |   |   |   |__ Clipboard History: clipboardHistory, clipboardListener
|   |   |   |   |   |__ Preferences: preferences
|   |   |   |   |__ Inner Classes:
|   |   |   |   |   |__ DeepSeekAPI: Handles DeepSeek API requests (translate, askQuestion)
|   |   |   |   |   |__ GPTAPI: Handles GPT API requests (translate, askQuestion)
|   |   |   |   |__ Override Functions:
|   |   |   |   |   |__ onCreate: Initializes service, logger, APIs, TTS, clipboard listener
|   |   |   |   |   |__ onDestroy: Releases resources (TTS, coroutines)
|   |   |   |   |   |__ onInit: TTS initialization callback
|   |   |   |   |   |__ onKeyDown, onKeyUp: Hardware key event handling
|   |   |   |   |   |__ onCreateInputView: Inflates keyboard layout, initializes UI
|   |   |   |   |   |__ onStartInputView: Called when input view starts
|   |   |   |   |   |__ onUpdateSelection: Handles text selection changes
|   |   |   |   |__ Functions:
|   |   |   |   |   |__ API & AI Features: handleDeepSeekTranslate, handleDeepSeekAsk, handleGptTranslate, processGPTAsk, getSelectedText, initializeAPIs, getApiKey, loadApiKeys, detectLanguage, captureGPTResponse, safeApiCall
|   |   |   |   |   |__ Smartbar Setup & Actions: setupSmartbar, toggleSmartbar, setupTTSButtons, setupAIButtons, setupGptModelSpinner, setupCalculatorKeyboard, setupLanguageSpinner, setupLanguageToggleButton, pasteAndReadText, speakText, stopTts, setupClipboardHistorySpinner, setupQuaCauButton, toggleLanguage
|   |   |   |   |   |__ Keyboard UI Setup: initializeViews, setupKeyboardButtons, setupCharacterButton, setupSpaceButton, setupShiftButton, setupSymbolButton, setupBackspaceButton, setupEnterButton, showKeyPopup, hideKeyPopup, updateShiftState
|   |   |   |   |   |__ Vietnamese Input: processVietnameseInput
|   |   |   |   |   |__ Calculator: handleCalculatorButtonClick, evaluateExpression, formatResult
|   |   |   |   |   |__ Clipboard History: initializeClipboardListener, addTextToClipboardHistory, getClipboardText, runOnMainThread
|   |   |   |   |   |__ Backspace Repeat: startBackspaceRepeat, stopBackspaceRepeat
|   |   |   |   |   |__ Utility: showToast, showError
|__ build.gradle.kts (Module-level - app/build.gradle.kts)
|   |__ plugins: (Kotlin Android, Android Application)
|   |__ android:
|   |   |__ compileSdk = 31, minSdkVersion = 21, targetSdk = 31, versionCode = 1, versionName = "1.0"
|   |   |__ defaultConfig: applicationId, testInstrumentationRunner
|   |   |__ buildTypes: release (minifyEnabled, proguardFiles)
|   |   |__ compileOptions: sourceCompatibility, targetCompatibility (Java 8)
|   |   |__ kotlinOptions: jvmTarget = "1.8"
|   |__ dependencies:
|   |   |__ implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.0")
|   |   |__ implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
|   |   |__ implementation("androidx.core:core-ktx:1.7.0")
|   |   |__ implementation("androidx.appcompat:appcompat:1.4.1")
|   |   |__ implementation("com.google.android.material:material:1.5.0")
|   |   |__ implementation("androidx.constraintlayout:constraintlayout:2.1.3")
|   |   |__ implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.4.1")
|   |   |__ implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.1")
|   |   |__ implementation("org.json:json:20210307")
|   |   |__ implementation("com.squareup.okhttp3:okhttp:4.9.3")
|__ res (Resources - app/res)
|   |__ anim (Animations - app/res/anim):
|   |   |__ popup_enter.xml: Fade-in animation for popup
|   |   |__ popup_exit.xml: Fade-out animation for popup
|   |   |__ slide_in_right.xml: Slide-in from right for Smartbar
|   |   |__ slide_out_right.xml: Slide-out to right for Smartbar
|   |__ drawable (Drawables - app/res/drawable):
|   |   |__ key_background.xml: Button background selector (pressed/normal)
|   |   |__ keyboard_button_background.xml: Ripple effect for keyboard buttons
|   |   |__ keyboard_button_active_background.xml: Active button background (green)
|   |   |__ popup_background.xml: General popup background
|   |   |__ rounded_button.xml: Rounded background for AI buttons
|   |   |__ spinner_background.xml: Background for Spinners
|   |   |__ ic_launcher_foreground.xml: Launcher icon foreground layer
|   |__ layout (Layouts - app/res/layout):
|   |   |__ activity_settings.xml: Layout for SettingsActivity (API Keys, Keyboard Settings)
|   |   |   |__ LinearLayout (Vertical): Root layout
|   |   |   |   |__ TextView: "API Settings" (section header)
|   |   |   |   |__ EditText: deepseekApiKeyEditText (DeepSeek API Key input)
|   |   |   |   |__ EditText: gptApiKeyEditText (GPT API Key input)
|   |   |   |   |__ Spinner: gptModelSpinner (GPT Model selection)
|   |   |   |   |__ Button: saveButton (Save API Keys)
|   |   |   |   |__ TextView: "Keyboard Settings" (section header)
|   |   |   |   |__ Button: enableKeyboardButton (Enable Keyboard)
|   |   |   |   |__ Button: selectKeyboardButton (Select Keyboard)
|   |   |__ keyboard_layout.xml: Main keyboard layout (QWERTY, Numbers, Symbols, Smartbar)
|   |   |   |__ LinearLayout (Vertical): Root layout
|   |   |   |   |__ include layout="@layout/smartbar_layout": Smartbar (top toolbar)
|   |   |   |   |__ LinearLayout (Horizontal): Number Row (1-0)
|   |   |   |   |__ LinearLayout (Horizontal): Letter Row 1 (QWERTYUIOP)
|   |   |   |   |__ LinearLayout (Horizontal): Letter Row 2 (ASDFGHJKL)
|   |   |   |   |__ LinearLayout (Horizontal): Letter Row 3 (Shift, ZXCVBNM, Backspace)
|   |   |   |   |__ LinearLayout (Horizontal): Bottom Row (Symbols, Comma, Space, Period, Enter)
|   |   |__ smartbar_layout.xml: Smartbar layout (toolbar above keyboard)
|   |   |   |__ LinearLayout (Horizontal): Root layout
|   |   |   |   |__ Button: btnSmartbarToggle (Toggle Smartbar visibility)
|   |   |   |   |__ HorizontalScrollView: Container for Smartbar buttons (scrollable)
|   |   |   |   |   |__ LinearLayout (Horizontal): Smartbar buttons container
|   |   |   |   |   |   |__ Button: btnQuaCau (Globe - Switch Keyboard)
|   |   |   |   |   |   |__ Button: btnTinhToan (Calculator Keyboard)
|   |   |   |   |   |   |__ Button: btnPasteAndRead (Paste & Read Clipboard)
|   |   |   |   |   |   |__ Button: btnStopTts (Stop Text-to-Speech)
|   |   |   |   |   |   |__ Button: languageButton (VN/EN Language Switch)
|   |   |   |   |   |   |__ Spinner: languageSpinner (Translation Language Select)
|   |   |   |   |   |   |__ Spinner: gptModelSpinner (GPT Model Select)
|   |   |   |   |   |   |__ Button: gptAskButton (GPT Ask)
|   |   |   |   |   |   |__ Button: gptTranslateButton (GPT Translate)
|   |   |   |   |   |   |__ Button: askButton (DeepSeek Ask)
|   |   |   |   |   |   |__ Button: translateButton (DeepSeek Translate)
|   |   |   |   |   |   |__ Spinner: clipboardHistorySpinner (Clipboard History)
|   |   |__ calculator_keyboard.xml: Calculator keyboard popup layout
|   |   |   |__ LinearLayout (Vertical): Root layout
|   |   |   |   |__ LinearLayout (Horizontal): Top row buttons (QuayLai, InVanBan)
|   |   |   |   |__ TextView: calculatorResult (Calculator result display)
|   |   |   |   |__ Button: btnClear (Clear button)
|   |   |   |   |__ LinearLayout (Vertical): calculatorKeyboardContainer (Calculator keys container)
|   |   |__ keyboard_key_preview.xml: Layout for key press popup preview (TextView)
|   |   |__ dialog_clipboard_history.xml: Layout for clipboard history dialog (unused - TextView, ListView)
|   |__ mipmap-anydpi-v26 (Adaptive Icons - app/res/mipmap-anydpi-v26):
|   |   |__ ic_launcher.xml: Adaptive launcher icon XML
|   |   |__ ic_launcher_round.xml: Rounded adaptive launcher icon XML
|   |__ values (Values - app/res/values):
|   |   |__ colors.xml: Color definitions (purple, teal, black, white, etc.)
|   |   |__ dimens.xml: Dimension values (key sizes, margins, spacings)
|   |   |__ styles.xml: UI styles (KeyboardButtonStyle, AIButton, SmartBarButton, KeyboardRow, AppTheme)
|   |   |__ themes.xml: Application themes (Theme.AIKeyboard)
|   |__ xml (XML Configurations - app/res/xml):
|   |   |__ method.xml: Input Method Service configuration (settingsActivity, subtype)
|__ src (Source Code - app/src/main/java/com/example/aikeyboard)
|   |__ com.example.aikeyboard (Package - app/src/main/java/com/example/aikeyboard):
|   |   |__ AIKeyboardService.kt: AI Keyboard Service Class
|   |   |   |__ Member Variables: ... (See detailed list in previous response)
|   |   |   |__ Inner Classes:
|   |   |   |   |__ DeepSeekAPI: ... (Functions: translate, askQuestion, etc.)
|   |   |   |   |__ GPTAPI: ... (Functions: translate, askQuestion, etc.)
|   |   |   |__ Override Functions: ... (onCreate, onDestroy, onInit, onKeyDown, onKeyUp, onCreateInputView, onStartInputView, onUpdateSelection)
|   |   |   |__ Functions: ... (API & AI, Smartbar, Keyboard UI, Vietnamese Input, Calculator, Clipboard History, Utility)
|   |   |__ SettingsActivity.kt: Settings Activity Class
|   |   |   |__ UI Elements: deepseekApiKeyEditText, gptApiKeyEditText, gptModelSpinner, saveButton, enableKeyboardButton, selectKeyboardButton
|   |   |   |__ Functionality: onCreate, saveButton.setOnClickListener, enableKeyboardButton.setOnClickListener, selectKeyboardButton.setOnClickListener, checkStoragePermission, onRequestPermissionsResult
|   |   |__ DeepSeekAPI.kt: DeepSeek API Client Class
|   |   |   |__ Member Variables: baseUrl, apiKey, isRequestPending, requestMutex
|   |   |   |__ Functions: translate, askQuestion, createConnection, makeRequest, processResponse
|   |   |__ GPTAPI.kt: GPT API Client Class
|   |   |   |__ Member Variables: baseUrl, apiKey, model, modelContextWindows, isRequestPending, requestMutex, messageHistory
|   |   |   |__ Functions: translate, askQuestion, createConnection, makeRequest, parseResponse, calculateMaxTokens
|   |   |__ CustomButton.kt: Custom Button Data Class
|   |   |   |__ Data Class: CustomButton(val name: String, val prompt: String)
|   |   |   |__ Functions: toJson(), companion object { fromJson() }
|   |   |__ Logger.kt: Logger Utility Object
|   |   |   |__ Object Logger: Singleton
|   |   |   |__ Member Variables: TAG, logFile
|   |   |   |__ Functions: initialize, log, getLogFilePath
|   |   |__ text (Text Processing Package):
|   |   |   |__ TextProcessor.kt: Text Processor Class
|   |   |   |   |__ Member Variables: composingText, isComposing, composer
|   |   |   |   |__ Functions: processText, reset, clear, commitText, deleteLastCharacter
|   |   |   |__ TelexComposer.kt: Telex Vietnamese Composer Class
|   |   |   |   |__ Class TelexComposer : Composer
|   |   |   |   |__ Override Properties: id, label, toRead
|   |   |   |   |__ Member Variables: vowels, consonants, diacriticRules
|   |   |   |   |__ Override Functions: getActions
|   |   |   |__ composing (Composer Subpackage):
|   |   |   |   |__ Composer.kt: Composer Interface
|   |   |   |   |   |__ Interface Composer
|   |   |   |   |   |__ Properties: id, label, toRead
|   |   |   |   |   |__ Functions: getActions
|   |   |   |   |__ Appender.kt: Appender Composer Class
|   |   |   |   |   |__ Class Appender : Composer
|   |   |   |   |   |__ Override Properties: id, label, toRead
|   |   |   |   |   |__ Override Functions: getActions
|   |   |   |   |__ TelesComposer.kt: Teles Composer Class (Dictionary-based Telex)
|   |   |   |   |   |__ Class TelesComposer : Composer
|   |   |   |   |   |__ Override Properties: id, label, toRead
|   |   |   |   |   |__ Member Variables: rules
|   |   |   |   |   |__ Override Functions: getActions
|   |   |__ WelcomeActivity.kt: Welcome Activity Class
|   |   |   |__ Functionality: onCreate (Welcome screen setup)
|__ gradle (Gradle Wrapper - gradle): Gradle wrapper files
|   |__ wrapper: gradle-wrapper.jar, gradle-wrapper.properties
|__ gradlew: Gradle Wrapper script (shell script)
|__ gradlew.bat: Gradle Wrapper batch script (Windows)
|__ gradle.properties: Gradle project properties
|__ local.properties: Local project properties
|__ .gradle: Gradle cache directory
|__ .vscode: VS Code config directory
|   |__ settings.json: VS Code settings


```
--- START OF FILE bàn phím_en.md ---

Excellent! Here is a detailed and easy-to-understand diagram of the Android AI keyboard source code structure you provided. This diagram is designed to be easily visualized for both beginners and children.

```
|-- banphim.txt (Root file containing the entire code, used for structure reference)
|-- app (Main application directory)
|   |-- src (Main source code of the application)
|   |   |-- main (Contains the main components of the application)
|   |   |   |-- AndroidManifest.xml (Application manifest file)
|   |   |   |   |-- <?xml version="1.0" encoding="utf-8"?> (XML declaration)
|   |   |   |   |-- <manifest ...> (Root tag, defines package and permissions)
|   |   |   |   |   |-- xmlns:android="http://schemas.android.com/apk/res/android" (Android namespace)
|   |   |   |   |   |-- <uses-permission android:name="android.permission.INTERNET" /> (Internet access permission)
|   |   |   |   |   |-- <uses-permission android:name="android.permission.READ_CLIPBOARD" /> (Read clipboard permission)
|   |   |   |   |   |-- <uses-permission android:name="android.permission.WRITE_CLIPBOARD" /> (Write clipboard permission)
|   |   |   |   |   |-- <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" /> (Write external storage permission)
|   |   |   |   |   |-- <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" /> (Read external storage permission)
|   |   |   |   |   |-- <application ...> (Application component declaration)
|   |   |   |   |   |   |-- android:allowBackup="true" (Allows application backup)
|   |   |   |   |   |   |-- android:icon="@mipmap/ic_launcher" (Application icon)
|   |   |   |   |   |   |-- android:label="AI Keyboard" (Application name)
|   |   |   |   |   |   |-- android:supportsRtl="true" (Supports right-to-left languages)
|   |   |   |   |   |   |-- android:theme="@style/Theme.AIKeyboard" (Application theme)
|   |   |   |   |   |   |-- <activity android:name=".SettingsActivity" android:exported="true"> (Declaration of Settings Activity)
|   |   |   |   |   |   |   |-- <intent-filter> (Intent filter for main Activity)
|   |   |   |   |   |   |   |   |-- <action android:name="android.intent.action.MAIN" /> (MAIN Action - First activity to launch)
|   |   |   |   |   |   |   |   |-- <category android:name="android.intent.category.LAUNCHER" /> (LAUNCHER Category - Show on application launcher)
|   |   |   |   |   |   |   |-- </intent-filter>
|   |   |   |   |   |   |-- </activity>
|   |   |   |   |   |   |-- <activity android:name=".WelcomeActivity" android:exported="true" android:theme="@style/Theme.AppCompat.Light.DarkActionBar" /> (Declaration of Welcome Activity)
|   |   |   |   |   |   |-- <service android:name=".AIKeyboardService" android:label="AI Keyboard" android:permission="android.permission.BIND_INPUT_METHOD" ... android:exported="true"> (Declaration of Keyboard Service)
|   |   |   |   |   |   |   |-- android:name=".AIKeyboardService" (Service class name)
|   |   |   |   |   |   |   |-- android:label="AI Keyboard" (Display name of the keyboard)
|   |   |   |   |   |   |   |-- android:permission="android.permission.BIND_INPUT_METHOD" (System permission required to be a keyboard)
|   |   |   |   |   |   |   |-- android:windowSoftInputMode="adjustResize" (How the keyboard interacts with the application window)
|   |   |   |   |   |   |   |-- android:exported="true" (Allows other applications to access the Service)
|   |   |   |   |   |   |   |-- <intent-filter> (Intent filter for Keyboard Service)
|   |   |   |   |   |   |   |   |-- <action android:name="android.view.InputMethod" /> (InputMethod Action - Identifies as Input Method Service)
|   |   |   |   |   |   |   |-- </intent-filter>
|   |   |   |   |   |   |   |-- <meta-data android:name="android.view.im" android:resource="@xml/method" /> (Metadata linking Service to keyboard configuration file)
|   |   |   |   |   |   |-- </service>
|   |   |   |   |   |-- </application>
|   |   |   |   |-- </manifest>
|   |   |   |-- java (Java/Kotlin source code)
|   |   |   |   |-- com.example.aikeyboard (Application package name)
|   |   |   |   |   |-- AIKeyboardService.kt (Main AI Keyboard Service)
|   |   |   |   |   |   |-- package com.example.aikeyboard (Package declaration)
|   |   |   |   |   |   |-- import ... (Import necessary libraries)
|   |   |   |   |   |   |-- class AIKeyboardService : InputMethodService(), TextToSpeech.OnInitListener { ... } (AIKeyboardService class inherits InputMethodService and TextToSpeech.OnInitListener interface)
|   |   |   |   |   |   |   |-- // Member variables (variables):
|   |   |   |   |   |   |   |   |-- private var keyboard: View? = null (Keyboard layout View)
|   |   |   |   |   |   |   |   |-- private var keyPopupText: TextView? = null (Popup TextView when key is pressed)
|   |   |   |   |   |   |   |   |-- private var keyPopup: PopupWindow? = null (Popup to display keyPopupText)
|   |   |   |   |   |   |   |   |-- private var btnPasteAndRead: Button? = null ("Paste & Read" Button)
|   |   |   |   |   |   |   |   |-- private var btnStopTts: Button? = null ("Stop TTS" Button)
|   |   |   |   |   |   |   |   |-- private var btnSmartbarToggle: Button? = null (Smartbar toggle Button)
|   |   |   |   |   |   |   |   |-- private var smartbarScrollView: HorizontalScrollView? = null (ScrollView containing Smartbar)
|   |   |   |   |   |   |   |   |-- private var translateButton: Button? = null ("Translate" Button)
|   |   |   |   |   |   |   |   |-- private var askButton: Button? = null ("Ask" Button)
|   |   |   |   |   |   |   |   |-- private var languageSpinner: Spinner? = null (Language selection Spinner)
|   |   |   |   |   |   |   |   |-- private var preferences: SharedPreferences? = null (SharedPreferences for storing settings)
|   |   |   |   |   |   |   |   |-- private var telexComposer = TelexComposer() (Telex input processor object)
|   |   |   |   |   |   |   |   |-- private var vietnameseInputBuffer = StringBuilder() (Buffer for Vietnamese input)
|   |   |   |   |   |   |   |   |-- private val serviceJob = Job() (Coroutine Job for Service)
|   |   |   |   |   |   |   |   |-- private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob) (Coroutine Scope for Service)
|   |   |   |   |   |   |   |   |-- private var currentLanguage = "Vietnamese" (Current language)
|   |   |   |   |   |   |   |   |-- private val supportedLanguages = ... (List of supported languages)
|   |   |   |   |   |   |   |   |-- private var tts: TextToSpeech? = null (TextToSpeech engine)
|   |   |   |   |   |   |   |   |-- private var isTtsInitialized = false (TTS initialized status)
|   |   |   |   |   |   |   |   |-- private var textToSpeech: TextToSpeech? = null (TextToSpeech engine - duplicate?)
|   |   |   |   |   |   |   |   |-- private var lastDetectedLanguage = "vi" (Last detected language)
|   |   |   |   |   |   |   |   |-- private var isShiftEnabled = false (Shift state)
|   |   |   |   |   |   |   |   |-- private var isSymbolMode = false (Symbol mode state)
|   |   |   |   |   |   |   |   |-- private var isVietnameseMode = true (Vietnamese mode state)
|   |   |   |   |   |   |   |   |-- private val requestMutex = Mutex() (Mutex for API request synchronization)
|   |   |   |   |   |   |   |   |-- private var deepSeekAPI: DeepSeekAPI? = null (DeepSeek API object)
|   |   |   |   |   |   |   |   |-- private var gptAPI: GPTAPI? = null (GPT API object)
|   |   |   |   |   |   |   |   |-- private val clipboardManager by lazy { ... } (ClipboardManager)
|   |   |   |   |   |   |   |   |-- private val deleteHandler = Handler(Looper.getMainLooper()) (Handler for repeated deletion)
|   |   |   |   |   |   |   |   |-- private val deleteRunnable = object : Runnable { ... } (Runnable to perform repeated deletion)
|   |   |   |   |   |   |   |   |-- private var thinkingTextLength = 0 ("Thinking..." text length)
|   |   |   |   |   |   |   |   |-- private val handler = Handler(Looper.getMainLooper()) (General handler)
|   |   |   |   |   |   |   |   |-- private var popupHideRunnable: Runnable? = null (Runnable to hide popup)
|   |   |   |   |   |   |   |   |-- private val backspaceHandler = Handler(Looper.getMainLooper()) (Handler for repeated backspace)
|   |   |   |   |   |   |   |   |-- private val BACKSPACE_INITIAL_DELAY = 400L (Initial backspace delay)
|   |   |   |   |   |   |   |   |-- private val BACKSPACE_INITIAL_INTERVAL = 100L (Initial backspace interval)
|   |   |   |   |   |   |   |   |-- private val BACKSPACE_MIN_INTERVAL = 20L (Minimum backspace interval)
|   |   |   |   |   |   |   |   |-- private val BACKSPACE_ACCELERATION = 10L (Interval reduction after each deletion)
|   |   |   |   |   |   |   |   |-- private var currentBackspaceInterval = BACKSPACE_INITIAL_INTERVAL (Current backspace interval)
|   |   |   |   |   |   |   |   |-- private var shiftableButtons: List<Button> = emptyList() (List of shiftable letter buttons)
|   |   |   |   |   |   |   |   |-- private var btnTinhToan: Button? = null ("Tính Toán" / Calculator Button)
|   |   |   |   |   |   |   |   |-- private var calculatorKeyboard: View? = null (Calculator keyboard layout View)
|   |   |   |   |   |   |   |   |-- private var calculatorResult: TextView? = null (TextView to display calculator result)
|   |   |   |   |   |   |   |   |-- private var calculatorExpression = StringBuilder() (StringBuilder for calculator expression)
|   |   |   |   |   |   |   |   |-- private var lastCalculationResult: Double? = null (Last calculator result)
|   |   |   |   |   |   |   |   |-- private var calculatorPopup: PopupWindow? = null (Popup for calculator keyboard)
|   |   |   |   |   |   |   |   |-- private val calculatorKeys = arrayOf(...) (Array of calculator keys)
|   |   |   |   |   |   |   |   |-- private val normalKeys = arrayOf(...) (Array of normal keys)
|   |   |   |   |   |   |   |   |-- private val symbolKeys = arrayOf(...) (Array of symbol keys)
|   |   |   |   |   |   |   |   |-- private var _gptModelSpinner: Spinner? = null (GPT model selection Spinner - backing property)
|   |   |   |   |   |   |   |   |-- private val gptModelSpinner: Spinner (GPT model selection Spinner)
|   |   |   |   |   |   |   |   |-- private val clipboardHistory = mutableListOf<String>() (Clipboard history)
|   |   |   |   |   |   |   |   |-- private var _clipboardHistorySpinner: Spinner? = null (Clipboard history Spinner - backing property)
|   |   |   |   |   |   |   |   |-- private val clipboardHistorySpinner: Spinner (Clipboard history Spinner)
|   |   |   |   |   |   |   |   |-- private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener { ... } (Clipboard change listener)
|   |   |   |   |   |   |   |   |-- private val textProcessor = TextProcessor(TelesComposer()) (TextProcessor instance with TelesComposer)
|   |   |   |   |   |   |   |-- // Inner classes (classes inside AIKeyboardService class):
|   |   |   |   |   |   |   |   |-- class DeepSeekAPI(...) { ... } (DeepSeek API processing class)
|   |   |   |   |   |   |   |   |   |-- // Function makeRequest (generic API call)
|   |   |   |   |   |   |   |   |   |-- // Function translate (translate text using DeepSeek API)
|   |   |   |   |   |   |   |   |   |-- // Function askQuestion (ask and answer using DeepSeek API)
|   |   |   |   |   |   |   |   |   |-- // Function parseResponse (parse response from API)
|   |   |   |   |   |   |   |   |-- class GPTAPI(...) { ... } (GPT API processing class)
|   |   |   |   |   |   |   |   |   |-- // Function createConnection (create HTTP connection)
|   |   |   |   |   |   |   |   |   |-- // Function makeRequest (generic API call)
|   |   |   |   |   |   |   |   |   |-- // Function translate (translate text using GPT API)
|   |   |   |   |   |   |   |   |   |-- // Function askQuestion (ask and answer using GPT API)
|   |   |   |   |   |   |   |   |   |-- // Function parseResponse (parse response from API)
|   |   |   |   |   |   |   |-- // Override functions (overriding parent class functions):
|   |   |   |   |   |   |   |   |-- override fun onCreate() { ... } (Service initialization, called when Service is created)
|   |   |   |   |   |   |   |   |-- override fun onDestroy() { ... } (Resource release, called when Service is destroyed)
|   |   |   |   |   |   |   |   |-- override fun onInit(status: Int) { ... } (Handle TTS initialization result)
|   |   |   |   |   |   |   |   |-- override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean { ... } (Handle hardware key press event)
|   |   |   |   |   |   |   |   |-- override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean { ... } (Handle hardware key release event)
|   |   |   |   |   |   |   |   |-- override fun onCreateInputView(): View { ... } (Create virtual keyboard View)
|   |   |   |   |   |   |   |   |-- override fun onStartInputView(info: EditorInfo?, restarting: Boolean) { ... } (Start displaying InputView)
|   |   |   |   |   |   |   |   |-- override fun onUpdateSelection(...) { ... } (Handle selection changes)
|   |   |   |   |   |   |   |-- // Functions (functions):
|   |   |   |   |   |   |   |   |-- private fun initializeAPIs() { ... } (Initialize DeepSeek and GPT APIs)
|   |   |   |   |   |   |   |   |-- private fun getApiKey(): String? { ... } (Get API key from SharedPreferences)
|   |   |   |   |   |   |   |   |-- private fun loadApiKeys() { ... } (Load API keys from SharedPreferences)
|   |   |   |   |   |   |   |   |-- private fun initializeViews() { ... } (Initialize Views)
|   |   |   |   |   |   |   |   |-- private fun setupLanguageSpinner() { ... } (Setup language selection Spinner)
|   |   |   |   |   |   |   |   |-- private fun setupAIButtons() { ... } (Setup AI Buttons)
|   |   |   |   |   |   |   |   |-- private fun setupSmartbarButtons() { ... } (Setup Smartbar Buttons)
|   |   |   |   |   |   |   |   |-- private fun setupGptModelSpinner() { ... } (Setup GPT model selection Spinner)
|   |   |   |   |   |   |   |   |-- private fun setupCalculatorKeyboard() { ... } (Setup calculator keyboard)
|   |   |   |   |   |   |   |   |-- private fun handleCalculatorButtonClick(key: String) { ... } (Handle calculator button click)
|   |   |   |   |   |   |   |   |-- private fun evaluateExpression(expression: String): Double { ... } (Evaluate mathematical expression)
|   |   |   |   |   |   |   |   |-- private fun formatResult(result: Double): String { ... } (Format calculator result)
|   |   |   |   |   |   |   |   |-- private fun setupKeyboardButtons() { ... } (Setup main keyboard Buttons)
|   |   |   |   |   |   |   |   |-- private fun setupCharacterButton(button: Button, key: String) { ... } (Setup character Button)
|   |   |   |   |   |   |   |   |-- private fun setupSpaceButton(button: Button) { ... } (Setup Space Button)
|   |   |   |   |   |   |   |   |-- private fun processVietnameseInput(char: Char) { ... } (Process Vietnamese Telex input)
|   |   |   |   |   |   |   |   |-- private fun showKeyPopup(button: Button) { ... } (Show key popup)
|   |   |   |   |   |   |   |   |-- private fun hideKeyPopup() { ... } (Hide key popup)
|   |   |   |   |   |   |   |   |-- private fun handleBackspace() { ... } (Handle Backspace key)
|   |   |   |   |   |   |   |   |-- private fun commitText(text: String) { ... } (Commit text to InputConnection)
|   |   |   |   |   |   |   |   |-- private fun setupShiftButton(button: Button) { ... } (Setup Shift Button)
|   |   |   |   |   |   |   |   |-- private fun updateShiftState() { ... } (Update Shift state)
|   |   |   |   |   |   |   |   |-- private fun setupSymbolButton(button: Button) { ... } (Setup Symbols Button)
|   |   |   |   |   |   |   |   |-- private fun setupBackspaceButton(button: Button) { ... } (Setup Backspace Button)
|   |   |   |   |   |   |   |   |-- private fun setupEnterButton(button: Button) { ... } (Setup Enter Button)
|   |   |   |   |   |   |   |   |-- private fun setupSmartbar() { ... } (Setup Smartbar)
|   |   |   |   |   |   |   |   |-- private fun setupLanguageToggleButton() { ... } (Setup VN/EN language toggle Button)
|   |   |   |   |   |   |   |   |-- private fun toggleLanguage() { ... } (Toggle VN/EN language)
|   |   |   |   |   |   |   |   |-- private fun setupTTSButtons() { ... } (Setup TTS Buttons)
|   |   |   |   |   |   |   |   |-- private fun pasteAndReadText() { ... } (Handle Paste & Read Text)
|   |   |   |   |   |   |   |   |-- private fun speakText(text: String) { ... } (Speak text using TTS)
|   |   |   |   |   |   |   |   |-- private fun stopTts() { ... } (Stop TTS)
|   |   |   |   |   |   |   |   |-- private fun handleDeepSeekTranslate() { ... } (Handle translation using DeepSeek API)
|   |   |   |   |   |   |   |   |-- private fun handleDeepSeekAsk() { ... } (Handle question answering using DeepSeek API)
|   |   |   |   |   |   |   |   |-- private fun handleGptTranslate() { ... } (Handle translation using GPT API)
|   |   |   |   |   |   |   |   |-- private fun processGPTAsk(clipboardText: String) { ... } (Handle question answering using GPT API)
|   |   |   |   |   |   |   |   |-- private fun getSelectedText(): String { ... } (Get selected text)
|   |   |   |   |   |   |   |   |-- private fun showToast(message: String) { ... } (Show Toast message)
|   |   |   |   |   |   |   |   |-- private fun showError(message: String) { ... } (Show error and commit error text)
|   |   |   |   |   |   |   |   |-- private fun setupQuaCauButton() { ... } (Setup "Globe" button to switch keyboard)
|   |   |   |   |   |   |   |   |-- private fun initializeClipboardListener() { ... } (Initialize clipboard listener)
|   |   |   |   |   |   |   |   |-- private fun addTextToClipboardHistory(text: String, showToast: Boolean) { ... } (Add text to clipboard history)
|   |   |   |   |   |   |   |   |-- private fun runOnMainThread(action: () -> Unit) { ... } (Run action on Main Thread)
|   |   |   |   |   |   |   |   |-- private fun setupClipboardHistorySpinner() { ... } (Setup clipboard history Spinner)
|   |   |   |   |   |   |   |   |-- private fun getClipboardText(): String? { ... } (Get text from clipboard)
|   |   |   |   |   |   |   |   |-- private fun startBackspaceRepeat() { ... } (Start repeated deletion when holding Backspace)
|   |   |   |   |   |   |   |   |-- private fun stopBackspaceRepeat() { ... } (Stop repeated deletion when releasing Backspace)
|   |   |   |   |   |   |   |   |-- private fun detectLanguage(text: String): String { ... } (Detect language of text)
|   |   |   |   |   |   |   |   |-- private fun captureGPTResponse(response: String) { ... } (Save GPT response to clipboard history)
|   |   |   |   |   |-- CustomButton.kt (Data class for custom button)
|   |   |   |   |   |   |-- package com.example.aikeyboard (Package declaration)
|   |   |   |   |   |   |-- data class CustomButton(...) { ... } (CustomButton data class)
|   |   |   |   |   |   |   |-- val name: String (Button name)
|   |   |   |   |   |   |   |-- val prompt: String (Button prompt)
|   |   |   |   |   |   |   |-- fun toJson(): String { ... } (Function to convert CustomButton object to JSON String)
|   |   |   |   |   |   |   |-- companion object { ... } (Companion object)
|   |   |   |   |   |   |   |   |-- fun fromJson(json: String): CustomButton { ... } (Function to create CustomButton from JSON String)
|   |   |   |   |   |-- DeepSeekAPI.kt (DeepSeek API processing class)
|   |   |   |   |   |   |-- package com.example.aikeyboard (Package declaration)
|   |   |   |   |   |   |-- import ... (Import necessary libraries)
|   |   |   |   |   |   |-- class DeepSeekAPI(private val apiKey: String) { ... } (DeepSeekAPI class)
|   |   |   |   |   |   |   |-- private val baseUrl = "https://api.deepseek.com" (Base URL of the API)
|   |   |   |   |   |   |   |-- init { ... } (Init block, log when API is initialized)
|   |   |   |   |   |   |   |-- private fun createConnection(): HttpURLConnection { ... } (Function to create HTTP connection)
|   |   |   |   |   |   |   |-- private suspend fun makeRequest(...) : String { ... } (Function to make generic API call)
|   |   |   |   |   |   |   |-- private var isRequestPending = false (Flag indicating pending request)
|   |   |   |   |   |   |   |-- private val requestMutex = Mutex() (Mutex for request synchronization)
|   |   |   |   |   |   |   |-- suspend fun translate(...) { ... } (Function to translate text)
|   |   |   |   |   |   |   |-- suspend fun askQuestion(...) { ... } (Function to ask question)
|   |   |   |   |   |   |   |-- private fun processResponse(...) { ... } (Function to process API response)
|   |   |   |   |   |-- GPTAPI.kt (GPT API processing class)
|   |   |   |   |   |   |-- package com.example.aikeyboard (Package declaration)
|   |   |   |   |   |   |-- import ... (Import necessary libraries)
|   |   |   |   |   |   |-- class GPTAPI(private val apiKey: String, private val model: String = "gpt-3.5-turbo") { ... } (GPTAPI class)
|   |   |   |   |   |   |   |-- private val baseUrl = "https://api.openai.com/v1" (Base URL of the API)
|   |   |   |   |   |   |   |-- private val modelContextWindows = mapOf(...) (Map of model and context window size)
|   |   |   |   |   |   |   |-- private fun calculateMaxTokens(model: String): Int { ... } (Calculate max tokens based on model)
|   |   |   |   |   |   |   |-- init { ... } (Init block, log when API is initialized)
|   |   |   |   |   |   |   |-- private fun createConnection(): HttpURLConnection { ... } (Function to create HTTP connection)
|   |   |   |   |   |   |   |-- data class GPTResponse(...) (GPTResponse data class)
|   |   |   |   |   |   |   |-- private var isRequestPending = false (Flag indicating pending request)
|   |   |   |   |   |   |   |-- private val requestMutex = Mutex() (Mutex for request synchronization)
|   |   |   |   |   |   |   |-- private val messageHistory = mutableListOf<JSONObject>() (Conversation history)
|   |   |   |   |   |   |   |-- private fun parseResponse(response: String): GPTResponse { ... } (Parse API response)
|   |   |   |   |   |   |   |-- private suspend fun makeRequest(...) : String { ... } (Function to make generic API call)
|   |   |   |   |   |   |   |-- suspend fun askQuestion(...) : String { ... } (Function to ask question, handles conversation continuation)
|   |   |   |   |   |   |   |-- suspend fun translate(...) : String { ... } (Function to translate text, can be extended for conversation continuation)
|   |   |   |   |   |-- Logger.kt (Logger Object for file logging)
|   |   |   |   |   |   |-- package com.example.aikeyboard (Package declaration)
|   |   |   |   |   |   |-- import ... (Import necessary libraries)
|   |   |   |   |   |   |-- object Logger { ... } (Logger Object)
|   |   |   |   |   |   |   |-- private const val TAG = "AIKeyboard" (Logcat tag)
|   |   |   |   |   |   |   |-- private var logFile: File? = null (Log file)
|   |   |   |   |   |   |   |-- fun initialize(context: Context) { ... } (Initialize Logger, create log file)
|   |   |   |   |   |   |   |-- fun log(message: String, throwable: Throwable? = null) { ... } (Function to write log)
|   |   |   |   |   |   |   |-- fun getLogFilePath(): String? { ... } (Get log file path)
|   |   |   |   |   |-- SettingsActivity.kt (Settings Activity)
|   |   |   |   |   |   |-- package com.example.aikeyboard (Package declaration)
|   |   |   |   |   |   |-- import ... (Import necessary libraries)
|   |   |   |   |   |   |-- class SettingsActivity : AppCompatActivity() { ... } (SettingsActivity class inherits AppCompatActivity)
|   |   |   |   |   |   |   |-- private lateinit var deepseekApiKeyEditText: EditText (EditText to input DeepSeek API Key)
|   |   |   |   |   |   |   |-- private lateinit var gptApiKeyEditText: EditText (EditText to input GPT API Key)
|   |   |   |   |   |   |   |-- private lateinit var saveButton: Button ("Save API Keys" Button)
|   |   |   |   |   |   |   |-- private lateinit var enableKeyboardButton: Button ("Enable Keyboard" Button)
|   |   |   |   |   |   |   |-- private lateinit var selectKeyboardButton: Button ("Select Keyboard" Button)
|   |   |   |   |   |   |   |-- companion object { ... } (Companion object)
|   |   |   |   |   |   |   |   |-- private const val STORAGE_PERMISSION_CODE = 100 (Storage permission request code)
|   |   |   |   |   |   |   |-- override fun onCreate(savedInstanceState: Bundle?) { ... } (Activity initialization, called when Activity is created)
|   |   |   |   |   |   |   |-- private fun checkStoragePermission() { ... } (Check and request storage permission)
|   |   |   |   |   |   |   |-- override fun onRequestPermissionsResult(...) { ... } (Handle permission request results)
|   |   |   |   |   |-- TelexComposer.kt (Telex Vietnamese processing class - old version, misplaced)
|   |   |   |   |   |-- text (Directory containing text processing code)
|   |   |   |   |   |   |-- TelexComposer.kt (Telex Vietnamese processing class - new version, correct location)
|   |   |   |   |   |   |   |-- package com.example.aikeyboard.text (Package declaration)
|   |   |   |   |   |   |   |-- import ... (Import necessary libraries)
|   |   |   |   |   |   |   |-- class TelexComposer : Composer { ... } (TelexComposer class inherits Composer)
|   |   |   |   |   |   |   |   |-- override val id = "telex" (ID of Composer)
|   |   |   |   |   |   |   |   |-- override val label = "Telex" (Label of Composer)
|   |   |   |   |   |   |   |   |-- override val toRead = 2 (Number of characters to read before compose)
|   |   |   |   |   |   |   |   |-- private val vowels = setOf(...) (Set of vowels)
|   |   |   |   |   |   |   |   |-- private val consonants = setOf(...) (Set of consonants)
|   |   |   |   |   |   |   |   |-- private val diacriticRules = mapOf(...) (Map of Telex rules)
|   |   |   |   |   |   |   |   |-- override fun getActions(...) : Pair<Int, String> { ... } (Function to get action based on input)
|   |   |   |   |   |   |-- TextProcessor.kt (Text input processing class)
|   |   |   |   |   |   |   |-- package com.example.aikeyboard.text (Package declaration)
|   |   |   |   |   |   |   |-- import ... (Import necessary libraries)
|   |   |   |   |   |   |   |-- class TextProcessor(...) { ... } (TextProcessor class)
|   |   |   |   |   |   |   |   |-- private var composingText = StringBuilder() (StringBuilder for composing text)
|   |   |   |   |   |   |   |   |-- private var isComposing = false (Composing state)
|   |   |   |   |   |   |   |   |-- fun processText(char: Char): Boolean { ... } (Process text input)
|   |   |   |   |   |   |   |   |-- fun reset() { ... } (Reset composing state)
|   |   |   |   |   |   |   |   |-- fun clear() { ... } (Clear composing text and reset)
|   |   |   |   |   |   |   |   |-- fun commitText() { ... } (Commit composing text)
|   |   |   |   |   |   |   |   |-- fun deleteLastCharacter(): Boolean { ... } (Delete last character in composing text)
|   |   |   |   |   |   |-- composing (Directory containing Composer classes)
|   |   |   |   |   |   |   |-- Appender.kt (Appender Composer class - fallback)
|   |   |   |   |   |   |   |   |-- package com.example.aikeyboard.text.composing (Package declaration)
|   |   |   |   |   |   |   |   |-- import ... (Import necessary libraries)
|   |   |   |   |   |   |   |   |-- class Appender : Composer { ... } (Appender class inherits Composer)
|   |   |   |   |   |   |   |   |   |-- override val id = "appender" (ID of Composer)
|   |   |   |   |   |   |   |   |   |-- override val label = "Appender" (Label of Composer)
|   |   |   |   |   |   |   |   |   |-- override val toRead = 0 (Number of characters to read)
|   |   |   |   |   |   |   |   |   |-- override fun getActions(...) : Pair<Int, String> { ... } (getActions function - simple appender)
|   |   |   |   |   |   |   |-- Composer.kt (Composer Interface)
|   |   |   |   |   |   |   |   |-- package com.example.aikeyboard.text.composing (Package declaration)
|   |   |   |   |   |   |   |   |-- interface Composer { ... } (Composer Interface)
|   |   |   |   |   |   |   |   |   |-- val id: String (ID of Composer)
|   |   |   |   |   |   |   |   |   |-- val label: String (Label of Composer)
|   |   |   |   |   |   |   |   |   |-- val toRead: Int (Number of characters to read)
|   |   |   |   |   |   |   |   |   |-- fun getActions(...) : Pair<Int, String> (Function to get compose action)
|   |   |   |   |   |   |   |-- TelesComposer.kt (TelesComposer class - dictionary-based)
|   |   |   |   |   |   |   |   |-- package com.example.aikeyboard.text.composing (Package declaration)
|   |   |   |   |   |   |   |   |-- import ... (Import necessary libraries)
|   |   |   |   |   |   |   |   |-- class TelesComposer : Composer { ... } (TelesComposer class inherits Composer)
|   |   |   |   |   |   |   |   |   |-- override val id = "telex" (ID of Composer)
|   |   |   |   |   |   |   |   |   |-- override val label = "Telex" (Label of Composer)
|   |   |   |   |   |   |   |   |   |-- override val toRead = 2 (Number of characters to read)
|   |   |   |   |   |   |   |   |   |-- private val rules = mapOf(...) (Dictionary of English words to keep as is)
|   |   |   |   |   |   |   |   |   |-- override fun getActions(...) : Pair<Int, String> (Function to get action based on dictionary)
|   |   |   |-- res (Application resources - layouts, drawables, strings, styles, etc.)
|   |   |   |   |-- anim (Contains XML animations)
|   |   |   |   |   |-- popup_enter.xml (Popup animation when showing)
|   |   |   |   |   |   |-- <set xmlns:android="http://schemas.android.com/apk/res/android"> (Root element - animation set)
|   |   |   |   |   |   |   |-- <alpha android:duration="150" android:fromAlpha="0.0" android:toAlpha="1.0" /> (Alpha animation - fade in)
|   |   |   |   |   |-- popup_exit.xml (Popup animation when hiding)
|   |   |   |   |   |   |-- <set xmlns:android="http://schemas.android.com/apk/res/android"> (Root element - animation set)
|   |   |   |   |   |   |   |-- <alpha android:duration="100" android:fromAlpha="1.0" android:toAlpha="0.0" /> (Alpha animation - fade out)
|   |   |   |   |   |-- slide_in_right.xml (Slide in animation from right)
|   |   |   |   |   |   |-- <set xmlns:android="http://schemas.android.com/apk/res/android"> (Root element - animation set)
|   |   |   |   |   |   |   |-- <translate android:duration="300" android:fromXDelta="100%" android:toXDelta="0%" /> (Translate animation - slide in X)
|   |   |   |   |   |   |   |-- <alpha android:duration="300" android:fromAlpha="0.0" android:toAlpha="1.0" /> (Alpha animation - fade in)
|   |   |   |   |   |-- slide_out_right.xml (Slide out animation to right)
|   |   |   |   |   |   |-- <set xmlns:android="http://schemas.android.com/apk/res/android"> (Root element - animation set)
|   |   |   |   |   |   |   |-- <translate android:duration="300" android:fromXDelta="0%" android:toXDelta="100%" /> (Translate animation - slide out X)
|   |   |   |   |   |   |   |-- <alpha android:duration="300" android:fromAlpha="1.0" android:toAlpha="0.0" /> (Alpha animation - fade out)
|   |   |   |   |-- drawable (Contains drawable files - images, shapes, layer-lists, etc.)
|   |   |   |   |   |-- ic_launcher_foreground.xml (Launcher icon - foreground)
|   |   |   |   |   |   |-- <vector xmlns:android="http://schemas.android.com/apk/res/android" ...> (Root element - vector drawable)
|   |   |   |   |   |   |   |-- <path android:fillColor="#FF6200EE" android:pathData="M38,38h32v32h-32z"/> (Path for purple square)
|   |   |   |   |   |   |   |-- <path android:fillColor="#FFFFFF" android:pathData="M45,50h16v8h-16z"/> (Path for white rectangle)
|   |   |   |   |   |-- keyboard_button_active_background.xml (Active button background)
|   |   |   |   |   |   |-- <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"> (Root element - shape)
|   |   |   |   |   |   |   |-- <solid android:color="#4CAF50" /> (Green background color)
|   |   |   |   |   |   |   |-- <corners android:radius="4dp" /> (4dp rounded corners)
|   |   |   |   |   |   |   |-- <stroke android:width="1dp" android:color="#388E3C" /> (1dp dark green border)
|   |   |   |   |   |-- keyboard_button_background.xml (Normal button background)
|   |   |   |   |   |   |-- <ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="?android:colorControlHighlight"> (Root element - ripple effect)
|   |   |   |   |   |   |   |-- <item android:id="@android:id/mask"> ... </item> (Mask for ripple effect)
|   |   |   |   |   |   |   |-- <item> ... </item> (Normal button shape)
|   |   |   |   |   |-- key_background.xml (Key background - selector for pressed state)
|   |   |   |   |   |   |-- <selector xmlns:android="http://schemas.android.com/apk/res/android"> (Root element - selector)
|   |   |   |   |   |   |   |-- <item android:state_pressed="true"> ... </item> (Item for pressed state)
|   |   |   |   |   |   |   |-- <item> ... </item> (Default item)
|   |   |   |   |   |-- key_popup_background.xml (Key popup background)
|   |   |   |   |   |   |-- <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"> (Root element - shape)
|   |   |   |   |   |   |   |-- <solid android:color="#FFFFFF" /> (White background color)
|   |   |   |   |   |   |   |-- <corners android:radius="8dp" /> (8dp rounded corners)
|   |   |   |   |   |   |   |-- <stroke android:width="1dp" android:color="#CCCCCC" /> (1dp gray border)
|   |   |   |   |   |   |   |-- <padding ... /> (Inner padding)
|   |   |   |   |   |-- popup_background.xml (General popup background)
|   |   |   |   |   |   |-- <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"> (Root element - shape)
|   |   |   |   |   |   |   |-- <solid android:color="#FFFFFF" /> (White background color)
|   |   |   |   |   |   |   |-- <corners android:radius="4dp" /> (4dp rounded corners)
|   |   |   |   |   |   |   |-- <stroke android:width="1dp" android:color="#CCCCCC" /> (1dp gray border)
|   |   |   |   |   |   |   |-- <padding ... /> (Inner padding)
|   |   |   |   |   |-- rounded_button.xml (Rounded button background)
|   |   |   |   |   |   |-- <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"> (Root element - shape)
|   |   |   |   |   |   |   |-- <solid android:color="#4CAF50" /> (Green background color)
|   |   |   |   |   |   |   |-- <corners android:radius="8dp" /> (8dp rounded corners)
|   |   |   |   |   |-- spinner_background.xml (Spinner background)
|   |   |   |   |   |   |-- <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"> (Root element - shape)
|   |   |   |   |   |   |   |-- <solid android:color="#FFFFFF" /> (White background color)
|   |   |   |   |   |   |   |-- <corners android:radius="4dp" /> (4dp rounded corners)
|   |   |   |   |   |   |   |-- <stroke android:width="1dp" android:color="#CCCCCC" /> (1dp gray border)
|   |   |   |   |-- layout (Contains XML layout files - user interface)
|   |   |   |   |   |-- activity_settings.xml (Layout for Settings Activity)
|   |   |   |   |   |   |-- <LinearLayout ...> (Main layout - Vertical LinearLayout)
|   |   |   |   |   |   |   |-- <TextView ... text="API Settings" ... /> (TextView title "API Settings")
|   |   |   |   |   |   |   |-- <TextView ... text="DeepSeek API Key" ... /> (TextView label for DeepSeek API Key)
|   |   |   |   |   |   |   |-- <EditText android:id="@+id/deepseekApiKeyEditText" ... /> (EditText to input DeepSeek API Key)
|   |   |   |   |   |   |   |-- <TextView ... text="GPT API Key" ... /> (TextView label for GPT API Key)
|   |   |   |   |   |   |   |-- <EditText android:id="@+id/gptApiKeyEditText" ... /> (EditText to input GPT API Key)
|   |   |   |   |   |   |   |-- <Button android:id="@+id/saveButton" ... text="Save API Keys" /> ("Save API Keys" Button)
|   |   |   |   |   |   |   |-- <View ... background="#CCCCCC" /> (Divider line)
|   |   |   |   |   |   |   |-- <TextView ... text="Keyboard Settings" ... /> (TextView title "Keyboard Settings")
|   |   |   |   |   |   |   |-- <Button android:id="@+id/enableKeyboardButton" ... text="Enable Keyboard" /> ("Enable Keyboard" Button)
|   |   |   |   |   |   |   |-- <Button android:id="@+id/selectKeyboardButton" ... text="Select Keyboard" /> ("Select Keyboard" Button)
|   |   |   |   |   |-- calculator_keyboard.xml (Layout for calculator keyboard)
|   |   |   |   |   |   |-- <LinearLayout ...> (Main layout - Vertical LinearLayout)
|   |   |   |   |   |   |   |-- <LinearLayout ... orientation="horizontal" ... > (Horizontal LinearLayout containing top 2 buttons)
|   |   |   |   |   |   |   |   |-- <Button android:id="@+id/btnQuayLai" ... text="Quay Lại" ... /> ("Quay Lại" / Back Button)
|   |   |   |   |   |   |   |   |-- <Button android:id="@+id/btnInVanBan" ... text="In Văn Bản" ... /> ("In Văn Bản" / Print Text Button)
|   |   |   |   |   |   |   |-- </LinearLayout>
|   |   |   |   |   |   |   |-- <TextView android:id="@+id/calculatorResult" ... text="0" ... /> (TextView to display calculator result)
|   |   |   |   |   |   |   |-- <Button android:id="@+id/btnClear" ... text="C" ... /> ("C" / Clear Button)
|   |   |   |   |   |   |   |-- <LinearLayout android:id="@+id/calculatorKeyboardContainer" ... orientation="vertical" /> (Vertical LinearLayout containing number key rows)
|   |   |   |   |   |-- dialog_clipboard_history.xml (Layout for clipboard history dialog - not used in current code)
|   |   |   |   |   |-- keyboard_key_preview.xml (Layout for key preview popup)
|   |   |   |   |   |   |-- <TextView xmlns:android="http://schemas.android.com/apk/res/android" ... /> (TextView for key preview)
|   |   |   |   |   |-- keyboard_layout.xml (Main keyboard layout)
|   |   |   |   |   |   |-- <LinearLayout ...> (Main layout - Vertical LinearLayout)
|   |   |   |   |   |   |   |-- <include layout="@layout/smartbar_layout" /> (Include Smartbar layout)
|   |   |   |   |   |   |   |-- <LinearLayout android:id="@+id/numberRow" ... > (Horizontal LinearLayout - number key row)
|   |   |   |   |   |   |   |   |-- <Button android:id="@+id/button_1" ... text="1" ... /> ("1" Button)
|   |   |   |   |   |   |   |   |-- ... (Similar Buttons for numbers 2-9, 0)
|   |   |   |   |   |   |   |-- </LinearLayout>
|   |   |   |   |   |   |   |-- <LinearLayout android:id="@+id/letterRow1" ... > (Horizontal LinearLayout - QWERTY letter row)
|   |   |   |   |   |   |   |   |-- <Button android:id="@+id/button_q" ... text="q" ... /> ("q" Button)
|   |   |   |   |   |   |   |   |-- ... (Similar Buttons for w,e,r,t,y,u,i,o,p)
|   |   |   |   |   |   |   |-- </LinearLayout>
|   |   |   |   |   |   |   |-- <LinearLayout android:id="@+id/letterRow2" ... > (Horizontal LinearLayout - ASDFG letter row)
|   |   |   |   |   |   |   |   |-- <Button android:id="@+id/button_a" ... text="a" ... /> ("a" Button)
|   |   |   |   |   |   |   |   |-- ... (Similar Buttons for s,d,f,g,h,j,k,l)
|   |   |   |   |   |   |   |-- </LinearLayout>
|   |   |   |   |   |   |   |-- <LinearLayout android:id="@+id/letterRow3" ... > (Horizontal LinearLayout - ZXCVB letter row and Shift, Backspace)
|   |   |   |   |   |   |   |   |-- <Button android:id="@+id/button_shift" ... text="⇧" ... /> (Shift Button)
|   |   |   |   |   |   |   |   |-- <Button android:id="@+id/button_z" ... text="z" ... /> ("z" Button)
|   |   |   |   |   |   |   |   |-- ... (Similar Buttons for x,c,v,b,n,m)
|   |   |   |   |   |   |   |   |-- <Button android:id="@+id/button_backspace" ... text="⌫" ... /> (Backspace Button)
|   |   |   |   |   |   |   |-- </LinearLayout>
|   |   |   |   |   |   |   |-- <LinearLayout android:id="@+id/bottomRow" ... > (Horizontal LinearLayout - bottom row: 123, space, enter, ...)
|   |   |   |   |   |   |   |   |-- <Button android:id="@+id/button_symbols" ... text="123" ... /> (Symbols/Numbers Button)
|   |   |   |   |   |   |   |   |-- <Button android:id="@+id/button_comma" ... text="," ... /> (Comma Button)
|   |   |   |   |   |   |   |   |-- <Button android:id="@+id/button_space" ... text="Space" ... /> (Space Button)
|   |   |   |   |   |   |   |   |-- <Button android:id="@+id/button_period" ... text="." ... /> (Period Button)
|   |   |   |   |   |   |   |   |-- <Button android:id="@+id/button_enter" ... text="⏎" ... /> (Enter Button)
|   |   |   |   |   |   |   |-- </LinearLayout>
|   |   |   |   |   |-- smartbar_layout.xml (Smartbar layout - toolbar above keyboard)
|   |   |   |   |   |   |-- <LinearLayout ...> (Main layout - Horizontal LinearLayout)
|   |   |   |   |   |   |   |-- <Button android:id="@+id/btnSmartbarToggle" ... text="⚡" ... /> (Smartbar toggle Button)
|   |   |   |   |   |   |   |-- <HorizontalScrollView android:id="@+id/smartbarScrollView" ... > (Horizontal ScrollView containing Smartbar content)
|   |   |   |   |   |   |   |   |-- <LinearLayout ... orientation="horizontal" ... > (Horizontal LinearLayout containing Smartbar buttons)
|   |   |   |   |   |   |   |   |   |-- <Button android:id="@+id/btnQuaCau" ... text="⌨️" ... /> ("Globe" Button - switch keyboard)
|   |   |   |   |   |   |   |   |   |-- <Button android:id="@+id/btnTinhToan" ... text="Tính Toán" ... /> ("Tính Toán" / Calculator Button)
|   |   |   |   |   |   |   |   |   |-- <Button android:id="@+id/btnPasteAndRead" ... text="Đọc clipboard" ... /> ("Đọc clipboard" / Paste and Read Button)
|   |   |   |   |   |   |   |   |   |-- <Button android:id="@+id/btnStopTts" ... text="Dừng đọc" ... /> ("Dừng đọc" / Stop TTS Button)
|   |   |   |   |   |   |   |   |   |-- <Button android:id="@+id/languageButton" ... text="VN" ... /> (VN/EN language toggle Button)
|   |   |   |   |   |   |   |   |   |-- <Spinner android:id="@+id/languageSpinner" ... /> (Translation language selection Spinner)
|   |   |   |   |   |   |   |   |   |-- <Spinner android:id="@+id/gptModelSpinner" ... /> (GPT model selection Spinner)
|   |   |   |   |   |   |   |   |   |-- <Button android:id="@+id/gptAskButton" ... text="GPT Ask" ... /> ("GPT Ask" Button)
|   |   |   |   |   |   |   |   |   |-- <Button android:id="@+id/gptTranslateButton" ... text="GPT Trans" ... /> ("GPT Trans" Button)
|   |   |   |   |   |   |   |   |   |-- <Button android:id="@+id/askButton" ... text="Depsek Ask" ... /> ("Depsek Ask" Button)
|   |   |   |   |   |   |   |   |   |-- <Button android:id="@+id/translateButton" ... text="Depsek Trans" ... /> ("Depsek Trans" Button)
|   |   |   |   |   |   |   |   |   |-- <Spinner android:id="@+id/clipboardHistorySpinner" ... /> (Clipboard history Spinner)
|   |   |   |   |-- mipmap-anydpi-v26 (Contains launcher icons for Android 8.0+ - adaptive icon)
|   |   |   |   |   |-- ic_launcher.xml (Main launcher icon)
|   |   |   |   |   |   |-- <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"> (Root element - adaptive icon)
|   |   |   |   |   |   |   |-- <background android:drawable="@color/ic_launcher_background"/> (Icon background)
|   |   |   |   |   |   |   |-- <foreground android:drawable="@drawable/ic_launcher_foreground"/> (Icon foreground)
|   |   |   |   |   |-- ic_launcher_round.xml (Rounded launcher icon)
|   |   |   |   |   |   |-- <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"> (Root element - adaptive icon)
|   |   |   |   |   |   |   |-- <background android:drawable="@color/ic_launcher_background"/> (Icon background)
|   |   |   |   |   |   |   |-- <foreground android:drawable="@drawable/ic_launcher_foreground"/> (Icon foreground)
|   |   |   |   |-- values (Contains value files - colors, dimens, strings, styles, themes, etc.)
|   |   |   |   |   |-- colors.xml (Color definitions file)
|   |   |   |   |   |   |-- <?xml version="1.0" encoding="utf-8"?> (XML declaration)
|   |   |   |   |   |   |   |-- <resources> (Root tag - resources)
|   |   |   |   |   |   |   |   |-- <color name="purple_200">#FFBB86FC</color> (purple 200 color)
|   |   |   |   |   |   |   |   |-- ... (Other colors: purple_500, purple_700, teal_200, teal_700, black, white, ic_launcher_background, colorPrimary, colorPrimaryDark, colorAccent)
|   |   |   |   |   |   |   |-- </resources>
|   |   |   |   |   |-- dimens.xml (Dimension definitions file)
|   |   |   |   |   |   |-- <?xml version="1.0" encoding="utf-8"?> (XML declaration)
|   |   |   |   |   |   |   |-- <resources> (Root tag - resources)
|   |   |   |   |   |   |   |   |-- <dimen name="key_width">50dp</dimen> (Key width dimension)
|   |   |   |   |   |   |   |   |-- <dimen name="key_height">48dp</dimen> (Key height dimension)
|   |   |   |   |   |   |   |   |-- <dimen name="space_key_width">108dp</dimen> (Space key width dimension)
|   |   |   |   |   |   |   |   |-- <dimen name="key_margin">1.3dp</dimen> (Margin between keys dimension)
|   |   |   |   |   |   |   |   |-- <dimen name="row_spacing">2dp</dimen> (Spacing between key rows dimension)
|   |   |   |   |   |   |   |   |-- <dimen name="xoa_key_width">68dp</dimen> (Backspace key width dimension)
|   |   |   |   |   |   |   |-- </resources>
|   |   |   |   |   |-- styles.xml (Style definitions file)
|   |   |   |   |   |   |-- <?xml version="1.0" encoding="utf-8"?> (XML declaration)
|   |   |   |   |   |   |   |-- <resources> (Root tag - resources)
|   |   |   |   |   |   |   |   |-- <style name="KeyboardRow"> ... </style> (Style for keyboard row)
|   |   |   |   |   |   |   |   |-- <style name="KeyboardButtonStyle" parent="Widget.AppCompat.Button"> ... </style> (Style for normal keyboard button)
|   |   |   |   |   |   |   |   |-- <style name="KeyboardButtonWide" parent="KeyboardButtonStyle"> ... </style> (Style for wide keyboard buttons (Shift, Backspace))
|   |   |   |   |   |   |   |   |-- <style name="KeyboardButtonExtraWide" parent="KeyboardButtonStyle"> ... </style> (Style for extra wide keyboard button (Space))
|   |   |   |   |   |   |   |   |-- <style name="AIButton" parent="Widget.AppCompat.Button"> ... </style> (Style for AI button)
|   |   |   |   |   |   |   |   |-- <style name="SmartBarButton" parent="Widget.AppCompat.Button"> ... </style> (Style for Smartbar button)
|   |   |   |   |   |   |   |   |-- <style name="AppTheme" parent="Theme.AppCompat.Light.DarkActionBar"> ... </style> (Style for App Theme - not used in keyboard code)
|   |   |   |   |   |   |   |-- </resources>
|   |   |   |   |   |-- themes.xml (Application theme definitions file)
|   |   |   |   |   |   |-- <?xml version="1.0" encoding="utf-8"?> (XML declaration)
|   |   |   |   |   |   |   |-- <resources xmlns:tools="http://schemas.android.com/tools"> (Root tag - resources)
|   |   |   |   |   |   |   |   |-- <style name="Theme.AIKeyboard" parent="Theme.MaterialComponents.DayNight.DarkActionBar"> ... </style> (Main theme of the keyboard application)
|   |   |   |   |-- xml (Contains XML configuration files, e.g., for Input Method)
|   |   |   |   |   |-- method.xml (Input Method Service configuration file)
|   |   |   |   |   |   |-- <?xml version="1.0" encoding="utf-8"?> (XML declaration)
|   |   |   |   |   |   |   |-- <input-method xmlns:android="http://schemas.android.com/apk/res/android" ...> (Root element - input-method)
|   |   |   |   |   |   |   |   |-- android:settingsActivity="com.example.aikeyboard.SettingsActivity" (Settings Activity for the keyboard)
|   |   |   |   |   |   |   |   |-- android:supportsSwitchingToNextInputMethod="true" (Supports switching to the next input method)
|   |   |   |   |   |   |   |   |-- <subtype android:label="AI Keyboard" android:imeSubtypeLocale="en_US" android:imeSubtypeMode="keyboard" /> (Keyboard subtype declaration)
|-- .gradle (Gradle cache and temporary files)
|   |-- 7.0.2 (Gradle version directory)
|   |   |-- gc.properties (Gradle configuration properties)
|   |   |-- dependencies-accessors (Dependency accessors directory)
|   |   |   |-- gc.properties (Gradle dependencies accessors properties)
|   |-- buildOutputCleanup (Build output cleanup directory)
|   |   |-- cache.properties (Cache properties for build output cleanup)
|   |-- vcs-1 (VCS version control system directory)
|   |   |-- gc.properties (VCS properties)
|-- .vscode (VS Code configuration directory)
|   |-- settings.json (VS Code configuration file for the project)
|   |   |-- { ... } (JSON object)
|   |   |   |-- "java.configuration.updateBuildConfiguration": "interactive" (Java build config update setting)
|-- gradle (Gradle wrapper directory)
|   |-- wrapper (Wrapper directory)
|   |   |-- gradle-wrapper.jar (Gradle wrapper JAR file - to run Gradle without installation)
|   |   |-- gradle-wrapper.properties (Gradle wrapper configuration file)
|   |   |   |-- distributionBase=GRADLE_USER_HOME (Distribution base)
|   |   |   |-- distributionPath=wrapper/dists (Distribution path)
|   |   |   |-- distributionUrl=https\://services.gradle.org/distributions/gradle-7.0.2-bin.zip (Gradle distribution download URL)
|   |   |   |-- networkTimeout=10000 (Network timeout)
|   |   |   |-- validateDistributionUrl=true (Validate distribution URL)
|   |   |   |-- zipStoreBase=GRADLE_USER_HOME (Zip store base)
|   |   |   |-- zipStorePath=wrapper/dists (Zip store path)
|-- gradlew.bat (Gradle wrapper script for Windows - batch file)
|   |-- @rem ... (Batch file comments)
|   |-- @if "%DEBUG%" == "" @echo off (Turn off echo if not debugging)
|   |-- @rem ########################################################################## (Comment header)
|   |-- @rem  Gradle startup script for Windows (Comment description)
|   |-- ... (Batch script code - find Java, set classpath, execute Gradle Wrapper)
|   |-- :omega (Omega label - not used in code, possibly a placeholder)
|-- gradlew (Gradle wrapper script for Linux/MacOS - shell script)
|   |-- #!/usr/bin/env sh (Shebang - specify shell)
|   |-- #
|   |-- # Copyright 2015 the original author or authors. (Copyright comment)
|   |-- ... (Shell script code - find Java, set classpath, execute Gradle Wrapper)
|   |-- APP_NAME="gradlew" (Set APP_NAME variable)
|   |-- addClasspath() { (addClasspath function - add classpath)
|   |-- }
|   |-- loadClasspath (Call loadClasspath function)
|   |-- eval set -- "$new_args" (Eval and set arguments)
|   |-- :omega (Omega label - not used in code, possibly a placeholder)
|-- gradle.properties (General Gradle properties file for the project)
|   |-- # Project-wide Gradle settings (Comment header)
|   |-- org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 (JVM arguments)
|   |-- android.useAndroidX=true (Use AndroidX)
|   |-- kotlin.code.style=official (Kotlin code style - official)
|   |-- android.nonTransitiveRClass=true (Non-transitive R class)
|   |-- org.gradle.java.home=C:\\Program Files\\Java\\jdk-11 (Java home path)
|-- local.properties (Local properties file - usually contains SDK path)
|   |-- sdk.dir=C:\\Users\\ok\\AppData\\Local\\Android\\Sdk (Android SDK path)
```

**Additional explanation for children or coding beginners:**

* **The AI Keyboard app is like a house:** The `app` directory is like the main house of the application. Inside, there are many rooms (`src`, `res`, `java`, `layout`, `drawable`...).
* **`AndroidManifest.xml` is the building permit:** It tells the phone what this application is, what it needs (permissions), and important "rooms" (Activities, Services).
* **`java` is where the "workers" work:** The `.kt` (Kotlin code) files are where instructions are written for the keyboard to work. For example, `AIKeyboardService.kt` is the main "worker" controlling everything about the keyboard.
* **`res` is where beautiful decorations are kept:** Subdirectories like `layout` (blueprints of rooms - interface), `drawable` (decorative pictures - images, icons), `anim` (special effects - animations)... make the application house beautiful and easy to use.
* **`gradle` is the construction manager:** It helps build the application into an installation file, like a construction manager building a house.
* **`.properties`, `.json`, `.xml` files are papers, configurations:** They contain settings and configurations for the application and tools.

**Notes:**

* This diagram focuses on the structure and main purpose of each file.
* Details inside each function and class can be further expanded if more depth is needed.
* For children, the diagram can be simplified and explained using images, for example, icons for each file/directory type.

Hope this diagram helps you and everyone easily visualize the structure and functions of this AI keyboard code!
```

```
```
|-- gradle.properties
|-- gradlew.bat
|-- local.properties
|-- .gradle
|   |-- config.properties
|   |-- 7.0.2
|   |   |-- gc.properties
|   |   |-- dependencies-accessors
|   |   |   |-- gc.properties
|   |-- 8.0
|   |   |-- gc.properties
|   |   |-- dependencies-accessors
|   |   |   |-- gc.properties
|   |-- 8.0.2
|   |   |-- gc.properties
|   |   |-- dependencies-accessors
|   |   |   |-- gc.properties
|   |-- 8.10.2
|   |   |-- gc.properties
|   |   |-- dependencies-accessors
|   |   |   |-- gc.properties
|   |-- buildOutputCleanup
|   |   |-- cache.properties
|   |-- vcs-1
|   |   |-- gc.properties
|-- .idea
|   |-- compiler.xml
|   |-- deploymentTargetSelector.xml
|   |-- gradle.xml
|   |-- kotlinc.xml
|   |-- migrations.xml
|   |-- misc.xml
|   |-- runConfigurations.xml
|   |-- workspace.xml
|   |-- caches
|   |   |-- deviceStreaming.xml
|-- .vscode
|   |-- settings.json
|-- app
|   |-- src
|   |   |-- main
|   |   |   |-- AndroidManifest.xml
|   |   |   |-- java
|   |   |   |   |-- com
|   |   |   |   |   |-- example
|   |   |   |   |   |   |-- aikeyboard
|   |   |   |   |   |   |   |-- AIKeyboardService.kt
|   |   |   |   |   |   |   |-- ClipboardHelper.kt
|   |   |   |   |   |   |   |-- CustomButton.kt
|   |   |   |   |   |   |   |-- DeepSeekAPI.kt
|   |   |   |   |   |   |   |-- GPTAPI.kt
|   |   |   |   |   |   |   |-- Logger.kt
|   |   |   |   |   |   |   |-- SettingsActivity.kt
|   |   |   |   |   |   |   |-- TelexComposer.kt
|   |   |   |   |   |   |   |-- text
|   |   |   |   |   |   |   |   |-- TextProcessor.kt
|   |   |   |   |   |   |   |   |-- TelexComposer.kt
|   |   |   |   |   |   |   |   |-- composing
|   |   |   |   |   |   |   |       |-- Composer.kt
|   |   |   |   |   |   |   |       |-- TelesComposer.kt
|   |   |   |-- res
|   |   |   |   |-- anim
|   |   |   |   |   |-- popup_enter.xml
|   |   |   |   |   |-- popup_exit.xml
|   |   |   |   |   |-- slide_in_right.xml
|   |   |   |   |   |-- slide_out_right.xml
|   |   |   |   |-- drawable
|   |   |   |   |   |-- ic_launcher_foreground.xml
|   |   |   |   |   |-- keyboard_button_active_background.xml
|   |   |   |   |   |-- keyboard_button_background.xml
|   |   |   |   |   |-- key_background.xml
|   |   |   |   |   |-- key_popup_background.xml
|   |   |   |   |   |-- popup_background.xml
|   |   |   |   |   |-- rounded_button.xml
|   |   |   |   |   |-- spinner_background.xml
|   |   |   |   |-- layout
|   |   |   |   |   |-- activity_settings.xml
|   |   |   |   |   |-- calculator_keyboard.xml
|   |   |   |   |   |-- dialog_clipboard_history.xml
|   |   |   |   |   |-- keyboard_key_preview.xml
|   |   |   |   |   |-- keyboard_layout.xml
|   |   |   |   |   |-- key_button.xml
|   |   |   |   |   |-- key_popup.xml
|   |   |   |   |   |-- number_keyboard.xml
|   |   |   |   |   |-- smartbar_layout.xml
|   |   |   |   |-- mipmap-anydpi-v26
|   |   |   |   |   |-- ic_launcher.xml
|   |   |   |   |   |-- ic_launcher_round.xml
|   |   |   |   |-- values
|   |   |   |   |   |-- colors.xml
|   |   |   |   |   |-- dimens.xml
|   |   |   |   |   |-- strings.xml
|   |   |   |   |   |-- styles.xml
|   |   |   |   |   |-- themes.xml
|   |   |   |   |-- xml
|   |   |   |       |-- backup_rules.xml
|   |   |   |       |-- data_extraction_rules.xml
|   |   |   |       |-- method.xml
|-- gradle
|   |-- wrapper
|       |-- gradle-wrapper.properties

```

**Giải thích chi tiết:**

*   **Root (banphim300.txt):**  Đây là thư mục gốc, đại diện cho toàn bộ project.

*   **Project-level files:**
    *   `gradle.properties`:  Cấu hình Gradle toàn project (JVM args, AndroidX, v.v.).
    *   `gradlew.bat`:  Script để chạy Gradle Wrapper trên Windows.
    *   `local.properties`:  Cấu hình *local* (SDK path, không nên commit lên VCS).
    *   `.gradle`: Thư mục chứa cache và cấu hình của Gradle.
    *   `.idea`:  Thư mục chứa cấu hình của IntelliJ IDEA / Android Studio.
    *   `.vscode`: Thư mục chứa cấu hình của VS Code.
    *   `app`:  Thư mục chứa mã nguồn chính của ứng dụng Android.

*   **`app/src/main`:** Chứa source code chính của ứng dụng.

    *   `AndroidManifest.xml`:  File khai báo quan trọng, định nghĩa các thành phần (Activity, Service), permissions, và các thông tin cơ bản khác của ứng dụng.

    *   `java/com/example/aikeyboard`:  Chứa mã nguồn Kotlin.
        *   `AIKeyboardService.kt`:  Đây là *trái tim* của ứng dụng,  lớp `InputMethodService` xử lý toàn bộ logic của bàn phím:
            *   Xử lý sự kiện nhấn phím.
            *   Giao tiếp với InputConnection (để gửi text vào trường văn bản).
            *   Quản lý layout bàn phím (normal, symbol, calculator).
            *   Tích hợp API DeepSeek và GPT (dịch, hỏi đáp).
            *   Quản lý Text-to-Speech (TTS).
            *   Xử lý tiếng Việt (Telex).
            *   Quản lý Clipboard History.
            *   Xử lý Speech-to-Text
        *   `ClipboardHelper.kt`:  Lớp tiện ích để tương tác với clipboard (copy, paste, quản lý lịch sử).
        *   `CustomButton.kt`: Data class để lưu trữ thông tin về các nút tùy chỉnh (tên, prompt).
        *   `DeepSeekAPI.kt`:  Lớp đóng gói lời gọi API DeepSeek (non-streaming).
        *   `GPTAPI.kt`:  Lớp đóng gói lời gọi API GPT (non-streaming).
        *   `Logger.kt`:  Lớp tiện ích để ghi log (vào logcat và file).
        *   `SettingsActivity.kt`:  Activity để người dùng cấu hình API keys và các tùy chọn khác.
        *   `TelexComposer.kt`: Lớp `Composer` xử lý gõ tiếng Việt Telex.
        *   `text`: Gói chứa các lớp liên quan đến xử lý văn bản
          *   `TextProcessor.kt`: Xử lý văn bản, bao gồm cả việc sử dụng Composer để gõ tiếng Việt.
          * `composing`:
            *   `Composer.kt`:  Interface cho các bộ gõ (Telex, VNI, v.v.).
            *   `TelesComposer.kt`: Composer xử lý gõ tiếng Việt Telex.
            *   `Appender.kt`: Composer mặc định, chỉ đơn giản là nối text.
            *    `WithRules.kt`: Composer dựa trên các quy tắc.

    *   `res`:  Chứa tài nguyên của ứng dụng.
        *   `anim`:  Animations (popup, slide).
        *   `drawable`:  Hình ảnh, hình dạng, selector cho các thành phần UI.
        *   `layout`:  File XML định nghĩa giao diện (keyboard, smartbar, settings, calculator, v.v.).
        *   `mipmap-anydpi-v26`:  Icon của ứng dụng (adaptive icon).
        *   `values`:  Các file XML chứa các giá trị như màu sắc, kích thước, chuỗi, style.
        *   `xml`:
            *   `backup_rules.xml`:  Quy tắc sao lưu (hiện tại trống).
            *   `data_extraction_rules.xml`: Quy tắc trích xuất dữ liệu (cloud backup).
            *   `method.xml`:  Khai báo `InputMethodService`, liên kết với `SettingsActivity`.

*   **`gradle/wrapper`:**  Chứa Gradle Wrapper (giúp build project mà không cần cài Gradle thủ công).

**Tóm tắt chức năng chính:**

1.  **Bàn phím ảo:**  `AIKeyboardService` là lớp chính, cung cấp một bàn phím ảo đầy đủ chức năng.
2.  **Gõ tiếng Việt:**  Sử dụng `TelexComposer` (hoặc các `Composer` khác) để hỗ trợ gõ tiếng Việt.
3.  **Tích hợp AI:**  Gọi API DeepSeek và GPT để dịch thuật và hỏi đáp, dựa trên text được copy vào clipboard.
4.  **Text-to-Speech:**  Đọc text từ clipboard.
5.  **Clipboard History:**  Lưu trữ và cho phép truy cập lịch sử clipboard.
6.  **Cấu hình:**  `SettingsActivity` cho phép người dùng nhập API keys và bật/chọn bàn phím.
7.  **Speech-to-Text:** Chuyển đổi giọng nói thành văn bản.
8. **Máy tính:** Tích hợp máy tính đơn giản.
9. **Smartbar:** Thanh công cụ hiển thị các nút chức năng và danh sách lịch sử clipboard.

Đây là sơ đồ và giải thích chi tiết, giúp bạn hiểu rõ cấu trúc và luồng hoạt động của ứng dụng bàn phím AI này.
