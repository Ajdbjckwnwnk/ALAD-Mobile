package com.alad.app.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alad.app.core.audio.AudioCaptureManager
import com.alad.app.core.audio.AudioPlayerManager
import com.alad.app.core.network.ALADWebSocketManager
import com.alad.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import kotlin.math.sqrt

class AudioDubbingForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "alad_dubbing_channel"
        private const val NOTIFICATION_ID = 101
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_CAPTURE_MODE = "EXTRA_CAPTURE_MODE"
        private const val TAG = "DubbingService"
        
        val isRunning = MutableStateFlow(false)
        val audioAmplitude = MutableStateFlow(0f)
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    
    private var mediaProjection: MediaProjection? = null
    private var audioCaptureManager: AudioCaptureManager? = null
    private var audioPlayerManager: AudioPlayerManager? = null
    private var webSocketManager: ALADWebSocketManager? = null
    private var langObservationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val captureMode = intent?.getStringExtra(EXTRA_CAPTURE_MODE) ?: "MIC"
        
        when (action) {
            ACTION_START -> {
                val notification = createNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val serviceType = if (captureMode == "SYSTEM") {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        } else {
                            0
                        }
                    }
                    if (serviceType != 0) {
                        startForeground(NOTIFICATION_ID, notification, serviceType)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                
                startDubbing(captureMode, resultCode, data)
            }
            ACTION_STOP -> {
                stopDubbing()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startDubbing(captureMode: String, resultCode: Int, data: Intent?) {
        isRunning.value = true
        if (captureMode == "SYSTEM" && data != null && resultCode != 0) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        }
        
        serviceScope.launch {
            val repository = UserPreferencesRepository(applicationContext)
            
            val apiKey = repository.apiKeyFlow.first()
            val targetLang = repository.targetLangFlow.first()
            val volumeRatio = repository.volumeRatioFlow.first()
            val filterNativeSpeech = repository.filterNativeSpeechFlow.first()
            val vadEnabled = repository.vadEnabledFlow.first()
            val noiseGateThreshold = repository.noiseGateThresholdFlow.first()
            val customWsUrl = repository.customWsUrlFlow.first()
            val proxyEnabled = repository.proxyEnabledFlow.first()
            val proxyHost = repository.proxyHostFlow.first()
            val proxyPort = repository.proxyPortFlow.first()
            val proxyType = repository.proxyTypeFlow.first()

            // Configure OkHttpClient with optional Proxy
            val clientBuilder = OkHttpClient.Builder()
                .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)

            if (proxyEnabled && proxyHost.isNotBlank() && proxyPort > 0) {
                try {
                    val pType = if (proxyType.equals("HTTP", ignoreCase = true)) {
                        java.net.Proxy.Type.HTTP
                    } else {
                        java.net.Proxy.Type.SOCKS
                    }
                    clientBuilder.proxy(java.net.Proxy(pType, java.net.InetSocketAddress(proxyHost.trim(), proxyPort)))
                    Log.d(TAG, "Configured proxy $proxyType://$proxyHost:$proxyPort")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to configure proxy", e)
                }
            }

            webSocketManager = ALADWebSocketManager(clientBuilder.build(), customWsUrl)
            
            audioPlayerManager = AudioPlayerManager(applicationContext)
            audioPlayerManager?.start()
            audioPlayerManager?.setVolume(volumeRatio)
            
            webSocketManager?.onStatusChanged = { status ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(applicationContext, "WS: $status", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            
            webSocketManager?.onBinaryMessageReceived = { audioChunk ->
                audioPlayerManager?.playAudioData(audioChunk)
            }
            
            webSocketManager?.connect(apiKey, "", targetLang, filterNativeSpeech)
            
            langObservationJob?.cancel()
            langObservationJob = serviceScope.launch {
                var firstEmit = true
                repository.targetLangFlow.collect { newLang ->
                    if (firstEmit) {
                        firstEmit = false
                    } else {
                        val currentKey = repository.apiKeyFlow.first()
                        val currentFilter = repository.filterNativeSpeechFlow.first()
                        webSocketManager?.disconnect()
                        webSocketManager?.connect(currentKey, "", newLang, currentFilter)
                    }
                }
            }
            
            audioCaptureManager = AudioCaptureManager()
            val appUid = applicationInfo.uid
            
            val onAudioChunk: (ByteArray) -> Unit = { pcmData ->
                webSocketManager?.sendAudioData(pcmData)
                
                // Calculate RMS amplitude for visualizer
                var sum = 0.0
                for (i in pcmData.indices step 2) {
                    if (i + 1 < pcmData.size) {
                        val sample = (pcmData[i].toInt() and 0xFF) or (pcmData[i+1].toInt() shl 8)
                        val signedSample = sample.toShort().toFloat()
                        sum += signedSample * signedSample
                    }
                }
                val rms = if (pcmData.isNotEmpty()) sqrt(sum / (pcmData.size / 2)).toFloat() else 0f
                val normalized = (rms / 32767f * 3f).coerceIn(0f, 1f)
                val current = audioAmplitude.value
                audioAmplitude.value = current * 0.5f + normalized * 0.5f
            }

            if (captureMode == "SYSTEM") {
                mediaProjection?.let { projection ->
                    audioCaptureManager?.startCaptureSystem(
                        projection,
                        appUid,
                        enableVad = vadEnabled,
                        noiseGateThreshold = noiseGateThreshold,
                        onAudioData = onAudioChunk
                    )
                }
            } else {
                audioCaptureManager?.startCaptureMic(
                    enableNoiseSuppression = true,
                    enableVad = vadEnabled,
                    noiseGateThreshold = noiseGateThreshold,
                    onAudioData = onAudioChunk
                )
            }
        }
    }

    private fun stopDubbing() {
        isRunning.value = false
        audioCaptureManager?.stopCapture()
        audioCaptureManager = null
        
        audioPlayerManager?.stop()
        audioPlayerManager = null
        
        webSocketManager?.disconnect()
        webSocketManager = null
        
        audioAmplitude.value = 0f
        
        langObservationJob?.cancel()
        langObservationJob = null
        
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRunning.value) {
            stopDubbing()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Live Dubbing Service"
            val descriptionText = "Capturing and streaming audio for dubbing"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, com.alad.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AudioDubbingForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this, 1, stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ALAD Live Dubbing")
            .setContentText("Connected and dubbing...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
