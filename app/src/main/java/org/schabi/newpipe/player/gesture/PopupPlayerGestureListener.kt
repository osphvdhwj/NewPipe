package org.schabi.newpipe.player.gesture

import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.player.ui.PopupPlayerUi
import kotlin.math.abs

class PopupPlayerGestureListener(
    private val playerUi: PopupPlayerUi,
) : BasePlayerGestureListener(playerUi), OnTouchListener {

    private var isMoving = false

    private var initialPopupX: Int = -1
    private var initialPopupY: Int = -1
    private var isResizing = false

    private var initPointerDistance = -1.0
    private var initFirstPointerX = -1f
    private var initFirstPointerY = -1f
    private var initSecPointerX = -1f
    private var initSecPointerY = -1f

    private var isHoldingFor2x = false
    private var holdStartTime = 0L
    private val holdToSpeedDelay = 500L

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        super.onTouch(v, event)
        if (event.pointerCount == 1 && !isMoving && !isResizing) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> if (isCenterAreaTouch(event)) {
                    holdStartTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_UP -> if (isHoldingFor2x) {
                    stopSpeedBoost()
                    return true
                }
            }
            if (event.action == MotionEvent.ACTION_MOVE && !isHoldingFor2x
                && holdStartTime > 0 && isCenterAreaTouch(event)) {
                val holdDuration = System.currentTimeMillis() - holdStartTime
                if (holdDuration >= holdToSpeedDelay) {
                    startSpeedBoost()
                }
            }
        }
        if (event.pointerCount == 2 && !isMoving && !isResizing) {
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
            return handleMultiDrag(event)
        }
        if (event.action == MotionEvent.ACTION_UP) {
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
        return event.x >= leftBound && event.x <= rightBound
                && event.y >= topBound && event.y <= bottomBound
    }

    private fun startSpeedBoost() {
        if (isHoldingFor2x) return
        isHoldingFor2x = true
        try {
            player.exoPlayer?.let { exoPlayer ->
                val currentParams = exoPlayer.playbackParameters
                val newParams = com.google.android.exoplayer2.PlaybackParameters(
                    2.0f, currentParams.pitch
                )
                exoPlayer.setPlaybackParameters(newParams)
            }
            showSpeedIndicator()
            val vib = player.context.getSystemService(
                android.content.Context.VIBRATOR_SERVICE
            ) as? android.os.Vibrator
            vib?.let {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    it.vibrate(
                        android.os.VibrationEffect.createOneShot(
                            25, android.os.VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(25)
                }
            }
        } catch (_: Exception) {
            // Ignore exceptions
        }
    }

    private fun stopSpeedBoost() {
        if (!isHoldingFor2x) return
        isHoldingFor2x = false
        try {
            player.exoPlayer?.let { exoPlayer ->
                val currentParams = exoPlayer.playbackParameters
                val newParams = com.google.android.exoplayer2.PlaybackParameters(
                    1.0f, currentParams.pitch
                )
                exoPlayer.setPlaybackParameters(newParams)
            }
            hideSpeedIndicator()
        } catch (_: Exception) {
            // Ignore exceptions
        }
    }

    private fun showSpeedIndicator() {
        try {
            playerUi.binding.fastSeekOverlay.animate(true, 150)
        } catch (_: Exception) {
            // Ignore exceptions
        }
    }

    private fun hideSpeedIndicator() {
        try {
            playerUi.binding.fastSeekOverlay.animate(false, 150)
        } catch (_: Exception) {
            // Ignore exceptions
        }
    }

    override fun onScrollEnd(event: MotionEvent) {
        super.onScrollEnd(event)
    }

    private fun handleMultiDrag(event: MotionEvent): Boolean {
        if (initPointerDistance == -1.0 || event.pointerCount != 2) return false
        val firstPointerMove = kotlin.math.hypot(
            (event.getX(0) - initFirstPointerX).toDouble(),
            (event.getY(0) - initFirstPointerY).toDouble()
        )
        val secPointerMove = kotlin.math.hypot(
            (event.getX(1) - initSecPointerX).toDouble(),
            (event.getY(1) - initSecPointerY).toDouble()
        )
        val minimumMove = android.view.ViewConfiguration.get(player.context).scaledTouchSlop
        if (kotlin.math.max(firstPointerMove, secPointerMove) <= minimumMove) return false
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
        playerUi.binding.loadingPanel.visibility = View.GONE
        playerUi.hideControls(0, 0)
        playerUi.binding.fastSeekOverlay.animate(false, 0)
        playerUi.binding.currentDisplaySeek.animate(false, 0, AnimationType.ALPHA, 0)
        if (isHoldingFor2x) {
            stopSpeedBoost()
        }
    }

    private fun onPopupResizingEnd() {
        // No-op
    }

    override fun onLongPress(e: MotionEvent) {
        if (!isResizing && !isMoving && isCenterAreaTouch(e)) {
            if (!isHoldingFor2x) {
                startSpeedBoost()
            }
        }
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return true
    }

    override fun onDownNotDoubleTapping(e: MotionEvent): Boolean {
        initialPopupX = 0
        initialPopupY = 0
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
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
        if (isHoldingFor2x) {
            stopSpeedBoost()
        }
        isMoving = true
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
        private const val TOSS_FLING_VELOCITY = 2500
    }
}
