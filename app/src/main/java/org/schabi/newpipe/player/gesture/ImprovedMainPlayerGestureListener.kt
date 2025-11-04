package org.schabi.newpipe.player.gesture

import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.ProgressBar
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.ui.MainPlayerUi
import kotlin.math.abs

class ImprovedMainPlayerGestureListener(
    private val playerUi: MainPlayerUi
) : BasePlayerGestureListener(playerUi), OnTouchListener, PlayerGestureCallbacks {

    private var isMoving = false
    private var speedOverlay: android.widget.TextView? = null
    private val gestureController = PlayerGestureController(player.context, this)

    private var downX = 0f
    private var downY = 0f
    private val touchSlopPx by lazy { (player.context.resources.displayMetrics.density * 6).toInt() }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        super.onTouch(v, event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > touchSlopPx || abs(event.y - downY) > touchSlopPx) {
                    // movement cancels any long-press 2x internally
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isMoving) { isMoving = false; onScrollEnd(event) }
            }
        }
        if (gestureController.handleTouchEvent(event)) return true
        return when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> { v.parent?.requestDisallowInterceptTouchEvent(playerUi.isFullscreen); true }
            MotionEvent.ACTION_UP -> { v.parent?.requestDisallowInterceptTouchEvent(false); false }
            else -> true
        }
    }

    override fun onDown(e: MotionEvent): Boolean = super.onDown(e)

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean = false

    override fun onScroll(
        initialEvent: MotionEvent?,
        movingEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (initialEvent == null || !playerUi.isFullscreen) return false
        val statusBarHeight = org.schabi.newpipe.util.ThemeHelper.getAndroidDimenPx(player.context, "status_bar_height")
        val navigationBarHeight = org.schabi.newpipe.util.ThemeHelper.getAndroidDimenPx(player.context, "navigation_bar_height")
        val isTouchingStatusBar = initialEvent.y < statusBarHeight
        val isTouchingNavigationBar = initialEvent.y > (binding.root.height - navigationBarHeight)
        if (isTouchingStatusBar || isTouchingNavigationBar) return false
        val insideThreshold = kotlin.math.abs(movingEvent.y - initialEvent.y) <= MOVEMENT_THRESHOLD
        if (!isMoving && (insideThreshold || kotlin.math.abs(distanceX) > kotlin.math.abs(distanceY))) return false
        isMoving = true
        if (getDisplayHalfPortion(initialEvent) == DisplayPortion.RIGHT_HALF) {
            when (PlayerHelper.getActionForRightGestureSide(player.context)) {
                player.context.getString(R.string.volume_control_key) -> onScrollVolume(distanceY)
                player.context.getString(R.string.brightness_control_key) -> onScrollBrightness(distanceY)
            }
        } else {
            when (PlayerHelper.getActionForLeftGestureSide(player.context)) {
                player.context.getString(R.string.volume_control_key) -> onScrollVolume(distanceY)
                player.context.getString(R.string.brightness_control_key) -> onScrollBrightness(distanceY)
            }
        }
        return true
    }

    private fun onScrollVolume(distanceY: Float) {
        val bar: ProgressBar = binding.volumeProgressBar
        val audioReactor: AudioReactor = player.audioReactor
        if (!binding.volumeRelativeLayout.isShown) {
            val volumePercent: Float = audioReactor.volume / audioReactor.maxVolume.toFloat()
            bar.progress = (volumePercent * bar.max).toInt()
        }
        binding.volumeProgressBar.incrementProgressBy(distanceY.toInt())
        val currentProgressPercent: Float = bar.progress / bar.max.toFloat()
        val currentVolume = (audioReactor.maxVolume * currentProgressPercent).toInt()
        audioReactor.volume = currentVolume
        binding.volumeImageView.setImageDrawable(
            androidx.appcompat.content.res.AppCompatResources.getDrawable(
                player.context,
                when {
                    currentProgressPercent <= 0 -> R.drawable.ic_volume_off
                    currentProgressPercent < 0.25 -> R.drawable.ic_volume_mute
                    currentProgressPercent < 0.75 -> R.drawable.ic_volume_down
                    else -> R.drawable.ic_volume_up
                }
            )
        )
        if (!binding.volumeRelativeLayout.isShown) binding.volumeRelativeLayout.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
        binding.brightnessRelativeLayout.visibility = View.GONE
    }

    private fun onScrollBrightness(distanceY: Float) {
        val parent = player.context as? androidx.appcompat.app.AppCompatActivity ?: return
        val window = parent.window
        val layoutParams = window.attributes
        val bar: ProgressBar = binding.brightnessProgressBar
        val oldBrightness = layoutParams.screenBrightness
        bar.progress = (bar.max * oldBrightness.coerceIn(0f, 1f)).toInt()
        bar.incrementProgressBy(distanceY.toInt())
        val currentProgressPercent = bar.progress.toFloat() / bar.max
        layoutParams.screenBrightness = currentProgressPercent
        window.attributes = layoutParams
        PlayerHelper.setScreenBrightness(parent, currentProgressPercent)
        binding.brightnessImageView.setImageDrawable(
            androidx.appcompat.content.res.AppCompatResources.getDrawable(
                player.context,
                when {
                    currentProgressPercent < 0.25 -> R.drawable.ic_brightness_low
                    currentProgressPercent < 0.75 -> R.drawable.ic_brightness_medium
                    else -> R.drawable.ic_brightness_high
                }
            )
        )
        if (!binding.brightnessRelativeLayout.isShown) binding.brightnessRelativeLayout.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
        binding.volumeRelativeLayout.visibility = View.GONE
    }

    override fun onScrollEnd(event: MotionEvent) {
        super.onScrollEnd(event)
        if (binding.volumeRelativeLayout.isShown) binding.volumeRelativeLayout.animate(false, 200, AnimationType.SCALE_AND_ALPHA, 200)
        if (binding.brightnessRelativeLayout.isShown) binding.brightnessRelativeLayout.animate(false, 200, AnimationType.SCALE_AND_ALPHA, 200)
    }

    override fun getDisplayPortion(e: MotionEvent): DisplayPortion = when {
        e.x < binding.root.width / 3.0 -> DisplayPortion.LEFT
        e.x > binding.root.width * 2.0 / 3.0 -> DisplayPortion.RIGHT
        else -> DisplayPortion.MIDDLE
    }

    override fun getDisplayHalfPortion(e: MotionEvent): DisplayPortion = when {
        e.x < binding.root.width / 2.0 -> DisplayPortion.LEFT_HALF
        else -> DisplayPortion.RIGHT_HALF
    }

    override fun showControls() { playerUi.showControls(0); ensureControlButtonsVisible() }

    override fun hideControls() { playerUi.hideControls(0, 0) }

    private fun ensureControlButtonsVisible() {
        val binding = playerUi.binding
        binding.playPauseButton.visibility = View.VISIBLE
        binding.playPreviousButton.visibility = View.VISIBLE
        binding.playNextButton.visibility = View.VISIBLE
        if (binding.secondaryControls.visibility == View.VISIBLE) {
            binding.resizeTextView.visibility = View.VISIBLE
            binding.captionTextView.visibility = View.VISIBLE
            binding.share.visibility = View.VISIBLE
            binding.openInBrowser.visibility = View.VISIBLE
            binding.switchMute.visibility = View.VISIBLE
        }
    }

    override fun showSpeedIndicator(speed: Float) {
        val overlay = ensureSpeedOverlay(); overlay.text = "${speed.toInt()}×"; overlay.bringToFront(); overlay.animate().alpha(1.0f).setDuration(120).start()
    }

    override fun hideSpeedIndicator() { speedOverlay?.animate()?.alpha(0f)?.setDuration(120)?.start() }

    override fun setPlaybackSpeed(speed: Float) {
        player.exoPlayer?.let { exoPlayer ->
            val currentParams = exoPlayer.playbackParameters
            val newParams = com.google.android.exoplayer2.PlaybackParameters(speed, currentParams.pitch)
            exoPlayer.setPlaybackParameters(newParams)
        }
    }

    override fun onHapticFeedback() {
        val vib = player.context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        vib?.let { if (android.os.Build.VERSION.SDK_INT >= 26) it.vibrate(android.os.VibrationEffect.createOneShot(25, android.os.VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") it.vibrate(25) }
    }

    private fun ensureSpeedOverlay(): android.widget.TextView {
        speedOverlay?.let { return it }
        val context = player.context
        val overlay = android.widget.TextView(context).apply {
            text = "2×"; setTextColor(0xFFFFFFFF.toInt()); textSize = 14f; setPadding(32, 16, 32, 16)
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 32f; setColor(0x66000000) }
            alpha = 0.0f; isClickable = false; isFocusable = false; isFocusableInTouchMode = false; gravity = android.view.Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL; topMargin = (context.resources.displayMetrics.density * 16).toInt() }
        playerUi.binding.playerOverlays.addView(overlay, params)
        speedOverlay = overlay; return overlay
    }

    fun cleanup() { gestureController.cleanup() }

    companion object { private const val MOVEMENT_THRESHOLD = 40 }
}
