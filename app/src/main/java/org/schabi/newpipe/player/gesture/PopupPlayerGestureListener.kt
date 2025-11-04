package org.schabi.newpipe.player.gesture

import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.exoplayer2.PlaybackParameters
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.gesture.DisplayPortion.MIDDLE
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.util.ThemeHelper.getAndroidDimenPx
import kotlin.math.abs

class PopupPlayerGestureListener(
    private val playerUi: MainPlayerUi,
) : BasePlayerGestureListener(playerUi), OnTouchListener {

    private var isMoving = false

    private var initialPopupX: Int = -1
    private var initialPopupY: Int = -1
    private var isResizing = false

    // initial coordinates and distance between fingers
    private var initPointerDistance = -1.0
    private var initFirstPointerX = -1f
    private var initFirstPointerY = -1f
    private var initSecPointerX = -1f
    private var initSecPointerY = -1f

    // Hold-to-2x speed functionality for popup mode
    private var isHoldingFor2x = false
    private var holdStartTime = 0L
    private val holdToSpeedDelay = 500L // Longer delay to avoid conflicts with drag/resize

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        super.onTouch(v, event)

        // Handle hold-to-2x speed for center area single touch
        if (event.pointerCount == 1 && !isMoving && !isResizing) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isCenterAreaTouch(event)) { // safe center area
                        holdStartTime = System.currentTimeMillis()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isHoldingFor2x) {
                        stopSpeedBoost()
                        return true
                    }
                }
            }
            if (event.action == MotionEvent.ACTION_MOVE && !isHoldingFor2x &&
                holdStartTime > 0 && isCenterAreaTouch(event)
            ) {
                val holdDuration = System.currentTimeMillis() - holdStartTime
                if (holdDuration >= holdToSpeedDelay) {
                    startSpeedBoost()
                }
            }
        }

        if (event.pointerCount == 2 && !isMoving && !isResizing) {
            if (DEBUG) {
                Log.d(TAG, "onTouch() 2 finger pointer detected, enabling resizing.")
            }
            onPopupResizingStart()

            initFirstPointerX = event.getX(0)
            initFirstPointerY = event.getY(0)
            initSecPointerX = event.getX(1)
            initSecPointerY = event.getY(1)
            initPointerDistance = kotlin.math.hypot(
                (initFirstPointerX - initSecPointerX).toDouble(),
                (initFirstPointerY - initSecPointerY).toDouble()
            )
            isResizing = true
        }
        if (event.action == MotionEvent.ACTION_MOVE && !isMoving && isResizing) {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "onTouch() ACTION_MOVE > v = [$v], e1.getRaw =[${event.rawX}, ${event.rawY}]"
                )
            }
            return handleMultiDrag(event)
        }
        if (event.action == MotionEvent.ACTION_UP) {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "onTouch() ACTION_UP > v = [$v], e1.getRaw = [${event.rawX}, ${event.rawY}]"
                )
            }
            if (isMoving) {
                isMoving = false
                onScrollEnd(event)
            }
            if (isResizing) {
                isResizing = false
                initPointerDistance = (-1).toDouble()
                initFirstPointerX = (-1).toFloat()
                initFirstPointerY = (-1).toFloat()
                initSecPointerX = (-1).toFloat()
                initSecPointerY = (-1).toFloat()
                onPopupResizingEnd()
                player.changeState(player.currentState)
            }
            if (!playerUi.isPopupClosing) {
                playerUi.savePopupPositionAndSizeToPrefs()
            }
            holdStartTime = 0L
        }

        v.performClick()
        return true
    }

    private fun isCenterAreaTouch(event: MotionEvent): Boolean {
        val width = playerUi.popupLayoutParams.width
        val height = playerUi.popupLayoutParams.height
        val safeMargin = 0.2f
        val leftBound = width * safeMargin
        val rightBound = width * (1 - safeMargin)
        val topBound = height * safeMargin
        val bottomBound = height * (1 - safeMargin)
        return event.x >= leftBound && event.x <= rightBound &&
            event.y >= topBound && event.y <= bottomBound
    }

    private fun startSpeedBoost() {
        if (isHoldingFor2x) return
        isHoldingFor2x = true
        try {
            player.exoPlayer?.let { exoPlayer ->
                val currentParams = exoPlayer.playbackParameters
                val newParams = com.google.android.exoplayer2.PlaybackParameters(2.0f, currentParams.pitch)
                exoPlayer.setPlaybackParameters(newParams)
            }
            showSpeedIndicator()
            val vib = player.context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            vib?.let {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    it.vibrate(android.os.VibrationEffect.createOneShot(25, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION") it.vibrate(25)
                }
            }
            if (DEBUG) Log.d(TAG, "Started 2x speed boost in popup mode")
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Error starting speed boost in popup", e)
        }
    }

    private fun stopSpeedBoost() {
        if (!isHoldingFor2x) return
        isHoldingFor2x = false
        try {
            player.exoPlayer?.let { exoPlayer ->
                val currentParams = exoPlayer.playbackParameters
                val newParams = com.google.android.exoplayer2.PlaybackParameters(1.0f, currentParams.pitch)
                exoPlayer.setPlaybackParameters(newParams)
            }
            hideSpeedIndicator()
            if (DEBUG) Log.d(TAG, "Stopped 2x speed boost in popup mode")
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Error stopping speed boost in popup", e)
        }
    }

    private fun showSpeedIndicator() {
        try {
            binding.fastSeekOverlay.animate(true, 150)
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Error showing speed indicator", e)
        }
    }

    private fun hideSpeedIndicator() {
        try {
            binding.fastSeekOverlay.animate(false, 150)
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Error hiding speed indicator", e)
        }
    }

    override fun onScrollEnd(event: MotionEvent) {
        super.onScrollEnd(event)
        if (playerUi.isInsideClosingRadius(event)) {
            playerUi.closePopup()
        } else if (!playerUi.isPopupClosing) {
            playerUi.closeOverlayBinding.closeButton.animate(false, 200)
            binding.closingOverlay.animate(false, 200)
        }
    }

    private fun handleMultiDrag(event: MotionEvent): Boolean {
        if (initPointerDistance == -1.0 || event.pointerCount != 2) {
            return false
        }
        val firstPointerMove = kotlin.math.hypot(
            (event.getX(0) - initFirstPointerX).toDouble(),
            (event.getY(0) - initFirstPointerY).toDouble()
        )
        val secPointerMove = kotlin.math.hypot(
            (event.getX(1) - initSecPointerX).toDouble(),
            (event.getY(1) - initSecPointerY).toDouble()
        )
        val minimumMove = android.view.ViewConfiguration.get(player.context).scaledTouchSlop
        if (kotlin.math.max(firstPointerMove, secPointerMove) <= minimumMove) {
            return false
        }
        val currentPointerDistance = kotlin.math.hypot(
            (event.getX(0) - event.getX(1)).toDouble(),
            (event.getY(0) - event.getY(1)).toDouble()
        )
        val popupWidth = playerUi.popupLayoutParams.width.toDouble()
        val newWidth = popupWidth * currentPointerDistance / initPointerDistance
        initPointerDistance = currentPointerDistance
        playerUi.popupLayoutParams.x += ((popupWidth - newWidth) / 2.0).toInt()
        playerUi.checkPopupPositionBounds()
        playerUi.updateScreenSize()
        playerUi.changePopupSize(kotlin.math.min(playerUi.screenWidth.toDouble(), newWidth).toInt())
        return true
    }

    private fun onPopupResizingStart() {
        if (DEBUG) Log.d(TAG, "onPopupResizingStart called")
        binding.loadingPanel.visibility = View.GONE
        playerUi.hideControls(0, 0)
        binding.fastSeekOverlay.animate(false, 0)
        binding.currentDisplaySeek.animate(false, 0, AnimationType.ALPHA, 0)
        if (isHoldingFor2x) stopSpeedBoost()
    }

    private fun onPopupResizingEnd() {
        if (DEBUG) Log.d(TAG, "onPopupResizingEnd called")
    }

    override fun onLongPress(e: MotionEvent) {
        if (!isResizing && !isMoving && getDisplayPortion(e) == MIDDLE && isCenterAreaTouch(e)) {
            if (!isHoldingFor2x) startSpeedBoost()
        } else {
            playerUi.updateScreenSize()
            playerUi.checkPopupPositionBounds()
            playerUi.changePopupSize(playerUi.screenWidth)
        }
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return if (player.popupPlayerSelected()) {
            val absVelocityX = kotlin.math.abs(velocityX)
            val absVelocityY = kotlin.math.abs(velocityY)
            if (absVelocityX.coerceAtLeast(absVelocityY) > TOSS_FLING_VELOCITY) {
                if (absVelocityX > TOSS_FLING_VELOCITY) {
                    playerUi.popupLayoutParams.x = velocityX.toInt()
                }
                if (absVelocityY > TOSS_FLING_VELOCITY) {
                    playerUi.popupLayoutParams.y = velocityY.toInt()
                }
                playerUi.checkPopupPositionBounds()
                playerUi.windowManager.updateViewLayout(binding.root, playerUi.popupLayoutParams)
                return true
            }
            return false
        } else {
            true
        }
    }

    override fun onDownNotDoubleTapping(e: MotionEvent): Boolean {
        playerUi.updateScreenSize()
        playerUi.checkPopupPositionBounds()
        playerUi.popupLayoutParams.let {
            initialPopupX = it.x
            initialPopupY = it.y
        }
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (DEBUG) Log.d(TAG, "onSingleTapConfirmed() called with: e = [$e]")
        if (isDoubleTapping) return true
        if (player.exoPlayerIsNull()) return false
        onSingleTap()
        return true
    }

    override fun onScroll(
        initialEvent: MotionEvent?,
        movingEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (initialEvent == null) return false
        if (isResizing) return super.onScroll(initialEvent, movingEvent, distanceX, distanceY)
        if (isHoldingFor2x) stopSpeedBoost()
        if (!isMoving) {
            playerUi.closeOverlayBinding.closeButton.animate(true, 200)
        }
        isMoving = true
        val diffX = (movingEvent.rawX - initialEvent.rawX)
        val posX = (initialPopupX + diffX).coerceIn(
            0f,
            (playerUi.screenWidth - playerUi.popupLayoutParams.width).toFloat().coerceAtLeast(0f)
        )
        val diffY = (movingEvent.rawY - initialEvent.rawY)
        val posY = (initialPopupY + diffY).coerceIn(
            0f,
            (playerUi.screenHeight - playerUi.popupLayoutParams.height).toFloat().coerceAtLeast(0f)
        )
        playerUi.popupLayoutParams.x = posX.toInt()
        playerUi.popupLayoutParams.y = posY.toInt()
        val showClosingOverlayView: Boolean = playerUi.isInsideClosingRadius(movingEvent)
        if (binding.closingOverlay.isVisible != showClosingOverlayView) {
            binding.closingOverlay.animate(showClosingOverlayView, 200)
        }
        playerUi.windowManager.updateViewLayout(binding.root, playerUi.popupLayoutParams)
        return true
    }

    override fun getDisplayPortion(e: MotionEvent): DisplayPortion = when {
        e.x < playerUi.popupLayoutParams.width / 3.0 -> DisplayPortion.LEFT
        e.x > playerUi.popupLayoutParams.width * 2.0 / 3.0 -> DisplayPortion.RIGHT
        else -> DisplayPortion.MIDDLE
    }

    override fun getDisplayHalfPortion(e: MotionEvent): DisplayPortion = when {
        e.x < playerUi.popupLayoutParams.width / 2.0 -> DisplayPortion.LEFT_HALF
        else -> DisplayPortion.RIGHT_HALF
    }

    companion object {
        private val TAG = PopupPlayerGestureListener::class.java.simpleName
        private val DEBUG = MainActivity.DEBUG
        private const val TOSS_FLING_VELOCITY = 2500
    }
}
