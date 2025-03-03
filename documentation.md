# AIKeyboard Documentation

## Overview

AIKeyboard is an Android custom keyboard application that provides a standard keyboard interface with enhanced features including Vietnamese language support, AI-powered text generation and translation, speech-to-text, text-to-speech, and a calculator function.
```
AIKeyboard/
├── .gradle/
├── .idea/
├── .vscode/
├── app/
│   ├── build/
│   ├── build.gradle
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/
│           │   └── com/
│           │       └── example/
│           │           └── aikeyboard/
│           │               ├── AIKeyboardOllama.kt
│           │               ├── AIKeyboardService.kt
│           │               ├── ClipboardHelper.kt
│           │               ├── CustomButton.kt
│           │               ├── DeepSeekAPI.kt
│           │               ├── GPTAPI.kt
│           │               ├── Logger.kt
│           │               ├── OllamaChatActivity.kt
│           │               ├── SettingsActivity.kt
│           │               ├── WelcomeActivity.kt
│           │               └── text/
│           └── res/
│               ├── anim/
│               ├── drawable/
│               ├── layout/
│               ├── mipmap-anydpi-v26/
│               ├── mipmap-hdpi/
│               ├── mipmap-mdpi/
│               ├── mipmap-xhdpi/
│               ├── mipmap-xxhdpi/
│               ├── mipmap-xxxhdpi/
│               ├── values/
│               └── xml/
├── build/
├── build.gradle
├── gradle/
├── gradle.properties
├── gradlew
├── gradlew.bat
├── local.properties
└── settings.gradle

## Keyboard Layout Structure
```
```
AIKeyboard
├── Smartbar (Toggle using ⚡ button)
│   ├── Input/Language Tools
│   ├── AI Functions
│   └── Clipboard & Calculations
├── Main Keyboard
│   ├── Number Row (1-0)
│   ├── Letter Row 1 (QWERTYUIOP)
│   ├── Letter Row 2 (ASDFGHJKL)
│   ├── Letter Row 3 (ZXCVBNM)
│   └── Bottom Row (Symbols, Space, Enter)
└── Specialty Keyboards
    └── Calculator Keyboard
```

```

## Basic Keys Functionality

### Character Input Keys
| Key Type | Description | Behavior |
|----------|-------------|----------|
| Letters (A-Z) | Standard alphabet keys | Type letters in lower or uppercase based on Shift state. In Vietnamese mode, supports Telex input for Vietnamese characters. |
| Numbers (0-9) | Standard number keys | Type numeric characters. Changes to symbols when in symbol mode. |
| Punctuation | Period, comma, etc. | Insert punctuation marks at cursor position |

### Special Function Keys

| Key | Description | Behavior |
|-----|-------------|----------|
| Space | Inserts space | Code: 32. Adds space character at cursor position. |
| ⇧ (Shift) | Changes letter case | Code: -1. Three states: Normal (lowercase), Single Shift (next letter uppercase), Caps Lock (all letters uppercase). Double-tap for Caps Lock. |
| 123/ABC | Symbol mode toggle | Code: 35, 64. Switches between letter mode and symbol mode. |
| ⌫ (Backspace) | Deletes characters | Deletes character before cursor. Hold to delete continuously with acceleration. |
| ↵ (Enter) | Line break or action | Inserts newline or triggers action based on context (search, send, etc.). |
| VN/EN | Language toggle | Switches between Vietnamese and English input modes. |

## Smartbar Functionality

### Input & Language Tools

| Button | Description | Functionality |
|--------|-------------|---------------|
| ⌨️ | Keyboard switcher | Opens system keyboard selection menu |
| 🎤 | Microphone | Activates speech-to-text recognition |
| VN/EN | Language toggle | Switches between Vietnamese and English input modes |
| Language Spinner | Language selection | Dropdown to select target language for translation (Vietnamese, English, Chinese, Japanese, Korean, French, German, Spanish, Russian, Italian) |

### AI Functions

| Button | Description | Functionality |
|--------|-------------|---------------|
| GPT Ask | GPT AI Query | Sends text from clipboard to OpenAI API and displays response |
| GPT Trans | GPT Translation | Translates clipboard text to selected language using OpenAI API |
| Ollama Ask | Ollama AI Query | Sends text from clipboard to local Ollama API server and displays response |
| Deepseek Ask | Deepseek AI Query | Sends text from clipboard to Deepseek API and displays response |
| Deepseek Trans | Deepseek Translation | Translates clipboard text to selected language using Deepseek API |
| GPT Model Spinner | Model Selection | Dropdown to select GPT model (gpt-3.5-turbo-1106, o3-mini-2025-01-31, gpt-4o-2024-11-20, gpt-4o-mini-2024-07-18, o1-2024-12-17, o1-preview-2024-09-12) |

### Utility Functions

| Button | Description | Functionality |
|--------|-------------|---------------|
| ⚡ | Smartbar Toggle | Shows/hides the smartbar |
| Đọc clipboard | Text-to-Speech | Reads clipboard text aloud using TTS engine |
| Dừng đọc | Stop TTS | Stops text-to-speech playback |
| Tính Toán | Calculator | Opens calculator keyboard overlay |
| Clipboard History | Clipboard Selection | Dropdown menu to access previously copied text items |

## Calculator Keyboard

| Button | Description | Functionality |
|--------|-------------|---------------|
| 0-9 | Number buttons | Enter numbers for calculation |
| +,-,×,÷ | Operator buttons | Addition, subtraction, multiplication, division |
| = | Equals button | Performs the calculation and displays result |
| C | Clear button | Clears the last character from the expression |
| Quay lại | Return button | Closes calculator and returns to main keyboard |
| In văn bản | Print Text | Sends the current calculator result to the text field |

## Vietnamese Input System

The keyboard uses a Telex input method for Vietnamese with the following key combinations:

| Telex Combination | Result | Example |
|-------------------|--------|---------|
| a + a | â | aa → â |
| e + e | ê | ee → ê |
| o + o | ô | oo → ô |
| o + w | ơ | ow → ơ |
| u + w | ư | uw → ư |
| d + d | đ | dd → đ |
| Vowel + s | Acute accent (´) | as → á |
| Vowel + f | Grave accent (`) | af → à |
| Vowel + r | Hook accent (̉) | ar → ả |
| Vowel + x | Tilde accent (˜) | ax → ã |
| Vowel + j | Dot accent (.) | aj → ạ |

## Key Events

| Event | Behavior |
|-------|----------|
| Key Press | Shows key popup with enlarged character, commits text or processes Vietnamese input |
| Long Press | For backspace, continuously deletes with accelerating speed |
| Double-tap | For shift key, toggles Caps Lock mode |

## API Integrations

| API | Purpose | Configuration |
|-----|---------|---------------|
| OpenAI GPT | Text generation and translation | API key required in settings |
| Deepseek AI | Text generation and translation | API key required in settings |
| Ollama | Local AI model interactions | Local server URL required (default: http://192.168.0.1:11434) |
| Android TTS | Text-to-speech functionality | Uses system TTS engine |
| Android STT | Speech recognition | Uses system speech recognition |

## Settings Configuration

Settings for the keyboard can be configured in the SettingsActivity, including:
- API keys for GPT, Deepseek
- Ollama base URL and model selection
- Default language preferences
- TTS voice settings

## Usage Guidelines

1. **AI Functions**: All AI functions use clipboard text as input. Copy text first, then press the appropriate AI button.
2. **Vietnamese Input**: Activate Vietnamese mode with VN button, then use Telex combinations.
3. **Calculator**: Open with "Tính Toán" button, perform calculations, then use "In văn bản" to insert the result.
4. **Shift Modes**: Single press for capitalizing one letter, double-tap for Caps Lock mode.
5. **Symbol Mode**: Switch between letters and symbols using the 123/ABC button.

## Troubleshooting

- **AI Features Not Working**: Verify API keys are correctly set in Settings
- **Vietnamese Input Issues**: Ensure Vietnamese mode is active (VN button)
- **Ollama Connection Errors**: Check local server URL and ensure Ollama is running
- **TTS Not Working**: Verify system TTS engine is properly configured

## Undo/Redo Implementation

#### Core Components
- **Button IDs**: 
  - `R.id.btnUndo`
  - `R.id.btnRedo`
- **Responsible Functions**: 
  - `setupUndoRedoButtons()` trong AIKeyboardService.kt
- **Data Structures**:
  - `undoStack`: Stack lưu trữ các hành động để có thể hoàn tác
  - `redoStack`: Stack lưu trữ các hành động đã hoàn tác để có thể làm lại

#### Implementation Details
```kotlin
// Cấu trúc dữ liệu cho các hành động
data class TextAction(
    val text: String,
    val cursorPosition: Int,
    val actionType: ActionType
)

enum class ActionType {
    ADD, DELETE, REPLACE
}

// Stack cho hoàn tác và làm lại
private val undoStack = Stack<TextAction>()
private val redoStack = Stack<TextAction>()

// Hàm thiết lập nút Undo/Redo
private fun setupUndoRedoButtons() {
    val undoButton = keyboard?.findViewById<Button>(R.id.btnUndo)
    undoButton?.setOnClickListener {
        performUndo()
    }
    
    val redoButton = keyboard?.findViewById<Button>(R.id.btnRedo)
    redoButton?.setOnClickListener {
        performRedo()
    }
    
    // Cập nhật trạng thái kích hoạt dựa trên stack
    updateUndoRedoState()
}

// Thực hiện hoàn tác
private fun performUndo() {
    if (undoStack.isEmpty()) return
    
    val action = undoStack.pop()
    
    // Lưu trạng thái hiện tại vào redoStack
    saveCurrentStateToRedo()
    
    // Khôi phục trạng thái trước đó
    when (action.actionType) {
        ActionType.ADD -> {
            // Nếu là thêm, xóa văn bản đã thêm
            currentInputConnection?.deleteSurroundingText(
                action.text.length, 0
            )
        }
        ActionType.DELETE -> {
            // Nếu là xóa, thêm lại văn bản đã xóa
            currentInputConnection?.commitText(action.text, 1)
        }
        ActionType.REPLACE -> {
            // Nếu là thay thế, xóa văn bản mới và thêm lại văn bản cũ
            val currentText = getCurrentText()
            currentInputConnection?.deleteSurroundingText(
                currentText.length, 0
            )
            currentInputConnection?.commitText(action.text, 1)
        }
    }
    
    // Đặt lại vị trí con trỏ
    currentInputConnection?.setSelection(action.cursorPosition, action.cursorPosition)
    
    // Cập nhật trạng thái của nút
    updateUndoRedoState()
}

// Thực hiện làm lại
private fun performRedo() {
    if (redoStack.isEmpty()) return
    
    val action = redoStack.pop()
    
    // Lưu trạng thái hiện tại vào undoStack
    saveCurrentStateToUndo()
    
    // Khôi phục trạng thái
    val currentText = getCurrentText()
    currentInputConnection?.deleteSurroundingText(
        currentText.length, 0
    )
    currentInputConnection?.commitText(action.text, 1)
    
    // Đặt lại vị trí con trỏ
    currentInputConnection?.setSelection(action.cursorPosition, action.cursorPosition)
    
    // Cập nhật trạng thái của nút
    updateUndoRedoState()
}

// Lưu trạng thái hiện tại vào undoStack
private fun saveCurrentStateToUndo() {
    val currentText = getCurrentText()
    val cursorPosition = getCurrentCursorPosition()
    
    undoStack.push(TextAction(
        text = currentText,
        cursorPosition = cursorPosition,
        actionType = ActionType.REPLACE
    ))
    
    // Giới hạn kích thước của stack
    if (undoStack.size > MAX_UNDO_STACK_SIZE) {
        undoStack.removeAt(0)
    }
}

// Lưu trạng thái hiện tại vào redoStack
private fun saveCurrentStateToRedo() {
    val currentText = getCurrentText()
    val cursorPosition = getCurrentCursorPosition()
    
    redoStack.push(TextAction(
        text = currentText,
        cursorPosition = cursorPosition,
        actionType = ActionType.REPLACE
    ))
    
    // Giới hạn kích thước của stack
    if (redoStack.size > MAX_REDO_STACK_SIZE) {
        redoStack.removeAt(0)
    }
}

// Cập nhật trạng thái kích hoạt của nút Undo/Redo
private fun updateUndoRedoState() {
    keyboard?.findViewById<Button>(R.id.btnUndo)?.isEnabled = undoStack.isNotEmpty()
    keyboard?.findViewById<Button>(R.id.btnRedo)?.isEnabled = redoStack.isNotEmpty()
}

// Lấy văn bản hiện tại
private fun getCurrentText(): String {
    return currentInputConnection?.getTextBeforeCursor(Integer.MAX_VALUE, 0)?.toString() ?: ""
}

// Lấy vị trí con trỏ hiện tại
private fun getCurrentCursorPosition(): Int {
    return currentInputConnection?.getTextBeforeCursor(Integer.MAX_VALUE, 0)?.length ?: 0
}
```

#### Cách Hoạt Động

1. **Tracking Thay Đổi**:
   - Mỗi khi một thay đổi quan trọng được thực hiện (thêm, xóa, thay thế văn bản), trạng thái hiện tại sẽ được lưu vào `undoStack`
   - Trạng thái bao gồm văn bản đầy đủ, vị trí con trỏ và loại hành động

2. **Undo (Hoàn Tác)**:
   - Khi người dùng nhấn nút Undo, trạng thái hiện tại được lưu vào `redoStack`
   - Hành động trên cùng của `undoStack` được lấy ra và áp dụng ngược lại
   - Vị trí con trỏ được khôi phục đến vị trí trước khi thay đổi

3. **Redo (Làm Lại)**:
   - Khi người dùng nhấn nút Redo, trạng thái hiện tại được lưu vào `undoStack`
   - Hành động trên cùng của `redoStack` được lấy ra và áp dụng lại
   - Vị trí con trỏ được khôi phục đến vị trí sau khi thay đổi

4. **Quản Lý Stack**:
   - Kích thước của cả hai stack được giới hạn để tránh sử dụng quá nhiều bộ nhớ
   - Khi thêm hành động mới vào `undoStack`, `redoStack` được xóa

## Copy/Paste Implementation

#### Core Components
- **Button IDs**: 
  - `R.id.btnCopy`
  - `R.id.btnPaste`
  - `R.id.btnCut`
- **System Services**:
  - `ClipboardManager`: Quản lý bộ nhớ tạm của hệ thống

#### Implementation Details
```kotlin
// Trong setupSmartbar()
private fun setupCopyPasteButtons() {
    val copyButton = keyboard?.findViewById<Button>(R.id.btnCopy)
    val pasteButton = keyboard?.findViewById<Button>(R.id.btnPaste)
    val cutButton = keyboard?.findViewById<Button>(R.id.btnCut)
    
    copyButton?.setOnClickListener {
        performCopy()
    }
    
    pasteButton?.setOnClickListener {
        performPaste()
    }
    
    cutButton?.setOnClickListener {
        performCut()
    }
}

// Thực hiện sao chép
private fun performCopy() {
    // Kiểm tra xem có văn bản được chọn không
    val selectedText = currentInputConnection?.getSelectedText(0)
    
    if (selectedText != null && selectedText.isNotEmpty()) {
        // Sao chép văn bản được chọn vào clipboard
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("AIKeyboard Text", selectedText)
        clipboardManager.setPrimaryClip(clipData)
        
        // Hiển thị thông báo
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    } else {
        // Nếu không có văn bản được chọn, thông báo cho người dùng
        Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
    }
}

// Thực hiện dán
private fun performPaste() {
    // Lấy văn bản từ clipboard
    val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    
    if (clipboardManager.hasPrimaryClip()) {
        val clipData = clipboardManager.primaryClip
        
        if (clipData != null && clipData.itemCount > 0) {
            val pasteText = clipData.getItemAt(0).text
            
            // Nếu có văn bản được chọn, thay thế nó
            val selectedText = currentInputConnection?.getSelectedText(0)
            if (selectedText != null && selectedText.isNotEmpty()) {
                // Lưu vào undoStack trước khi thay thế
                saveCurrentStateToUndo()
                
                // Thay thế văn bản được chọn
                currentInputConnection?.commitText(pasteText, 1)
            } else {
                // Nếu không có văn bản được chọn, chèn tại vị trí con trỏ
                saveCurrentStateToUndo()
                currentInputConnection?.commitText(pasteText, 1)
            }
            
            // Xóa redoStack vì đã có thay đổi mới
            redoStack.clear()
            updateUndoRedoState()
        }
    } else {
        // Nếu clipboard trống, thông báo cho người dùng
        Toast.makeText(this, "Clipboard empty", Toast.LENGTH_SHORT).show()
    }
}

// Thực hiện cắt
private fun performCut() {
    // Kiểm tra xem có văn bản được chọn không
    val selectedText = currentInputConnection?.getSelectedText(0)
    
    if (selectedText != null && selectedText.isNotEmpty()) {
        // Lưu vào undoStack trước khi cắt
        saveCurrentStateToUndo()
        
        // Sao chép văn bản được chọn vào clipboard
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("AIKeyboard Text", selectedText)
        clipboardManager.setPrimaryClip(clipData)
        
        // Xóa văn bản được chọn
        currentInputConnection?.commitText("", 1)
        
        // Hiển thị thông báo
        Toast.makeText(this, "Cut to clipboard", Toast.LENGTH_SHORT).show()
        
        // Xóa redoStack vì đã có thay đổi mới
        redoStack.clear()
        updateUndoRedoState()
    } else {
        // Nếu không có văn bản được chọn, thông báo cho người dùng
        Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
    }
}
```

#### Selection Handling
```kotlin
// Theo dõi sự kiện chọn văn bản để kích hoạt/vô hiệu hóa các nút
override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    
    // Kiểm tra xem có văn bản được chọn không
    val hasSelection = newSelStart != newSelEnd
    
    // Cập nhật trạng thái của nút copy và cut
    keyboard?.findViewById<Button>(R.id.btnCopy)?.isEnabled = hasSelection
    keyboard?.findViewById<Button>(R.id.btnCut)?.isEnabled = hasSelection
}
```

#### Xử Lý Sự Kiện Từ Ứng Dụng Khác
```kotlin
// Xử lý các sự kiện bàn phím từ ứng dụng chủ
override fun onUpdateExtractingVisibility(opts: EditorInfo) {
    super.onUpdateExtractingVisibility(opts)
    
    // Kiểm tra xem ứng dụng có cho phép sao chép/dán không
    val allowCopyPaste = (opts.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI) == 0
    
    // Cập nhật trạng thái của các nút
    keyboard?.findViewById<Button>(R.id.btnCopy)?.isEnabled = allowCopyPaste
    keyboard?.findViewById<Button>(R.id.btnPaste)?.isEnabled = allowCopyPaste
    keyboard?.findViewById<Button>(R.id.btnCut)?.isEnabled = allowCopyPaste
}
```

#### Tương Tác với ClipboardManager
- Lớp `ClipboardManager` của Android được sử dụng để truy cập và thay đổi nội dung của clipboard hệ thống
- `ClipData` cung cấp cấu trúc dữ liệu để lưu trữ văn bản hoặc các dữ liệu khác trong clipboard
- Phương thức `setPrimaryClip()` được sử dụng để lưu dữ liệu vào clipboard
- Phương thức `getPrimaryClip()` và `hasPrimaryClip()` được sử dụng để truy xuất và kiểm tra dữ liệu trong clipboard

### Error Handling

AIKeyboard implements comprehensive error handling to ensure stability and provide meaningful feedback to users when issues occur. The following are key aspects of error handling in the application:

#### Network Error Handling

```kotlin
private fun handleApiResponse(response: Response) {
    if (response.isSuccessful) {
        val responseBody = response.body?.string()
        // Process successful response
    } else {
        // Handle HTTP error codes
        val errorMessage = when (response.code) {
            401 -> "Authentication error: Invalid API key"
            429 -> "Rate limit exceeded: Too many requests"
            500 -> "Server error: Please try again later"
            else -> "Error code: ${response.code}"
        }
        showToast(errorMessage)
    }
}

private fun handleNetworkFailure(e: Exception) {
    val errorMessage = when (e) {
        is UnknownHostException -> "Network error: Unable to connect to server"
        is SocketTimeoutException -> "Network error: Connection timed out"
        is SSLException -> "Security error: SSL certificate issue"
        else -> "Network error: ${e.message}"
    }
    showToast(errorMessage)
    Log.e(TAG, "API request failed", e)
}

```

### Input Validation
```kotlin
#### Input Validation and Error Handling

The keyboard implements input validation to ensure that user interactions are processed correctly:

```kotlin
private fun validateInput(text: String): Boolean {
    // Check for empty input
    if (text.isEmpty()) {
        showToast("Input text cannot be empty")
        return false
    }
    
    // Check for maximum length
    if (text.length > MAX_INPUT_LENGTH) {
        showToast("Input exceeds maximum length of $MAX_INPUT_LENGTH characters")
        return false
    }
    
    // Check for disallowed characters
    val disallowedPattern = Pattern.compile("[^\\p{L}\\p{N}\\p{P}\\p{Z}]")
    if (disallowedPattern.matcher(text).find()) {
        showToast("Input contains disallowed characters")
        return false
    }
    
    return true
}

```

```kotlin
#### Input Validation and Error Handling

The keyboard implements input validation to ensure that user interactions are processed correctly:

```kotlin
private fun validateInput(text: String): Boolean {
    // Check for empty input
    if (text.isEmpty()) {
        showToast("Input text cannot be empty")
        return false
    }
    
    // Check for maximum length
    if (text.length > MAX_INPUT_LENGTH) {
        showToast("Input exceeds maximum length of $MAX_INPUT_LENGTH characters")
        return false
    }
    
    // Check for disallowed characters
    val disallowedPattern = Pattern.compile("[^\\p{L}\\p{N}\\p{P}\\p{Z}]")
    if (disallowedPattern.matcher(text).find()) {
        showToast("Input contains disallowed characters")
        return false
    }
    
    return true
}

```

```kotlin
#### Input Validation and Error Handling

The keyboard implements input validation to ensure that user interactions are processed correctly:

```kotlin
private fun validateInput(text: String): Boolean {
    // Check for empty input
    if (text.isEmpty()) {
        showToast("Input text cannot be empty")
        return false
    }
    
    // Check for maximum length
    if (text.length > MAX_INPUT_LENGTH) {
        showToast("Input exceeds maximum length of $MAX_INPUT_LENGTH characters")
        return false
    }
    
    // Check for disallowed characters
    val disallowedPattern = Pattern.compile("[^\\p{L}\\p{N}\\p{P}\\p{Z}]")
    if (disallowedPattern.matcher(text).find()) {
        showToast("Input contains disallowed characters")
        return false
    }
    
    return true
}
