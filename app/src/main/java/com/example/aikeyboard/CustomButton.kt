package com.example.aikeyboard

import org.json.JSONObject

data class CustomButton(
    val name: String,
    val prompt: String
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("name", name)
            put("prompt", prompt)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): CustomButton {
            val obj = JSONObject(json)
            return CustomButton(
                name = obj.getString("name"),
                prompt = obj.getString("prompt")
            )
        }
    }
}