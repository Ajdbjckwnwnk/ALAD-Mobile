package com.alad.app.core.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.projection.MediaProjection
import android.util.Log
import kotlin.math.sqrt

class AudioCaptureManager {
    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var isRecording = false

    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT).coerceAtLeast(2048)
        
        // Hangover chunks to avoid clipping sentence endings (~300-400ms)
        private const val SPEECH_HANGOVER_CHUNKS = 8
    }

    @SuppressLint("MissingPermission")
    fun startCaptureSystem(
        mediaProjection: MediaProjection,
        appUid: Int,
        enableVad: Boolean = true,
        noiseGateThreshold: Float = 300f,
        onAudioData: (ByteArray) -> Unit
    ) {
        if (isRecording) return

        try {
            val configBuilder = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                
            if (appUid > 0) {
                configBuilder.excludeUid(appUid)
            }
                
            val config = configBuilder.build()

            val format = AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .build()

            audioRecord = AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(config)
                .setBufferSizeInBytes(BUFFER_SIZE)
                .build()

            audioRecord?.startRecording()
            isRecording = true
            
            startProcessingLoop(enableVad, noiseGateThreshold, onAudioData)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting system audio capture", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startCaptureMic(
        enableNoiseSuppression: Boolean = true,
        enableVad: Boolean = true,
        noiseGateThreshold: Float = 350f,
        onAudioData: (ByteArray) -> Unit
    ) {
        if (isRecording) return

        try {
            // VOICE_RECOGNITION source has Android-level DSP speech tuning
            val audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION
            audioRecord = AudioRecord(
                audioSource,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE
            )

            // Attach hardware acoustic effects if supported by the device
            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0 && enableNoiseSuppression) {
                try {
                    if (NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                            enabled = true
                            Log.d(TAG, "Hardware NoiseSuppressor enabled")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to enable NoiseSuppressor", e)
                }

                try {
                    if (AcousticEchoCanceler.isAvailable()) {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                            enabled = true
                            Log.d(TAG, "Hardware AcousticEchoCanceler enabled")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to enable AcousticEchoCanceler", e)
                }
            }

            audioRecord?.startRecording()
            isRecording = true
            startProcessingLoop(enableVad, noiseGateThreshold, onAudioData)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting microphone capture", e)
        }
    }

    private fun startProcessingLoop(
        enableVad: Boolean,
        noiseGateThreshold: Float,
        onAudioData: (ByteArray) -> Unit
    ) {
        Thread {
            val buffer = ByteArray(BUFFER_SIZE)
            var speechHangover = 0
            var chunksRead = 0

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    chunksRead++
                    val chunk = buffer.copyOf(read)
                    
                    if (!enableVad) {
                        // VAD disabled: pass through all audio
                        onAudioData(chunk)
                    } else {
                        // Calculate Root-Mean-Square (RMS) audio energy
                        val rms = calculateRms(chunk, read)
                        if (rms >= noiseGateThreshold) {
                            speechHangover = SPEECH_HANGOVER_CHUNKS
                            onAudioData(chunk)
                        } else if (speechHangover > 0) {
                            speechHangover--
                            onAudioData(chunk)
                        } else {
                            // Silence / ambient hum: dropped to conserve battery and API bandwidth
                        }
                    }

                    if (chunksRead % 100 == 0) {
                        Log.d(TAG, "Processed 100 audio chunks (VAD active=$enableVad)")
                    }
                }
            }
        }.start()
    }

    private fun calculateRms(buffer: ByteArray, readSize: Int): Float {
        var sum = 0.0
        val sampleCount = readSize / 2
        for (i in 0 until readSize - 1 step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val s = sample.toShort().toFloat()
            sum += s * s
        }
        return if (sampleCount > 0) sqrt(sum / sampleCount).toFloat() else 0f
    }

    fun stopCapture() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audioRecord", e)
        } finally {
            audioRecord = null
        }

        try {
            noiseSuppressor?.release()
            echoCanceler?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio effects", e)
        } finally {
            noiseSuppressor = null
            echoCanceler = null
        }
    }
}
