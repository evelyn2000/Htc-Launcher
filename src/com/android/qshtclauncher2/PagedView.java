/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.qshtclauncher2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import com.mediatek.common.widget.IMtkWidget;
import android.widget.Scroller;


import java.util.ArrayList;

/**
 * An abstraction of the original Workspace which supports browsing through a
 * sequential list of "pages"
 */
public abstract class PagedView extends ViewGroup implements QsScreenIndicatorLister, ViewGroup.OnHierarchyChangeListener {
    private static final String TAG = "PagedView";
    private static final boolean DEBUG = false;
    public final static boolean QS_SUPPORT_LOOP_SLIDE = true;
    
    protected static final int INVALID_PAGE = (QS_SUPPORT_LOOP_SLIDE ? -2 : -1);

    // the min drag distance for a fling to register, to prevent random page shifts
    protected static final int MIN_LENGTH_FOR_FLING = 25;

    protected static final int PAGE_SNAP_ANIMATION_DURATION = 550;
    protected static final int MAX_PAGE_SNAP_DURATION = 750;
    protected static final int SLOW_PAGE_SNAP_ANIMATION_DURATION = 950;
    protected static final float NANOTIME_DIV = 1000000000.0f;

    private static final float OVERSCROLL_ACCELERATE_FACTOR = 2;
    private static final float OVERSCROLL_DAMP_FACTOR = 0.14f;

    private static final float RETURN_TO_ORIGINAL_PAGE_THRESHOLD = 0.33f;
    // The page is moved more than halfway, automatically move to the next page on touch up.
    private static final float SIGNIFICANT_MOVE_THRESHOLD = 0.4f;

    // The following constants need to be scaled based on density. The scaled versions will be
    // assigned to the corresponding member variables below.
    private static final int FLING_THRESHOLD_VELOCITY = 500;
    private static final int MIN_SNAP_VELOCITY = 1500;
    private static final int MIN_FLING_VELOCITY = 250;

    static final int AUTOMATIC_PAGE_SPACING = -1;

    protected int mFlingThresholdVelocity;
    protected int mMinFlingVelocity;
    protected int mMinSnapVelocity;

    protected float mDensity;
    protected float mSmoothingTime;
    protected float mTouchX;

    protected boolean mFirstLayout = true;

    protected int mCurrentPage;
    protected int mNextPage = INVALID_PAGE;
    protected int mMaxScrollX;
    protected Scroller mScroller;
    private VelocityTracker mVelocityTracker;

    protected float mDownMotionX;
    protected float mLastMotionX;
    protected float mLastMotionXRemainder;
    protected float mLastMotionY;
    protected float mTotalMotionX;
    private int mLastScreenCenter = -1;
    private int[] mChildOffsets;
    private int[] mChildRelativeOffsets;
    private int[] mChildOffsetsWithLayoutScale;

    protected final static int TOUCH_STATE_REST = 0;
    protected final static int TOUCH_STATE_SCROLLING = 1;
    protected final static int TOUCH_STATE_PREV_PAGE = 2;
    protected final static int TOUCH_STATE_NEXT_PAGE = 3;
    protected final static int TOUCH_STATE_SCALE = 4;
    protected final static float ALPHA_QUANTIZE_LEVEL = 0.0001f;

    protected int mTouchState = TOUCH_STATE_REST;
    protected boolean mForceScreenScrolled = false;

    protected OnLongClickListener mLongClickListener;

    protected boolean mAllowLongPress = true;

    protected int mTouchSlop;
    private int mPagingTouchSlop;
    private int mMaximumVelocity;
    private int mMinimumWidth;
    protected int mPageSpacing;
    protected int mPageLayoutPaddingTop;
    protected int mPageLayoutPaddingBottom;
    protected int mPageLayoutPaddingLeft;
    protected int mPageLayoutPaddingRight;
    protected int mPageLayoutWidthGap;
    protected int mPageLayoutHeightGap;
    protected int mCellCountX = 0;
    protected int mCellCountY = 0;
    protected boolean mCenterPagesVertically;
    protected boolean mAllowOverScroll = true;
    protected int mUnboundedScrollX;
    protected int[] mTempVisiblePagesRange = new int[2];
    protected boolean mForceDrawAllChildrenNextFrame;

    // mOverScrollX is equal to getScrollX() when we're within the normal scroll range. Otherwise
    // it is equal to the scaled overscroll position. We use a separate value so as to prevent
    // the screens from continuing to translate beyond the normal bounds.
    protected int mOverScrollX;

    // parameter that adjusts the layout to be optimized for pages with that scale factor
    protected float mLayoutScale = 1.0f;

    protected static final int INVALID_POINTER = -1;

    protected int mActivePointerId = INVALID_POINTER;

    private PageSwitchListener mPageSwitchListener;

    protected ArrayList<Boolean> mDirtyPageContent;

    // If true, syncPages and syncPageItems will be called to refresh pages
    protected boolean mContentIsRefreshable = true;

    // If true, modify alpha of neighboring pages as user scrolls left/right
    protected boolean mFadeInAdjacentScreens = true;

    // It true, use a different slop parameter (pagingTouchSlop = 2 * touchSlop) for deciding
    // to switch to a new page
    protected boolean mUsePagingTouchSlop = true;

    // If true, the subclass should directly update scrollX itself in its computeScroll method
    // (SmoothPagedView does this)
    protected boolean mDeferScrollUpdate = false;

    protected boolean mIsPageMoving = false;

    // All syncs and layout passes are deferred until data is ready.
    protected boolean mIsDataReady = false;

    // Scrolling indicator
    private ValueAnimator mScrollIndicatorAnimator;
    private View mScrollIndicator;
    private int mScrollIndicatorPaddingLeft;
    private int mScrollIndicatorPaddingRight;
    private boolean mHasScrollIndicator = true;
    private boolean mShouldShowScrollIndicator = false;
    private boolean mShouldShowScrollIndicatorImmediately = false;
    protected static final int sScrollIndicatorFadeInDuration = 150;
    protected static final int sScrollIndicatorFadeOutDuration = 650;
    protected static final int sScrollIndicatorFlashDuration = 650;
    private boolean mScrollingPaused = false;

    // If set, will defer loading associated pages until the scrolling settles
    protected boolean mDeferLoadAssociatedPagesUntilScrollCompletes;

    /// M: add for IMtkWidget. @{
    private static boolean sCanSendMessage = true;

    private static boolean sCanCallEnterAppWidget = true;
    /// @}
    private final boolean mIsEnableLoop;
    
    private final float mMinScrollDeltaX;
    
    protected final static boolean LOAD_DATA_STYLE_OLD = true;

    public interface PageSwitchListener {
        void onPageSwitch(View newPage, int newPageIndex);
    }

    public PagedView(Context context) {
        this(context, null);
    }

    public PagedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.PagedView, defStyle, 0);
        setPageSpacing(a.getDimensionPixelSize(R.styleable.PagedView_pageSpacing, 0));
        mPageLayoutPaddingTop = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutPaddingTop, 0);
        mPageLayoutPaddingBottom = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutPaddingBottom, 0);
        mPageLayoutPaddingLeft = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutPaddingLeft, 0);
        mPageLayoutPaddingRight = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutPaddingRight, 0);
        mPageLayoutWidthGap = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutWidthGap, 0);
        mPageLayoutHeightGap = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutHeightGap, 0);
        mScrollIndicatorPaddingLeft =
            a.getDimensionPixelSize(R.styleable.PagedView_scrollIndicatorPaddingLeft, 0);
        mScrollIndicatorPaddingRight =
            a.getDimensionPixelSize(R.styleable.PagedView_scrollIndicatorPaddingRight, 0);
        
        mHasScrollIndicator = a.getBoolean(R.styleable.PagedView_hasScrollIndicator, false);
        
        mIsEnableLoop = a.getBoolean(R.styleable.PagedView_scrollEnableLoop, false);
        a.recycle();

        setHapticFeedbackEnabled(false);
        init();
        
        mMinScrollDeltaX = mDensity * 5.0f;
    }

    /**
     * Initializes various states for this workspace.
     */
    protected void init() {
        mDirtyPageContent = new ArrayList<Boolean>();
        mDirtyPageContent.ensureCapacity(32);
        mScroller = new Scroller(getContext(), new ScrollInterpolator());
        mCurrentPage = 0;
        mCenterPagesVertically = true;

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mPagingTouchSlop = configuration.getScaledPagingTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mDensity = getResources().getDisplayMetrics().density;

        mFlingThresholdVelocity = (int) (FLING_THRESHOLD_VELOCITY * mDensity);
        mMinFlingVelocity = (int) (MIN_FLING_VELOCITY * mDensity);
        mMinSnapVelocity = (int) (MIN_SNAP_VELOCITY * mDensity);
        setOnHierarchyChangeListener(this);
    }

    public void setPageSwitchListener(PageSwitchListener pageSwitchListener) {
        mPageSwitchListener = pageSwitchListener;
        if (mPageSwitchListener != null) {
            mPageSwitchListener.onPageSwitch(getPageAt(mCurrentPage), mCurrentPage);
        }
    }
    
    // jz
    protected QsScreenIndicatorCallback mQsWorkspaceCallback;
    public void setQsScreenIndicatorCallback(QsScreenIndicatorCallback callback){
    	mQsWorkspaceCallback = callback;
    }
    /**
     * Called by subclasses to mark that data is ready, and that we can begin loading and laying
     * out pages.
     */
    protected void setDataIsReady() {
        mIsDataReady = true;
    }
    protected boolean isDataReady() {
        return mIsDataReady;
    }
    protected boolean isLoopingEnabled()
    {
    	if(QS_SUPPORT_LOOP_SLIDE)
    		return isLoopingEnabled(getPageCount());
//        boolean flag = true;
//        if(/*!isLoopingEnabledInCSC() || */getPageCount() < 2)
//            flag = false;
//        return flag;
    	
    	return false;
    }
    
    protected boolean isLoopingEnabled(int childCount){
    	if(QS_SUPPORT_LOOP_SLIDE && mIsEnableLoop && childCount > 1)
    		return true;
    	
    	return false;
    }

    /**
     * Returns the index of the currently displayed page.
     *
     * @return The index of the currently displayed page.
     */
    public int getCurrentPage() {
        return mCurrentPage;
    }
    public int getNextPage() {
        return (mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage;
    }

    public int getPageCount() {
        return getChildCount();
    }

    public View getPageAt(int index) {
        return getChildAt(index);
    }

    protected int indexToPage(int index) {
        return index;
    }
    
//    protected int pageIndexToChild(int index) {
//        return index;
//    }
    
    public View getCurrentPageView(){
    	if(mNextPage != INVALID_PAGE){
    		final int count = getPageCount();
        	if(isLoopingEnabled(count)){
        		//index = 0;
        		if(mNextPage >= count)
        			return getPageAt(0);
        		else if(mNextPage < 0)
        			return getPageAt(count - 1);
        	}
    		return getPageAt(mNextPage);
    	}
    	
    	return getPageAt(mCurrentPage);
    }
    
    public View getNextPageView(){
    	int index = (mNextPage != INVALID_PAGE ? mNextPage : mCurrentPage)+ 1;
    	final int count = getPageCount();
    	if(index >= count && isLoopingEnabled(count)){
    		index = 0;
    	}
    	return getPageAt(index);
    }
    
    public View getPreviousPageView(){
    	int index = (mNextPage != INVALID_PAGE ? mNextPage : mCurrentPage) - 1;
    	int count = getPageCount();
    	if(index < 0 && isLoopingEnabled(count))
    		index = count - 1;
    	return getPageAt(index);
    }

    /**
     * Updates the scroll of the current page immediately to its final scroll position.  We use this
     * in CustomizePagedView to allow tabs to share the same PagedView while resetting the scroll of
     * the previous tab page.
     */
    protected void updateCurrentPageScroll() {
    	int newX = 0;
        if (mCurrentPage >= 0 && mCurrentPage < getPageCount()) {
        	int offset = getChildOffset(indexToPage(mCurrentPage));
            int relOffset = getRelativeChildOffset(indexToPage(mCurrentPage));
            newX = offset - relOffset;
        }
        
        scrollTo(newX, 0);
        mScroller.setFinalX(newX);
        mScroller.forceFinished(true);
    }

    /**
     * Called during AllApps/Home transitions to avoid unnecessary work. When that other animation
     * ends, {@link #resumeScrolling()} should be called, along with
     * {@link #updateCurrentPageScroll()} to correctly set the final state and re-enable scrolling.
     */
    void pauseScrolling() {
        mScroller.forceFinished(true);
        cancelScrollingIndicatorAnimations();
        mScrollingPaused = true;
    }

    /**
     * Enables scrolling again.
     * @see #pauseScrolling()
     */
    void resumeScrolling() {
        mScrollingPaused = false;
    }
    /**
     * Sets the current page.
     */
    void setCurrentPage(int currentPage) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "setCurrentPage: currentPage = " + currentPage + ", mCurrentPage = "
                    + mCurrentPage + ", mScrollX = " + mScrollX + ", this = " + this);
        }

        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
        // don't introduce any checks like mCurrentPage == currentPage here-- if we change the
        // the default
        if (getPageCount() == 0) {
            return;
        }

        mCurrentPage = Math.max(0, Math.min(currentPage, getPageCount() - 1));
        updateCurrentPageScroll();
        updateScrollingIndicator();
        notifyPageSwitchListener();
        invalidate();
    }

    protected void notifyPageSwitchListener() {
        if (mPageSwitchListener != null) {
            mPageSwitchListener.onPageSwitch(getPageAt(mCurrentPage), mCurrentPage);
        }
        
        if(mQsWorkspaceCallback != null)
        	mQsWorkspaceCallback.onChangeToScreen(mCurrentPage);
    }

    protected void pageBeginMoving() {
        if (!mIsPageMoving) {
            mIsPageMoving = true;
            onPageBeginMoving();
        }
    }

    protected void pageEndMoving() {
        if (mIsPageMoving) {
            mIsPageMoving = false;
            onPageEndMoving();
        }
    }

    protected boolean isPageMoving() {
        return mIsPageMoving;
    }

    // a method that subclasses can override to add behavior
    protected void onPageBeginMoving() {
    }

    // a method that subclasses can override to add behavior
    protected void onPageEndMoving() {
    }

    /**
     * Registers the specified listener on each page contained in this workspace.
     *
     * @param l The listener used to respond to long clicks.
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mLongClickListener = l;
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            getChildAt(i).setOnLongClickListener(l);
        }
    }
    
    protected int getOffsetScrollX(){
    	return 0;
    }

    @Override
    public void scrollBy(int x, int y) {
    	//android.util.Log.i("QsLog", "scrollBy==x:"+x+"=mUnboundedScrollX:"+mUnboundedScrollX
    	//		+"=mTouchX:"+mTouchX);
        scrollTo(mUnboundedScrollX + x, getScrollY() + y);
    }

    @Override
    public void scrollTo(int x, int y) {
//    	android.util.Log.d("QsLog", "scrollTo==x:"+x+"=mUnboundedScrollX:"+mUnboundedScrollX
//    			+"=mTouchX:"+mTouchX
//    			+"=OffsetScrollX:"+getOffsetScrollX()
//    			+"=mMaxScrollX:"+mMaxScrollX);
    	
        mUnboundedScrollX = x;
        if(isLoopingEnabled()){
        	mOverScrollX = x;
        	super.scrollTo(x, y);
        	
        } else {
        	
	        if (x < getOffsetScrollX()) {
	            super.scrollTo(getOffsetScrollX(), y);
	            if (mAllowOverScroll) {
	                overScroll(x);
	            }
	        } else if (x > mMaxScrollX) {
	            super.scrollTo(mMaxScrollX, y);
	            if (mAllowOverScroll) {
	                overScroll(x - mMaxScrollX);
	            }
	        } else {
	            mOverScrollX = x;
	            super.scrollTo(x, y);
	        }
        }

        mTouchX = x;
        mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
    }

    // we moved this functionality to a helper function so SmoothPagedView can reuse it
    protected boolean computeScrollHelper() {
        if (mScroller.computeScrollOffset()) {
            // Don't bother scrolling if the page does not need to be moved
            if (getScrollX() != mScroller.getCurrX()
                || getScrollY() != mScroller.getCurrY()
                || mOverScrollX != mScroller.getCurrX()) {
                scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            }
            invalidate();
            return true;
        } else if (mNextPage != INVALID_PAGE) {
        	//android.util.Log.d("QsLog", "PagedView::computeScroll(1)====mNextPage:"+mNextPage);
        	if(QS_SUPPORT_LOOP_SLIDE && isLoopingEnabled()){
	        	//android.util.Log.w("QsLog", "PagedView::computeScroll()====mNextPage:"+mNextPage);
	        	if (mNextPage == -1) {
	        		mNextPage = mCurrentPage = getPageCount() - 1;
	                scrollTo(getOffsetScrollX()+mCurrentPage * getWidth(), getScrollY());
	                //updateWallpaperOffset();
	            } else if (mNextPage == getPageCount()) {
	            	mNextPage = mCurrentPage = 0;
	                scrollTo(getOffsetScrollX()+0, getScrollY());
	            } else {
	            	mCurrentPage = Math.max(0, Math.min(mNextPage, getPageCount() - 1));
	            } 
        	}
        	
        	moveInAppWidget(mNextPage);
            sCanCallEnterAppWidget = true;
            if (mNextPage != mCurrentPage) {
                leaveAppWidget(mCurrentPage);
                enterAppWidget(mNextPage);
            }
            mCurrentPage = Math.max(0, Math.min(mNextPage, getPageCount() - 1));
            mNextPage = INVALID_PAGE;
            notifyPageSwitchListener();

            // Load the associated pages if necessary
            if (mDeferLoadAssociatedPagesUntilScrollCompletes) {
                loadAssociatedPages(mCurrentPage);
                mDeferLoadAssociatedPagesUntilScrollCompletes = false;
            }

            // We don't want to trigger a page end moving unless the page has settled
            // and the user has stopped scrolling
            if (mTouchState == TOUCH_STATE_REST) {
                pageEndMoving();
            }

            // Notify the user when the page changes
            AccessibilityManager accessibilityManager = (AccessibilityManager)
                    getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (accessibilityManager.isEnabled()) {
                AccessibilityEvent ev =
                    AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED);
                ev.getText().add(getCurrentPageDescription());
                sendAccessibilityEventUnchecked(ev);
            }
            return true;
        }
        return false;
    }

    @Override
    public void computeScroll() {
        computeScrollHelper();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!mIsDataReady) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Workspace can only be used in EXACTLY mode.");
        }

        // Return early if we aren't given a proper dimension
        if (widthSize <= 0 || heightSize <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        /* Allow the height to be set as WRAP_CONTENT. This allows the particular case
         * of the All apps view on XLarge displays to not take up more space then it needs. Width
         * is still not allowed to be set as WRAP_CONTENT since many parts of the code expect
         * each page to have the same width.
         */
        int maxChildHeight = 0;

        final int verticalPadding = getPaddingTop() + getPaddingBottom();
        final int horizontalPadding = getPaddingLeft() + getPaddingRight();


        // The children are given the same width and height as the workspace
        // unless they were set to WRAP_CONTENT
        final int childCount = getChildCount();
        if (LauncherLog.DEBUG_LAYOUT) {
        	LauncherLog.e(TAG, "PagedView.onMeasure(): " + widthSize + ", " + heightSize+"==childCount:"+childCount);
        }
        
        for (int i = 0; i < childCount; i++) {
            // disallowing padding in paged view (just pass 0)
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            int childWidthMode;
            if (lp.width == LayoutParams.WRAP_CONTENT) {
                childWidthMode = MeasureSpec.AT_MOST;
            } else {
                childWidthMode = MeasureSpec.EXACTLY;
            }

            int childHeightMode;
            if (lp.height == LayoutParams.WRAP_CONTENT) {
                childHeightMode = MeasureSpec.AT_MOST;
            } else {
                childHeightMode = MeasureSpec.EXACTLY;
            }

            final int childWidthMeasureSpec =
                MeasureSpec.makeMeasureSpec(widthSize - horizontalPadding, childWidthMode);
            final int childHeightMeasureSpec =
                MeasureSpec.makeMeasureSpec(heightSize - verticalPadding, childHeightMode);

            child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            maxChildHeight = Math.max(maxChildHeight, child.getMeasuredHeight());
            if (LauncherLog.DEBUG_LAYOUT) {
                LauncherLog.e(TAG, "onMeasure() measure-child " + i 
                        + ", childWidthMode = " + childWidthMode + ", childHeightMode = "
                        + childHeightMode + ", this = " + this);
            }            
        }

        if (heightMode == MeasureSpec.AT_MOST) {
            heightSize = maxChildHeight + verticalPadding;
        }

        setMeasuredDimension(widthSize, heightSize);

        // We can't call getChildOffset/getRelativeChildOffset until we set the measured dimensions.
        // We also wait until we set the measured dimensions before flushing the cache as well, to
        // ensure that the cache is filled with good values.
        invalidateCachedOffsets();

        if (childCount > 0) {
            if (DEBUG) Log.d(TAG, "getRelativeChildOffset(): " + getMeasuredWidth() + ", "
                    + getChildWidth(0));

            // Calculate the variable page spacing if necessary
            if (mPageSpacing == AUTOMATIC_PAGE_SPACING) {
                // The gap between pages in the PagedView should be equal to the gap from the page
                // to the edge of the screen (so it is not visible in the current screen).  To
                // account for unequal padding on each side of the paged view, we take the maximum
                // of the left/right gap and use that as the gap between each page.
                int offset = getRelativeChildOffset(0);
                int spacing = Math.max(offset, widthSize - offset -
                        getChildAt(0).getMeasuredWidth());
                setPageSpacing(spacing);
            }
        }

        updateScrollingIndicatorPosition();

        if (childCount > 0) {
            mMaxScrollX = getChildOffset(childCount - 1) - getRelativeChildOffset(childCount - 1);
        } else {
            mMaxScrollX = 0;
        }
    }

    protected void scrollToNewPageWithoutMovingPages(int newCurrentPage) {
        int newX = getChildOffset(indexToPage(newCurrentPage)) - getRelativeChildOffset(indexToPage(newCurrentPage));
        int delta = newX - getScrollX();
        final int pageCount = getPageCount();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "Scroll to new page without moving pages: newCurrentPage = "
                    + newCurrentPage + ", newX = " + newX + ", mScrollX = " + mScrollX);
        }

        for (int i = 0; i < pageCount; i++) {
            View page = (View) getPageAt(i);
            page.setX(page.getX() + delta);
        }
        setCurrentPage(newCurrentPage);
    }

    // A layout scale of 1.0f assumes that the pages, in their unshrunken state, have a
    // scale of 1.0f. A layout scale of 0.8f assumes the pages have a scale of 0.8f, and
    // tightens the layout accordingly
    public void setLayoutScale(float childrenScale) {
        mLayoutScale = childrenScale;
        invalidateCachedOffsets();

        // Now we need to do a re-layout, but preserving absolute X and Y coordinates
        int childCount = getPageCount();
        float childrenX[] = new float[childCount];
        float childrenY[] = new float[childCount];
        for (int i = 0; i < childCount; i++) {
            final View child = getPageAt(i);
            childrenX[i] = child.getX();
            childrenY[i] = child.getY();
        }
        // Trigger a full re-layout (never just call onLayout directly!)
        int widthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY);
        requestLayout();
        measure(widthSpec, heightSpec);
        /// M: If call setLayoutScale before onAttachedToWindow, measure widthSize = 0, heightSize = 0, not layout.
        if (getMeasuredWidth() != 0 && getMeasuredHeight() != 0) {
            layout(getLeft(), getTop(), getRight(), getBottom());
        }
        for (int i = 0; i < childCount; i++) {
            final View child = getPageAt(i);
            child.setX(childrenX[i]);
            child.setY(childrenY[i]);
        }

        // Also, the page offset has changed  (since the pages are now smaller);
        // update the page offset, but again preserving absolute X and Y coordinates
        scrollToNewPageWithoutMovingPages(mCurrentPage);
    }

    public void setPageSpacing(int pageSpacing) {
        mPageSpacing = pageSpacing;
        invalidateCachedOffsets();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!mIsDataReady) {
            return;
        }
        //android.util.Log.e("QsLog", "PageView::onLayout()===ChildCount:"+getChildCount());
        if (DEBUG) Log.d(TAG, "PagedView.onLayout()");
        final int verticalPadding = getPaddingTop() + getPaddingBottom();
        final int childCount = getChildCount();
        int childLeft = getRelativeChildOffset(0);

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                final int childWidth = getScaledMeasuredWidth(child);
                final int childHeight = child.getMeasuredHeight();
                int childTop = getPaddingTop();
                if (mCenterPagesVertically) {
                    childTop += ((getMeasuredHeight() - verticalPadding) - childHeight) / 2;
                }

                if (DEBUG) Log.d(TAG, "\tlayout-child" + i + ": " + childLeft + ", " + childTop);
                child.layout(childLeft, childTop,
                        childLeft + child.getMeasuredWidth(), childTop + childHeight);
                childLeft += childWidth + mPageSpacing;
            }
        }

        if (mFirstLayout && mCurrentPage >= 0 && mCurrentPage < getPageCount()) {
            setHorizontalScrollBarEnabled(false);
            updateCurrentPageScroll();
            setHorizontalScrollBarEnabled(true);
            mFirstLayout = false;
        }
    }

    protected void screenScrolled(int screenCenter) {
        if (isScrollingIndicatorEnabled()) {
            updateScrollingIndicator();
        }
        boolean isInOverscroll = mOverScrollX < 0 || mOverScrollX > mMaxScrollX;

        if (mFadeInAdjacentScreens && !isInOverscroll) {
            for (int i = 0; i < getPageCount(); i++) {
                View child = getPageAt(i);
                if (child != null) {
                    float scrollProgress = getScrollProgress(screenCenter, child, i);
                    float alpha = 1 - Math.abs(scrollProgress);
                    child.setAlpha(alpha);
                }
            }
            invalidate();
        }
    }
    
    protected boolean isValidPageIndex(int page){
    	return (page >= 0 && page < getPageCount());
    }

    @Override
    public void onChildViewAdded(View parent, View child) {
        // This ensures that when children are added, they get the correct transforms / alphas
        // in accordance with any scroll effects.
    	//android.util.Log.e("QsLog", "onChildViewAdded()===ChildCount:"+getChildCount());
        mForceScreenScrolled = true;
        if(mQsWorkspaceCallback != null)
        	mQsWorkspaceCallback.onPageCountChanged(getPageCount());
        invalidate();
        invalidateCachedOffsets();
    }

    @Override
    public void onChildViewRemoved(View parent, View child){
    	mForceScreenScrolled = true;
        
        if(mQsWorkspaceCallback != null && isValidPageIndex(super.indexOfChild(child)))
        	mQsWorkspaceCallback.onPageCountChanged(getPageCount()-1);
        //android.util.Log.i("QsLog", "onChildViewRemoved==PageCount:"+getPageCount()+"==parent:"+((ViewGroup)parent).getChildCount());
        invalidate();
		//invalidateCachedOffsets();
    }

    protected void invalidateCachedOffsets() {
        int count = getChildCount();
        
        if (count == 0) {
            mChildOffsets = null;
            mChildRelativeOffsets = null;
            mChildOffsetsWithLayoutScale = null;
            return;
        }

        mChildOffsets = new int[count];
        mChildRelativeOffsets = new int[count];
        mChildOffsetsWithLayoutScale = new int[count];
        for (int i = 0; i < count; i++) {
            mChildOffsets[i] = -1;
            mChildRelativeOffsets[i] = -1;
            mChildOffsetsWithLayoutScale[i] = -1;
        }
    }

    protected int getChildOffset(int index) {
    	if(index < 0)
    		return 0;
        int[] childOffsets = Float.compare(mLayoutScale, 1f) == 0 ?
                mChildOffsets : mChildOffsetsWithLayoutScale;

        if (childOffsets != null && childOffsets[index] != -1) {
            return childOffsets[index];
        } else {
            if (getChildCount() == 0)
                return 0;

            int offset = getRelativeChildOffset(0);
            for (int i = 0; i < index; ++i) {
                offset += getScaledMeasuredWidth(getChildAt(i)) + mPageSpacing;
            }
            if (childOffsets != null) {
                childOffsets[index] = offset;
            }
            return offset;
        }
    }

    protected int getRelativeChildOffset(int index) {
    	if(index < 0)
    		return 0;
    	
        if (mChildRelativeOffsets != null && mChildRelativeOffsets[index] != -1) {
            if (LauncherLog.DEBUG_DRAW) {
                LauncherLog.d(TAG, "getRelativeChildOffset 1: index = " + index +
                        ", mChildRelativeOffsets[" + index + "] = " + mChildRelativeOffsets[index] +
                        ", this = " + this);
            }
            return mChildRelativeOffsets[index];
        } else {
            final int padding = getPaddingLeft() + getPaddingRight();
            final int offset = getPaddingLeft() +
                    (getMeasuredWidth() - padding - getChildWidth(index)) / 2;
            if (mChildRelativeOffsets != null) {
                mChildRelativeOffsets[index] = offset;
            }
            if (LauncherLog.DEBUG_DRAW) {
                LauncherLog.d(TAG, "getRelativeChildOffset 2: index = " + index
                        + ", mPaddingLeft = " + mPaddingLeft + ", mPaddingRight = " + mPaddingRight
                        + ", padding = " + padding + ", offset = " + offset + ", measure width = "
                        + getMeasuredWidth() + ", this = " + this);
            }
            return offset;
        }
    }

    protected int getScaledMeasuredWidth(View child) {
        // This functions are called enough times that it actually makes a difference in the
        // profiler -- so just inline the max() here
        final int measuredWidth = child == null ? 0 : child.getMeasuredWidth();
        if(LauncherLog.QS_STYLE_HTC){ // dont show left or right screen...
        	return measuredWidth;
        } else {
	        final int minWidth = mMinimumWidth;
	        final int maxWidth = (minWidth > measuredWidth) ? minWidth : measuredWidth;
	        return (int) (maxWidth * mLayoutScale + 0.5f);
        }
    }

    protected void getVisiblePages(int[] range) {
        final int pageCount = getPageCount();
        if (pageCount > 0) {
            final int screenWidth = getMeasuredWidth();
            int leftScreen = 0;
            int rightScreen = 0;
            View currPage = getPageAt(leftScreen);
            while (leftScreen < pageCount - 1 &&
                    currPage.getX() + currPage.getWidth() -
                    currPage.getPaddingRight() < getScrollX()) {
                leftScreen++;
                currPage = getPageAt(leftScreen);
            }
            rightScreen = leftScreen;
            currPage = getPageAt(rightScreen + 1);
            while (rightScreen < pageCount - 1 &&
                    currPage.getX() - currPage.getPaddingLeft() < getScrollX() + screenWidth) {
                rightScreen++;
                currPage = getPageAt(rightScreen + 1);
            }
            range[0] = leftScreen;
            range[1] = rightScreen;
        } else {
            range[0] = -1;
            range[1] = -1;
        }
    }

	private boolean isScreenNoValid(int screen) {
		if(screen >= 0 && screen < getPageCount()){
//			if(Workspace.QS_SUPPORT_LOCK_CELLLAYOUT){
//				if(getPageAt(screen).getVisibility() == View.GONE){
//					return false;
//				}
//			}
			return true;
		}
		
		return false;
	}
	
    protected boolean shouldDrawChild(View child) {
        return child.getAlpha() > 0;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int halfScreenSize = getMeasuredWidth() / 2;
        // mOverScrollX is equal to getScrollX() when we're within the normal scroll range.
        // Otherwise it is equal to the scaled overscroll position.
        int screenCenter = mOverScrollX + halfScreenSize;

        if (screenCenter != mLastScreenCenter || mForceScreenScrolled) {
            // set mForceScreenScrolled before calling screenScrolled so that screenScrolled can
            // set it for the next frame
            mForceScreenScrolled = false;
            screenScrolled(screenCenter);
            mLastScreenCenter = screenCenter;
        }
		
		if(QS_SUPPORT_LOOP_SLIDE && isLoopingEnabled()){
			final boolean fastDraw = mTouchState != TOUCH_STATE_SCROLLING && mNextPage == INVALID_PAGE;
	        // If we are not scrolling or flinging, draw only the current screen
	        if (fastDraw) {
	            drawChild(canvas, getPageAt(mCurrentPage), getDrawingTime());
	        } else {
	        	
	        	long drawingTime = getDrawingTime();
	            int width = getWidth();
	            float scrollPos = (float) getScrollX() / width;
	            boolean endlessScrolling = true;
	
	            int leftScreen;
	            int rightScreen;
	            boolean isScrollToRight = false;
	            int childCount = getPageCount();
	            if (scrollPos < 0 && endlessScrolling) {
	                leftScreen = childCount - 1;
	                rightScreen = 0;
	            } else {
	                leftScreen = Math.min( (int) scrollPos, childCount - 1 );
	                rightScreen = leftScreen + 1;
	                if (endlessScrolling) {
	                    rightScreen = rightScreen % childCount;
	                    isScrollToRight = true;
	                }
	            }
	
	            if (isScreenNoValid(leftScreen)) {
	                if (rightScreen == 0 && !isScrollToRight) { 
	                    int offset = childCount * width;
	                    canvas.translate(-offset, 0);
	                    drawChild(canvas, getPageAt(leftScreen), drawingTime);
	                    canvas.translate(+offset, 0);
	                } else {
	                    drawChild(canvas, getPageAt(leftScreen), drawingTime);
	                }
	            }
	            if (scrollPos != leftScreen && isScreenNoValid(rightScreen)) {
	                if (endlessScrolling && rightScreen == 0  && isScrollToRight) {
	                     int offset = childCount * width;
	                     canvas.translate(+offset, 0);
	                     drawChild(canvas, getPageAt(rightScreen), drawingTime);
	                     canvas.translate(-offset, 0);
	                } else {
	                    drawChild(canvas, getPageAt(rightScreen), drawingTime);
	                }
	            }
	        }
			return;
		}

        // Find out which screens are visible; as an optimization we only call draw on them
        final int pageCount = getPageCount();
        if (LauncherLog.DEBUG_MOTION || LauncherLog.DEBUG_DRAW) {
            LauncherLog.d(TAG, "dispatchDraw: mScrollX = " + mScrollX + ", screenCenter = "
                    + screenCenter + ", mOverScrollX = " + mOverScrollX + ", pageCount = "
                    + pageCount + ", mLeft = " + mLeft + ", mRight = " + mRight + ", this = " + this);
        }

        if (pageCount > 0) {
            getVisiblePages(mTempVisiblePagesRange);
            final int leftScreen = mTempVisiblePagesRange[0];
            final int rightScreen = mTempVisiblePagesRange[1];
            if (leftScreen != -1 && rightScreen != -1) {
                final long drawingTime = getDrawingTime();
                // Clip to the bounds
                canvas.save();
                canvas.clipRect(getScrollX(), getScrollY(), getScrollX() + getRight() - getLeft(),
                        getScrollY() + getBottom() - getTop());


                for (int i = getPageCount() - 1; i >= 0; i--) {
                    final View v = getPageAt(i);
                    if (v != null && (mForceDrawAllChildrenNextFrame ||
                               (leftScreen <= i && i <= rightScreen && shouldDrawChild(v)))) {
                        drawChild(canvas, v, drawingTime);
                    }
                }
                mForceDrawAllChildrenNextFrame = false;
                canvas.restore();
            }
        }
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        int page = indexToPage(indexOfChild(child));
        if (page != mCurrentPage || !mScroller.isFinished()) {
            snapToPage(page);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        int focusablePage;
        if (mNextPage != INVALID_PAGE) {
            focusablePage = mNextPage;
        } else {
            focusablePage = mCurrentPage;
        }
        View v = getPageAt(focusablePage);
        if (v != null) {
            return v.requestFocus(direction, previouslyFocusedRect);
        }
        return false;
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        if (direction == View.FOCUS_LEFT) {
        	if(isLoopingEnabled()){
        		if (getCurrentPage() > -1) {
	                snapToPage(getCurrentPage() - 1);
	                return true;
	            }
        		
//        		if (mScroller.isFinished()) {
//    	            if (mCurrentPage > -1) snapToPage(mCurrentPage - 1);
//    	        } else {
//    	            if (mNextPage > -1) snapToPage(mNextPage - 1);
//    	        }
        	} else {
	            if (getCurrentPage() > 0) {
	                snapToPage(getCurrentPage() - 1);
	                return true;
	            }
        	}
        } else if (direction == View.FOCUS_RIGHT) {
        	if(isLoopingEnabled()){
        		
        		if (getCurrentPage() < getPageCount()) {
	                snapToPage(getCurrentPage() + 1);
	                return true;
	            }
        		
        	} else {
        	
	            if (getCurrentPage() < getPageCount() - 1) {
	                snapToPage(getCurrentPage() + 1);
	                return true;
	            }
        	}
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (mCurrentPage >= 0 && mCurrentPage < getPageCount()) {
            getPageAt(mCurrentPage).addFocusables(views, direction, focusableMode);
        }
        if (direction == View.FOCUS_LEFT) {
            if (mCurrentPage > 0) {
                getPageAt(mCurrentPage - 1).addFocusables(views, direction, focusableMode);
            }
        } else if (direction == View.FOCUS_RIGHT) {
            if (mCurrentPage < getPageCount() - 1) {
                getPageAt(mCurrentPage + 1).addFocusables(views, direction, focusableMode);
            }
        }
    }

    /**
     * If one of our descendant views decides that it could be focused now, only
     * pass that along if it's on the current page.
     *
     * This happens when live folders requery, and if they're off page, they
     * end up calling requestFocus, which pulls it on page.
     */
    @Override
    public void focusableViewAvailable(View focused) {
        View current = getPageAt(mCurrentPage);
        View v = focused;
        while (true) {
            if (v == current) {
                super.focusableViewAvailable(focused);
                return;
            }
            if (v == this) {
                return;
            }
            ViewParent parent = v.getParent();
            if (parent instanceof View) {
                v = (View)v.getParent();
            } else {
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            // We need to make sure to cancel our long press if
            // a scrollable widget takes over touch events
            final View currentPage = getPageAt(mCurrentPage);
            currentPage.cancelLongPress();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    /**
     * Return true if a tap at (x, y) should trigger a flip to the previous page.
     */
    protected boolean hitsPreviousPage(float x, float y) {
        return (x < getRelativeChildOffset(indexToPage(mCurrentPage)) - mPageSpacing);
    }

    /**
     * Return true if a tap at (x, y) should trigger a flip to the next page.
     */
    protected boolean hitsNextPage(float x, float y) {
        return  (x > (getMeasuredWidth() - getRelativeChildOffset(indexToPage(mCurrentPage)) + mPageSpacing));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (LauncherLog.DEBUG_MOTION) {
            LauncherLog.d(TAG, "onInterceptTouchEvent: ev = " + ev +
                    ", mScrollX = " + mScrollX + ", this = " + this);
        }

        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onTouchEvent will be called and we do the actual
         * scrolling there.
         */
        acquireVelocityTrackerAndAddMovement(ev);

        // Skip touch handling if there are no pages to swipe
        if (getPageCount() <= 0) {
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "There are no pages to swipe, page count = " + getPageCount());
            }
        	return super.onInterceptTouchEvent(ev);
        }

        /*
         * Shortcut the most recurring case: the user is in the dragging
         * state and he is moving his finger.  We want to intercept this
         * motion.
         */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) &&
                (mTouchState == TOUCH_STATE_SCROLLING)) {
            LauncherLog.d(TAG, "onInterceptTouchEvent: touch move during scrolling.");
            return true;
        }

        switch (action & MotionEvent.ACTION_MASK) {
	        case MotionEvent.ACTION_POINTER_DOWN:
	        	if(isSupportMultiScale() && mTouchState == TOUCH_STATE_REST)
	        		mTouchState = TOUCH_STATE_SCALE;
	        	
	        	onHandleMultiPointEvent(MotionEvent.ACTION_POINTER_DOWN, ev);
	        	break;
            case MotionEvent.ACTION_MOVE: {
                /*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */
                if (mActivePointerId != INVALID_POINTER) {
                    determineScrollingStart(ev);
                    break;
                }
                // if mActivePointerId is INVALID_POINTER, then we must have missed an ACTION_DOWN
                // event. in that case, treat the first occurence of a move event as a ACTION_DOWN
                // i.e. fall through to the next case (don't break)
                // (We sometimes miss ACTION_DOWN events in Workspace because it ignores all events
                // while it's small- this was causing a crash before we checked for INVALID_POINTER)
            }

            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                // Remember location of down touch
                mDownMotionX = x;
                mLastMotionX = x;
                mLastMotionY = y;
                mLastMotionXRemainder = 0;
                mTotalMotionX = 0;
                mActivePointerId = ev.getPointerId(0);
                mAllowLongPress = true;
                
                onHandleMultiPointEvent(MotionEvent.ACTION_DOWN, ev);
                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't.  mScroller.isFinished should be false when
                 * being flinged.
                 */
                final int xDist = Math.abs(mScroller.getFinalX() - mScroller.getCurrX());
                final boolean finishedScrolling = (mScroller.isFinished() || xDist < mTouchSlop);
                if (finishedScrolling) {
                    mTouchState = TOUCH_STATE_REST;
                    mScroller.abortAnimation();
                } else {
                    mTouchState = TOUCH_STATE_SCROLLING;
                }

                // check if this can be the beginning of a tap on the side of the pages
                // to scroll the current page
                if (mTouchState != TOUCH_STATE_PREV_PAGE && mTouchState != TOUCH_STATE_NEXT_PAGE) {
                    if (getPageCount() > 0) {
                        if (hitsPreviousPage(x, y)) {
                            mTouchState = TOUCH_STATE_PREV_PAGE;
                        } else if (hitsNextPage(x, y)) {
                            mTouchState = TOUCH_STATE_NEXT_PAGE;
                        }
                    }
                }
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "onInterceptTouchEvent touch down: finishedScrolling = "
                            + finishedScrolling + ", mScrollX = " + mScrollX + ", xDist = " + xDist
                            + ", mTouchState = " + mTouchState + ", this = " + this);
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                /*
                 * It means the workspace is in the middle if the scrollX can
                 * not be divided by the width of its child, need to snap to edge.
                 */
                snapToDestination();
                mTouchState = TOUCH_STATE_REST;
                mAllowLongPress = false;
                mActivePointerId = INVALID_POINTER;
                releaseVelocityTracker();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                releaseVelocityTracker();
                break;
        }

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return mTouchState != TOUCH_STATE_REST;
    }

    protected void determineScrollingStart(MotionEvent ev) {
        determineScrollingStart(ev, 1.0f);
    }

    /*
     * Determines if we should change the touch state to start scrolling after the
     * user moves their touch point too far.
     */
    protected void determineScrollingStart(MotionEvent ev, float touchSlopScale) {
        /*
         * Locally do absolute value. mLastMotionX is set to the y value
         * of the down event.
         */
        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (pointerIndex == -1 || (isSupportMultiScale() && mTouchState == TOUCH_STATE_REST && ev.getPointerCount() > 1)) {
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "(PagedView)determineScrollingStart pointerIndex == -1.");
            }
            return;
        }
        final float x = ev.getX(pointerIndex);
        final float y = ev.getY(pointerIndex);
        final int xDiff = (int) Math.abs(x - mLastMotionX);
        final int yDiff = (int) Math.abs(y - mLastMotionY);

        final int touchSlop = Math.round(touchSlopScale * mTouchSlop);
        boolean xPaged = xDiff > mPagingTouchSlop;
        boolean xMoved = xDiff > touchSlop;
        boolean yMoved = yDiff > touchSlop;

        if (xMoved || xPaged || yMoved) {
            if (mUsePagingTouchSlop ? xPaged : xMoved) {
                // Scroll if the user moved far enough along the X axis
                mTouchState = TOUCH_STATE_SCROLLING;
                mTotalMotionX += Math.abs(mLastMotionX - x);
                mLastMotionX = x;
                mLastMotionXRemainder = 0;
                mTouchX = getScrollX();
                mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
                pageBeginMoving();
            }
            // Either way, cancel any pending longpress
            cancelCurrentPageLongPress();
        }
    }

    protected void cancelCurrentPageLongPress() {
        if (mAllowLongPress) {
            mAllowLongPress = false;
            // Try canceling the long press. It could also have been scheduled
            // by a distant descendant, so use the mAllowLongPress flag to block
            // everything
            final View currentPage = getPageAt(mCurrentPage);
            if (currentPage != null) {
                currentPage.cancelLongPress();
            }
        }
    }

    protected float getScrollProgress(int screenCenter, View v, int page) {
        final int halfScreenSize = getMeasuredWidth() / 2;

        int totalDistance = getScaledMeasuredWidth(v) + mPageSpacing;
        int delta = screenCenter - (getChildOffset(indexToPage(page)) -
                getRelativeChildOffset(indexToPage(page)) + halfScreenSize);

        float scrollProgress = delta / (totalDistance * 1.0f);
        scrollProgress = Math.min(scrollProgress, 1.0f);
        scrollProgress = Math.max(scrollProgress, -1.0f);
        return scrollProgress;
    }

    // This curve determines how the effect of scrolling over the limits of the page dimishes
    // as the user pulls further and further from the bounds
    private float overScrollInfluenceCurve(float f) {
        f -= 1.0f;
        return f * f * f + 1.0f;
    }

    protected void acceleratedOverScroll(float amount) {
        int screenSize = getMeasuredWidth();

        // We want to reach the max over scroll effect when the user has
        // over scrolled half the size of the screen
        float f = OVERSCROLL_ACCELERATE_FACTOR * (amount / screenSize);

        if (f == 0) return;

        // Clamp this factor, f, to -1 < f < 1
        if (Math.abs(f) >= 1) {
            f /= Math.abs(f);
        }

        int overScrollAmount = (int) Math.round(f * screenSize);
        if (amount < 0) {
            mOverScrollX = overScrollAmount;
            super.scrollTo(getOffsetScrollX(), getScrollY());
        } else {
            mOverScrollX = mMaxScrollX + overScrollAmount;
            super.scrollTo(mMaxScrollX, getScrollY());
        }
        invalidate();
    }

    protected void dampedOverScroll(float amount) {
        int screenSize = getMeasuredWidth();

        float f = (amount / screenSize);

        if (f == 0) return;
        f = f / (Math.abs(f)) * (overScrollInfluenceCurve(Math.abs(f)));

        // Clamp this factor, f, to -1 < f < 1
        if (Math.abs(f) >= 1) {
            f /= Math.abs(f);
        }

        int overScrollAmount = (int) Math.round(OVERSCROLL_DAMP_FACTOR * f * screenSize);
        if (amount < 0) {
            mOverScrollX = overScrollAmount;
            super.scrollTo(getOffsetScrollX(), getScrollY());
        } else {
            mOverScrollX = mMaxScrollX + overScrollAmount;
            super.scrollTo(mMaxScrollX, getScrollY());
        }
        invalidate();
    }

    protected void overScroll(float amount) {
        dampedOverScroll(amount);
    }

    protected float maxOverScroll() {
        // Using the formula in overScroll, assuming that f = 1.0 (which it should generally not
        // exceed). Used to find out how much extra wallpaper we need for the over scroll effect
        float f = 1.0f;
        f = f / (Math.abs(f)) * (overScrollInfluenceCurve(Math.abs(f)));
        return OVERSCROLL_DAMP_FACTOR * f;
    }
    
    protected boolean onHandleMultiPointEvent(int action, MotionEvent ev){
    	return false;
    }
    
    protected boolean isSupportMultiScale(){
    	return false;
    }
    
    protected boolean isStartScalingMotion(){
    	return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Skip touch handling if there are no pages to swipe
        if (getPageCount() <= 0) {
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "(PagedView)onTouchEvent getPageCount() = " + getPageCount());
            }
        	return super.onTouchEvent(ev);
        }

        acquireVelocityTrackerAndAddMovement(ev);

        final int action = ev.getAction();

        switch (action & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
            /*
             * If being flinged and user touches, stop the fling. isFinished
             * will be false if being flinged.
             */
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }

            // Remember where the motion event started
            mDownMotionX = mLastMotionX = ev.getX();
            mLastMotionXRemainder = 0;
            mTotalMotionX = 0;
            mActivePointerId = ev.getPointerId(0);
            if (LauncherLog.DEBUG_MOTION) {
                LauncherLog.d(TAG, "Touch down: mDownMotionX = " + mDownMotionX
                        + ", mTouchState = " + mTouchState + ", mCurrentPage = " + mCurrentPage
                        + ", mScrollX = " + mScrollX + ", this = " + this);
            }
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                pageBeginMoving();
            }
            break;

        case MotionEvent.ACTION_MOVE:
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                // M: Just begin to move, call the appropriate callback for the current page.
                if (sCanSendMessage) {
                    boolean result = moveOutAppWidget(mCurrentPage);
                    if (!result) {
                        if (LauncherLog.DEBUG_SURFACEWIDGET) {
                            LauncherLog.d(Launcher.TAG_SURFACEWIDGET, "moveOut false.");
                        }
                        return true;
                    }
                    if (LauncherLog.DEBUG_SURFACEWIDGET) {
                        LauncherLog.d(Launcher.TAG_SURFACEWIDGET, "moveOut true.");
                    }
                }

                // Scroll to follow the motion event
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                final float x = ev.getX(pointerIndex);
                final float deltaX = mLastMotionX + mLastMotionXRemainder - x;
                
                /// M: Just begin to move, call the appropriate callback for the current page.
                if (sCanCallEnterAppWidget) {
                    int page = mCurrentPage;
                    if (deltaX < 0) {
                        page = mCurrentPage > 0 ? mCurrentPage - 1 : 0;
                    } else {
                        page = mCurrentPage < getPageCount() - 1 ? mCurrentPage + 1 : getPageCount() - 1;
                    }
                    sCanCallEnterAppWidget = false;
                    enterAppWidget(page);
                }

                mTotalMotionX += Math.abs(deltaX);

                // Only scroll and update mLastMotionX if we have moved some discrete amount.  We
                // keep the remainder because we are actually testing if we've moved from the last
                // scrolled position (which is discrete).
                //if (Math.abs(deltaX) >= 1.0f) {
                if(Math.abs(deltaX) >= mMinScrollDeltaX){
                    mTouchX += deltaX;
                    mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
                    if (!mDeferScrollUpdate) {
                        scrollBy((int) deltaX, 0);
                        
                    } else {
                    	if(QS_SUPPORT_LOOP_SLIDE && isLoopingEnabled()){
//                    		android.util.Log.d("QsLog", "onTouchEvent==ACTION_MOVE==mTouchX:"+mTouchX+"==deltaX:"+deltaX+"==scx:"+getScrollX()
//                    				+"==w:"+super.getWidth());
	                    	if (deltaX < 0) {
	                            if (mTouchX > 0) {
	                                scrollBy((int) Math.max(-getScrollX(), deltaX), 0);
	                            }
	                            else if(-mTouchX < super.getWidth())
	                            {
	                            	scrollBy((int) Math.max(-super.getWidth()-mTouchX, deltaX), 0);
	                            }
	                        } else if (deltaX > 0) {
	                            final int availableToScroll = (int)(getPageCount() * getWidth()
	                                    - mTouchX + getWidth());
	                            //android.util.Log.d("QsLog", "onTouchEvent==ACTION_MOVE==availableToScroll:"+availableToScroll+"==deltaX:"+deltaX+"==scx:"+getScrollX());
	                            
	                            if (availableToScroll > 0) {
	
	                                scrollBy((int) Math.min(availableToScroll, deltaX), 0);
	                            }
	                        } else {
	                            awakenScrollBars();
	                        }
                    	} else {
                    		invalidate();
                    	}
                    }
                    mLastMotionX = x;
                    mLastMotionXRemainder = deltaX - (int) deltaX;
                } else {
                    awakenScrollBars();
                }

                if (LauncherLog.DEBUG_MOTION) {
                    LauncherLog.d(TAG, "Touch move scroll: x = " + x + ", deltaX = " + deltaX
                            + ", mTotalMotionX = " + mTotalMotionX + ", mLastMotionX = "
                            + mLastMotionX + ", mCurrentPage = " + mCurrentPage + ",mTouchX = "
                            + mTouchX + " ,mLastMotionX = " + mLastMotionX + ", mScrollX = " + mScrollX);
                }
            } else {
            	
            	if(onHandleMultiPointEvent(MotionEvent.ACTION_MOVE, ev))
            		break;
            		
//            	if(isStartScalingMotion())
//            		break;
            	
                determineScrollingStart(ev);
            }
            break;

        case MotionEvent.ACTION_UP:
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                final int activePointerId = mActivePointerId;
                final int pointerIndex = ev.findPointerIndex(activePointerId);
                final float x = ev.getX(pointerIndex);
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocityX = (int) velocityTracker.getXVelocity(activePointerId);
                final int deltaX = (int) (x - mDownMotionX);
                final int pageWidth = getScaledMeasuredWidth(getPageAt(mCurrentPage));
                boolean isSignificantMove = Math.abs(deltaX) > pageWidth *
                        SIGNIFICANT_MOVE_THRESHOLD;

                mTotalMotionX += Math.abs(mLastMotionX + mLastMotionXRemainder - x);

                boolean isFling = mTotalMotionX > MIN_LENGTH_FOR_FLING &&
                        Math.abs(velocityX) > mFlingThresholdVelocity;

                if (LauncherLog.DEBUG_MOTION) {
                    LauncherLog.d(TAG, "Touch up scroll: x = " + x + ", deltaX = " + deltaX
                            + ", mTotalMotionX = " + mTotalMotionX + ", mLastMotionX = "
                            + mLastMotionX + ", velocityX = " + velocityX + ", mCurrentPage = "
                            + mCurrentPage + ", pageWidth = " + pageWidth + ", isFling = "
                            + isFling + ", isSignificantMove = " + isSignificantMove
                            + ", mScrollX = " + getScrollX());
                }
                // In the case that the page is moved far to one direction and then is flung
                // in the opposite direction, we use a threshold to determine whether we should
                // just return to the starting page, or if we should skip one further.
                boolean returnToOriginalPage = false;
                if (Math.abs(deltaX) > pageWidth * RETURN_TO_ORIGINAL_PAGE_THRESHOLD &&
                        Math.signum(velocityX) != Math.signum(deltaX) && isFling) {
                    if (LauncherLog.DEBUG_MOTION) {
                        LauncherLog.d(TAG, "Return to origin page: deltaX = " + deltaX
                                + ", velocityX = " + velocityX + ", isFling = " + isFling);
                    }
                    returnToOriginalPage = true;
                }

                int finalPage;
                // We give flings precedence over large moves, which is why we short-circuit our
                // test for a large move if a fling has been registered. That is, a large
                // move to the left and fling to the right will register as a fling to the right.
                if (((isSignificantMove && deltaX > 0 && !isFling) ||
                        (isFling && velocityX > 0)) && mCurrentPage > (isLoopingEnabled() ? -1 : 0)) {
                    finalPage = returnToOriginalPage ? mCurrentPage : mCurrentPage - 1;
                    if (LauncherLog.DEBUG_MOTION) {
                        LauncherLog.d(TAG, "1 finalPage = " + finalPage + ",mCurrentPage = "
                                + mCurrentPage + ",velocityX = " + velocityX);
                    }
                    snapToPageWithVelocity(finalPage, velocityX);
                } else if (((isSignificantMove && deltaX < 0 && !isFling) ||
                        (isFling && velocityX < 0)) &&
                        mCurrentPage < getPageCount() - (isLoopingEnabled() ? 0 : 1)) {
                    finalPage = returnToOriginalPage ? mCurrentPage : mCurrentPage + 1;
                    if (LauncherLog.DEBUG_MOTION) {
                        LauncherLog.d(TAG, "2 finalPage = " + finalPage + ",mCurrentPage = "
                                + mCurrentPage + ",velocityX = " + velocityX);
                    }
                    snapToPageWithVelocity(finalPage, velocityX);
                } else {
                	if (LauncherLog.DEBUG_MOTION) {
                        LauncherLog.w(TAG, "3 mCurrentPage = " + mCurrentPage + ",mScrollX = " + mScrollX
						+"==deltaX:"+deltaX+"==w"+(0 - pageWidth * 0.7f));
                    }
                    if(isLoopingEnabled() && (deltaX < (0 - pageWidth * 0.7f)))
                    	snapToPage(-1);
                    else
                    	snapToDestination();
                }
            } else if (mTouchState == TOUCH_STATE_PREV_PAGE) {
                // at this point we have not moved beyond the touch slop
                // (otherwise mTouchState would be TOUCH_STATE_SCROLLING), so
                // we can just page
                int nextPage = Math.max(0, mCurrentPage - 1);
                if (LauncherLog.DEBUG_MOTION) {
                    LauncherLog.d(TAG, "TOUCH_STATE_PREV_PAGE: mCurrentPage = " + mCurrentPage
                            + ",nextPage = " + nextPage + ",this = " + this);
                }
                if (nextPage != mCurrentPage) {
                    snapToPage(nextPage);
                } else {
                    snapToDestination();
                }
            } else if (mTouchState == TOUCH_STATE_NEXT_PAGE) {
                // at this point we have not moved beyond the touch slop
                // (otherwise mTouchState would be TOUCH_STATE_SCROLLING), so
                // we can just page
                int nextPage = Math.min(getPageCount() - 1, mCurrentPage + 1);
                if (LauncherLog.DEBUG_MOTION) {
                    LauncherLog.d(TAG, "TOUCH_STATE_NEXT_PAGE: mCurrentPage = " + mCurrentPage
                            + ",nextPage = " + nextPage + ",this = " + this);
                }
                if (nextPage != mCurrentPage) {
                    snapToPage(nextPage);
                } else {
                    snapToDestination();
                }
            } else {
                if (LauncherLog.DEBUG_MOTION) {
                    LauncherLog.d(TAG, "[--Case Watcher--]Touch up unhandled: mCurrentPage = "
                            + mCurrentPage + ",mTouchState = " + mTouchState + ",mScrollX = "
                            + mScrollX + ",this = " + this);
                }
                /*
                 * Handle special wrong case, the child stop in the middle,
                 * we need to snap it to destination, but we have no
                 * efficient way to detect this case, so do the snap process
                 * all the way, this has no side effect because the distance
                 * will be 0 if it is a normal case.
                 */
                snapToDestination();
                onUnhandledTap(ev);
            }
            mTouchState = TOUCH_STATE_REST;
            mActivePointerId = INVALID_POINTER;
            releaseVelocityTracker();
            break;

        case MotionEvent.ACTION_CANCEL:
            if (LauncherLog.DEBUG_MOTION) {
                LauncherLog.d(TAG, "Touch cancel: mCurrentPage = " + mCurrentPage
                        + ", mTouchState = " + mTouchState + ", mScrollX = " + mScrollX
                        + ", this = " + this);
            }
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                snapToDestination();
            }
            mTouchState = TOUCH_STATE_REST;
            mActivePointerId = INVALID_POINTER;
            releaseVelocityTracker();
            break;

        case MotionEvent.ACTION_POINTER_UP:
            if (LauncherLog.DEBUG_MOTION) {
                LauncherLog.d(TAG, "Touch ACTION_POINTER_UP: mCurrentPage = " + mCurrentPage
                        + ", mTouchState = " + mTouchState + ", mActivePointerId = "
                        + mActivePointerId + ", this = " + this);
            }
            onSecondaryPointerUp(ev);
            break;
        }

        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL: {
                    // Handle mouse (or ext. device) by shifting the page depending on the scroll
                    final float vscroll;
                    final float hscroll;
                    if ((event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0) {
                        vscroll = 0;
                        hscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    } else {
                        vscroll = -event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                        hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                    }
                    if (hscroll != 0 || vscroll != 0) {
                        if (hscroll > 0 || vscroll > 0) {
                            scrollRight();
                        } else {
                            scrollLeft();
                        }
                        return true;
                    }
                }
            }
        }
        return super.onGenericMotionEvent(event);
    }

    private void acquireVelocityTrackerAndAddMovement(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
    }

    private void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = mDownMotionX = ev.getX(newPointerIndex);
            mLastMotionY = ev.getY(newPointerIndex);
            mLastMotionXRemainder = 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    protected void onUnhandledTap(MotionEvent ev) {}

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        int page = indexToPage(indexOfChild(child));
        if (page >= 0 && page != getCurrentPage() && !isInTouchMode()) {
            snapToPage(page);
        }
    }

    protected int getChildIndexForRelativeOffset(int relativeOffset) {
        final int childCount = getChildCount();
        int left;
        int right;
        for (int i = 0; i < childCount; ++i) {
            left = getRelativeChildOffset(i);
            right = (left + getScaledMeasuredWidth(getChildAt(i)));
            if (left <= relativeOffset && relativeOffset <= right) {
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "getChildIndexForRelativeOffset i = " + i);
                }
                return i;
            }
        }
        return -1;
    }

    protected int getChildWidth(int index) {
        // This functions are called enough times that it actually makes a difference in the
        // profiler -- so just inline the max() here
        final int measuredWidth = getChildAt(index).getMeasuredWidth();
        if (LauncherLog.DEBUG_LAYOUT) {
            LauncherLog.d(TAG, "getChildWidth: index = " + index + ", child = " + getChildAt(index)
                    + ", measured width = " + measuredWidth + ", mMinimumWidth = " + mMinimumWidth);
        }        
        final int minWidth = mMinimumWidth;
        return (minWidth > measuredWidth) ? minWidth : measuredWidth;
    }

    int getPageNearestToCenterOfScreen() {
        int minDistanceFromScreenCenter = Integer.MAX_VALUE;
        int minDistanceFromScreenCenterIndex = -1;
        int screenCenter = getScrollX() + (getMeasuredWidth() / 2);
        final int childCount = getPageCount();
        for (int i = 0; i < childCount; ++i) {
            View layout = (View) getPageAt(i);
            int childWidth = getScaledMeasuredWidth(layout);
            int halfChildWidth = (childWidth / 2);
            int childCenter = getChildOffset(indexToPage(i)) + halfChildWidth;
            int distanceFromScreenCenter = Math.abs(childCenter - screenCenter);
            if (distanceFromScreenCenter < minDistanceFromScreenCenter) {
                minDistanceFromScreenCenter = distanceFromScreenCenter;
                minDistanceFromScreenCenterIndex = i;
            }
        }
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "getPageNearestToCenterOfScreen: minDistanceFromScreenCenterIndex = "
                    + minDistanceFromScreenCenterIndex + ", mScrollX = " + mScrollX);
        }
        return minDistanceFromScreenCenterIndex;
    }

    protected int snapToDestination() {
        return snapToPage(getPageNearestToCenterOfScreen(), PAGE_SNAP_ANIMATION_DURATION);
    }

    private static class ScrollInterpolator implements Interpolator {
        public ScrollInterpolator() {
        }

        public float getInterpolation(float t) {
            t -= 1.0f;
            return t*t*t*t*t + 1;
        }
    }

    // We want the duration of the page snap animation to be influenced by the distance that
    // the screen has to travel, however, we don't want this duration to be effected in a
    // purely linear fashion. Instead, we use this method to moderate the effect that the distance
    // of travel has on the overall snap duration.
    float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    protected int snapToPageWithVelocity(int whichPage, int velocity) {
    	final int nDestPage;
    	if(isLoopingEnabled()){
    		whichPage = Math.max(-1, Math.min(whichPage, getPageCount()));
    		if(whichPage < 0)
    			nDestPage = getPageCount() - 1;
    		else if(whichPage >= getPageCount())
    			nDestPage = 0;
    		else
    			nDestPage = whichPage;
    	}else{
    		nDestPage = whichPage = Math.max(0, Math.min(whichPage, getPageCount() - 1));
    	}
    	
        int halfScreenSize = getMeasuredWidth() / 2;

        final int newX = isLoopingEnabled() ? (getOffsetScrollX() + whichPage * getWidth()) : (getChildOffset(indexToPage(nDestPage)) - getRelativeChildOffset(indexToPage(nDestPage)));
        int delta = newX - mUnboundedScrollX ;
        int duration = 0;
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "snapToPage.getChildOffset(): " + getChildOffset(indexToPage(nDestPage))
                    + ",measured width = " + +getMeasuredWidth() + ", " + getChildWidth(indexToPage(nDestPage))
                    + ",newX = " + newX + ",mUnboundedScrollX = " + mUnboundedScrollX
                    + ",halfScreenSize = " + halfScreenSize);
        }

        if (Math.abs(velocity) < mMinFlingVelocity) {
            // If the velocity is low enough, then treat this more as an automatic page advance
            // as opposed to an apparent physical response to flinging
            LauncherLog.i(TAG, "snapToPageWithVelocity: velocity = " + velocity + ",whichPage = "
                    + whichPage + ",MIN_FLING_VELOCITY = " + MIN_FLING_VELOCITY + ",this = " + this);
            return snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION);
        }

        // Here we compute a "distance" that will be used in the computation of the overall
        // snap duration. This is a function of the actual distance that needs to be traveled;
        // we keep this value close to half screen size in order to reduce the variance in snap
        // duration as a function of the distance the page needs to travel.
        float distanceRatio = Math.min(1f, 1.0f * Math.abs(delta) / (2 * halfScreenSize));
        float distance = halfScreenSize + halfScreenSize *
                distanceInfluenceForSnapDuration(distanceRatio);

        velocity = Math.abs(velocity);
        velocity = Math.max(mMinSnapVelocity, velocity);

        // we want the page's snap velocity to approximately match the velocity at which the
        // user flings, so we scale the duration by a value near to the derivative of the scroll
        // interpolator at zero, ie. 5. We use 4 to make it a little slower.
        duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
        duration = Math.min(duration, MAX_PAGE_SNAP_DURATION);

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "snapToPageWithVelocity: velocity = " + velocity + ", whichPage = "
                    + whichPage + ", duration = " + duration + ", delta = " + delta + ", mScrollX = "
                    + mScrollX + ", mUnboundedScrollX = " + mUnboundedScrollX + ", this = " + this);
        }
        return snapToPage(whichPage, delta, duration);
    }

    protected int snapToPage(int whichPage) {
        return snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION);
    }

    protected int snapToPage(int whichPage, int duration) {
    	final int nDestPage;
    	if(isLoopingEnabled()){
    		whichPage = Math.max(-1, Math.min(whichPage, getPageCount()));
    		if(whichPage < 0)
    			nDestPage = getPageCount() - 1;
    		else if(whichPage >= getPageCount())
    			nDestPage = 0;
    		else
    			nDestPage = whichPage;
    		
    	} else {
    		nDestPage = whichPage = Math.max(0, Math.min(whichPage, getPageCount() - 1));
    	}

        if (DEBUG) Log.d(TAG, "snapToPage.getChildOffset(): " + getChildOffset(indexToPage(nDestPage)));
        if (DEBUG) Log.d(TAG, "snapToPage.getRelativeChildOffset(): " + getMeasuredWidth() + ", "
                + getChildWidth(indexToPage(nDestPage)));
        int newX = isLoopingEnabled() ? (getOffsetScrollX() + whichPage * getWidth()) : (getChildOffset(indexToPage(nDestPage)) - getRelativeChildOffset(indexToPage(nDestPage)));
        final int sX = mUnboundedScrollX;
        final int delta = newX - sX;
        return snapToPage(whichPage, delta, duration);
    }

    protected int snapToPage(int whichPage, int delta, int duration) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "(PagedView)snapToPage whichPage = " + whichPage + ", delta = "
                    + delta + ", duration = " + duration + ", mNextPage = " + mNextPage
                    + ", mUnboundedScrollX = " + mUnboundedScrollX + ", mDeferScrollUpdate = "
                    + mDeferScrollUpdate + ", mScrollX = " + mScrollX + ", this = " + this);
        }      
  
        mNextPage = whichPage;
        final int nDestPage;
        if(isLoopingEnabled()){
	        if(whichPage < 0)
				nDestPage = getPageCount() - 1;
			else if(whichPage >= getPageCount())
				nDestPage = 0;
			else
				nDestPage = whichPage;
        } else {
        	nDestPage = whichPage;
        }
        
        View focusedChild = getFocusedChild();
        if (focusedChild != null && whichPage != mCurrentPage &&
                focusedChild == getPageAt(mCurrentPage)) {
            focusedChild.clearFocus();
        }

        pageBeginMoving();
        awakenScrollBars(duration);
        if (duration == 0) {
            duration = Math.abs(delta);
        }
        
        moveOutAppWidget(nDestPage);

        if (!mScroller.isFinished()) mScroller.abortAnimation();
        mScroller.startScroll(mUnboundedScrollX, 0, delta, 0, duration);

        // Load associated pages immediately if someone else is handling the scroll, otherwise defer
        // loading associated pages until the scroll settles
        if (mDeferScrollUpdate) {
            loadAssociatedPages(mNextPage);
        } else {
            mDeferLoadAssociatedPagesUntilScrollCompletes = true;
        }
        notifyPageSwitchListener();
        invalidate();
        
        return nDestPage;
    }

    public void scrollLeft() {
    	if(isLoopingEnabled()){
    		if (mScroller.isFinished()) {
	            if (mCurrentPage > -1) snapToPage(mCurrentPage - 1);
	        } else {
	            if (mNextPage > -1) snapToPage(mNextPage - 1);
	        }
    	} else {
	        if (mScroller.isFinished()) {
	            if (mCurrentPage > 0) snapToPage(mCurrentPage - 1);
	        } else {
	            if (mNextPage > 0) snapToPage(mNextPage - 1);
	        }
    	}
    }

    public void scrollRight() {
    	if(isLoopingEnabled()){
    		if (mScroller.isFinished()) {
	            if (mCurrentPage < getPageCount()) snapToPage(mCurrentPage + 1);
	        } else {
	            if (mNextPage < getPageCount()) snapToPage(mNextPage + 1);
	        }
    	} else {
	        if (mScroller.isFinished()) {
	            if (mCurrentPage < getPageCount() -1) snapToPage(mCurrentPage + 1);
	        } else {
	            if (mNextPage < getPageCount() -1) snapToPage(mNextPage + 1);
	        }
    	}
    }

    public int getPageForView(View v) {
        int result = -1;
        if (v != null) {
            ViewParent vp = v.getParent();
            int count = getPageCount();
            for (int i = 0; i < count; i++) {
                if (vp == getPageAt(i)) {
                    return i;
                }
            }
        }
        return result;
    }

    /**
     * @return True is long presses are still allowed for the current touch
     */
    public boolean allowLongPress() {
        return mAllowLongPress;
    }

    /**
     * Set true to allow long-press events to be triggered, usually checked by
     * {@link Launcher} to accept or block dpad-initiated long-presses.
     */
    public void setAllowLongPress(boolean allowLongPress) {
        mAllowLongPress = allowLongPress;
    }

    public static class SavedState extends BaseSavedState {
        int currentPage = -1;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            currentPage = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(currentPage);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    protected void loadAssociatedPages(int page) {
        loadAssociatedPages(page, false);
    }

    protected void loadAssociatedPages(int page, boolean immediateAndOnly) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "loadAssociatedPages: page = " + page 
            		+", pagecount="+getPageCount()
                    + ", immediateAndOnly = " + immediateAndOnly + ",mContentIsRefreshable = "
                    + mContentIsRefreshable + ", mDirtyPageContent = " + mDirtyPageContent);
        }

        if (mContentIsRefreshable) {
            final int count = getPageCount();
            if (page < count) {
            	
				if(LOAD_DATA_STYLE_OLD){
					int lowerPageBound = getAssociatedLowerPageBound(page);
	                int upperPageBound = getAssociatedUpperPageBound(page);
	                if (LauncherLog.DEBUG) {
	                    LauncherLog.d(TAG, "loadAssociatedPages: " + lowerPageBound + "/"
	                            + upperPageBound + ", page = " + page + ", count = " + count);      
	                }
	                // First, clear any pages that should no longer be loaded
	                for (int i = 0; i < count; ++i) {
	                    Page layout = (Page) getPageAt(i);
	                    if ((i < lowerPageBound) || (i > upperPageBound)) {
	                        if (layout.getPageChildCount() > 0) {
	                            layout.removeAllViewsOnPage();
	                        }
	                        mDirtyPageContent.set(indexToPage(i), true);
	                    }
	                }
	                // Next, load any new pages
	                for (int i = 0; i < count; ++i) {
	                    if ((i != page) && immediateAndOnly) {
	                        continue;
	                    }
	                    if (lowerPageBound <= i && i <= upperPageBound) {
	                    	int index = indexToPage(i);
//	                    	if (LauncherLog.DEBUG) {
//	    	                    LauncherLog.e(TAG, "loadAssociatedPages(5): Page=" + i 
//	    	                    		+ ", index = " + index
//	    	                    		+ ", Dirty = " + mDirtyPageContent.get(index));
//	    	                }
	                    	
	                        if (mDirtyPageContent.get(index)) {
	                            syncPageItems(i, (i == page) && immediateAndOnly);
	                            mDirtyPageContent.set(index, false);
	                        }
	                    }
	                }
				} else {
					for (int i = 0; i < count; ++i) {
	                    if ((i != page) && immediateAndOnly) {
	                        continue;
	                    }
                    
	                    syncPageItems(i, (i == page) && immediateAndOnly);
	                }
				}
            }
        }
    }

    protected int getAssociatedLowerPageBound(int page) {
        return Math.max(0, page - 1);
    }
    protected int getAssociatedUpperPageBound(int page) {
        final int count = getPageCount();
        return Math.min(page + 1, count - 1);
    }

    /**
     * This method is called ONLY to synchronize the number of pages that the paged view has.
     * To actually fill the pages with information, implement syncPageItems() below.  It is
     * guaranteed that syncPageItems() will be called for a particular page before it is shown,
     * and therefore, individual page items do not need to be updated in this method.
     */
    public abstract void syncPages();

    /**
     * This method is called to synchronize the items that are on a particular page.  If views on
     * the page can be reused, then they should be updated within this method.
     */
    public abstract void syncPageItems(int page, boolean immediate);

    protected void invalidatePageData() {
        invalidatePageData(-1, false);
    }

    protected void invalidatePageData(int currentPage) {
        invalidatePageData(currentPage, false);
    }

    protected void invalidatePageData(int currentPage, boolean immediateAndOnly) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "invalidatePageData: currentPage = " + currentPage 
                    + ", immediateAndOnly = " + immediateAndOnly + ", mIsDataReady = "
                    + mIsDataReady + ", mContentIsRefreshable = " + mContentIsRefreshable
                    + ", mScrollX = " + mScrollX + ", this = " + this);
        }
        
        if (!mIsDataReady) {
            return;
        }

        if (mContentIsRefreshable) {
            // Force all scrolling-related behavior to end
            mScroller.forceFinished(true);
            mNextPage = INVALID_PAGE;

            // Update all the pages
            syncPages();

            // We must force a measure after we've loaded the pages to update the content width and
            // to determine the full scroll width
            measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));

            // Set a new page as the current page if necessary
            if (currentPage > -1) {
                setCurrentPage(Math.min(getPageCount() - 1, currentPage));
            }

            // Mark each of the pages as dirty
            final int count = LOAD_DATA_STYLE_OLD ? getChildCount() : getPageCount();
            mDirtyPageContent.clear();
            for (int i = 0; i < count; ++i) {
                mDirtyPageContent.add(true);
            }

            // Load any pages that are necessary for the current window of views
            loadAssociatedPages(mCurrentPage, immediateAndOnly);
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "[--Case Watcher--]invalidatePageData: currentPage = " + currentPage
                        + ", immediateAndOnly = " + immediateAndOnly + ", mScrollX = " + mScrollX);
            }
            /*
             * M: The scroller is force finished at the very begin, sometimes the
             * page will stop in the middle, we need to snap it to the right
             * destination to make pages position to the bounds.
             */
            snapToDestination();
            requestLayout();
        }
    }

    protected View getScrollingIndicator() {
        // We use mHasScrollIndicator to prevent future lookups if there is no sibling indicator
        // found
        if (mHasScrollIndicator && mScrollIndicator == null) {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) {
                mScrollIndicator = (View) (parent.findViewById(R.id.paged_view_indicator));
                //mHasScrollIndicator = mScrollIndicator != null;
                if (mHasScrollIndicator) {
                    mScrollIndicator.setVisibility(View.VISIBLE);
                }
            }
        }
        return mScrollIndicator;
    }

    protected boolean isScrollingIndicatorEnabled() {
        return true;
    }

    Runnable hideScrollingIndicatorRunnable = new Runnable() {
        @Override
        public void run() {
            hideScrollingIndicator(false);
        }
    };
    protected void flashScrollingIndicator(boolean animated) {
        removeCallbacks(hideScrollingIndicatorRunnable);
        showScrollingIndicator(!animated);
        postDelayed(hideScrollingIndicatorRunnable, sScrollIndicatorFlashDuration);
    }

    protected void showScrollingIndicator(boolean immediately) {
        mShouldShowScrollIndicator = true;
        mShouldShowScrollIndicatorImmediately = true;
        if (getPageCount() <= 1) return;
        if (!isScrollingIndicatorEnabled()) return;

        mShouldShowScrollIndicator = false;
        getScrollingIndicator();
        if (mScrollIndicator != null) {
            // Fade the indicator in
            updateScrollingIndicatorPosition();
            mScrollIndicator.setVisibility(View.VISIBLE);
            cancelScrollingIndicatorAnimations();
            if (immediately || mScrollingPaused) {
                mScrollIndicator.setAlpha(1f);
            } else {
                mScrollIndicatorAnimator = LauncherAnimUtils.ofFloat(mScrollIndicator, "alpha", 1f);
                mScrollIndicatorAnimator.setDuration(sScrollIndicatorFadeInDuration);
                mScrollIndicatorAnimator.start();
            }
        }
    }

    protected void cancelScrollingIndicatorAnimations() {
        if (mScrollIndicatorAnimator != null) {
            mScrollIndicatorAnimator.cancel();
        }
    }

    protected void hideScrollingIndicator(boolean immediately) {
        if (getPageCount() <= 1) return;
        if (!isScrollingIndicatorEnabled()) return;

        // M: Per our UE design, we never hide scrolling indicator when the user
        // is moving the pages.
        //
        // ALPS00414625: [Launcher] Scrollbar disappear
        if (isPageMoving()) return;

        getScrollingIndicator();
        if (mScrollIndicator != null) {
            // Fade the indicator out

            // M: No need to update indicator here. We will update it when we
            // want to show it.
            //
            // ALPS00415515: [Launcher] The scrollbar is not normal in APP list
            //updateScrollingIndicatorPosition();

            cancelScrollingIndicatorAnimations();
            if (immediately || mScrollingPaused) {
                mScrollIndicator.setVisibility(View.INVISIBLE);
                mScrollIndicator.setAlpha(0f);
            } else {
                mScrollIndicatorAnimator = LauncherAnimUtils.ofFloat(mScrollIndicator, "alpha", 0f);
                mScrollIndicatorAnimator.setDuration(sScrollIndicatorFadeOutDuration);
                mScrollIndicatorAnimator.addListener(new AnimatorListenerAdapter() {
                    private boolean cancelled = false;
                    @Override
                    public void onAnimationCancel(android.animation.Animator animation) {
                        cancelled = true;
                    }
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!cancelled) {
                            mScrollIndicator.setVisibility(View.INVISIBLE);
                        }
                    }
                });
                mScrollIndicatorAnimator.start();
            }
        }
    }

    /**
     * To be overridden by subclasses to determine whether the scroll indicator should stretch to
     * fill its space on the track or not.
     */
    protected boolean hasElasticScrollIndicator() {
        return true;
    }

    private void updateScrollingIndicator() {
        if (getPageCount() <= 1) return;
        if (!isScrollingIndicatorEnabled()) return;

        getScrollingIndicator();
        if (mScrollIndicator != null) {
            updateScrollingIndicatorPosition();
        }
        if (mShouldShowScrollIndicator) {
            showScrollingIndicator(mShouldShowScrollIndicatorImmediately);
        }
    }

    private void updateScrollingIndicatorPosition() {
        if (!isScrollingIndicatorEnabled()) return;
        if (mScrollIndicator == null) return;
        int numPages = getPageCount();
        int pageWidth = getMeasuredWidth();
        int lastChildIndex = Math.max(0, getPageCount() - 1);
        int maxScrollX = getChildOffset(indexToPage(lastChildIndex)) - getRelativeChildOffset(indexToPage(lastChildIndex));
        int trackWidth = pageWidth - mScrollIndicatorPaddingLeft - mScrollIndicatorPaddingRight;
        int indicatorWidth = mScrollIndicator.getMeasuredWidth() -
                mScrollIndicator.getPaddingLeft() - mScrollIndicator.getPaddingRight();

        float offset = ((float) getScrollX() / maxScrollX);
        int indicatorSpace = trackWidth / numPages;
        int indicatorPos = (int) (offset * (trackWidth - indicatorSpace)) + mScrollIndicatorPaddingLeft;
        if (hasElasticScrollIndicator()) {
            if (mScrollIndicator.getMeasuredWidth() != indicatorSpace) {
                mScrollIndicator.getLayoutParams().width = indicatorSpace;
                mScrollIndicator.requestLayout();
            }
        } else {
            int indicatorCenterOffset = indicatorSpace / 2 - indicatorWidth / 2;
            indicatorPos += indicatorCenterOffset;
        }
        mScrollIndicator.setTranslationX(indicatorPos);
    }

    public void showScrollIndicatorTrack() {
    	if(mQsWorkspaceCallback != null)
        	((View)mQsWorkspaceCallback).setVisibility(View.VISIBLE);
    }

    public void hideScrollIndicatorTrack() {
    	if(mQsWorkspaceCallback != null)
        	((View)mQsWorkspaceCallback).setVisibility(View.GONE);
    }

    /* Accessibility */
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setScrollable(getPageCount() > 1);
		if(isLoopingEnabled()){
		} else {
	        if (getCurrentPage() < getPageCount() - 1) {
	            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
	        }
	        if (getCurrentPage() > 0) {
	            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
	        }
		}
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setScrollable(true);
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            event.setFromIndex(mCurrentPage);
            event.setToIndex(mCurrentPage);
            event.setItemCount(getPageCount());
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (super.performAccessibilityAction(action, arguments)) {
            return true;
        }
        switch (action) {
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD: {
                if (isLoopingEnabled() || (getCurrentPage() < getPageCount() - 1)) {
                    scrollRight();
                    return true;
                }
            } break;
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD: {
                if (isLoopingEnabled() || (getCurrentPage() > 0)) {
                    scrollLeft();
                    return true;
                }
            } break;
        }
        return false;
    }

    protected String getCurrentPageDescription() {
        return String.format(getContext().getString(R.string.default_scroll_format),
                 getNextPage() + 1, getPageCount());
    }

    @Override
    public boolean onHoverEvent(android.view.MotionEvent event) {
        return true;
    }
    
    /**
     * M: Call the "enterAppWidgetScreen" callback for the IMtkWidget on the given page when slide into the given page.
     * 
     * @param page
     */
    public void enterAppWidget(final int page) {
        final View mtkWidgetView = getMTKWidgetView(page);
        if (mtkWidgetView != null) {
            ((IMtkWidget) mtkWidgetView).enterAppwidgetScreen();
            if (LauncherLog.DEBUG_SURFACEWIDGET) {
                LauncherLog.d(Launcher.TAG_SURFACEWIDGET, "action_move: enterAppWidgetScreen whichMtkWidgetView = "
                        + mtkWidgetView);
            }
        }
    }
    
    /**
     * M: Call the "leaveAppwidgetScreen" callback for the IMtkWidget on the given page when slide out the given page.
     * 
     * @param page
     */
    public void leaveAppWidget(final int page) {
        final View mtkWidgetView = getMTKWidgetView(page);
        if (mtkWidgetView != null) {
            ((IMtkWidget) mtkWidgetView).leaveAppwidgetScreen();
            if (LauncherLog.DEBUG_SURFACEWIDGET) {
                LauncherLog.d(Launcher.TAG_SURFACEWIDGET, "leaveAppWidgetScreen whichMtkWidgetView = " + mtkWidgetView);
            }
        }
    }

    /**
     * M: Call the "startDragAppWidget" callback for the IMtkWidget on the given page when long click and begin to drag
     * appWidget.
     * 
     * @param page
     */
    public void startDragAppWidget(final int page) {
        final View mtkWidgetView = getMTKWidgetView(page);
        if (mtkWidgetView != null) {
            ((IMtkWidget) mtkWidgetView).startDrag();
            if (LauncherLog.DEBUG_SURFACEWIDGET) {
                LauncherLog.d(Launcher.TAG_SURFACEWIDGET, "startDrag:mtkWidgetView = " + mtkWidgetView);
            }
        }
    }

    /**
     * M: Call the "stopDragAppWidget" callback for the IMtkWidget on the given page when release your finger and drop
     * appWidget on home screen.
     * 
     * @param page
     */
    public void stopDragAppWidget(final int page) {
        final View mtkWidgetView = getMTKWidgetView(page);
        if (mtkWidgetView != null) {
            ((IMtkWidget) mtkWidgetView).setScreen(page);
            ((IMtkWidget) mtkWidgetView).stopDrag();
            if (LauncherLog.DEBUG_SURFACEWIDGET) {
                LauncherLog.d(Launcher.TAG_SURFACEWIDGET, "stopDrag: mtkWidgetView = " + mtkWidgetView);
            }
        }
    }

    /**
     * M: Call the "moveInAppWidget" callback for the IMtkWidget on the given page.
     * 
     * @param page
     */
    public void moveInAppWidget(final int page) {
        final View mtkWidgetView = getMTKWidgetView(page);
        if (mtkWidgetView != null) {
            ((IMtkWidget) mtkWidgetView).moveIn(page);
            sCanSendMessage = true;
            if (LauncherLog.DEBUG_SURFACEWIDGET) {
                LauncherLog.d(Launcher.TAG_SURFACEWIDGET, "moveIn: mtkWidgetView = " + mtkWidgetView);
            }
        }
    }

    /**
     * M: Call the "moveOutAppWidget" callback for the IMtkWidget on the given page.
     * 
     * @param page
     * @return
     */
    public boolean moveOutAppWidget(final int page) {
        boolean result = true;
        final View mtkWidgetView = getMTKWidgetView(page);
        if (mtkWidgetView != null) {
            if (LauncherLog.DEBUG_SURFACEWIDGET) {
                LauncherLog.d(Launcher.TAG_SURFACEWIDGET, "moveOut: mtkWidgetView = " + mtkWidgetView);
            }
            sCanSendMessage = false;
            result = ((IMtkWidget) mtkWidgetView).moveOut(mCurrentPage);
            return result;
        }
        return result;
    }

    /**
     * M: Call the "startCovered" callback for the IMtkWidget on the given page when enter all apps list.
     * 
     * @param page
     */
    public void startCovered(final int page) {
        final View mtkWidgetView = getMTKWidgetView(page);
        if (mtkWidgetView != null) {
            ((IMtkWidget) mtkWidgetView).startCovered(page);
            if (LauncherLog.DEBUG_SURFACEWIDGET) {
                LauncherLog.d(Launcher.TAG_SURFACEWIDGET, "startCovered mtkWidgetView = " + mtkWidgetView);
            }
        }
    }

    /**
     * M: Call the "stopCovered" callback for the IMtkWidget on the given page when leave all apps list.
     * 
     * @param page
     */
    public void stopCovered(int page) {
        final View mtkWidgetView = getMTKWidgetView(page);
        if (mtkWidgetView != null) {
            ((IMtkWidget) mtkWidgetView).stopCovered(page);
            if (LauncherLog.DEBUG_SURFACEWIDGET) {
                LauncherLog.d(Launcher.TAG_SURFACEWIDGET, "stopCovered mtkWidgetView = " + mtkWidgetView);
            }
        }
    }

    /**
     * M: Call the "onPauseWhenShown" callback for the IMtkWidget on the given page when the activity is paused.
     * 
     * @param page
     */
    public void onPauseWhenShown(int page) {
        final View mtkWidgetView = getMTKWidgetView(page);
        if (mtkWidgetView != null) {
            ((IMtkWidget) mtkWidgetView).onPauseWhenShown(page);
            if (LauncherLog.DEBUG_SURFACEWIDGET) {
                LauncherLog.d(Launcher.TAG_SURFACEWIDGET, "onPauseWhenShown: mtkWidgetView = " + mtkWidgetView);
            }
        }
    }

    /**
     * M: Call the "onResumeWhenShown" callback for the IMtkWidget on the given page when the activity is resumed.
     * 
     * @param page
     */
    public void onResumeWhenShown(int page) {
        final View mtkWidgetView = getMTKWidgetView(page);
        if (mtkWidgetView != null) {
            ((IMtkWidget) mtkWidgetView).onResumeWhenShown(page);
            if (LauncherLog.DEBUG_SURFACEWIDGET) {
                LauncherLog.d(Launcher.TAG_SURFACEWIDGET, "onResumeWhenShown: mtkWidgetView = " + mtkWidgetView);
            }
        }
    }

    /**
     * M: Call the "setAppWidgetIdAndScreen" callback for the IMtkWidget on the given page, set the appWidgetId and page to
     * the appWidget
     * 
     * @param hostView the view host the IMtkWidget
     * @param page
     * @param appWidgetId
     */
    public void setAppWidgetIdAndScreen(View hostView, int page, int appWidgetId) {
        final View mtkWidgetView = searchIMtkWidget(hostView);
        if (mtkWidgetView != null) {
            ((IMtkWidget) mtkWidgetView).setScreen(page);
            ((IMtkWidget) mtkWidgetView).setWidgetId(appWidgetId);
        }
    }

    /**
     * M: Find the IMTKWiget View on the given page.
     * 
     * @param page
     * @return the IMtkWidget view on the given page
     */
    public View getMTKWidgetView(int page) {
        final View whichHostView = getChildAt(page);
        final View mtkWidgetView = searchIMtkWidget(whichHostView);
        return mtkWidgetView;
    }

    /**
     * M: Find the IMtkWidget View which providerName equals the given providerName.
     * 
     * @param hostView
     * @param providerName
     * @return
     */
    public View searchIMtkWidget(View hostView, String providerName) {
        if (hostView instanceof IMtkWidget) {
            return hostView;
        } else if (hostView instanceof ViewGroup) {
            int childCount = ((ViewGroup) hostView).getChildCount();
            for (int i = 0; i < childCount; i++) {
                View mtkWidgetView = searchIMtkWidget(((ViewGroup) hostView).getChildAt(i), providerName);
                if (mtkWidgetView != null) {
                    View v = (View) mtkWidgetView.getParent();
                    if (v instanceof LauncherAppWidgetHostView) {
                        LauncherAppWidgetHostView parent = (LauncherAppWidgetHostView) v;
                        AppWidgetProviderInfo info = (AppWidgetProviderInfo) parent.getAppWidgetInfo();
                        if (info.provider.getClassName().equals(providerName)) {
                            return mtkWidgetView;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * M: Find the IMtkWidget view.
     * 
     * @param hostView
     * @return
     */
    private View searchIMtkWidget(View hostView) {
        if (hostView instanceof IMtkWidget) {
            return hostView;
        } else if (hostView instanceof ViewGroup) {
            int childCount = ((ViewGroup) hostView).getChildCount();
            for (int i = 0; i < childCount; i++) {
                View mtkWidgetView = searchIMtkWidget(((ViewGroup) hostView).getChildAt(i));
                if (mtkWidgetView != null) {
                    return mtkWidgetView;
                }
            }
        }
        return null;
    }
}
