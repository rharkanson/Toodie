/*
 * Copyright (C) 2014 Russell Harkanson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.harkanson.android.widget.toodie;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AnimationUtils;
import android.widget.EdgeEffect;
import android.widget.FrameLayout;
import android.widget.OverScroller;

import java.util.List;

/**
 * Layout container for a view hierarchy that can be scrolled by the user,
 * allowing it to be larger than the physical display.  A TwoDimensionalScrollView
 * is a {@link FrameLayout}, meaning you should place one child in it
 * containing the entire contents to scroll; this child may itself be a layout
 * manager with a complex hierarchy of objects.  A child that is often used
 * is a LinearLayout in a horizontal orientation, presenting a horizontal
 * array of top-level items that the user can scroll through.
 *
 * <p>You should never use a TwoDimensionalScrollView with a ListView,
 * since ListView takes care of its own scrolling.  Most importantly, doing this
 * defeats all of the important optimizations in ListView for dealing with
 * large lists, since it effectively forces the ListView to display its entire
 * list of items to fill up the infinite container supplied by
 * TwoDimensionalScrollView.
 *
 * <p>The TextView class also
 * takes care of its own scrolling, so does not require a ScrollView, but
 * using the two together is possible to achieve the effect of a text view
 * within a larger container.
 *
 * <p>TwoDimensionalScrollView supports horizontal and vertical scrolling.
 *
 * @attr ref android.R.styleable#HorizontalScrollView_fillViewport
 */

public class TwoDimensionalScrollView extends FrameLayout {
    static final int ANIMATED_SCROLL_GAP = 250;
    static final float MAX_SCROLL_FACTOR = 0.5f;

    private float mVerticalScrollFactor;

    private long mLastScroll;

    private final Rect mTempRect = new Rect();

    private OverScroller mScroller;
    private EdgeEffect mEdgeGlowLeft;
    private EdgeEffect mEdgeGlowRight;
    private EdgeEffect mEdgeGlowTop;
    private EdgeEffect mEdgeGlowBottom;

    /**
     * Position of the last motion event.
     */
    private float mLastMotionX;
    private float mLastMotionY;

    /**
     * True when the layout has changed but the traversal has not come through yet.
     * Ideally the view hierarchy would keep track of this for us.
     */
    private boolean mIsLayoutDirty = true;

    /**
     * The child to give focus to in the event that a child has requested focus while the
     * layout is dirty. This prevents the scroll from being wrong if the child has not been
     * laid out before requesting focus.
     */
    private View mChildToScrollTo = null;

    /**
     * True if the user is currently dragging this ScrollView around. This is
     * not the same as 'is being flinged', which can be checked by
     * mScroller.isFinished() (flinging begins when the user lifts his finger).
     */
    private boolean mIsBeingDragged = false;

    /**
     * Determines speed during touch scrolling
     */
    private VelocityTracker mVelocityTracker;

    /**
     * When set to true, the scroll view measure its child to make it fill the currently
     * visible area.
     */
    @ViewDebug.ExportedProperty(category = "layout")
    private boolean mFillViewport;

    /**
     * Whether arrow scrolling is animated.
     */
    private boolean mSmoothScrollingEnabled = true;

    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;

    private int mOverscrollDistance;
    private int mOverflingDistance;

    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private int mActivePointerId = INVALID_POINTER;

    /**
     * Sentinel value for no current active pointer.
     * Used by {@link #mActivePointerId}.
     */
    private static final int INVALID_POINTER = -1;


    public TwoDimensionalScrollView(Context context) {
        super(context);
        initParallaxView();
    }

    public TwoDimensionalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initParallaxView();
    }

    public TwoDimensionalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initParallaxView();
    }


    @Override
    protected float getLeftFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }

        final int length = getHorizontalFadingEdgeLength();

        if (getScrollX() < length) {
            return getScrollX() / (float) length;
        }

        return 1.0f;
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }

        final int length = getHorizontalFadingEdgeLength();
        final int rightEdge = getWidth() - getPaddingRight();
        final int span = getChildAt(0).getRight() - getScrollX() - rightEdge;
        if (span < length) {
            return span / (float) length;
        }

        return 1.0f;
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }

        final int length = getVerticalFadingEdgeLength();

        if (getScrollY() < length) {
            return getScrollY() / (float) length;
        }

        return 1.0f;
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }

        final int length = getVerticalFadingEdgeLength();
        final int bottomEdge = getHeight() - getPaddingBottom();
        final int span = getChildAt(0).getBottom() - getScrollY() - bottomEdge;
        if (span < length) {
            return span / (float) length;
        }

        return 1.0f;
    }

    /**
     * @return The maximum amount this scroll view will horizontally scroll in response to
     *   an arrow event.
     */
    public int getMaxScrollAmountX() {
        return (int) (MAX_SCROLL_FACTOR * (getRight() - getLeft()));
    }

    /**
     * @return The maximum amount this scroll view will vertically scroll in response to
     *   an arrow event.
     */
    public int getMaxScrollAmountY() {
        return (int) (MAX_SCROLL_FACTOR * (getBottom() - getTop()));
    }

    private void initParallaxView() {
        mScroller = new OverScroller(getContext());
        setFocusable(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setWillNotDraw(false);
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mOverscrollDistance = configuration.getScaledOverscrollDistance();
        mOverflingDistance = configuration.getScaledOverflingDistance();
    }

    @Override
    public void addView(View child) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("TwoDimensionalScrollView can host only one direct child");
        }

        super.addView(child);
    }

    @Override
    public void addView(View child, int index) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("TwoDimensionalScrollView can host only one direct child");
        }

        super.addView(child, index);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("TwoDimensionalScrollView can host only one direct child");
        }

        super.addView(child, params);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("TwoDimensionalScrollView can host only one direct child");
        }

        super.addView(child, index, params);
    }

    /**
     * @return Returns true if this TwoDimensionalScrollView can be scrolled
     */
    private boolean canScroll() {
        View child = getChildAt(0);
        if (child != null) {
            int childWidth = child.getWidth();
            int childHeight = child.getHeight();
            return (getWidth() < childWidth + getPaddingLeft() + getPaddingRight())
                    || (getHeight() < childHeight + getPaddingTop() + getPaddingBottom());
        }
        return false;
    }

    /**
     * Indicates whether this TwoDimensionalScrollView's content is stretched to
     * fill the viewport.
     *
     * @return True if the content fills the viewport, false otherwise.
     *
     * @attr ref android.R.styleable#TwoDimensionalScrollView_fillViewport
     */
    public boolean isFillViewport() {
        return mFillViewport;
    }

    /**
     * Indicates this TwoDimensionalScrollView whether it should stretch its content width
     * to fill the viewport or not.
     *
     * @param fillViewport True to stretch the content's width to the viewport's
     *        boundaries, false otherwise.
     *
     * @attr ref android.R.styleable#TwoDimensionalScrollView_fillViewport
     */
    public void setFillViewport(boolean fillViewport) {
        if (fillViewport != mFillViewport) {
            mFillViewport = fillViewport;
            requestLayout();
        }
    }

    /**
     * @return Whether arrow scrolling will animate its transition.
     */
    public boolean isSmoothScrollingEnabled() {
        return mSmoothScrollingEnabled;
    }

    /**
     * Set whether arrow scrolling will animate its transition.
     * @param smoothScrollingEnabled whether arrow scrolling will animate its transition
     */
    public void setSmoothScrollingEnabled(boolean smoothScrollingEnabled) {
        mSmoothScrollingEnabled = smoothScrollingEnabled;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (!mFillViewport) {
            return;
        }

        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if(widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED) {
            return;
        }

        if (getChildCount() > 0) {
            final View child = getChildAt(0);
            int width = getMeasuredWidth();
            int height = getMeasuredHeight();

            if (child.getMeasuredWidth() < width || child.getMeasuredHeight() < height) {

                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec, getPaddingTop() + getPaddingBottom(), lp.height);
                int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, getPaddingLeft() + getPaddingRight(), lp.width);

                if (child.getMeasuredWidth() < width) {
                    width -= getPaddingLeft();
                    width -= getPaddingRight();
                    childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
                }

                if (child.getMeasuredHeight() < height) {
                    height -= getPaddingTop();
                    height -= getPaddingBottom();
                    childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
                }

                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return super.dispatchKeyEvent(event) || executeKeyEvent(event);
    }

    /**
     * You can call this function yourself to have the scroll view perform
     * scrolling from a key event, just as if the event had been dispatched to
     * it by the view hierarchy.
     *
     * @param event The key event to execute.
     * @return Return true if the event was handled, else false.
     */
    public boolean executeKeyEvent(KeyEvent event) {
        mTempRect.setEmpty();

        if (!canScroll()) {
            if (isFocused() && event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
                View currentFocused = findFocus();
                if (currentFocused == this) currentFocused = null;
                View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, View.FOCUS_RIGHT);

                return nextFocused != null && nextFocused != this && nextFocused.requestFocus(View.FOCUS_RIGHT);
            }
            return false;
        }

        boolean handled = false;

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_LEFT);
                    } else {
                        handled = fullScroll(View.FOCUS_LEFT);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_RIGHT);
                    } else {
                        handled = fullScroll(View.FOCUS_RIGHT);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_UP);
                    } else {
                        handled = fullScroll(View.FOCUS_UP);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_DOWN);
                    } else {
                        handled = fullScroll(View.FOCUS_DOWN);
                    }
                    break;
                case KeyEvent.KEYCODE_SPACE:
                    pageScroll(event.isShiftPressed() ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
                    break;
            }
        }

        return handled;
    }

    private boolean inChild(int x, int y) {
        if(getChildCount() > 0) {
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            final View child = getChildAt(0);
            return !(y < child.getTop() - scrollY
                    || y >= child.getBottom() - scrollY
                    || x < child.getLeft() - scrollX
                    || x >= child.getRight() - scrollX);
        }
        return false;
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            recycleVelocityTracker();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) && (mIsBeingDragged)) {
            return true;
        }

        //TODO possibly get rid of this
        /*
        if(!canScroll()) {
            mIsBeingDragged = false;
            return false;
        }
        */

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {

                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    break;
                }

                final int pointerIndex = ev.findPointerIndex(activePointerId);
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);
                final int xDiff = (int) Math.abs(x - mLastMotionX);
                final int yDiff = (int) Math.abs(x - mLastMotionY);
                if (xDiff > mTouchSlop || yDiff > mTouchSlop) {
                    mIsBeingDragged = true;
                    mLastMotionX = x;
                    mLastMotionY = y;
                    initVelocityTrackerIfNotExists();
                    mVelocityTracker.addMovement(ev);
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            }
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                if (!inChild((int) x, (int) y)) {
                    mIsBeingDragged = false;
                    recycleVelocityTracker();
                    break;
                }

                mLastMotionX = x;
                mLastMotionY = y;
                mActivePointerId = ev.getPointerId(0);

                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(ev);

                mIsBeingDragged = !mScroller.isFinished();
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                if (mScroller.springBack(getScrollX(), getScrollY(), 0, getScrollRangeX(), 0, getScrollRangeY())) {
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                mLastMotionX = ev.getX(index);
                mLastMotionY = ev.getY(index);
                mActivePointerId = ev.getPointerId(index);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                mLastMotionX = ev.getX(ev.findPointerIndex(mActivePointerId));
                mLastMotionY = ev.getY(ev.findPointerIndex(mActivePointerId));
                break;
        }
        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        initVelocityTrackerIfNotExists();
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction();

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                mIsBeingDragged = getChildCount() != 0;
                if (!mIsBeingDragged) {
                    return false;
                }

                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }

                mLastMotionX = ev.getX();
                mLastMotionY = ev.getY();
                mActivePointerId = ev.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (mIsBeingDragged) {
                    final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                    final float x = ev.getX(activePointerIndex);
                    final float y = ev.getY(activePointerIndex);
                    final int deltaX = (int) (mLastMotionX - x);
                    final int deltaY = (int) (mLastMotionY - y);
                    mLastMotionX = x;
                    mLastMotionY = y;

                    final int oldX = getScrollX();
                    final int oldY = getScrollY();
                    final int rangeX = getScrollRangeX();
                    final int rangeY = getScrollRangeY();
                    final int overscrollMode = getOverScrollMode();
                    final boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS
                            || (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && (rangeX > 0 || rangeY > 0));

                    //TODO possibly break this into two statements
                    if (overScrollBy(deltaX, deltaY, getScrollX(), getScrollY(), rangeX, rangeY, mOverscrollDistance, mOverscrollDistance, true)) {
                        mVelocityTracker.clear();
                    }
                    onScrollChanged(getScrollX(), getScrollY(), oldX, oldY);

                    if (canOverscroll) {
                        final int pulledToX = oldX + deltaX;
                        final int pulledToY = oldY + deltaY;

                        if (pulledToX < 0) {
                            mEdgeGlowLeft.onPull((float) deltaX / getWidth());
                            if (!mEdgeGlowRight.isFinished()) {
                                mEdgeGlowRight.onRelease();
                            }
                        } else if (pulledToX > rangeX) {
                            mEdgeGlowRight.onPull((float) deltaX / getWidth());
                            if (!mEdgeGlowLeft.isFinished()) {
                                mEdgeGlowLeft.onRelease();
                            }
                        }
                        if (mEdgeGlowLeft != null
                                && (!mEdgeGlowLeft.isFinished() || !mEdgeGlowRight.isFinished())) {
                            invalidate();
                        }

                        if (pulledToY < 0) {
                            mEdgeGlowTop.onPull((float) deltaY / getHeight());
                            if (!mEdgeGlowBottom.isFinished()) {
                                mEdgeGlowBottom.onRelease();
                            }
                        } else if (pulledToY > rangeY) {
                            mEdgeGlowBottom.onPull((float) deltaY / getHeight());
                            if (!mEdgeGlowTop.isFinished()) {
                                mEdgeGlowTop.onRelease();
                            }
                        }
                        if (mEdgeGlowTop != null
                                && (!mEdgeGlowTop.isFinished() || !mEdgeGlowBottom.isFinished())) {
                            invalidate();
                        }
                    }

                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialVelocityX = (int) velocityTracker.getXVelocity(mActivePointerId);
                    int initialVelocityY = (int) velocityTracker.getYVelocity(mActivePointerId);
                    int initialVelocity = (int) Math.sqrt(Math.pow(velocityTracker.getXVelocity(mActivePointerId), 2)
                            + Math.pow(velocityTracker.getYVelocity(mActivePointerId), 2));

                    if (getChildCount() > 0) {
                        if ((Math.abs(initialVelocity) > mMaximumVelocity)) {
                            fling(-initialVelocityX, -initialVelocityY);
                        } else {
                            if (mScroller.springBack(getScrollX(), getScrollY(), 0, getScrollRangeX(), 0, getScrollRangeY())) {
                                invalidate();
                            }
                        }
                    }

                    mActivePointerId = INVALID_POINTER;
                    mIsBeingDragged = false;
                    recycleVelocityTracker();

                    if (mEdgeGlowLeft != null) {
                        mEdgeGlowLeft.onRelease();
                        mEdgeGlowRight.onRelease();
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged && getChildCount() > 0) {
                    if (mScroller.springBack(getScrollX(), getScrollY(), 0, getScrollRangeX(), 0, getScrollRangeY())) {
                        invalidate();
                    }
                    mActivePointerId = INVALID_POINTER;
                    mIsBeingDragged = false;
                    recycleVelocityTracker();

                    if (mEdgeGlowLeft != null) {
                        mEdgeGlowLeft.onRelease();
                        mEdgeGlowRight.onRelease();
                    }

                    if (mEdgeGlowTop != null) {
                        mEdgeGlowTop.onRelease();
                        mEdgeGlowBottom.onRelease();
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }
        return true;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {

            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = ev.getX();
            mLastMotionY = ev.getY();
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL: {
                    if (!mIsBeingDragged) {
                        final float hscroll;
                        final float vscroll;
                        if ((event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0) {
                            hscroll = -event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                            vscroll = -event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                        } else {
                            hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                            vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                        }
                        if (hscroll != 0 || vscroll != 0) {
                            final int deltaX = (int) (hscroll * getHorizontalScrollFactor());
                            final int deltaY = (int) (vscroll * getVerticalScrollFactor());
                            final int rangeX = getScrollRangeX();
                            final int rangeY = getScrollRangeY();
                            int oldScrollX = getScrollX();
                            int oldScrollY = getScrollY();
                            int newScrollX = oldScrollX + deltaX;
                            int newScrollY = oldScrollY + deltaY;

                            if (newScrollX < 0) {
                                newScrollX = 0;
                            } else if (newScrollX > rangeX) {
                                newScrollX = rangeX;
                            }
                            if (newScrollY < 0) {
                                newScrollY = 0;
                            } else if (newScrollY > rangeY) {
                                newScrollY = rangeY;
                            }

                            if (newScrollX != oldScrollX || newScrollY != oldScrollY) {
                                super.scrollTo(newScrollX, newScrollY);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        if (!mScroller.isFinished()) {
            setScrollX(scrollX);
            setScrollY(scrollY);
            invalidateParentIfNeeded();
            if (clampedX || clampedY) {
                mScroller.springBack(getScrollX(), getScrollY(), 0, getScrollRangeX(), 0, getScrollRangeY());
            }
        } else {
            super.scrollTo(scrollX, scrollY);
        }
        awakenScrollBars();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setScrollable(getScrollRangeX() > 0 || getScrollRangeY() > 0);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setScrollable(getScrollRangeX() > 0 || getScrollRangeY() > 0 );
        event.setScrollX(getScrollX());
        event.setScrollY(getScrollY());
        event.setMaxScrollX(getScrollRangeX());
        event.setMaxScrollY(getScrollRangeY());
    }

    private int getScrollRangeX() {
        int scrollRange = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            scrollRange = Math.max(0,
                    child.getWidth() - (getWidth() - getPaddingLeft() - getPaddingRight()));
        }
        return scrollRange;
    }

    private int getScrollRangeY() {
        int scrollRange = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            scrollRange = Math.max(0,
                    child.getHeight() - (getHeight() - getPaddingBottom() - getPaddingTop()));
        }
        return scrollRange;
    }

    /**
     * <p>
     * Finds the next focusable component that fits in this View's bounds
     * (excluding fading edges) pretending that this View's left is located at
     * the parameter left.
     * </p>
     *
     * @param leftFocus          look for a candidate is the one at the left of the bounds
     *                           if leftFocus is true, or at the right of the bounds if leftFocus
     *                           is false
     * @param left               the left offset of the bounds in which a focusable must be
     *                           found (the fading edge is assumed to start at this position)
     * @param preferredFocusable the View that has highest priority and will be
     *                           returned if it is within my bounds (null is valid)
     * @return the next focusable component in the bounds or null if none can be found
     */
    private View findFocusableViewInMyBounds(final boolean leftFocus, final int left, final boolean topFocus, final int top, View preferredFocusable) {
        final int fadingEdgeLengthX = getHorizontalFadingEdgeLength() / 2;
        final int fadingEdgeLengthY = getVerticalFadingEdgeLength() / 2;
        final int leftWithoutFadingEdge = left + fadingEdgeLengthX;
        final int topWithoutFadingEdge = top + fadingEdgeLengthY;
        final int rightWithoutFadingEdge = left + getWidth() - fadingEdgeLengthX;
        final int bottomWithoutFadingEdge = top + getHeight() - fadingEdgeLengthY;

        if ((preferredFocusable != null)
                && (preferredFocusable.getLeft() < rightWithoutFadingEdge)
                && (preferredFocusable.getRight() > leftWithoutFadingEdge)
                && (preferredFocusable.getTop() < bottomWithoutFadingEdge)
                && (preferredFocusable.getBottom() > topWithoutFadingEdge)) {
            return preferredFocusable;
        }

        return findFocusableViewInBounds(leftFocus, leftWithoutFadingEdge, rightWithoutFadingEdge, topFocus, topWithoutFadingEdge, bottomWithoutFadingEdge);
    }

    /**
     * <p>
     * Finds the next focusable component that fits in the specified bounds.
     * </p>
     *
     * @param leftFocus look for a candidate is the one at the left of the bounds
     *                  if leftFocus is true, or at the right of the bounds if
     *                  leftFocus is false
     * @param left      the left offset of the bounds in which a focusable must be
     *                  found
     * @param right     the right offset of the bounds in which a focusable must
     *                  be found
     * @return the next focusable component in the bounds or null if none can
     *         be found
     */
    private View findFocusableViewInBounds(boolean leftFocus, int left, int right, boolean topFocus, int top, int bottom) {
        List<View> focusables = getFocusables(View.FOCUS_FORWARD);
        View focusCandidate = null;

        boolean foundFullyContainedFocusable = false;

        int count = focusables.size();
        for (int i=0; i < count; i++) {
            View view = focusables.get(i);
            int viewLeft = view.getLeft();
            int viewRight = view.getRight();
            int viewTop = view.getTop();
            int viewBottom = view.getBottom();

            if (left < viewRight && viewLeft < right && top < viewBottom && viewTop < bottom) {

                final boolean viewIsFullyContained = (left < viewLeft) && (viewRight < right)
                        && (top < viewTop) && (viewBottom < bottom);

                if (focusCandidate == null) {
                    focusCandidate = view;
                    foundFullyContainedFocusable = viewIsFullyContained;
                } else {
                    final boolean viewIsCloserToBoundary =
                            ((leftFocus && viewLeft < focusCandidate.getLeft())
                                    || (!leftFocus && viewRight > focusCandidate.getRight()))
                                    && ((topFocus && viewTop < focusCandidate.getTop())
                                    || (!topFocus && viewBottom > focusCandidate.getBottom()));

                    if (foundFullyContainedFocusable) {
                        if (viewIsFullyContained && viewIsCloserToBoundary) {
                            focusCandidate = view;
                        }
                    } else {
                        if (viewIsFullyContained) {
                            focusCandidate = view;
                            foundFullyContainedFocusable = true;
                        } else if (viewIsCloserToBoundary) {
                            focusCandidate = view;
                        }
                    }
                }
            }
        }

        return focusCandidate;
    }

    /**
     * <p>Handles scrolling in response to a "page up/down" shortcut press. This
     * method will scroll the view by one page left or right and give the focus
     * to the leftmost/rightmost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.</p>
     *
     * @param direction the scroll direction: {@link android.view.View#FOCUS_LEFT}
     *                  to go one page left or {@link android.view.View#FOCUS_RIGHT}
     *                  to go one page right
     * @return true if the key event is consumed by this method, false otherwise
     */
    public boolean pageScroll(int direction) {
        int width = getWidth();
        int height = getHeight();

        switch (direction) {
            case View.FOCUS_RIGHT:
                mTempRect.left = getScrollX() + width;
                int countX = getChildCount();
                if (countX > 0) {
                    View view = getChildAt(0);
                    if (mTempRect.left + width > view.getRight()) {
                        mTempRect.left = view.getRight() - width;
                    }
                }
                break;
            case View.FOCUS_LEFT:
                mTempRect.left = getScrollX() - width;
                if (mTempRect.left < 0) {
                    mTempRect.left = 0;
                }
                break;
            case View.FOCUS_DOWN:
                mTempRect.top = getScrollY() + height;
                int countY = getChildCount();
                if (countY > 0) {
                    View view = getChildAt(0);
                    if (mTempRect.top + height > view.getBottom()) {
                        mTempRect.top = view.getBottom() - height;
                    }
                }
                break;
            default:
                mTempRect.top = getScrollY() - height;
                if (mTempRect.top < 0) {
                    mTempRect.top = 0;
                }
        }
        mTempRect.right = mTempRect.left + width;
        mTempRect.bottom = mTempRect.top + height;

        return scrollAndFocus(direction, mTempRect.left, mTempRect.right, mTempRect.top, mTempRect.bottom);
    }

    /**
     * <p>Handles scrolling in response to a "home/end" shortcut press. This
     * method will scroll the view to the left or right and give the focus
     * to the leftmost/rightmost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.</p>
     *
     * @param direction the scroll direction: {@link android.view.View#FOCUS_LEFT}
     *                  to go the left of the view or {@link android.view.View#FOCUS_RIGHT}
     *                  to go the right
     * @return true if the key event is consumed by this method, false otherwise
     */
    public boolean fullScroll(int direction) {
        int width = getWidth();
        int height = getHeight();

        mTempRect.left = 0;
        mTempRect.right = width;
        mTempRect.top = 0;
        mTempRect.bottom = height;

        switch (direction) {
            case View.FOCUS_RIGHT:
                int countX = getChildCount();
                if (countX > 0) {
                    View view = getChildAt(0);
                    mTempRect.right = view.getRight();
                    mTempRect.left = mTempRect.right - width;
                }
                break;
            case View.FOCUS_DOWN:
                int countY = getChildCount();
                if (countY > 0) {
                    View view = getChildAt(0);
                    mTempRect.bottom = view.getBottom();
                    mTempRect.top = mTempRect.top - height;
                }
        }

        return scrollAndFocus(direction, mTempRect.left, mTempRect.right, mTempRect.top, mTempRect.bottom);
    }

    /**
     * <p>Scrolls the view to make the area defined by <code>left</code> and
     * <code>right</code> visible. This method attempts to give the focus
     * to a component visible in this area. If no component can be focused in
     * the new visible area, the focus is reclaimed by this scrollview.</p>
     *
     * @param direction the scroll direction: {@link android.view.View#FOCUS_LEFT}
     *                  to go left {@link android.view.View#FOCUS_RIGHT} to right
     * @param left     the left offset of the new area to be made visible
     * @param right    the right offset of the new area to be made visible
     * @return true if the key event is consumed by this method, false otherwise
     */
    private boolean scrollAndFocus(int direction, int left, int right, int top, int bottom) {
        boolean handled = true;

        int width = getWidth();
        int height = getHeight();
        int containerLeft = getScrollX();
        int containerRight = containerLeft + width;
        int containerTop = getScrollY();
        int containerBottom = getScrollRangeY() + height;
        boolean goLeft = direction == View.FOCUS_LEFT;
        boolean goUp = direction == View.FOCUS_UP;

        View newFocused = findFocusableViewInBounds(goLeft, left, right, goUp, top, bottom);
        if (newFocused == null) {
            newFocused = this;
        }

        if (left >= containerLeft && right <= containerRight && top >= containerTop && bottom <= containerBottom) {
            handled = false;
        } else {
            int deltaX = goLeft ? (left - containerLeft) : (right - containerRight);
            int deltaY = goUp ? (top - containerTop) : (bottom - containerBottom);
            doScroll(deltaX, deltaY);
        }

        if (newFocused != findFocus()) newFocused.requestFocus(direction);

        return handled;
    }

    /**
     * Handle scrolling in response to a left or right arrow click.
     *
     * @param direction The direction corresponding to the arrow key that was
     *                  pressed
     * @return True if we consumed the event, false otherwise
     */
    public boolean arrowScroll(int direction) {

        View currentFocused = findFocus();
        if (currentFocused == this) currentFocused = null;

        View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction);

        final int maxJumpX = getMaxScrollAmountX();
        final int maxJumpY = getMaxScrollAmountY();

        if (nextFocused != null && isWithinDeltaOfScreen(nextFocused, maxJumpX, maxJumpY)) {
            nextFocused.getDrawingRect(mTempRect);
            offsetDescendantRectToMyCoords(nextFocused, mTempRect);
            int scrollDeltaX = computeScrollDeltaXToGetChildRectOnScreen(mTempRect);
            int scrollDeltaY = computeScrollDeltaYToGetChildRectOnScreen(mTempRect);
            doScroll(scrollDeltaX, scrollDeltaY);
            nextFocused.requestFocus(direction);
        } else {
            int scrollDeltaX = maxJumpX;
            int scrollDeltaY = maxJumpY;

            if (direction == View.FOCUS_LEFT && getScrollX() < scrollDeltaX) {
                scrollDeltaX = getScrollX() * -1;
            } else if (direction == View.FOCUS_RIGHT && getChildCount() > 0) {
                int daRight = getChildAt(0).getRight();
                int screenRight = getScrollRangeX() + getWidth();
                if (daRight - screenRight < maxJumpX) {
                    scrollDeltaX = daRight - screenRight;
                }
            }

            if (direction == View.FOCUS_UP && getScrollY() < scrollDeltaY) {
                scrollDeltaY = getScrollY() * -1;
            } else if (direction == View.FOCUS_DOWN && getChildCount() > 0) {
                int daBottom = getChildAt(0).getBottom(); //...now we here.
                int screenBottom = getScrollRangeY() + getHeight();
                if (daBottom - screenBottom < maxJumpX) {
                    scrollDeltaY = daBottom - screenBottom;
                }
            }

            if (scrollDeltaX == 0 && scrollDeltaY == 0) {
                return false;
            }

            doScroll(scrollDeltaX, scrollDeltaY);
        }

        if (currentFocused != null && currentFocused.isFocused() && isOffScreen(currentFocused)) {
            final int descendantFocusability = getDescendantFocusability();
            setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            requestFocus();
            setDescendantFocusability(descendantFocusability);
        }
        return true;
    }

    /**
     * @return whether the descendant of this scroll view is scrolled off
     *  screen.
     */
    private boolean isOffScreen(View descendant) {
        return !isWithinDeltaOfScreen(descendant, 0, 0);
    }

    /**
     * @return whether the descendant of this scroll view is within delta
     *  pixels of being on the screen.
     */
    private boolean isWithinDeltaOfScreen(View descendant, int deltaX, int deltaY) {
        descendant.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(descendant, mTempRect);

        return ((mTempRect.right + deltaX) >= getScrollX()
                && (mTempRect.left - deltaX) <= (getScrollX() + getWidth())
                && (mTempRect.bottom + deltaY) >= getScrollY()
                && (mTempRect.top - deltaY) <= (getScrollY() + getHeight()));
    }

    /**
     * Smooth scroll by a X delta
     *
     * @param deltaX the number of pixels to scroll by on the X axis
     * @param deltaY the number of pixels to scroll by on the Y axis
     */
    private void doScroll(int deltaX, int deltaY) {
        if (deltaX != 0 && deltaY != 0) {
            if (mSmoothScrollingEnabled) {
                smoothScrollBy(deltaX, deltaY);
            } else {
                scrollBy(deltaX, deltaY);
            }
        }
    }

    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param dx the number of pixels to scroll by on the X axis
     * @param dy the number of pixels to scroll by on the Y axis
     */
    public final void smoothScrollBy(int dx, int dy) {
        if (getChildCount() == 0) {
            return;
        }
        long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
        if (duration> ANIMATED_SCROLL_GAP) {
            final int width = getWidth() - getPaddingRight() - getPaddingLeft();
            final int right = getChildAt(0).getWidth();
            final int maxX = Math.max(0, right - width);
            final int scrollX = getScrollX();
            dx = Math.max(0, Math.min(scrollX + dx, maxX)) - scrollX;

            final int height = getHeight() - getPaddingBottom() - getPaddingTop();
            final int bottom = getChildAt(0).getHeight();
            final int maxY = Math.max(0, bottom - height);
            final int scrollY = getScrollY();
            dy = Math.max(0, Math.min(scrollY + dy, maxY)) - scrollY;

            mScroller.startScroll(scrollX, scrollY, dx, dy);
            invalidate();
        } else {
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
            scrollBy(dx, dy);
        }
        mLastScroll = AnimationUtils.currentAnimationTimeMillis();
    }

    /**
     * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
     *
     * @param x the position where to scroll on the X axis
     * @param y the position where to scroll on the Y axis
     */
    public final void smoothScrollTo(int x, int y) {
        smoothScrollBy(x - getScrollX(), y - getScrollY());
    }

    /**
     * <p>The scroll range of a scroll view is the overall width of all of its
     * children.</p>
     */
    @Override
    protected int computeHorizontalScrollRange() {
        final int count = getChildCount();
        final int contentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        if (count == 0) {
            return contentWidth;
        }

        int scrollRange = getChildAt(0).getRight();
        final int scrollX = getScrollX();
        final int overscrollRight = Math.max(0, scrollRange - contentWidth);
        if (scrollX < 0) {
            scrollRange -= scrollX;
        } else if (scrollX > overscrollRight) {
            scrollRange += scrollX - overscrollRight;
        }

        return scrollRange;
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        return Math.max(0, super.computeHorizontalScrollOffset());
    }

    @Override
    protected int computeVerticalScrollRange() {
        final int count = getChildCount();
        final int contentHeight = getHeight() - getPaddingBottom() - getPaddingTop();
        if (count == 0) {
            return contentHeight;
        }

        int scrollRange = getChildAt(0).getBottom();
        final int scrollY = getScrollY();
        final int overscrollBottom = Math.max(0, scrollRange - contentHeight);
        if (scrollY < 0) {
            scrollRange -= scrollY;
        } else if (scrollY > overscrollBottom) {
            scrollRange += scrollY - overscrollBottom;
        }

        return scrollRange;
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return Math.max(0, super.computeVerticalScrollOffset());
    }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();

        int childWidthMeasureSpec;
        int childHeightMeasureSpec;

        childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, getPaddingTop()
                + getPaddingBottom(), lp.height);
        childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, getPaddingLeft()
                + getPaddingRight(), lp.width);

        //childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        //childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);


        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
                                           int parentHeightMeasureSpec, int heightUsed) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();

        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin
                        + heightUsed, lp.height);
        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin
                        + widthUsed, lp.width);

        //final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
        //        lp.leftMargin + lp.rightMargin, MeasureSpec.UNSPECIFIED);
        //final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
        //        lp.topMargin + lp.bottomMargin, MeasureSpec.UNSPECIFIED);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();

            if (oldX != x || oldY != y) {
                final int rangeX = getScrollRangeX();
                final int rangeY = getScrollRangeY();
                final int overscrollMode = getOverScrollMode();
                final boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS ||
                        (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && rangeX > 0 && rangeY > 0);

                overScrollBy(x - oldX, y - oldY, oldX, oldY, rangeX, rangeY, mOverflingDistance, mOverflingDistance, false);
                onScrollChanged(getScrollX(), getScrollY(), oldX, oldY);

                if (canOverscroll) {
                    if (x < 0 && oldX >= 0) {
                        mEdgeGlowLeft.onAbsorb((int) mScroller.getCurrVelocity());
                    } else if (x > rangeX && oldX <= rangeX) {
                        mEdgeGlowRight.onAbsorb((int) mScroller.getCurrVelocity());
                    } else if (y < 0 && oldY >= 0) {
                        mEdgeGlowTop.onAbsorb((int) mScroller.getCurrVelocity());
                    } else if (y > rangeY && oldY <= rangeY) {
                        mEdgeGlowBottom.onAbsorb((int) mScroller.getCurrVelocity());
                    }
                }
            }

            awakenScrollBars();

            postInvalidate();
        }
    }

    /**
     * Scrolls the view to the given child.
     *
     * @param child the View to scroll to
     */
    private void scrollToChild(View child) {
        child.getDrawingRect(mTempRect);

        offsetDescendantRectToMyCoords(child, mTempRect);

        int scrollDeltaX = computeScrollDeltaXToGetChildRectOnScreen(mTempRect);
        int scrollDeltaY = computeScrollDeltaYToGetChildRectOnScreen(mTempRect);

        if (scrollDeltaX != 0 || scrollDeltaY != 0) {
            scrollBy(scrollDeltaX, scrollDeltaY);
        }
    }

    /**
     * If rect is off screen, scroll just enough to get it (or at least the
     * first screen size chunk of it) on screen.
     *
     * @param rect      The rectangle.
     * @param immediate True to scroll immediately without animation
     * @return true if scrolling was performed
     */
    private boolean scrollToChildRect(Rect rect, boolean immediate) {
        final int deltaX = computeScrollDeltaXToGetChildRectOnScreen(mTempRect);
        final int deltaY = computeScrollDeltaYToGetChildRectOnScreen(mTempRect);

        if (deltaX != 0 || deltaY != 0) {
            if (immediate) {
                scrollBy(deltaX, deltaY);
            } else {
                smoothScrollBy(deltaX, deltaY);
            }
            return true;
        }
        return false;
    }

    /**
     * Compute the amount to scroll in the X direction in order to get
     * a rectangle completely on the screen (or, if taller than the screen,
     * at least the first screen size chunk of it).
     *
     * @param rect The rect.
     * @return The scroll delta.
     */
    protected int computeScrollDeltaXToGetChildRectOnScreen(Rect rect) {
        if (getChildCount() == 0) return 0;

        int width = getWidth();
        int screenLeft = getScrollX();
        int screenRight = screenLeft + width;

        int fadingEdge = getHorizontalFadingEdgeLength();

        if (rect.left > 0) {
            screenLeft += fadingEdge;
        }

        if (rect.right < getChildAt(0).getWidth()) {
            screenRight -= fadingEdge;
        }

        int scrollXDelta = 0;

        if (rect.right > screenRight && rect.left > screenLeft) {

            if (rect.width() > width) {
                scrollXDelta += (rect.left - screenLeft);
            } else {
                scrollXDelta += (rect.right - screenRight);
            }

            int right = getChildAt(0).getRight();
            int distanceToRight = right - screenRight;
            scrollXDelta = Math.min(scrollXDelta, distanceToRight);

        } else if (rect.left < screenLeft && rect.right < screenRight) {

            if (rect.width() > width) {
                scrollXDelta -= (screenRight - rect.right);
            } else {
                scrollXDelta -= (screenLeft - rect.left);
            }

            scrollXDelta = Math.max(scrollXDelta, -getScrollRangeX());
        }
        return scrollXDelta;
    }

    /**
     * When looking for focus in children of a scroll view, need to be a little
     * more careful not to give focus to something that is scrolled off screen.
     *
     * This is more expensive than the default {@link android.view.ViewGroup}
     * implementation, otherwise this behavior might have been made the default.
     */
    protected int computeScrollDeltaYToGetChildRectOnScreen(Rect rect) {
        if (getChildCount() == 0) return 0;

        int height = getHeight();
        int screenTop = getScrollY();
        int screenBottom = screenTop + height;

        int fadingEdge = getVerticalFadingEdgeLength();

        if (rect.top > 0) {
            screenTop += fadingEdge;
        }

        if (rect.bottom < getChildAt(0).getHeight()) {
            screenBottom -= fadingEdge;
        }

        int scrollYDelta = 0;

        if (rect.bottom > screenBottom && rect.top > screenTop) {

            if (rect.height() > height) {
                scrollYDelta += (rect.top - screenTop);
            } else {
                scrollYDelta += (rect.bottom - screenBottom);
            }

            int bottom = getChildAt(0).getBottom();
            int distanceToBottom = bottom - screenBottom;
            scrollYDelta = Math.min(scrollYDelta, distanceToBottom);

        } else if (rect.top < screenTop && rect.bottom < screenBottom) {

            if (rect.height() > height) {
                scrollYDelta -= (screenBottom - rect.bottom);
            } else {
                scrollYDelta -= (screenTop - rect.top);
            }

            scrollYDelta = Math.max(scrollYDelta, -getScrollY());
        }
        return scrollYDelta;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (!mIsLayoutDirty) {
            scrollToChild(focused);
        } else {
            mChildToScrollTo = focused;
        }
        super.requestChildFocus(child, focused);
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {

        if (direction == View.FOCUS_FORWARD) {
            direction = View.FOCUS_RIGHT;
        } else if (direction == View.FOCUS_BACKWARD) {
            direction = View.FOCUS_LEFT;
        }

        final View nextFocus = previouslyFocusedRect == null ?
                FocusFinder.getInstance().findNextFocus(this, null, direction) :
                FocusFinder.getInstance().findNextFocusFromRect(this, previouslyFocusedRect, direction);

        if (nextFocus == null) {
            return false;
        }

        if (isOffScreen(nextFocus)) {
            return false;
        }

        return nextFocus.requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());

        return scrollToChildRect(rectangle, immediate);
    }

    @Override
    public void requestLayout() {
        mIsLayoutDirty = true;
        super.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mIsLayoutDirty = false;

        if (mChildToScrollTo != null && isViewDescendantOf(mChildToScrollTo, this)) {
            scrollToChild(mChildToScrollTo);
        }
        mChildToScrollTo = null;

        scrollTo(getScrollX(), getScrollY());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        View currentFocused = findFocus();
        if (null == currentFocused || this == currentFocused)
            return;

        final int maxJumpX = getRight() - getLeft();
        final int maxJumpY = getBottom() - getTop();

        if (isWithinDeltaOfScreen(currentFocused, maxJumpX, maxJumpY)) {
            currentFocused.getDrawingRect(mTempRect);
            offsetDescendantRectToMyCoords(currentFocused, mTempRect);
            int scrollDeltaX = computeScrollDeltaXToGetChildRectOnScreen(mTempRect);
            int scrollDeltaY = computeScrollDeltaYToGetChildRectOnScreen(mTempRect);
            doScroll(scrollDeltaX, scrollDeltaY);
        }
    }

    /**
     * @return True if child is an descendant of parent, (or equal to the parent).
     */
    private boolean isViewDescendantOf(View child, View parent) {
        if (child == parent) {
            return true;
        }

        final ViewParent theParent = child.getParent();
        return (theParent instanceof ViewGroup) && isViewDescendantOf((View) theParent, parent);
    }

    /**
     * Fling the scroll view
     *
     * @param velocityX The initial velocity in the X direction. Positive
     *                  numbers mean that the finger/curor is moving down the screen,
     *                  which means we want to scroll towards the left.
     */
    public void fling(int velocityX, int velocityY) {
        if (getChildCount() > 0) {
            int width = getWidth() - getPaddingRight() - getPaddingLeft();
            int height = getHeight() - getPaddingTop() - getPaddingBottom();
            int right = getChildAt(0).getWidth();
            int bottom = getChildAt(0).getHeight();

            mScroller.fling(getScrollX(), getScrollY(), velocityX, velocityY, 0, Math.max(0, right-width), 0, Math.max(0, bottom-height), width/2, height/2);

            final boolean movingRight = velocityX > 0;
            final boolean movingDown = velocityY > 0;


            //TODO Finish this
            View currentFocused = findFocus();
            View newFocused = findFocusableViewInMyBounds(movingRight, mScroller.getFinalX(), movingDown, mScroller.getFinalY(), currentFocused);

            if (newFocused == null) {
                newFocused = this;
            }

            if (newFocused != currentFocused) {
                newFocused.requestFocus(movingRight ? View.FOCUS_RIGHT : View.FOCUS_LEFT);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>This version also clamps the scrolling to the bounds of our child.
     */
    @Override
    public void scrollTo(int x, int y) {
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            x = clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), child.getWidth());
            y = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), child.getHeight());
            if (x != getScrollX() || y != getScrollY()) {
                super.scrollTo(x, y);
            }
        }
        super.scrollTo(x, y);
    }

    @Override
    public void setOverScrollMode(int mode) {
        if (mode != OVER_SCROLL_NEVER) {
            if (mEdgeGlowLeft == null) {
                Context context = getContext();
                mEdgeGlowLeft = new EdgeEffect(context);
                mEdgeGlowRight = new EdgeEffect(context);
                mEdgeGlowTop = new EdgeEffect(context);
                mEdgeGlowBottom = new EdgeEffect(context);
            }
        } else {
            mEdgeGlowLeft = null;
            mEdgeGlowRight = null;
            mEdgeGlowTop = null;
            mEdgeGlowBottom = null;
        }
        super.setOverScrollMode(mode);
    }

    @SuppressWarnings({"SuspiciousNameCombination"})
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mEdgeGlowLeft != null && mEdgeGlowTop != null) {
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();

            if (!mEdgeGlowLeft.isFinished()) {
                final int restoreCount = canvas.save();
                final int height = getHeight() - getPaddingTop() - getPaddingBottom();

                canvas.rotate(270);
                canvas.translate(-height + getPaddingTop(), Math.min(0, scrollX));
                mEdgeGlowLeft.setSize(height, getWidth());
                if (mEdgeGlowLeft.draw(canvas)) {
                    invalidate();
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowRight.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth();
                final int height = getHeight() - getPaddingTop() - getPaddingBottom();

                canvas.rotate(90);
                canvas.translate(-getPaddingTop(),
                        -(Math.max(getScrollRangeX(), scrollX) + width));
                mEdgeGlowRight.setSize(height, width);
                if (mEdgeGlowRight.draw(canvas)) {
                    invalidate();
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowTop.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth() - getPaddingLeft() - getPaddingRight();

                canvas.translate(getPaddingLeft(), Math.min(0, scrollY));
                mEdgeGlowTop.setSize(width, getHeight());
                if (mEdgeGlowTop.draw(canvas)) {
                    invalidate();
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowBottom.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth() - getPaddingLeft() - getPaddingRight();
                final int height = getHeight();

                canvas.translate(-width + getPaddingLeft(),
                        Math.max(getScrollRangeY(), scrollY) + height);
                canvas.rotate(180, width, 0);
                mEdgeGlowBottom.setSize(width, height);
                if (mEdgeGlowBottom.draw(canvas)) {
                    invalidate();
                }
                canvas.restoreToCount(restoreCount);
            }
        }
    }

    private int clamp(int n, int my, int child) {
        if (my >= child || n < 0) {
            return 0;
        }
        if ((my + n) > child) {
            return child - my;
        }
        return n;
    }

    protected void invalidateParentIfNeeded() {
        if (isHardwareAccelerated() && getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
    }

    protected float getVerticalScrollFactor() {
        if (mVerticalScrollFactor == 0) {
            TypedValue outValue = new TypedValue();
            /*
            if (!getContext().getTheme().resolveAttribute(
                    com.android.internal.R.attr.listPreferredItemHeight, outValue, true)) {
                throw new IllegalStateException(
                        "Expected theme to define listPreferredItemHeight.");
            }
            */
            mVerticalScrollFactor = outValue.getDimension(
                    getContext().getResources().getDisplayMetrics());
        }
        return mVerticalScrollFactor;
    }

    protected float getHorizontalScrollFactor() {
        // TODO: Should use something else.
        return getVerticalScrollFactor();
    }

}
