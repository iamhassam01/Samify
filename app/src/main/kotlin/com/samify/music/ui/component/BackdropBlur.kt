package com.samify.music.ui.component

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Adaptive backdrop blur that adjusts based on device performance
 * Uses minimal blur on capable devices, fallback to overlay on low-end devices
 */
@Composable
fun SamifyModalBlur(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    content: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    
    val canUseBlur = remember { 
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && 
        !isLowEndDevice(context)
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Background blur layer
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            if (canUseBlur) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .blur(radius = 8.dp)
                )
            } else {
                // Fallback for devices that don't support blur
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )
            }
        }
        
        // Content layer on top
        content()
    }
}

/**
 * Lightweight blur for better performance
 * Uses minimal 2dp blur radius
 */
@Composable
fun SamifyLightBlur(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    val canUseBlur = remember { 
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
        !isLowEndDevice(context)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .let { baseModifier ->
                if (canUseBlur) {
                    baseModifier.blur(radius = 2.dp)
                } else {
                    baseModifier
                }
            }
    ) {
        content()
    }
}

/**
 * Fake blur effect using gradient overlay
 * Best performance option that still provides visual depth
 */
@Composable
fun SamifyFakeBlur(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.2f),
                        Color.Black.copy(alpha = 0.4f),
                        Color.Black.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        content()
    }
}

/**
 * Modifier extension for easy blur application
 */
fun Modifier.samifyBlur(): Modifier = this.blur(radius = 3.dp)

fun Modifier.samifyLightBlur(): Modifier = this.blur(radius = 2.dp)

fun Modifier.samifyModalOverlay(): Modifier = this.background(Color.Black.copy(alpha = 0.35f))

/**
 * Check if device is low-end to optimize performance
 */
private fun isLowEndDevice(context: Context): Boolean {
    return try {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.isLowRamDevice || 
        Runtime.getRuntime().maxMemory() < 512 * 1024 * 1024 // Less than 512MB heap
    } catch (e: Exception) {
        // Fallback to assuming it's a low-end device if we can't determine
        true
    }
}

/**
 * Performance-aware blur that adapts based on device capabilities
 */
@Composable
fun AdaptiveBlurBox(
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
    overlayAlpha: Float = 0.35f,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    val effectiveBlur = remember { 
        blurEnabled && 
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
        !isLowEndDevice(context)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = overlayAlpha))
            .let { baseModifier ->
                if (effectiveBlur) {
                    baseModifier.blur(radius = 3.dp)
                } else {
                    baseModifier
                }
            }
    ) {
        content()
    }
}
