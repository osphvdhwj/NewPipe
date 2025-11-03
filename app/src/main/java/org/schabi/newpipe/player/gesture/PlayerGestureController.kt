package org.schabi.newpipe.player.gesture

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible

/**
 * Enhanced gesture controller for NewPipe video player that eliminates UI loops
 * between control visibility and gesture detection. Inspired by YouTube's gesture behavior.
 *
 * Key improvements:
 * - Clean state machine prevents UI conflicts
 * - Proper touch event priority handling
 * - YouTube-like gesture precedence
 * - Eliminates control visibility loops
 */
class PlayerGestureController(
    private val context: Context,
    private val callbacks: PlayerGestureCallbacks
) {
    
    enum class ControlsState { VISIBLE, HIDDEN, LOCKED_HIDDEN }
    
    private var controlsState = ControlsState.HIDDEN
    private var isHoldingFor2x = false
    private val hideControlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    
    // Configuration - YouTube-like timings
    private val autoHideDelay = 3000L // 3 seconds like YouTube
    private val speedBoostValue = 2.0f
    private val longPressDelay = 350L // Responsive but avoids tap conflicts
    
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // Only handle tap if not holding for 2x - prevents conflicts
            if (!isHoldingFor2x) {
                toggleControlsVisibility()
                return true
            }
            return false
        }
        
        override fun onLongPress(e: MotionEvent) {
            // Start 2x speed - only if not already active
            if (!isHoldingFor2x && getDisplayPortion(e) == DisplayPortion.MIDDLE) {
                startSpeedBoost()
            }
        }
    })
    
    private var downX = 0f
    private var downY = 0f
    private val touchSlopPx by lazy {
        (context.resources.displayMetrics.density * 6).toInt()
    }
    
    fun handleTouchEvent(event: MotionEvent): Boolean {
        // Handle hold-to-2x logic first (higher priority than control toggles)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                // Start monitoring for potential long press
                gestureDetector.onTouchEvent(event)
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                // Cancel hold gesture if user moves finger (scrolling/swiping)
                if (kotlin.math.abs(event.x - downX) > touchSlopPx || 
                    kotlin.math.abs(event.y - downY) > touchSlopPx) {
                    cancelSpeedBoost()
                }
                gestureDetector.onTouchEvent(event)
                return true
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isHoldingFor2x) {
                    stopSpeedBoost()
                    return true // Consume the event - don't show controls after 2x
                }
                gestureDetector.onTouchEvent(event)
                return true
            }
            
            else -> {
                return gestureDetector.onTouchEvent(event)
            }
        }
    }
    
    private fun startSpeedBoost() {
        if (isHoldingFor2x) return
        
        isHoldingFor2x = true
        controlsState = ControlsState.LOCKED_HIDDEN
        
        // Hide controls immediately and lock them during 2x
        callbacks.hideControls()
        callbacks.showSpeedIndicator(speedBoostValue)
        callbacks.setPlaybackSpeed(speedBoostValue)
        callbacks.onHapticFeedback()
        
        // Cancel any pending hide operations
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
    }
    
    private fun stopSpeedBoost() {
        if (!isHoldingFor2x) return
        
        isHoldingFor2x = false
        controlsState = ControlsState.HIDDEN
        
        callbacks.hideSpeedIndicator()
        callbacks.setPlaybackSpeed(1.0f) // Reset to normal speed
        
        // Don't show controls immediately after 2x - YouTube behavior
        // User needs to tap again to show controls
    }
    
    private fun cancelSpeedBoost() {
        if (isHoldingFor2x) {
            stopSpeedBoost()
        }
    }
    
    private fun toggleControlsVisibility() {
        when (controlsState) {
            ControlsState.VISIBLE -> hideControls()
            ControlsState.HIDDEN -> showControls()
            ControlsState.LOCKED_HIDDEN -> {
                // Do nothing - controls are locked during 2x speed
            }
        }
    }
    
    private fun showControls() {
        if (controlsState == ControlsState.LOCKED_HIDDEN) return
        
        controlsState = ControlsState.VISIBLE
        callbacks.showControls()
        
        // Auto-hide after delay (YouTube behavior)
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
        hideControlsHandler.postDelayed(hideControlsRunnable, autoHideDelay)
    }
    
    private fun hideControls() {
        if (isHoldingFor2x) {
            controlsState = ControlsState.LOCKED_HIDDEN
        } else {
            controlsState = ControlsState.HIDDEN
        }
        
        callbacks.hideControls()
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
    }
    
    private fun getDisplayPortion(e: MotionEvent): DisplayPortion {
        return when {
            e.x < context.resources.displayMetrics.widthPixels / 3.0 -> DisplayPortion.LEFT
            e.x > context.resources.displayMetrics.widthPixels * 2.0 / 3.0 -> DisplayPortion.RIGHT
            else -> DisplayPortion.MIDDLE
        }
    }
    
    // Public API for external control
    fun forceHideControls() {
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
        hideControls()
    }
    
    fun isControlsVisible(): Boolean = controlsState == ControlsState.VISIBLE
    fun isHolding2x(): Boolean = isHoldingFor2x
    
    fun cleanup() {
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
        cancelSpeedBoost()
    }
}

/**
 * Callback interface for PlayerGestureController to communicate with the UI layer.
 * Implement this interface in your player UI class (MainPlayerUi, VideoPlayerUi, etc.)
 */
interface PlayerGestureCallbacks {
    fun showControls()
    fun hideControls()
    fun showSpeedIndicator(speed: Float)
    fun hideSpeedIndicator()
    fun setPlaybackSpeed(speed: Float)
    fun onHapticFeedback()
}