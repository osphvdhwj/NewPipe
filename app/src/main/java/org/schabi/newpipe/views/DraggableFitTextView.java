package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A draggable text view implementation for the "Fit" label, inspired by AVES Gallery.
 * This view can be freely moved around the screen by dragging, providing user flexibility
 * in positioning the resize control.
 */
public class DraggableFitTextView extends NewPipeTextView implements View.OnTouchListener {

    private float lastTouchX;
    private float lastTouchY;
    private float dX;
    private float dY;
    private boolean isDragging = false;
    private boolean hasMoved = false;

    // Boundaries to prevent dragging offscreen
    private int minX = 0;
    private int maxX = Integer.MAX_VALUE;
    private int minY = 0;
    private int maxY = Integer.MAX_VALUE;

    public DraggableFitTextView(@NonNull final Context context) {
        super(context);
        init();
    }

    public DraggableFitTextView(@NonNull final Context context, 
                                @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DraggableFitTextView(@NonNull final Context context,
                               @Nullable final AttributeSet attrs,
                               final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOnTouchListener(this);
        // Make the view clickable and focusable for better interaction
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    public boolean onTouch(final View view, final MotionEvent event) {
        final ViewParent parent = getParent();
        if (parent != null) {
            // Request that the parent not intercept touch events during drag
            parent.requestDisallowInterceptTouchEvent(true);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getRawX();
                lastTouchY = event.getRawY();
                dX = view.getX() - event.getRawX();
                dY = view.getY() - event.getRawY();
                isDragging = false;
                hasMoved = false;
                updateBoundaries();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!isDragging) {
                    // Check if this is a drag gesture (moved beyond touch slop)
                    final float deltaX = Math.abs(event.getRawX() - lastTouchX);
                    final float deltaY = Math.abs(event.getRawY() - lastTouchY);
                    final int touchSlop = android.view.ViewConfiguration
                            .get(getContext()).getScaledTouchSlop();

                    if (deltaX > touchSlop || deltaY > touchSlop) {
                        isDragging = true;
                        hasMoved = true;
                    }
                }

                if (isDragging) {
                    final float newX = event.getRawX() + dX;
                    final float newY = event.getRawY() + dY;

                    // Apply boundaries to prevent dragging offscreen
                    final float constrainedX = Math.max(minX, 
                            Math.min(maxX - getWidth(), newX));
                    final float constrainedY = Math.max(minY, 
                            Math.min(maxY - getHeight(), newY));

                    view.setX(constrainedX);
                    view.setY(constrainedY);
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(false);
                }

                if (!hasMoved) {
                    // This was a click, not a drag - perform click action
                    performClick();
                }

                isDragging = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(false);
                }
                isDragging = false;
                return true;

            default:
                return false;
        }
    }

    @Override
    public boolean performClick() {
        // Call super to handle accessibility
        super.performClick();

        // Only perform the actual click action if we didn't drag
        if (!hasMoved) {
            // This will trigger any click listeners set on the view
            return true;
        }

        return false;
    }

    /**
     * Update the boundaries based on the parent view size.
     * This prevents the draggable view from being moved offscreen.
     */
    private void updateBoundaries() {
        final ViewParent parent = getParent();
        if (parent instanceof View) {
            final View parentView = (View) parent;
            minX = 0;
            minY = 0;
            maxX = parentView.getWidth();
            maxY = parentView.getHeight();
        }
    }

    /**
     * Reset the position to a default location (e.g., original position).
     */
    public void resetPosition() {
        // You can implement logic to reset to original position or center
        animate().x(0).y(0).setDuration(300).start();
    }

    /**
     * Programmatically set the position with bounds checking.
     *
     * @param x the x coordinate to set
     * @param y the y coordinate to set
     */
    public void setPosition(final float x, final float y) {
        updateBoundaries();
        final float newX = Math.max(minX, Math.min(maxX - getWidth(), x));
        final float newY = Math.max(minY, Math.min(maxY - getHeight(), y));
        setX(newX);
        setY(newY);
    }

    /**
     * Check if the view is currently being dragged.
     *
     * @return true if the view is being dragged, false otherwise
     */
    public boolean isDragging() {
        return isDragging;
    }
}