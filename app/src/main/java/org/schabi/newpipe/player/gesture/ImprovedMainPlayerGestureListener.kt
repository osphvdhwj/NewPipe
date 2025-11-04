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
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.util.ThemeHelper.getAndroidDimenPx
import kotlin.math.abs

class ImprovedMainPlayerGestureListener(
    private val playerUi: MainPlayerUi
) : BasePlayerGestureListener(playerUi), OnTouchListener, PlayerGestureCallbacks {

    private var isMoving = false
    private var speedOverlay: TextView? = null
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

    override fun onDown(e: MotionEvent): Boolean {
        if (DEBUG) Log.d(TAG, "onDown called with e = [$e]")
        return super.onDown(e)
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean = false

    override fun onScroll(
        initialEvent: MotionEvent?,
        movingEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (initialEvent == null || !playerUi.isFullscreen) return false
        val statusBarHeight = getAndroidDimenPx(player.context, "status_bar_height")
        val navigationBarHeight = getAndroidDimenPx(player.context, "navigation_bar_height")
        val isTouchingStatusBar = initialEvent.y < statusBarHeight
        val isTouchingNavigationBar = initialEvent.y > (binding.root.height - navigationBarHeight)
        if (isTouchingStatusBar || isTouchingNavigationBar) return false
        val insideThreshold = abs(movingEvent.y - initialEvent.y) <= MOVEMENT_THRESHOLD
        if (!isMoving && (insideThreshold || abs(distanceX) > abs(distanceY))) return false
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
        if (!binding.volumeRelativeLayout.isVisible) {
            val volumePercent: Float = audioReactor.volume / audioReactor.maxVolume.toFloat()
            bar.progress = (volumePercent * bar.max).toInt()
        }
        binding.volumeProgressBar.incrementProgressBy(distanceY.toInt())
        val currentProgressPercent: Float = bar.progress / bar.max.toFloat()
        val currentVolume = (audioReactor.maxVolume * currentProgressPercent).toInt()
        audioReactor.volume = currentVolume
        if (DEBUG) Log.d(TAG, "onScroll().volumeControl, currentVolume = $currentVolume")
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
        if (!binding.volumeRelativeLayout.isVisible) binding.volumeRelativeLayout.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
        binding.brightnessRelativeLayout.isVisible = false
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
        if (DEBUG) Log.d(TAG, "onScroll().brightnessControl, currentBrightness = $currentProgressPercent")
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
        if (!binding.brightnessRelativeLayout.isVisible) binding.brightnessRelativeLayout.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
        binding.volumeRelativeLayout.isVisible = false
    }

    override fun onScrollEnd(event: MotionEvent) {
        super.onScrollEnd(event)
        if (binding.volumeRelativeLayout.isVisible) binding.volumeRelativeLayout.animate(false, 200, AnimationType.SCALE_AND_ALPHA, 200)
        if (binding.brightnessRelativeLayout.isVisible) binding.brightnessRelativeLayout.animate(false, 200, AnimationType.SCALE_AND_ALPHA, 200)
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

    override fun showControls() {
        playerUi.showControls(0)
        ensureControlButtonsVisible()
    }

    override fun hideControls() { playerUi.hideControls(0, 0) }

    private fun ensureControlButtonsVisible() {
        try {
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
        } catch (e: Exception) { if (DEBUG) Log.e(TAG, "Error ensuring control buttons visible", e) }
    }

    override fun showSpeedIndicator(speed: Float) {
        val overlay = ensureSpeedOverlay()
        overlay.text = "${speed.toInt()}×"
        overlay.bringToFront()
        overlay.animate().alpha(1.0f).setDuration(120).start()
    }

    override fun hideSpeedIndicator() { speedOverlay?.animate()?.alpha(0f)?.setDuration(120)?.start() }

    override fun setPlaybackSpeed(speed: Float) {
        try {
            player.exoPlayer?.let { exoPlayer ->
                val currentParams = exoPlayer.playbackParameters
                val newParams = PlaybackParameters(speed, currentParams.pitch)
                exoPlayer.setPlaybackParameters(newParams)
            }
        } catch (e: Exception) { if (DEBUG) Log.e(TAG, "Error setting playback speed", e) }
    }

    override fun onHapticFeedback() {
        try {
            val vib = player.context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            vib?.let {
                if (android.os.Build.VERSION.SDK_INT >= 26) it.vibrate(android.os.VibrationEffect.createOneShot(25, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") it.vibrate(25)
            }
        } catch (_: Exception) { }
    }

    private fun ensureSpeedOverlay(): TextView {
        speedOverlay?.let { return it }
        val context = player.context
        val overlay = TextView(context).apply {
            text = "2×"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setPadding(32, 16, 32, 16)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 32f
                setColor(0x66000000)
            }
            alpha = 0.0f
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = (context.resources.displayMetrics.density * 16).toInt()
        }
        playerUi.binding.playerOverlays.addView(overlay, params)
        speedOverlay = overlay
        return overlay
    }

    fun cleanup() { gestureController.cleanup() }

    companion object {
        private val TAG = ImprovedMainPlayerGestureListener::class.java.simpleName
        private val DEBUG = MainActivity.DEBUG
        private const val MOVEMENT_THRESHOLD = 40
    }
}
