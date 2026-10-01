package com.alad.app.core.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.alad.app.ui.theme.*

class OverlayWidgetService : LifecycleService() {

    companion object {
        val isWidgetActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private var params: WindowManager.LayoutParams? = null
    
    private val savedStateRegistryOwner by lazy { ServiceSavedStateRegistryOwner(this) }
    private val viewModelStoreOwner by lazy { ServiceViewModelStoreOwner() }

    override fun onCreate() {
        super.onCreate()
        isWidgetActive.value = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ALADTheme {
                    OverlayContent(
                        onDrag = { dx, dy ->
                            params?.x = (params?.x ?: 0) + dx.toInt()
                            params?.y = (params?.y ?: 0) + dy.toInt()
                            windowManager.updateViewLayout(composeView, params)
                        },
                        onClose = { stopSelf() },
                        onToggle = { isCurrentlyRunning ->
                            if (isCurrentlyRunning) {
                                // Stop Dubbing
                                val intent = Intent(this@OverlayWidgetService, AudioDubbingForegroundService::class.java).apply {
                                    action = AudioDubbingForegroundService.ACTION_STOP
                                }
                                startService(intent)
                            } else {
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    val repo = com.alad.app.data.repository.UserPreferencesRepository(applicationContext)
                                    val mode = kotlinx.coroutines.flow.first(repo.captureModeFlow)
                                    if (mode == com.alad.app.data.repository.CaptureMode.SYSTEM) {
                                        val intent = Intent(this@OverlayWidgetService, com.alad.app.TransparentCaptureActivity::class.java)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        startActivity(intent)
                                    } else {
                                        val intent = Intent(this@OverlayWidgetService, AudioDubbingForegroundService::class.java).apply {
                                            action = AudioDubbingForegroundService.ACTION_START
                                            putExtra(AudioDubbingForegroundService.EXTRA_CAPTURE_MODE, "MIC")
                                        }
                                        androidx.core.content.ContextCompat.startForegroundService(this@OverlayWidgetService, intent)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
        
        // Setup ViewTree requirements for Compose in a Service
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        composeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)

        windowManager.addView(composeView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        isWidgetActive.value = false
        if (::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
    }
}

@Composable
fun OverlayContent(onDrag: (Float, Float) -> Unit, onClose: () -> Unit, onToggle: (Boolean) -> Unit) {
    val isRunning by AudioDubbingForegroundService.isRunning.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "widget_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        DeepSpace.copy(alpha = 0.85f),
                        GlassSurfaceDark.copy(alpha = 0.90f)
                    )
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    )
                ),
                shape = CircleShape
            )
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onToggle(isRunning)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle Button with Halo Glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(50.dp)
        ) {
            if (isRunning) {
                Box(
                    modifier = Modifier
                        .size((46 * pulseScale).dp)
                        .clip(CircleShape)
                        .background(NeonCoral.copy(alpha = 0.35f))
                )
            }

            IconButton(
                onClick = { 
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onToggle(isRunning) 
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            if (isRunning) {
                                listOf(NeonRose, NeonCoral)
                            } else {
                                listOf(NeonCyan, NeonBlue)
                            }
                        )
                    )
            ) {
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(Color.White, shape = RoundedCornerShape(3.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = { 
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClose() 
            },
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Widget",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
