package com.example.aikeyboard.text

import android.view.inputmethod.InputConnection
import com.example.aikeyboard.text.composing.Composer
import com.example.aikeyboard.text.TelexComposer

class TextProcessor(
    private val inputConnection: InputConnection,
    private val composer: Composer = TelexComposer()
) {
    private var composingText = StringBuilder()
    private var isComposing = false

    fun processText(char: Char): Boolean {
        // Nếu không phải chữ cái và không đang compose
        if (!char.isLetter() && !isComposing) {
            inputConnection.commitText(char.toString(), 1)
            return true
        }

        // Bắt đầu composing nếu chưa
        if (!isComposing) {
            isComposing = true
            composingText.clear()
        }

        // Xử lý text với composer
        val result = composer.getActions(composingText.toString(), char.toString())

        // Nếu có thay đổi, cập nhật composing text
        if (result.first > 0) {
            composingText.delete(composingText.length - result.first, composingText.length)
        }
        composingText.append(result.second)


        // Hiển thị text đang compose
        inputConnection.setComposingText(composingText, 1)

        return true
    }

    fun reset() {
        if (isComposing) {
            // Commit text hiện tại
            inputConnection.finishComposingText()
            composingText.clear()
            isComposing = false
        }
    }


    fun clear() {
        composingText.clear()
        isComposing = false
        inputConnection.finishComposingText()
    }


    fun commitText() {
        if(isComposing){
            inputConnection.finishComposingText()
            composingText.clear()
            isComposing = false
        }

    }

    fun deleteLastCharacter(): Boolean {
        if (composingText.isNotEmpty()) {
            composingText.deleteCharAt(composingText.length - 1)
            if (composingText.isEmpty()) {
                reset() // Reset nếu composingText trống
            } else {
                inputConnection.setComposingText(composingText.toString(), 1) // Cập nhật composing text
            }
            return true
        }
        return false
    }
}
