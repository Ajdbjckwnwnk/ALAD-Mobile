package com.alad.app.core.network

import android.util.Base64
import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

class ALADWebSocketManager(
    private val client: OkHttpClient,
    private val customWsUrl: String? = null
) {
    private var webSocket: WebSocket? = null
    var onBinaryMessageReceived: ((ByteArray) -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null
    var onInterrupted: (() -> Unit)? = null

    companion object {
        private const val TAG = "ALADWebSocketManager"
        private const val DEFAULT_GEMINI_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }

    private var isGeminiConnection = true
    private var isSetupComplete = false

    fun connect(
        apiKey: String,
        sourceLang: String,
        targetLang: String,
        filterNativeSpeech: Boolean = true
    ) {
        val baseUrl = if (!customWsUrl.isNullOrBlank()) customWsUrl.trim() else DEFAULT_GEMINI_WS_URL
        val finalUrl = if (baseUrl.contains("?")) "$baseUrl&key=$apiKey" else "$baseUrl?key=$apiKey"
        val request = Request.Builder().url(finalUrl).build()
        isSetupComplete = false
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to Gemini Live API via ${if (!customWsUrl.isNullOrBlank()) "Custom Proxy" else "Direct Endpoint"}")
                onStatusChanged?.invoke("Connected")
                sendGeminiSetup(targetLang, filterNativeSpeech)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Raw message: $text")
                try {
                    val json = JSONObject(text)
                    
                    if (json.has("serverContent") || json.has("server_content")) {
                        val serverContent = json.optJSONObject("serverContent") ?: json.optJSONObject("server_content")
                        if (serverContent != null) {
                            if (serverContent.optBoolean("interrupted", false)) {
                                onInterrupted?.invoke()
                            }
                            if (serverContent.has("modelTurn") || serverContent.has("model_turn")) {
                                val modelTurn = serverContent.optJSONObject("modelTurn") ?: serverContent.optJSONObject("model_turn")
                                val parts = modelTurn?.optJSONArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val part = parts.getJSONObject(i)
                                        val inlineData = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                                        if (inlineData != null) {
                                            val base64Data = inlineData.getString("data")
                                            val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                            onBinaryMessageReceived?.invoke(audioBytes)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (json.has("setupComplete") || json.has("setup_complete")) {
                        isSetupComplete = true
                        onStatusChanged?.invoke("Gemini Ready")
                        pendingAudio.forEach { sendAudioNow(it) }
                        pendingAudio.clear()
                    } else if (json.has("error")) {
                        val errMessage = json.getJSONObject("error").optString("message", "Unknown error")
                        Log.e(TAG, "API Error: $errMessage")
                        onStatusChanged?.invoke("Error: $errMessage")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                onMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isSetupComplete = false
                Log.d(TAG, "Closing: $code $reason")
                onStatusChanged?.invoke("Disconnected: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isSetupComplete = false
                Log.e(TAG, "WebSocket error", t)
                onStatusChanged?.invoke("Error: ${t.message}")
            }
        })
    }
    
    private fun sendGeminiSetup(targetLang: String, filterNativeSpeech: Boolean) {
        val targetLangCode = targetLang.split("-")[0]
        
        val promptText = if (filterNativeSpeech) {
            """
                You are a professional real-time live interpreter and voice dubber.
                Target Language: $targetLangCode (Persian/Farsi).
                
                OPERATIONAL RULES:
                1. Listen to the input foreign audio (e.g. English broadcast, video speech, or lectures).
                2. Immediately translate and speak out the translation fluently in natural spoken $targetLangCode.
                3. NATIVE SPEECH SUPPRESSION: If the speaker is ALREADY speaking in $targetLangCode, or if people in the background talk in $targetLangCode, YOU MUST REMAIN 100% COMPLETELY SILENT. Do NOT echo, do NOT repeat, and do NOT translate.
                4. NON-SPEECH SOUNDS: Completely ignore background ambient noise, coughing, room noise, TV theme music, laughter, or breathing.
                5. Output ONLY speech audio in $targetLangCode. Never add commentary, introductory words, or conversational filler.
            """.trimIndent()
        } else {
            """
                You are a professional real-time live interpreter and voice dubber.
                Target Language: $targetLangCode.
                Translate any foreign speech immediately and fluently into spoken $targetLangCode.
                Do not add commentary.
            """.trimIndent()
        }

        val setupPayload = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/gemini-2.0-flash-exp")
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede")
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", promptText)
                    }))
                })
            })
        }
        webSocket?.send(setupPayload.toString())
    }

    private val pendingAudio = mutableListOf<String>()

    fun sendAudioData(pcmData: ByteArray) {
        val base64Audio = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        if (!isSetupComplete) {
            pendingAudio.add(base64Audio)
            if (pendingAudio.size > 8) pendingAudio.removeAt(0)
            return
        }
        sendAudioNow(base64Audio)
    }
    
    private fun sendAudioNow(base64Audio: String) {
        val inputPayload = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().put(JSONObject().apply {
                    put("mimeType", "audio/pcm;rate=16000")
                    put("data", base64Audio)
                }))
            })
        }
        webSocket?.send(inputPayload.toString())
    }

    fun disconnect() {
        pendingAudio.clear()
        webSocket?.close(1000, "User requested stop")
        webSocket = null
        onStatusChanged?.invoke("Disconnected")
    }
}
