// MainPlayerUi.java Integration Example
// This shows the minimal changes needed to integrate the improved gesture controller

package org.schabi.newpipe.player.ui;

// ... existing imports ...
import org.schabi.newpipe.player.gesture.ImprovedMainPlayerGestureListener;

public final class MainPlayerUi extends VideoPlayerUi implements View.OnLayoutChangeListener {
    
    // ... existing fields ...
    
    @Override
    protected void setupElementsVisibility() {
        // ... existing setup code ...
        
        // OLD CODE:
        // gestureListener = new MainPlayerGestureListener(this);
        
        // NEW CODE - Replace with improved version:
        gestureListener = new ImprovedMainPlayerGestureListener(this);
        
        // ... rest of existing setup code ...
    }
    
    @Override
    public void destroy() {
        // Clean up improved gesture listener
        if (gestureListener instanceof ImprovedMainPlayerGestureListener) {
            ((ImprovedMainPlayerGestureListener) gestureListener).cleanup();
        }
        
        // ... existing cleanup code ...
        super.destroy();
    }
    
    // ... rest of existing MainPlayerUi code remains unchanged ...
}

/*
 * INTEGRATION NOTES:
 * 
 * 1. ONLY CHANGE: Replace MainPlayerGestureListener with ImprovedMainPlayerGestureListener
 * 2. ADD CLEANUP: Call cleanup() in destroy() method
 * 3. NO OTHER CHANGES NEEDED: All existing functionality is preserved
 * 
 * BENEFITS:
 * - Eliminates UI loops between control visibility and gestures
 * - YouTube-inspired hold-to-2x speed functionality  
 * - Proper state management prevents conflicts
 * - All existing volume/brightness gestures still work
 * - Auto-hide controls after 3 seconds like YouTube
 * 
 * TESTING:
 * - Single tap: Shows/hides controls (no loops)
 * - Long press: Activates 2x speed with visual indicator
 * - Controls stay hidden during 2x speed
 * - Volume/brightness gestures work normally
 * - Scrolling cancels 2x speed appropriately
 */