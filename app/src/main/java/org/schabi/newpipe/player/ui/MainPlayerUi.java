package org.schabi.newpipe.player.ui;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static org.schabi.newpipe.MainActivity.DEBUG;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_NONE;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP;
import static org.schabi.newpipe.player.helper.PlayerHelper.getMinimizeOnExitAction;
import static org.schabi.newpipe.player.helper.PlayerHelper.globalScreenOrientationLocked;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PAUSE;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ItemTouchHelper;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.info_list.StreamSegmentAdapter;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener;
import org.schabi.newpipe.player.gesture.ImprovedMainPlayerGestureListener;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.playqueue.PlayQueueAdapter;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.views.DraggableFitTextView;

import java.util.Objects;
import java.util.Optional;

public final class MainPlayerUi extends VideoPlayerUi implements View.OnLayoutChangeListener {
    private static final String TAG = MainPlayerUi.class.getSimpleName();

    private static final int DETAIL_ROOT_MINIMUM_HEIGHT = 85; // dp
    private static final int DETAIL_TITLE_TEXT_SIZE_TV = 16; // sp
    private static final int DETAIL_TITLE_TEXT_SIZE_TABLET = 15; // sp

    private boolean isFullscreen = false;
    private boolean isVerticalVideo = false;
    private boolean fragmentIsVisible = false;

    private ContentObserver settingsContentObserver;

    private PlayQueueAdapter playQueueAdapter;
    private StreamSegmentAdapter segmentAdapter;
    private boolean isQueueVisible = false;
    private boolean areSegmentsVisible = false;

    private ItemTouchHelper itemTouchHelper;

    private ImprovedMainPlayerGestureListener improvedGestureListener;

    public MainPlayerUi(@NonNull final Player player,
                        @NonNull final PlayerBinding playerBinding) {
        super(player, playerBinding);
    }

    private void directlyOpenFullscreenIfNeeded() {
        if (PlayerHelper.isStartMainPlayerFullscreenEnabled(player.getService())
                && DeviceUtils.isTablet(player.getService())
                && PlayerHelper.globalScreenOrientationLocked(player.getService())) {
            player.getFragmentListener().ifPresent(
                    PlayerServiceEventListener::onScreenRotationButtonClicked);
        }
    }

    @Override
    public void setupAfterIntent() {
        directlyOpenFullscreenIfNeeded();
        super.setupAfterIntent();
        initVideoPlayer();
        binding.playPauseButton.requestFocus();
        setupDraggableFitLabel();
        if (player.getPlayWhenReady()) player.play(); else player.pause();
    }

    @Override
    BasePlayerGestureListener buildGestureListener() {
        improvedGestureListener = new ImprovedMainPlayerGestureListener(this);
        return improvedGestureListener;
    }

    @Override
    protected void initListeners() {
        super.initListeners();
        binding.screenRotationButton.setOnClickListener(makeOnClickListener(() -> {
            if (!isVerticalVideo || (isLandscape() && globalScreenOrientationLocked(context))) {
                player.getFragmentListener().ifPresent(PlayerServiceEventListener::onScreenRotationButtonClicked);
            } else {
                toggleFullscreen();
            }
        }));
        binding.queueButton.setOnClickListener(v -> onQueueClicked());
        binding.segmentsButton.setOnClickListener(v -> onSegmentsClicked());
        binding.addToPlaylistButton.setOnClickListener(v ->
                getParentActivity().map(FragmentActivity::getSupportFragmentManager)
                        .ifPresent(fragmentManager -> PlaylistDialog.showForPlayQueue(player, fragmentManager)));
        settingsContentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(final boolean selfChange) { setupScreenRotationButton(); }
        };
        context.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false,
                settingsContentObserver);
        binding.getRoot().addOnLayoutChangeListener(this);
        binding.moreOptionsButton.setOnLongClickListener(v -> {
            player.getFragmentListener().ifPresent(PlayerServiceEventListener::onMoreOptionsLongClicked);
            hideControls(0, 0); hideSystemUIIfNeeded(); return true;
        });
    }

    @Override
    protected void deinitListeners() {
        super.deinitListeners();
        binding.queueButton.setOnClickListener(null);
        binding.segmentsButton.setOnClickListener(null);
        binding.addToPlaylistButton.setOnClickListener(null);
        context.getContentResolver().unregisterContentObserver(settingsContentObserver);
        binding.getRoot().removeOnLayoutChangeListener(this);
        if (improvedGestureListener != null) improvedGestureListener.cleanup();
    }

    @Override
    public void initPlayback() {
        super.initPlayback();
        if (playQueueAdapter != null) playQueueAdapter.dispose();
        playQueueAdapter = new PlayQueueAdapter(context, Objects.requireNonNull(player.getPlayQueue()));
        segmentAdapter = new StreamSegmentAdapter(getStreamSegmentListener());
    }

    @Override
    public void removeViewFromParent() {
        final ViewParent parent = binding.getRoot().getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(binding.getRoot());
    }

    @Override
    public void destroy() {
        super.destroy();
        if (isFullscreen) toggleFullscreen();
        removeViewFromParent();
    }

    @Override
    public void destroyPlayer() {
        super.destroyPlayer();
        if (playQueueAdapter != null) { playQueueAdapter.unsetSelectedListener(); playQueueAdapter.dispose(); }
    }

    @Override
    public void smoothStopForImmediateReusing() {
        super.smoothStopForImmediateReusing();
        hideControls(0, 0);
        closeItemsList();
    }

    private void initVideoPlayer() {
        setResizeMode(PlayerHelper.retrieveResizeModeFromPrefs(player));
        binding.getRoot().setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    private void setupDraggableFitLabel() {
        try {
            if (binding.resizeTextView instanceof DraggableFitTextView) {
                // no-op, the custom view handles dragging
                if (DEBUG) Log.d(TAG, "Draggable fit label initialized successfully");
            }
        } catch (final Exception e) { if (DEBUG) Log.e(TAG, "Error setting up draggable fit label", e); }
    }

    @Override
    protected void setupElementsVisibility() {
        super.setupElementsVisibility();
        closeItemsList();
        showHideKodiButton();
        binding.fullScreenButton.setVisibility(View.GONE);
        setupScreenRotationButton();
        binding.resizeTextView.setVisibility(View.VISIBLE);
        binding.getRoot().findViewById(R.id.metadataView).setVisibility(View.VISIBLE);
        binding.moreOptionsButton.setVisibility(View.VISIBLE);
        binding.topControls.setOrientation(LinearLayout.VERTICAL);
        binding.primaryControls.getLayoutParams().width = MATCH_PARENT;
        binding.secondaryControls.setVisibility(View.INVISIBLE);
        binding.moreOptionsButton.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_expand_more));
        binding.share.setVisibility(View.VISIBLE);
        binding.openInBrowser.setVisibility(View.VISIBLE);
        binding.switchMute.setVisibility(View.VISIBLE);
        binding.playerCloseButton.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        binding.metadataView.setVisibility(isFullscreen ? View.VISIBLE : View.GONE);
        binding.audioTrackTextView.setMaxWidth(Integer.MAX_VALUE);
        ensureControlButtonsVisible();
    }

    private void ensureControlButtonsVisible() {
        try {
            binding.playPauseButton.setVisibility(View.VISIBLE);
            binding.playPreviousButton.setVisibility(View.VISIBLE);
            binding.playNextButton.setVisibility(View.VISIBLE);
            binding.playbackSeekBar.setVisibility(View.VISIBLE);
            binding.playbackCurrentTime.setVisibility(View.VISIBLE);
            binding.playbackEndTime.setVisibility(View.VISIBLE);
        } catch (final Exception e) { if (DEBUG) Log.e(TAG, "Error ensuring control buttons visible", e); }
    }

    @Override
    public void showControls(final long duration) {
        super.showControls(duration);
        ensureControlButtonsVisible();
        showOrHideButtons();
    }

    @Override
    protected void setupElementsSize(final Resources resources) {
        setupElementsSize(
                resources.getDimensionPixelSize(R.dimen.player_main_buttons_min_width),
                resources.getDimensionPixelSize(R.dimen.player_main_top_padding),
                resources.getDimensionPixelSize(R.dimen.player_main_controls_padding),
                resources.getDimensionPixelSize(R.dimen.player_main_buttons_padding)
        );
    }

    @Override
    public void onBroadcastReceived(final Intent intent) {
        super.onBroadcastReceived(intent);
        if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            closeItemsList();
        } else if (ACTION_PLAY_PAUSE.equals(intent.getAction())) {
            if (!fragmentIsVisible) onFragmentStopped();
        } else if (VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED.equals(intent.getAction())) {
            fragmentIsVisible = false; onFragmentStopped();
        } else if (VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED.equals(intent.getAction())) {
            fragmentIsVisible = true; player.useVideoSource(true);
            if (!isControlsVisible()) hideSystemUIIfNeeded();
        }
    }

    @Override
    public void onFragmentListenerSet() {
        super.onFragmentListenerSet();
        fragmentIsVisible = true;
        if (!isFullscreen) binding.playbackControlRoot.setPadding(0, 0, 0, 0);
        binding.itemsListPanel.setPadding(0, 0, 0, 0);
        player.getFragmentListener().ifPresent(PlayerServiceEventListener::onViewCreated);
    }

    private void onFragmentStopped() {
        if (player.isPlaying() || player.isLoading()) {
            switch (getMinimizeOnExitAction(context)) {
                case MINIMIZE_ON_EXIT_MODE_BACKGROUND: player.useVideoSource(false); break;
                case MINIMIZE_ON_EXIT_MODE_POPUP:
                    getParentActivity().ifPresent(activity -> {
                        player.setRecovery();
                        NavigationHelper.playOnPopupPlayer(activity, player.getPlayQueue(), true);
                    });
                    break;
                case MINIMIZE_ON_EXIT_MODE_NONE:
                default: player.pause(); break;
            }
        }
    }

    // ===== Added API for Java call sites =====

    public boolean isVerticalVideo() { return isVerticalVideo; }

    public boolean isLandscape() {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        // Delegate to existing UI hooks
        if (isFullscreen) {
            showSystemUIPartially();
        } else {
            hideSystemUIIfNeeded();
        }
        setupElementsVisibility();
    }

    public void closeItemsList() {
        // Safely hide queue/segments panels if present
        try {
            binding.itemsListPanel.setVisibility(View.GONE);
            isQueueVisible = false; areSegmentsVisible = false;
        } catch (final Exception ignored) { }
    }

    public void showHideKodiButton() {
        // Keep visible for now; original logic can be reintroduced
        try { binding.playWithKodi.setVisibility(View.VISIBLE); } catch (final Exception ignored) { }
    }

    public void setupScreenRotationButton() {
        // Placeholder: original logic can set icon/alpha based on rotation settings
        try { binding.screenRotationButton.setVisibility(View.VISIBLE); } catch (final Exception ignored) { }
    }

    public void checkLandscape() {
        if (isFullscreen && !isLandscape()) toggleFullscreen();
    }

    public Optional<FragmentActivity> getParentActivity() {
        return player.getFragmentListener().flatMap(PlayerServiceEventListener::getActivity);
    }

    public StreamSegmentAdapter.StreamSegmentListener getStreamSegmentListener() {
        // Provide a safe no-op listener; replace with actual implementation if needed
        return (segment, clickType) -> { /* no-op for now */ };
    }

    @Override
    protected void setupSubtitleView(final float captionScale) {
        final com.google.android.exoplayer2.ui.CaptionStyleCompat style =
                PlayerHelper.getCaptionStyle(context);
        binding.subtitleView.setApplyEmbeddedStyles(style == com.google.android.exoplayer2.ui.CaptionStyleCompat.DEFAULT);
        binding.subtitleView.setStyle(style);
        // captionScale can be applied to text size if desired; keep default for now
    }
}
