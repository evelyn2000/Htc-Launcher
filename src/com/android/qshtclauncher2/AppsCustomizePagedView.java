/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Insets;
import android.graphics.MaskFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.TableMaskFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Scroller;
import android.widget.Toast;

import com.android.qshtclauncher2.DropTarget.DragObject;

import java.lang.ref.WeakReference;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * A simple callback interface which also provides the results of the task.
 */
//interface AsyncTaskCallback {
//    void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data);
//}

abstract class WeakReferenceThreadLocal<T> {
    private ThreadLocal<WeakReference<T>> mThreadLocal;
    public WeakReferenceThreadLocal() {
        mThreadLocal = new ThreadLocal<WeakReference<T>>();
    }

    abstract T initialValue();

    public void set(T t) {
        mThreadLocal.set(new WeakReference<T>(t));
    }

    public T get() {
        WeakReference<T> reference = mThreadLocal.get();
        T obj;
        if (reference == null) {
            obj = initialValue();
            mThreadLocal.set(new WeakReference<T>(obj));
            return obj;
        } else {
            obj = reference.get();
            if (obj == null) {
                obj = initialValue();
                mThreadLocal.set(new WeakReference<T>(obj));
            }
            return obj;
        }
    }
}

class CanvasCache extends WeakReferenceThreadLocal<Canvas> {
    @Override
    protected Canvas initialValue() {
        return new Canvas();
    }
}

class PaintCache extends WeakReferenceThreadLocal<Paint> {
    @Override
    protected Paint initialValue() {
        return null;
    }
}

class BitmapCache extends WeakReferenceThreadLocal<Bitmap> {
    @Override
    protected Bitmap initialValue() {
        return null;
    }
}

class RectCache extends WeakReferenceThreadLocal<Rect> {
    @Override
    protected Rect initialValue() {
        return new Rect();
    }
}

/**
 * The Apps/Customize page that displays all the applications, widgets, and shortcuts.
 */
public class AppsCustomizePagedView extends PagedViewWithDraggableItems implements
        View.OnClickListener, View.OnKeyListener, DragSource,
        PagedViewIcon.PressedCallback, PagedViewWidget.ShortPressListener,
        LauncherTransitionable {
    private static final String TAG = "AppsCustomizePagedView";

    /**
     * The different content types that this paged view can show.
     */
//    public enum ContentType {
//    	Applications_Freq,
//    	Applications,
//    	Applications_Download,
//        Widgets
//    }
    public final static int ContentType_Unkown = 0;
    public final static int ContentType_Apps_Freq = 0x1;
    public final static int ContentType_Apps_Download = 0x2;
    public final static int ContentType_Apps = 0x4;
    public final static int ContentType_Widgets = 0x10;
    public final static int ContentType_Widgets_ShortCut = 0x20;
    
    public final static int ContentType_Style_Apps = (ContentType_Apps_Freq|ContentType_Apps_Download|ContentType_Apps);
    
    private final int mSupportContentType;
    private int mCurrentContentType;
    
    private final HashMap<Integer, Integer> mContentItemsPageIndexMap = new HashMap<Integer, Integer>();

    // Refs
    private Launcher mLauncher;
    private DragController mDragController;
    private final LayoutInflater mLayoutInflater;
    private final PackageManager mPackageManager;

    // Save and Restore
    private int mSaveInstanceStateItemIndex = -1;
    private PagedViewIcon mPressedIcon;

    // Content
    private ArrayList<ApplicationInfo> mApps;
    private ArrayList<Object> mWidgets;
    private ArrayList<ApplicationInfo> mAppsDownload;
    private ArrayList<ApplicationInfo> mAppsFreq;
    private ArrayList<ResolveInfo> mWidgetShortCut;
    private int mNumAppsFreqPages = 0;
    private int mNumAppsDownloadPages = 0;
    private int mNumWidgetShortCutPages = 0;
    
    // Cling
    private boolean mHasShownAllAppsCling;
    private int mClingFocusedX;
    private int mClingFocusedY;

    // Caching
    private Canvas mCanvas;
    private IconCache mIconCache;

    // Dimens
    private int mContentWidth;
    private int mAppIconSize;
    private int mMaxAppCellCountX, mMaxAppCellCountY;
    private int mWidgetCountX, mWidgetCountY;
    private int mWidgetWidthGap, mWidgetHeightGap;
    private final float sWidgetPreviewIconPaddingPercentage = 0.25f;
    private PagedViewCellLayout mWidgetSpacingLayout;
    private int mNumAppsPages;
    private int mNumWidgetPages;

    // Relating to the scroll and overscroll effects
    Workspace.ZInterpolator mZInterpolator = new Workspace.ZInterpolator(0.5f);
    private static float CAMERA_DISTANCE = 6500;
    private static float TRANSITION_SCALE_FACTOR = 0.74f;
    private static float TRANSITION_PIVOT = 0.65f;
    private static float TRANSITION_MAX_ROTATION = 22;
    private static final boolean PERFORM_OVERSCROLL_ROTATION = true;
    private AccelerateInterpolator mAlphaInterpolator = new AccelerateInterpolator(0.9f);
    private DecelerateInterpolator mLeftScreenAlphaInterpolator = new DecelerateInterpolator(4);

    // Previews & outlines
    ArrayList<AppsCustomizeAsyncTask> mRunningTasks;
    private static final int sPageSleepDelay = 200;

    private Runnable mInflateWidgetRunnable = null;
    private Runnable mBindWidgetRunnable = null;
    static final int WIDGET_NO_CLEANUP_REQUIRED = -1;
    static final int WIDGET_PRELOAD_PENDING = 0;
    static final int WIDGET_BOUND = 1;
    static final int WIDGET_INFLATED = 2;
    int mWidgetCleanupState = WIDGET_NO_CLEANUP_REQUIRED;
    int mWidgetLoadingId = -1;
    PendingAddWidgetInfo mCreateWidgetInfo = null;
    private boolean mDraggingWidget = false;

    private Toast mWidgetInstructionToast;

    // Deferral of loading widget previews during launcher transitions
    private boolean mInTransition;
    private ArrayList<AsyncTaskPageData> mDeferredSyncWidgetPageItems =
        new ArrayList<AsyncTaskPageData>();
    private ArrayList<Runnable> mDeferredPrepareLoadWidgetPreviewsTasks =
        new ArrayList<Runnable>();

    private Rect mTmpRect = new Rect();

    // Used for drawing shortcut previews
    BitmapCache mCachedShortcutPreviewBitmap = new BitmapCache();
    PaintCache mCachedShortcutPreviewPaint = new PaintCache();
    CanvasCache mCachedShortcutPreviewCanvas = new CanvasCache();

    // Used for drawing widget previews
    CanvasCache mCachedAppWidgetPreviewCanvas = new CanvasCache();
    RectCache mCachedAppWidgetPreviewSrcRect = new RectCache();
    RectCache mCachedAppWidgetPreviewDestRect = new RectCache();
    PaintCache mCachedAppWidgetPreviewPaint = new PaintCache();

    /// M: Flag to record whether the app list data has been set to AppsCustomizePagedView.  
    private boolean mAppsHasSet = false;
    private AppsChangeFreqAsyncTask mAppsChangeFreqAsyncTask;

    public AppsCustomizePagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLayoutInflater = LayoutInflater.from(context);
        mPackageManager = context.getPackageManager();
        mApps = new ArrayList<ApplicationInfo>();
        
        mIconCache = ((LauncherApplication) context.getApplicationContext()).getIconCache();
        mCanvas = new Canvas();
        mRunningTasks = new ArrayList<AppsCustomizeAsyncTask>();

        // Save the default widget preview background
        Resources resources = context.getResources();
        mAppIconSize = resources.getDimensionPixelSize(R.dimen.app_icon_size);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AppsCustomizePagedView, 0, 0);
        mSupportContentType = a.getInt(R.styleable.AppsCustomizePagedView_ContentType, (ContentType_Apps|ContentType_Apps_Freq|ContentType_Apps_Download));
        mMaxAppCellCountX = a.getInt(R.styleable.AppsCustomizePagedView_maxAppCellCountX, -1);
        mMaxAppCellCountY = a.getInt(R.styleable.AppsCustomizePagedView_maxAppCellCountY, -1);
        mWidgetWidthGap =
            a.getDimensionPixelSize(R.styleable.AppsCustomizePagedView_widgetCellWidthGap, 0);
        mWidgetHeightGap =
            a.getDimensionPixelSize(R.styleable.AppsCustomizePagedView_widgetCellHeightGap, 0);
        mWidgetCountX = a.getInt(R.styleable.AppsCustomizePagedView_widgetCountX, 2);
        mWidgetCountY = a.getInt(R.styleable.AppsCustomizePagedView_widgetCountY, 2);
        mClingFocusedX = a.getInt(R.styleable.AppsCustomizePagedView_clingFocusedX, 0);
        mClingFocusedY = a.getInt(R.styleable.AppsCustomizePagedView_clingFocusedY, 0);
        a.recycle();
        mWidgetSpacingLayout = new PagedViewCellLayout(getContext());
        
        if(this.isSupportWidget()){
        	mWidgets = new ArrayList<Object>();
        	mWidgetShortCut =  new ArrayList<ResolveInfo>();
        	mNumAppsFreqPages = 0;
        	mNumAppsDownloadPages = 0;
			mCurrentContentType = ContentType_Widgets;
			
        } else {
        	mNumWidgetShortCutPages = 0;
        	mNumAppsFreqPages = 1;
        	mNumAppsDownloadPages = 1;
        	
			mAppsDownload = new ArrayList<ApplicationInfo>();
	        mAppsFreq = new ArrayList<ApplicationInfo>();
		
			mCurrentContentType = ContentType_Apps;	
        }
        
        mContentItemsPageIndexMap.put(ContentType_Apps_Freq, 0);
    	mContentItemsPageIndexMap.put(ContentType_Apps_Download, 0);
    	mContentItemsPageIndexMap.put(ContentType_Apps, 0);
    	mContentItemsPageIndexMap.put(ContentType_Widgets, 0);
    	mContentItemsPageIndexMap.put(ContentType_Widgets_ShortCut, 0);
    	
        //mAppsFreq = new ArrayList<ApplicationInfo>();
        
        // The padding on the non-matched dimension for the default widget preview icons
        // (top + bottom)
        mFadeInAdjacentScreens = false;

        // Unless otherwise specified this view is important for accessibility.
        if (getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
    }
    private static class ScrollInterpolator implements Interpolator {
        public ScrollInterpolator() {
        }

        public float getInterpolation(float t) {
            t -= 1.0f;
            float ret = t*t*t*t*t + 1;
            if(ret > 1.0f)
            	return 1.0f;
            return ret;
        }
    }
    @Override
    protected void init() {
        super.init();
        mCenterPagesVertically = false;
        mAllowOverScroll = false;
        Context context = getContext();
        Resources r = context.getResources();
        setDragSlopeThreshold(r.getInteger(R.integer.config_appsCustomizeDragSlopeThreshold)/100f);
        
        mScroller = new Scroller(getContext(), new ScrollInterpolator());        
        super.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    /** Returns the item index of the center item on this page so that we can restore to this
     *  item index when we rotate. */
    private int getMiddleComponentIndexOnCurrentPage() {
        int i = -1;
        if (getPageCount() > 0) {
            int currentPage = getCurrentPage();
            PagedViewCellLayout layout = (PagedViewCellLayout) getPageAt(currentPage);
            PagedViewCellLayoutChildren childrenLayout = layout.getChildrenLayout();
            int numItemsPerPage = mCellCountX * mCellCountY;
            int childCount = childrenLayout.getChildCount();
            if (childCount > 0) {
                i = (currentPage * numItemsPerPage) + (childCount / 2);
                
//                if (mCurrentContentType == ContentType_Apps) {
//                	i += mAppsFreq.size();
//                } else if(mCurrentContentType == ContentType_Apps_Download){
//                	i += mAppsFreq.size() + mApps.size();
//                }
            }
        }
        return i;
    }

    /** Get the index of the item to restore to if we need to restore the current page. */
    int getSaveInstanceStateIndex() {
        if (mSaveInstanceStateItemIndex == -1) {
            mSaveInstanceStateItemIndex = getCurrentPage();//getMiddleComponentIndexOnCurrentPage();
        }
        return mSaveInstanceStateItemIndex;
    }

    /** Returns the page in the current orientation which is expected to contain the specified
     *  item index. */
    int getPageForComponent(int index) {
        if (index < 0) return 0;
        
        int numItemsPerPage = mCellCountX * mCellCountY;
        return (index / numItemsPerPage);
        
//        if(index < mAppsFreq.size()){
//        	return (index / numItemsPerPage);
//        } else if (index < (mApps.size() + mAppsFreq.size())) {
//            return mNumAppsFreqPages + ((index - mAppsFreq.size()) / numItemsPerPage);
//        } else {
//            return mNumAppsFreqPages + mNumAppsPages + ((index - mApps.size() - mAppsFreq.size()) / numItemsPerPage);
//        }
    }

    /** Restores the page for an item at the specified index */
    void restorePageForIndex(int index) {
        if (index < 0) return;
        mSaveInstanceStateItemIndex = index;
    }

    private void updatePageCounts() {
    	int numItemsPerPage = mCellCountX * mCellCountY;
    	if(numItemsPerPage == 0){
    		if(this.isSupportWidget()){
		        mNumWidgetPages = 1;
		        mNumWidgetShortCutPages = 1;
	    	} else {
	        	mNumAppsDownloadPages = 1;
	        	mNumAppsFreqPages = 1;
	        }
    		
    		mNumAppsPages = 1;
    	} else {
	    	if(this.isSupportWidget()){

		        mNumWidgetPages = Math.max(1, 
		        		(int) Math.ceil(mWidgets.size() / (float) (mWidgetCountX * mWidgetCountY)));
		        
		        mNumWidgetShortCutPages = Math.max(1, 
		        		(int) Math.ceil((float) mWidgetShortCut.size() / numItemsPerPage));
		        
	    	} else if(mAppsDownload != null) {
	        	mNumAppsDownloadPages = Math.max(1, (int) Math.ceil((float) mAppsDownload.size() / numItemsPerPage));
	        }
	    	
	    	//if((mSupportContentType&ContentType_Apps) > 0)
	    		mNumAppsPages = Math.max(1, (int) Math.ceil((float) mApps.size() / numItemsPerPage));
    	}
        
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updatePageCounts end: mNumWidgetPages = " + mNumWidgetPages
                    + ", mNumAppsPages = " + mNumAppsPages + ", mApps.size() = " + mApps.size()
                    + ", mNumAppsDownloadPages = " + mNumAppsDownloadPages 
                    + ", mCellCountX = " + mCellCountX + ", mCellCountY = " + mCellCountY
                    );
        }
    }

    protected void onDataReady(int width, int height) {
        // Note that we transpose the counts in portrait so that we get a similar layout
        boolean isLandscape = getResources().getConfiguration().orientation ==
            Configuration.ORIENTATION_LANDSCAPE;
        int maxCellCountX = Integer.MAX_VALUE;
        int maxCellCountY = Integer.MAX_VALUE;
        if (LauncherApplication.isScreenLarge()) {
            maxCellCountX = (isLandscape ? LauncherModel.getCellCountX() :
                LauncherModel.getCellCountY());
            maxCellCountY = (isLandscape ? LauncherModel.getCellCountY() :
                LauncherModel.getCellCountX());
        }
        if (mMaxAppCellCountX > -1) {
            maxCellCountX = Math.min(maxCellCountX, mMaxAppCellCountX);
        }
        // Temp hack for now: only use the max cell count Y for widget layout
        int maxWidgetCellCountY = maxCellCountY;
        if (mMaxAppCellCountY > -1) {
            maxWidgetCellCountY = Math.min(maxWidgetCellCountY, mMaxAppCellCountY);
        }

        // Now that the data is ready, we can calculate the content width, the number of cells to
        // use for each page
        mWidgetSpacingLayout.setGap(mPageLayoutWidthGap, mPageLayoutHeightGap);
        mWidgetSpacingLayout.setPadding(mPageLayoutPaddingLeft, mPageLayoutPaddingTop,
                mPageLayoutPaddingRight, mPageLayoutPaddingBottom);
        mWidgetSpacingLayout.calculateCellCount(width, height, maxCellCountX, maxCellCountY);
        mCellCountX = mWidgetSpacingLayout.getCellCountX();
        mCellCountY = mWidgetSpacingLayout.getCellCountY();
        updatePageCounts();

        // Force a measure to update recalculate the gaps
        int widthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST);
        mWidgetSpacingLayout.calculateCellCount(width, height, maxCellCountX, maxWidgetCellCountY);
        mWidgetSpacingLayout.measure(widthSpec, heightSpec);
        mContentWidth = mWidgetSpacingLayout.getContentWidth();

        AppsCustomizeTabHost host = (AppsCustomizeTabHost) getTabHost();
        final boolean hostIsTransitioning = host.isTransitioning();

        // Restore the page
        int page = mSaveInstanceStateItemIndex;//getPageForComponent(mSaveInstanceStateItemIndex);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDataReady: height = " + height + ", width = " + width
                    + ", isLandscape = " + isLandscape + ", page = " + page
                    + ", hostIsTransitioning = " + hostIsTransitioning + ", mContentWidth = "
                    + mContentWidth + ", mNumAppsPages = " + mNumAppsPages + ", mNumWidgetPages = "
                    +", mCellCountX="+mCellCountX
                    +", mCellCountY="+mCellCountY
                    + mNumWidgetPages + ", this = " + this);
        }
        invalidatePageData(Math.max(0, page), hostIsTransitioning);

        // Show All Apps cling if we are finished transitioning, otherwise, we will try again when
        // the transition completes in AppsCustomizeTabHost (otherwise the wrong offsets will be
        // returned while animating)
        if (false) {
            post(new Runnable() {
                @Override
                public void run() {
                    showAllAppsCling();
                }
            });
        }
    }

    void showAllAppsCling() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "showAllAppsCling: mHasShownAllAppsCling = " + mHasShownAllAppsCling);
        }

//        if (!mHasShownAllAppsCling && isDataReady()) {
//            mHasShownAllAppsCling = true;
//            // Calculate the position for the cling punch through
//            int[] offset = new int[2];
//            int[] pos = mWidgetSpacingLayout.estimateCellPosition(mClingFocusedX, mClingFocusedY);
//            mLauncher.getDragLayer().getLocationInDragLayer(this, offset);
//            // PagedViews are centered horizontally but top aligned
//            pos[0] += (getMeasuredWidth() - mWidgetSpacingLayout.getMeasuredWidth()) / 2 +
//                    offset[0];
//            pos[1] += offset[1];
//            mLauncher.showFirstRunAllAppsCling(pos);
//        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onMeasure : mApps = " + mApps.size() + ", mAppsHasSet = "
                    + mAppsHasSet + ", isDataReady() = " + isDataReady()
                    +", width="+width
                    +", height="+height);
        }
        
        if (!isDataReady()) {
            if (!mApps.isEmpty() && (mWidgets == null || !mWidgets.isEmpty()) && mAppsHasSet) {
                setDataIsReady();
                setMeasuredDimension(width, height);
                onDataReady(width, height);
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if(super.getChildCount() > 0){
        	int childCount = getPageCount(); 
	        if (childCount > 0) {
	            mMaxScrollX = getChildOffset(indexToPage(childCount - 1)) - getRelativeChildOffset(indexToPage(childCount - 1));
	        } else {
	            mMaxScrollX = 0;
	        }
        }

//        android.util.Log.i("QsLog", "onMeasure==childCount:"+childCount+"=index:"+indexToPage(childCount - 1)
//    			+"=OffsetScrollX:"+getOffsetScrollX()
//    			+"=mMaxScrollX:"+mMaxScrollX);
    }
    @Override
    public boolean isSupportWidget(){
    	return ((mSupportContentType&ContentType_Widgets) > 0);
    }
    
    public int getSupportContentType(){
    	return mSupportContentType;
    }
    
    public int getCurrentContentType(){
    	return mCurrentContentType;
    }

    public void onPackagesUpdated() {
        // Get the list of widgets and shortcuts
    	if(!isSupportWidget())
    		return;
    	
        mWidgets.clear();
        mWidgetShortCut.clear();
        List<AppWidgetProviderInfo> widgets =
            AppWidgetManager.getInstance(mLauncher).getInstalledProviders();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updatePackages: widgets size = " + widgets.size());
        }

        Intent shortcutsIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        List<ResolveInfo> shortcuts = mPackageManager.queryIntentActivities(shortcutsIntent, 0);
        for (AppWidgetProviderInfo widget : widgets) {
            if (widget.minWidth > 0 && widget.minHeight > 0) {
                // Ensure that all widgets we show can be added on a workspace of this size
                int[] spanXY = Launcher.getSpanForWidget(mLauncher, widget);
                int[] minSpanXY = Launcher.getMinSpanForWidget(mLauncher, widget);
                int minSpanX = Math.min(spanXY[0], minSpanXY[0]);
                int minSpanY = Math.min(spanXY[1], minSpanXY[1]);
                if (minSpanX <= LauncherModel.getCellCountX() &&
                        minSpanY <= LauncherModel.getCellCountY()) {
                    mWidgets.add(widget);
                } else {
                    LauncherLog.e(TAG, "Widget " + widget.provider + " can not fit on this device (" + widget.minWidth
                            + ", " + widget.minHeight + "), min span is (" + minSpanX + ", " + minSpanY + ")"
                            + "), span is (" + spanXY[0] + ", " + spanXY[1] + ")");
                }
            } else {
                LauncherLog.e(TAG, "Widget " + widget.provider + " has invalid dimensions (" +
                        widget.minWidth + ", " + widget.minHeight + ")");
            }
        }
        mWidgetShortCut.addAll(shortcuts);
        //mWidgets.addAll(shortcuts);
        Collections.sort(mWidgets,
                new LauncherModel.WidgetAndShortcutNameComparator(mPackageManager));
        
        Collections.sort(mWidgetShortCut,
                new LauncherModel.WidgetAndShortcutNameComparator(mPackageManager));
        updatePageCounts();
        invalidateOnDataChange();
    }

    @Override
    public void onClick(View v) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onClick: v = " + v + ", v.getTag() = " + v.getTag());
        }

        // When we have exited all apps or are in transition, disregard clicks
        if ((!mLauncher.isAllAppsVisible() && !mLauncher.isAllAppsWidgetVisible() )||
                mLauncher.getWorkspace().isSwitchingState()) return;

        /// M: Add for unread feature, the icon is placed in a RealtiveLayout.
        if (v instanceof MTKAppIcon) {
        	v = ((MTKAppIcon)v).mAppIcon;
        }
        
        if(isSupportWidget()){
        	bindSelectedItem((ItemInfo) v.getTag());
        	return;
        }
        
        if (v instanceof PagedViewIcon) {
            // Animate some feedback to the click
            final ApplicationInfo appInfo = (ApplicationInfo) v.getTag();
            
         // Lock the drawable state to pressed until we return to Launcher
            if (mPressedIcon != null) {
                mPressedIcon.lockDrawableState();
            }
            
            increaseAppFreqInfo(appInfo);
            mLauncher.startActivitySafely(v, appInfo.intent, appInfo);

        } else if (v instanceof PagedViewWidget) {
            // Let the user know that they have to long press to add a widget
            if (mWidgetInstructionToast != null) {
                mWidgetInstructionToast.cancel();
            }
            mWidgetInstructionToast = Toast.makeText(getContext(),R.string.long_press_widget_to_add,
                Toast.LENGTH_SHORT);
            mWidgetInstructionToast.show();

            // Create a little animation to show that the widget can move
            float offsetY = getResources().getDimensionPixelSize(R.dimen.dragViewOffsetY);
            final ImageView p = (ImageView) v.findViewById(R.id.widget_preview);
            AnimatorSet bounce = LauncherAnimUtils.createAnimatorSet();
            ValueAnimator tyuAnim = LauncherAnimUtils.ofFloat(p, "translationY", offsetY);
            tyuAnim.setDuration(125);
            ValueAnimator tydAnim = LauncherAnimUtils.ofFloat(p, "translationY", 0f);
            tydAnim.setDuration(100);
            bounce.play(tyuAnim).before(tydAnim);
            bounce.setInterpolator(new AccelerateInterpolator());
            bounce.start();
        }
    }

    private void bindSelectedItem(ItemInfo item){
    	if(item == null)
    		return;
    	
    	
    	final Workspace workspace = mLauncher.getWorkspace();
    	int screen = mLauncher.getCurrentWorkspaceScreen();
    	final CellLayout cellLayout = (CellLayout)workspace.getPageAt(screen);

    	int[] targetCell = new int[2];
    	int container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
    	
    	if (item instanceof PendingAddItemInfo) {
    		//final PendingAddItemInfo pendingInfo = (PendingAddItemInfo) item;
    		if(!cellLayout.findCellForSpan(targetCell, item.spanX, item.spanY)){
            	Toast.makeText(mLauncher, mLauncher.getString(R.string.completely_out_of_space),
                        Toast.LENGTH_SHORT).show();
            	return;
            }
    		
    		switch (item.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                int span[] = new int[2];
                span[0] = item.spanX;
                span[1] = item.spanY;
                mLauncher.addAppWidgetFromDrop(new PendingAddWidgetInfo((PendingAddWidgetInfo) item),
                        container, screen, targetCell, span, null);
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                mLauncher.processShortcutFromDrop(((PendingAddItemInfo) item).componentName,
                        container, screen, targetCell, null);
                break;
            default:
                throw new IllegalStateException("Unknown item type: " +
                		item.itemType);
            }
    		
    		//pendingInfo = new PendingAddWidgetInfo((PendingAddWidgetInfo) v.getTag());
    		
//    		View finalView = pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
//                    ? ((PendingAddWidgetInfo) pendingInfo).boundWidget : null;
    		
    	} else {
            if(!cellLayout.findCellForSpan(targetCell, 1, 1)){
            	Toast.makeText(mLauncher, mLauncher.getString(R.string.completely_out_of_space),
                        Toast.LENGTH_SHORT).show();
            	return;
            }
            
            mLauncher.completeAddApplication(((ApplicationInfo) item).intent, container, screen, targetCell[0], targetCell[0]);
//            ShortcutInfo info = null;
//            
//            switch (item.itemType) {
//            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
//            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
//                if (item instanceof ApplicationInfo) {
//                    // Came from all apps -- make a copy
//                	info = new ShortcutInfo((ApplicationInfo) item);
//                } else {
//                	info = new ShortcutInfo((ShortcutInfo)item);
//                }
//                break;
//            default:
//                throw new IllegalStateException("Unknown item type: " + item.itemType);
//            }
//
//            workspace.addApplicationShortcut(info, cellLayout, container, screen, 
//            		targetCell[0], targetCell[1], true, targetCell[0], targetCell[1]);

    	}
    }
    
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return FocusHelper.handleAppsCustomizeKeyEvent(v,  keyCode, event);
    }

    /*
     * PagedViewWithDraggableItems implementation
     */
    @Override
    protected void determineDraggingStart(android.view.MotionEvent ev) {
        // Disable dragging by pulling an app down for now.
    }

    private void beginDraggingApplication(View v) {
        mLauncher.getWorkspace().onDragStartedWithItem(v);
        mLauncher.getWorkspace().beginDragShared(v, this);
    }

    Bundle getDefaultOptionsForWidget(Launcher launcher, PendingAddWidgetInfo info) {
        Bundle options = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            AppWidgetResizeFrame.getWidgetSizeRanges(mLauncher, info.spanX, info.spanY, mTmpRect);
            Rect padding = AppWidgetHostView.getDefaultPaddingForWidget(mLauncher,
                    info.componentName, null);

            float density = getResources().getDisplayMetrics().density;
            int xPaddingDips = (int) ((padding.left + padding.right) / density);
            int yPaddingDips = (int) ((padding.top + padding.bottom) / density);

            options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                    mTmpRect.left - xPaddingDips);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    mTmpRect.top - yPaddingDips);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
                    mTmpRect.right - xPaddingDips);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                    mTmpRect.bottom - yPaddingDips);
        }
        return options;
    }

    private void preloadWidget(final PendingAddWidgetInfo info) {
    	if(!isSupportWidget())
    		return;
    	
        final AppWidgetProviderInfo pInfo = info.info;
        final Bundle options = getDefaultOptionsForWidget(mLauncher, info);

        if (LauncherLog.DEBUG) {
        	LauncherLog.d(TAG, "preloadWidget info = " + info + ", pInfo = " + pInfo + 
        			", pInfo.configure = " + pInfo.configure);
        }

        if (pInfo.configure != null) {
            info.bindOptions = options;
            return;
        }

        mWidgetCleanupState = WIDGET_PRELOAD_PENDING;
        mBindWidgetRunnable = new Runnable() {
            @Override
            public void run() {
                mWidgetLoadingId = mLauncher.getAppWidgetHost().allocateAppWidgetId();
                // Options will be null for platforms with JB or lower, so this serves as an
                // SDK level check.
                if (options == null) {
                    if (AppWidgetManager.getInstance(mLauncher).bindAppWidgetIdIfAllowed(
                            mWidgetLoadingId, info.componentName)) {
                        mWidgetCleanupState = WIDGET_BOUND;
                    }
                } else {
                    if (AppWidgetManager.getInstance(mLauncher).bindAppWidgetIdIfAllowed(
                            mWidgetLoadingId, info.componentName, options)) {
                        mWidgetCleanupState = WIDGET_BOUND;
                    }
                }
            }
        };
        post(mBindWidgetRunnable);

        mInflateWidgetRunnable = new Runnable() {
            @Override
            public void run() {
                if (mWidgetCleanupState != WIDGET_BOUND) {
                    return;
                }
                AppWidgetHostView hostView = mLauncher.
                        getAppWidgetHost().createView(getContext(), mWidgetLoadingId, pInfo);
                info.boundWidget = hostView;
                mWidgetCleanupState = WIDGET_INFLATED;
                hostView.setVisibility(INVISIBLE);
                int[] unScaledSize = mLauncher.getWorkspace().estimateItemSize(info.spanX,
                        info.spanY, info, false);

                // We want the first widget layout to be the correct size. This will be important
                // for width size reporting to the AppWidgetManager.
                DragLayer.LayoutParams lp = new DragLayer.LayoutParams(unScaledSize[0],
                        unScaledSize[1]);
                lp.x = lp.y = 0;
                lp.customPosition = true;
                hostView.setLayoutParams(lp);
                mLauncher.getDragLayer().addView(hostView);
            }
        };
        post(mInflateWidgetRunnable);
    }

    @Override
    public void onShortPress(View v) {
    	if (LauncherLog.DEBUG) {
    		LauncherLog.d(TAG, "onShortcutPress v = " + v + ", v.getTag() = " + v.getTag());
    	}
    	
    	if(!isSupportWidget())
    		return;

        // We are anticipating a long press, and we use this time to load bind and instantiate
        // the widget. This will need to be cleaned up if it turns out no long press occurs.
        if (mCreateWidgetInfo != null) {
            // Just in case the cleanup process wasn't properly executed. This shouldn't happen.
            cleanupWidgetPreloading(false);
        }
        mCreateWidgetInfo = new PendingAddWidgetInfo((PendingAddWidgetInfo) v.getTag());
        preloadWidget(mCreateWidgetInfo);
    }

    private void cleanupWidgetPreloading(boolean widgetWasAdded) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "cleanupWidgetPreloading widgetWasAdded = " + widgetWasAdded
                    + ", mCreateWidgetInfo = " + mCreateWidgetInfo + ", mWidgetLoadingId = "
                    + mWidgetLoadingId);
        }
        
        if(!isSupportWidget())
    		return;

        if (!widgetWasAdded) {
            // If the widget was not added, we may need to do further cleanup.
            PendingAddWidgetInfo info = mCreateWidgetInfo;
            mCreateWidgetInfo = null;

            if (mWidgetCleanupState == WIDGET_PRELOAD_PENDING) {
                // We never did any preloading, so just remove pending callbacks to do so
                removeCallbacks(mBindWidgetRunnable);
                removeCallbacks(mInflateWidgetRunnable);
            } else if (mWidgetCleanupState == WIDGET_BOUND) {
                 // Delete the widget id which was allocated
                if (mWidgetLoadingId != -1) {
                    mLauncher.getAppWidgetHost().deleteAppWidgetId(mWidgetLoadingId);
                }

                // We never got around to inflating the widget, so remove the callback to do so.
                removeCallbacks(mInflateWidgetRunnable);
            } else if (mWidgetCleanupState == WIDGET_INFLATED) {
                // Delete the widget id which was allocated
                if (mWidgetLoadingId != -1) {
                    mLauncher.getAppWidgetHost().deleteAppWidgetId(mWidgetLoadingId);
                }

                // The widget was inflated and added to the DragLayer -- remove it.
                AppWidgetHostView widget = info.boundWidget;
                mLauncher.getDragLayer().removeView(widget);
            }
        }
        mWidgetCleanupState = WIDGET_NO_CLEANUP_REQUIRED;
        mWidgetLoadingId = -1;
        mCreateWidgetInfo = null;
        PagedViewWidget.resetShortPressTarget();
    }

    @Override
    public void cleanUpShortPress(View v) {
        if (!mDraggingWidget) {
            cleanupWidgetPreloading(false);
        }
    }

    private boolean beginDraggingWidget(View v) {
    	if(!isSupportWidget())
    		return false;
    	
        mDraggingWidget = true;
        // Get the widget preview as the drag representation
        ImageView image = (ImageView) v.findViewById(R.id.widget_preview);
        PendingAddItemInfo createItemInfo = (PendingAddItemInfo) v.getTag();

        if (LauncherLog.DEBUG_DRAG) {
            LauncherLog.d(TAG, "beginDraggingWidget: createItemInfo = " + createItemInfo 
                    + ", v = " + v + ", image = " + image + ", this = " + this);
        }

        // If the ImageView doesn't have a drawable yet, the widget preview hasn't been loaded and
        // we abort the drag.
        if (image.getDrawable() == null) {
            mDraggingWidget = false;
            return false;
        }

        // Compose the drag image
        Bitmap preview;
        Bitmap outline;
        float scale = 1f;
        if (createItemInfo instanceof PendingAddWidgetInfo) {
            // This can happen in some weird cases involving multi-touch. We can't start dragging
            // the widget if this is null, so we break out.
            if (mCreateWidgetInfo == null) {
                return false;
            }

            PendingAddWidgetInfo createWidgetInfo = mCreateWidgetInfo;
            createItemInfo = createWidgetInfo;
            int spanX = createItemInfo.spanX;
            int spanY = createItemInfo.spanY;
            int[] size = mLauncher.getWorkspace().estimateItemSize(spanX, spanY,
                    createWidgetInfo, true);

            FastBitmapDrawable previewDrawable = (FastBitmapDrawable) image.getDrawable();
            float minScale = 1.25f;
            int maxWidth, maxHeight;
            maxWidth = Math.min((int) (previewDrawable.getIntrinsicWidth() * minScale), size[0]);
            maxHeight = Math.min((int) (previewDrawable.getIntrinsicHeight() * minScale), size[1]);
            preview = getWidgetPreview(createWidgetInfo.componentName, createWidgetInfo.previewImage,
                    createWidgetInfo.icon, spanX, spanY, maxWidth, maxHeight);

            // Determine the image view drawable scale relative to the preview
            float[] mv = new float[9];
            Matrix m = new Matrix();
            m.setRectToRect(
                    new RectF(0f, 0f, (float) preview.getWidth(), (float) preview.getHeight()),
                    new RectF(0f, 0f, (float) previewDrawable.getIntrinsicWidth(),
                            (float) previewDrawable.getIntrinsicHeight()),
                    Matrix.ScaleToFit.START);
            m.getValues(mv);
            scale = (float) mv[0];
        } else {
            PendingAddShortcutInfo createShortcutInfo = (PendingAddShortcutInfo) v.getTag();
            Drawable icon = mIconCache.getFullResIcon(createShortcutInfo.shortcutActivityInfo);
            preview = Bitmap.createBitmap(icon.getIntrinsicWidth(),
                    icon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);

            mCanvas.setBitmap(preview);
            mCanvas.save();
            renderDrawableToBitmap(icon, preview, 0, 0,
                    icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            mCanvas.restore();
            mCanvas.setBitmap(null);
            createItemInfo.spanX = createItemInfo.spanY = 1;
        }

        // Don't clip alpha values for the drag outline if we're using the default widget preview
        boolean clipAlpha = !(createItemInfo instanceof PendingAddWidgetInfo &&
                (((PendingAddWidgetInfo) createItemInfo).previewImage == 0));

        // Save the preview for the outline generation, then dim the preview
        outline = Bitmap.createScaledBitmap(preview, preview.getWidth(), preview.getHeight(),
                false);

        // Start the drag
        mLauncher.lockScreenOrientation();
        mLauncher.getWorkspace().onDragStartedWithItem(createItemInfo, outline, clipAlpha);
        mDragController.startDrag(image, preview, this, createItemInfo,
                DragController.DRAG_ACTION_COPY, null, scale);
        outline.recycle();
        preview.recycle();
        return true;
    }

    @Override
    protected boolean beginDragging(final View v) {
        if (LauncherLog.DEBUG_DRAG) {
            LauncherLog.d(TAG, "beginDragging: v = " + v + ", this = " + this);
        }

        if (!super.beginDragging(v)) return false;

        if (v instanceof PagedViewIcon) {
            beginDraggingApplication(v);
        } else if (v instanceof PagedViewWidget) {
            if (!beginDraggingWidget(v)) {
                return false;
            }
        }

        // We delay entering spring-loaded mode slightly to make sure the UI
        // thready is free of any work.
        postDelayed(new Runnable() {
            @Override
            public void run() {
                // We don't enter spring-loaded mode if the drag has been cancelled
                if (mLauncher.getDragController().isDragging()) {
                    // Dismiss the cling
                    mLauncher.dismissAllAppsCling(null);

                    // Reset the alpha on the dragged icon before we drag
                    resetDrawableState();

                    // Go into spring loaded mode (must happen before we startDrag())
                    if(isSupportWidget()){
                    	
                    } else {
                    	mLauncher.enterSpringLoadedDragMode(isSupportWidget());
                    }
                }
            }
        }, 150);

        return true;
    }

    /**
     * Clean up after dragging.
     *
     * @param target where the item was dragged to (can be null if the item was flung)
     */
    private void endDragging(View target, boolean isFlingToDelete, boolean success) {
        if (LauncherLog.DEBUG_DRAG) {
            LauncherLog.d(TAG, "endDragging: target = " + target + ", isFlingToDelete = " + isFlingToDelete + ", success = " + success);
        }

        if (isFlingToDelete || !success || (target != mLauncher.getWorkspace() &&
                !(target instanceof DeleteDropTarget))) {
            // Exit spring loaded mode if we have not successfully dropped or have not handled the
            // drop in Workspace
            mLauncher.exitSpringLoadedDragMode(/*isSupportWidget()*/);
        }
        mLauncher.unlockScreenOrientation(false);
    }

    @Override
    public View getContent() {
        return null;
    }

    @Override
    public void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace) {
    	if (LauncherLog.DEBUG) {
    		LauncherLog.d(TAG, "onLauncherTransitionPrepare l = " + l + ", animated = " + animated + 
    				", toWorkspace = " + toWorkspace);
    	}

        mInTransition = true;
        if (toWorkspace) {
            cancelAllTasks();
        }
    }

    @Override
    public void onLauncherTransitionStart(Launcher l, boolean animated, boolean toWorkspace) {
    }

    @Override
    public void onLauncherTransitionStep(Launcher l, float t) {
    }

    @Override
    public void onLauncherTransitionEnd(Launcher l, boolean animated, boolean toWorkspace) {
    	if (LauncherLog.DEBUG) {
    		LauncherLog.d(TAG, "onLauncherTransitionEnd l = " + l + ", animated = " + animated + 
    				", toWorkspace = " + toWorkspace);
    	}

        mInTransition = false;
        for (AsyncTaskPageData d : mDeferredSyncWidgetPageItems) {
            onSyncWidgetPageItems(d);
        }
        mDeferredSyncWidgetPageItems.clear();
        for (Runnable r : mDeferredPrepareLoadWidgetPreviewsTasks) {
            r.run();
        }
        mDeferredPrepareLoadWidgetPreviewsTasks.clear();
        mForceDrawAllChildrenNextFrame = !toWorkspace;
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean isFlingToDelete,
            boolean success) {
        if (LauncherLog.DEBUG_DRAG) {
            LauncherLog.d(TAG, "onDropCompleted: target = " + target + ", d = " + d + ", isFlingToDelete = " + isFlingToDelete + ", success = " + success);
        }

        // Return early and wait for onFlingToDeleteCompleted if this was the result of a fling
        if (isFlingToDelete) return;

        endDragging(target, false, success);

        // Display an error message if the drag failed due to there not being enough space on the
        // target layout we were dropping on.
        if (!success) {
            boolean showOutOfSpaceMessage = false;
            if (target instanceof Workspace) {
                int currentScreen = mLauncher.getCurrentWorkspaceScreen();
                Workspace workspace = (Workspace) target;
                CellLayout layout = (CellLayout) workspace.getChildAt(currentScreen);
                ItemInfo itemInfo = (ItemInfo) d.dragInfo;
                if (layout != null) {
                    layout.calculateSpans(itemInfo);
                    showOutOfSpaceMessage =
                            !layout.findCellForSpan(null, itemInfo.spanX, itemInfo.spanY);
                }
                /// M: Display an error message if the drag failed due to exist one IMTKWidget 
                /// which providerName equals the providerName of the dragInfo.
                if (d.dragInfo instanceof PendingAddWidgetInfo) {
                    PendingAddWidgetInfo info = (PendingAddWidgetInfo) d.dragInfo;
                    if (workspace.searchIMtkWidget(workspace, info.componentName.getClassName()) != null) {
                        mLauncher.showOnlyOneWidgetMessage(info);
                    }
                }
            }
            if (showOutOfSpaceMessage) {
                mLauncher.showOutOfSpaceMessage(false);
            }

            d.deferDragViewCleanupPostAnimation = false;
        }
        cleanupWidgetPreloading(success);
        mDraggingWidget = false;
    }

    @Override
    public void onFlingToDeleteCompleted() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onFlingToDeleteCompleted.");
        }

        // We just dismiss the drag when we fling, so cleanup here
        endDragging(null, true, true);
        cleanupWidgetPreloading(false);
        mDraggingWidget = false;
    }

    @Override
    public boolean supportsFlingToDelete() {
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDetachedFromWindow.");
        }
        if(mAppsChangeFreqAsyncTask != null){
        	mAppsChangeFreqAsyncTask.cancel(true);
        	mAppsChangeFreqAsyncTask = null;
        }
        cancelAllTasks();
    }

    public void clearAllWidgetPages() {
        cancelAllTasks();
        if(!isSupportWidget())
        	return;
        int count = getChildCount();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "clearAllWidgetPages: count = " + count);
        }

        for (int i = 0; i < count; i++) {
            View v = getChildAt(i);
            if (v instanceof PagedViewGridLayout) {
                ((PagedViewGridLayout) v).removeAllViewsOnPage();
                mDirtyPageContent.set(i, true);
            }
        }
    }

    private void cancelAllTasks() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "cancelAllTasks: mRunningTasks size = " + mRunningTasks.size());
        }

        // Clean up all the async tasks
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            task.cancel(false);
            iter.remove();
            mDirtyPageContent.set(task.page, true);

            // We've already preallocated the views for the data to load into, so clear them as well
            View v = getChildAt(task.page);
            if (v instanceof PagedViewGridLayout) {
                ((PagedViewGridLayout) v).removeAllViewsOnPage();
            }
        }
        mDeferredSyncWidgetPageItems.clear();
        mDeferredPrepareLoadWidgetPreviewsTasks.clear();
    }

    public void setContentType(int type) {
    	
    	mContentItemsPageIndexMap.put(mCurrentContentType, Math.min((getPageCount()-1), getCurrentPage()));
    	if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "setContentType: newTab = " + type + ", oldtab = "
                    + mCurrentContentType);
        }
    	//android.util.Log.i("QsLog", "setContentType()=====type:"+type);
//    	android.util.Log.v("QsLog", "setContentType(0): newTab = " + type + ", oldtab = "
//                + mCurrentContentType
//    			+"=OffsetScrollX:"+getOffsetScrollX()
//    			+"=mMaxScrollX:"+mMaxScrollX
//    			+"==PageCount:"+getPageCount());
    	
    	mCurrentContentType = type;
    	
    	int childCount = getPageCount(); 
        if (childCount > 0) {
            mMaxScrollX = getChildOffset(indexToPage(childCount - 1)) - getRelativeChildOffset(indexToPage(childCount - 1));
        } else {
            mMaxScrollX = 0;
        }
        
    	int page = Math.min(mContentItemsPageIndexMap.get(type), (childCount - 1));
    	
    	if(mQsWorkspaceCallback != null)
        	mQsWorkspaceCallback.onPageCountChanged(childCount);
    	
    	if(LOAD_DATA_STYLE_OLD){
    		invalidatePageData(page, true);
    	} else {
    		if (page > -1) {
	            setCurrentPage(page);
	        }
	    	//invalidatePageData(page, true);
	    	loadAssociatedPages(page, true);
	//    	android.util.Log.v("QsLog", "setContentType(2): "
	//    			+"=OffsetScrollX:"+getOffsetScrollX()
	//    			+"=mMaxScrollX:"+mMaxScrollX
	//    			+"=PageCount:"+getPageCount());
	    	super.invalidate();
    	}
    }
    
    protected int snapToDestination() {
    	if(getPageCount() > 1)
    		return super.snapToDestination();
    	return 0;
    }

    protected int snapToPage(int whichPage, int delta, int duration) {
//    	if(whichPage == getCurrentPage())
//    		return whichPage;
    	whichPage = super.snapToPage(whichPage, delta, duration);
    	//if(!isSupportWidget())
    	//	mDeferLoadAssociatedPagesUntilScrollCompletes = false;
    	
        updateCurrentTab(whichPage);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "snapToPage: whichPage = " + whichPage + ", delta = "
                    + delta + ", duration = " + duration + ", this = " + this);
        }

        // Update the thread priorities given the direction lookahead
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            int pageIndex = task.page;
            if ((mNextPage > mCurrentPage && pageIndex >= mCurrentPage) ||
                (mNextPage < mCurrentPage && pageIndex <= mCurrentPage)) {
                task.setThreadPriority(getThreadPriorityForPage(pageIndex));
            } else {
                task.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
            }
        }
        
        return whichPage;
    }

    private void updateCurrentTab(int currentPage) {
        AppsCustomizeTabHost tabHost = getTabHost();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updateCurrentTab: currentPage = " + currentPage
                    + ", mCurrentPage = " + mCurrentPage + ", this = " + this);
        }

        if (tabHost != null) {
            String tag = tabHost.getCurrentTabTag();
            if (tag != null && !tag.equals(tabHost.getTabTagForContentType(mCurrentContentType))) {
            	tabHost.setCurrentTabFromContent(mCurrentContentType);
            }
        }
    }

    /*
     * Apps PagedView implementation
     */
    private void setVisibilityOnChildren(ViewGroup layout, int visibility) {
        int childCount = layout.getChildCount();
        for (int i = 0; i < childCount; ++i) {
            layout.getChildAt(i).setVisibility(visibility);
        }
    }

    private void setupPage(PagedViewCellLayout layout) {
        layout.setCellCount(mCellCountX, mCellCountY);
        layout.setGap(mPageLayoutWidthGap, mPageLayoutHeightGap);
        layout.setPadding(mPageLayoutPaddingLeft, mPageLayoutPaddingTop,
                mPageLayoutPaddingRight, mPageLayoutPaddingBottom);

        // Note: We force a measure here to get around the fact that when we do layout calculations
        // immediately after syncing, we don't have a proper width.  That said, we already know the
        // expected page width, so we can actually optimize by hiding all the TextView-based
        // children that are expensive to measure, and let that happen naturally later.
        setVisibilityOnChildren(layout, View.GONE);
        int widthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST);
        layout.setMinimumWidth(getPageContentWidth());
        layout.measure(widthSpec, heightSpec);
        setVisibilityOnChildren(layout, View.VISIBLE);
    }
    
    public void syncAppsPageItems(int page, boolean immediate) {
    	syncAppsPageItems(page, mCurrentContentType, immediate);
    }
    public final static String PAGE_LOADED_TAG = "loaded";
    public void syncAppsPageItems(int page, int contentType, boolean immediate) {
        // ensure that we have the right number of items on the pages
    	int numCells = mCellCountX * mCellCountY;
    	int startIndex = page * numCells;
    	
    	final ArrayList<ApplicationInfo> appsList;
    	if(contentType == ContentType_Apps_Freq){
    		appsList = mAppsFreq;
    	} else if(contentType == ContentType_Apps_Download) {
    		appsList = mAppsDownload;
    	} else { 
    		appsList = mApps;
    	}

        int endIndex = Math.min(startIndex + numCells, appsList.size());
        
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "syncAppsPageItems: page = " + page + ", immediate = " + immediate
                    + ", numCells = " + numCells + ", startIndex = " + startIndex + ", endIndex = "
                    + endIndex + ", child count = "
                    + getChildCount() + ", this = " + this);
        }

        PagedViewCellLayout layout = (PagedViewCellLayout) getPageAt(page);
        if(PagedView.LOAD_DATA_STYLE_OLD){
        	
        } else {
	        if(contentType != ContentType_Apps_Freq && PAGE_LOADED_TAG.equals(layout.getTag())){
	        	return;
	        }
        }

        layout.removeAllViewsOnPage();
        //ArrayList<Object> items = new ArrayList<Object>();
        //ArrayList<Bitmap> images = new ArrayList<Bitmap>();
        for (int i = startIndex; i < endIndex; ++i) {
            final ApplicationInfo info = appsList.get(i);
            MTKAppIcon icon = (MTKAppIcon) mLayoutInflater.inflate(
                    R.layout.mtk_apps_customize_application, layout, false);
            icon.applyFromApplicationInfo(info, true, this);
            icon.mAppIcon.setOnClickListener(this);
            icon.mAppIcon.setOnLongClickListener(this);
            icon.setOnTouchListener(this);
            icon.mAppIcon.setOnKeyListener(this);

            int index = i - startIndex;
            int x = index % mCellCountX;
            int y = index / mCellCountX;
            layout.addViewToCellLayout(icon, -1, i, new PagedViewCellLayout.LayoutParams(x,y, 1,1));

            //items.add(info);
            //images.add(info.iconBitmap);
        }
        layout.setTag(PAGE_LOADED_TAG);
        
        layout.createHardwareLayers();
    }

    /**
     * A helper to return the priority for loading of the specified widget page.
     */
    private int getWidgetPageLoadPriority(int childindex) {
        // If we are snapping to another page, use that index as the target page index
        int toPage = indexToPage(mCurrentPage);
        if (mNextPage > -1) {
            toPage = indexToPage(mNextPage);
        }

        // We use the distance from the target page as an initial guess of priority, but if there
        // are no pages of higher priority than the page specified, then bump up the priority of
        // the specified page.
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        int minPageDiff = Integer.MAX_VALUE;
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            minPageDiff = Math.abs(task.page - toPage);
        }

        int rawPageDiff = Math.abs(childindex - toPage);
        return rawPageDiff - Math.min(rawPageDiff, minPageDiff);
    }
    /**
     * Return the appropriate thread priority for loading for a given page (we give the current
     * page much higher priority)
     */
    private int getThreadPriorityForPage(int childindex) {
        // TODO-APPS_CUSTOMIZE: detect number of cores and set thread priorities accordingly below
        int pageDiff = getWidgetPageLoadPriority(childindex);
        if (pageDiff <= 0) {
            return Process.THREAD_PRIORITY_LESS_FAVORABLE;
        } else if (pageDiff <= 1) {
            return Process.THREAD_PRIORITY_LOWEST;
        } else {
            return Process.THREAD_PRIORITY_LOWEST;
        }
    }

    private int getSleepForPage(int childindex) {
        int pageDiff = getWidgetPageLoadPriority(childindex);
        return Math.max(0, pageDiff * sPageSleepDelay);
    }

    /**
     * Creates and executes a new AsyncTask to load a page of widget previews.
     */
    private void prepareLoadWidgetPreviewsTask(int childindex, ArrayList<Object> widgets,
            int cellWidth, int cellHeight, int cellCountX) {
    	if(!isSupportWidget())
    		return;
    	
        // Prune all tasks that are no longer needed
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            int taskPage = task.page;
            if (taskPage < getAssociatedLowerPageBound(mCurrentPage) ||
                    taskPage > getAssociatedUpperPageBound(mCurrentPage)) {
                task.cancel(false);
                iter.remove();
            } else {
                task.setThreadPriority(getThreadPriorityForPage(taskPage));
            }
        }

        // We introduce a slight delay to order the loading of side pages so that we don't thrash
        final int sleepMs = getSleepForPage(childindex);
        AsyncTaskPageData pageData = new AsyncTaskPageData(childindex, widgets, cellWidth, cellHeight,
            new AsyncTaskCallback() {
                @Override
                public void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data) {
                    try {
                        try {
                            Thread.sleep(sleepMs);
                        } catch (Exception e) {}
                        loadWidgetPreviewsInBackground(task, data);
                    } finally {
                        if (task.isCancelled()) {
                            data.cleanup(true);
                        }
                    }
                }
            },
            new AsyncTaskCallback() {
                @Override
                public void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data) {
                    mRunningTasks.remove(task);
                    if (task.isCancelled()) {
                        return;
                    }
                    // do cleanup inside onSyncWidgetPageItems
                    onSyncWidgetPageItems(data);
                }
            });

        // Ensure that the task is appropriately prioritized and runs in parallel
        AppsCustomizeAsyncTask t = new AppsCustomizeAsyncTask(childindex,
                AsyncTaskPageData.Type.LoadWidgetPreviewData);
        t.setThreadPriority(getThreadPriorityForPage(childindex));
        t.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, pageData);
        mRunningTasks.add(t);
    }

    /*
     * Widgets PagedView implementation
     */
    private void setupPage(PagedViewGridLayout layout) {
        layout.setPadding(mPageLayoutPaddingLeft, mPageLayoutPaddingTop,
                mPageLayoutPaddingRight, mPageLayoutPaddingBottom);

        // Note: We force a measure here to get around the fact that when we do layout calculations
        // immediately after syncing, we don't have a proper width.
        int widthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST);
        layout.setMinimumWidth(getPageContentWidth());
        layout.measure(widthSpec, heightSpec);
    }

    private void renderDrawableToBitmap(Drawable d, Bitmap bitmap, int x, int y, int w, int h) {
        renderDrawableToBitmap(d, bitmap, x, y, w, h, 1f);
    }

    private void renderDrawableToBitmap(Drawable d, Bitmap bitmap, int x, int y, int w, int h,
            float scale) {
        if (bitmap != null) {
            Canvas c = new Canvas(bitmap);
            c.scale(scale, scale);
            Rect oldBounds = d.copyBounds();
            d.setBounds(x, y, x + w, y + h);
            d.draw(c);
            d.setBounds(oldBounds); // Restore the bounds
            c.setBitmap(null);
        }
    }

    private Bitmap getShortcutPreview(ResolveInfo info, int maxWidth, int maxHeight) {
        Bitmap tempBitmap = mCachedShortcutPreviewBitmap.get();
        final Canvas c = mCachedShortcutPreviewCanvas.get();
        if (tempBitmap == null ||
                tempBitmap.getWidth() != maxWidth ||
                tempBitmap.getHeight() != maxHeight) {
            tempBitmap = Bitmap.createBitmap(maxWidth, maxHeight, Config.ARGB_8888);
            mCachedShortcutPreviewBitmap.set(tempBitmap);
        } else {
            c.setBitmap(tempBitmap);
            c.drawColor(0, PorterDuff.Mode.CLEAR);
            c.setBitmap(null);
        }
        // Render the icon
        Drawable icon = mIconCache.getFullResIcon(info);

        int paddingTop =
                getResources().getDimensionPixelOffset(R.dimen.shortcut_preview_padding_top);
        int paddingLeft =
                getResources().getDimensionPixelOffset(R.dimen.shortcut_preview_padding_left);
        int paddingRight =
                getResources().getDimensionPixelOffset(R.dimen.shortcut_preview_padding_right);

        int scaledIconWidth = (maxWidth - paddingLeft - paddingRight);

        renderDrawableToBitmap(
                icon, tempBitmap, paddingLeft, paddingTop, scaledIconWidth, scaledIconWidth);

        Bitmap preview = Bitmap.createBitmap(maxWidth, maxHeight, Config.ARGB_8888);
        c.setBitmap(preview);
        Paint p = mCachedShortcutPreviewPaint.get();
        if (p == null) {
            p = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            p.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            p.setAlpha((int) (255 * 0.06f));
            //float density = 1f;
            //p.setMaskFilter(new BlurMaskFilter(15*density, BlurMaskFilter.Blur.NORMAL));
            mCachedShortcutPreviewPaint.set(p);
        }
        c.drawBitmap(tempBitmap, 0, 0, p);
        c.setBitmap(null);

        renderDrawableToBitmap(icon, preview, 0, 0, mAppIconSize, mAppIconSize);

        return preview;
    }

    private Bitmap getWidgetPreview(ComponentName provider, int previewImage,
            int iconId, int cellHSpan, int cellVSpan, int maxWidth,
            int maxHeight) {
    	
    	if(!isSupportWidget())
    		return null;

        // Load the preview image if possible
        String packageName = provider.getPackageName();
        ///M:maxWidth & maxHeight maybe zero which can lead to createBitmap JE
        if (maxWidth <= 0) {
            Log.w(TAG, "getWidgetPreview packageName=" + packageName +", maxWidth:" + maxWidth);
            maxWidth = Integer.MAX_VALUE;
        }
        if (maxHeight <= 0) {
            Log.w(TAG, "getWidgetPreview packageName=" + packageName +", maxHeight:" + maxHeight);
            maxHeight = Integer.MAX_VALUE;
        }

        Drawable drawable = null;
        if (previewImage != 0) {
            drawable = mPackageManager.getDrawable(packageName, previewImage, null);
            if (drawable == null) {
                Log.w(TAG, "Can't load widget preview drawable 0x" +
                        Integer.toHexString(previewImage) + " for provider: " + provider);
            }
        }

        ///M: initialized to 0 for build pass
        int bitmapWidth = 0;
        int bitmapHeight = 0;
        Bitmap defaultPreview = null;
        boolean widgetPreviewExists = (drawable != null);
        ///M:getIntrinsicWidth & getIntrinsicHeight maybe return -1 which can lead to createBitmap JE
        boolean useWidgetPreview = false;
        if (widgetPreviewExists) {
            bitmapWidth = drawable.getIntrinsicWidth();
            bitmapHeight = drawable.getIntrinsicHeight();
            if ((bitmapWidth <= 0) || (bitmapHeight <= 0)) {
                Log.w(TAG, "getWidgetPreview packageName=" + packageName +", getIntrinsicWidth():" +
                        bitmapWidth + ", getIntrinsicHeight(): " + bitmapHeight);
            } else {
                useWidgetPreview = true;
            }
        } 

        if (useWidgetPreview == false) {
            // Generate a preview image if we couldn't load one
            if (cellHSpan < 1) cellHSpan = 1;
            if (cellVSpan < 1) cellVSpan = 1;

            BitmapDrawable previewDrawable = (BitmapDrawable) getResources()
                    .getDrawable(R.drawable.widget_preview_tile);
            final int previewDrawableWidth = previewDrawable
                    .getIntrinsicWidth();
            final int previewDrawableHeight = previewDrawable
                    .getIntrinsicHeight();
            bitmapWidth = previewDrawableWidth * cellHSpan; // subtract 2 dips
            bitmapHeight = previewDrawableHeight * cellVSpan;

            defaultPreview = Bitmap.createBitmap(bitmapWidth, bitmapHeight,
                    Config.ARGB_8888);
            final Canvas c = mCachedAppWidgetPreviewCanvas.get();
            c.setBitmap(defaultPreview);
            previewDrawable.setBounds(0, 0, bitmapWidth, bitmapHeight);

            /**
             * M: Since the previous setTileModeXY function creates shader and paint which shared by many preview bitmaps,
             * native exception happens in libskia.so because race condition between multi-thread. We create fresh new
             * objects to avoid this, but will need allocate more memory than before. @{
             */
            final Bitmap previewBitmap = previewDrawable.getBitmap();
            final BitmapShader shader = new BitmapShader(previewBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            final Paint shaderPaint = new Paint();
            shaderPaint.setShader(shader);
            c.drawPaint(shaderPaint);
            /** @} */

            c.setBitmap(null);

            // Draw the icon in the top left corner
            int minOffset = (int) (mAppIconSize * sWidgetPreviewIconPaddingPercentage);
            int smallestSide = Math.min(bitmapWidth, bitmapHeight);
            float iconScale = Math.min((float) smallestSide
                    / (mAppIconSize + 2 * minOffset), 1f);

            try {
                Drawable icon = null;
                int hoffset =
                        (int) ((previewDrawableWidth - mAppIconSize * iconScale) / 2);
                int yoffset =
                        (int) ((previewDrawableHeight - mAppIconSize * iconScale) / 2);
                if (iconId > 0) {
                    icon = mIconCache.getFullResIcon(packageName, iconId);
                }
                if (icon != null) {
                    renderDrawableToBitmap(icon, defaultPreview, hoffset,
                            yoffset, (int) (mAppIconSize * iconScale),
                            (int) (mAppIconSize * iconScale));
                }
            } catch (Resources.NotFoundException e) {
            }
        }

        // Scale to fit width only - let the widget preview be clipped in the
        // vertical dimension
        float scale = 1f;
        if (bitmapWidth > maxWidth) {
            scale = maxWidth / (float) bitmapWidth;
        }
        if (scale != 1f) {
            bitmapWidth = (int) (scale * bitmapWidth);
            bitmapHeight = (int) (scale * bitmapHeight);
        }

        Bitmap preview = Bitmap.createBitmap(bitmapWidth, bitmapHeight,
                Config.ARGB_8888);

        // Draw the scaled preview into the final bitmap
        if (widgetPreviewExists) {
            renderDrawableToBitmap(drawable, preview, 0, 0, bitmapWidth,
                    bitmapHeight);
        } else {
            final Canvas c = mCachedAppWidgetPreviewCanvas.get();
            final Rect src = mCachedAppWidgetPreviewSrcRect.get();
            final Rect dest = mCachedAppWidgetPreviewDestRect.get();
            c.setBitmap(preview);
            src.set(0, 0, defaultPreview.getWidth(), defaultPreview.getHeight());
            dest.set(0, 0, preview.getWidth(), preview.getHeight());

            Paint p = mCachedAppWidgetPreviewPaint.get();
            if (p == null) {
                p = new Paint();
                p.setFilterBitmap(true);
                mCachedAppWidgetPreviewPaint.set(p);
            }
            c.drawBitmap(defaultPreview, src, dest, p);
            c.setBitmap(null);
        }
        return preview;
    }

    public void syncWidgetPageItems(final int page, final boolean immediate, boolean isShortcut) {
    	if(!isSupportWidget()){
    		return;
    	}
    	final int countX = isShortcut ? mCellCountX : mWidgetCountX;
    	final int countY = isShortcut ? mCellCountY : mWidgetCountY;
        int numItemsPerPage = countX * countY;
        final int widthGap = isShortcut ? mPageLayoutWidthGap : mWidgetWidthGap;
        final int heightGap = isShortcut ? mPageLayoutHeightGap : mWidgetHeightGap;

        // Calculate the dimensions of each cell we are giving to each widget
        final ArrayList<Object> items = new ArrayList<Object>();
        int contentWidth = mWidgetSpacingLayout.getContentWidth();
        final int cellWidth = ((contentWidth - mPageLayoutPaddingLeft - mPageLayoutPaddingRight
                - ((countX - 1) * widthGap)) / countX);
        int contentHeight = mWidgetSpacingLayout.getContentHeight();
        final int cellHeight = ((contentHeight - mPageLayoutPaddingTop - mPageLayoutPaddingBottom
                - ((countY - 1) * heightGap)) / countY);

        // Prepare the set of widgets to load previews for in the background
        int offset = page* numItemsPerPage;
        if(isShortcut){
        	final int endindex = Math.min(offset + numItemsPerPage, mWidgetShortCut.size());
	        for (int i = offset; i < endindex; ++i) {
	            items.add(mWidgetShortCut.get(i));
	        }
        } else {
        	final int endindex = Math.min(offset + numItemsPerPage, mWidgets.size());
	        for (int i = offset; i < endindex; ++i) {
	            items.add(mWidgets.get(i));
	        }
        }
        
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "syncWidgetPageItems: page = " + page + ", immediate = " + immediate
                    + ", numItemsPerPage = " + numItemsPerPage + ", cellWidth = " + cellWidth
                    + ", contentHeight = " + contentHeight + ", cellHeight = " + cellHeight
                    + ", offset = " + offset + ", this = " + this);
        }

        // Prepopulate the pages with the other widget info, and fill in the previews later
        final PagedViewGridLayout layout = (PagedViewGridLayout) getChildAt(indexToPage(page));
        layout.setColumnCount(layout.getCellCountX());
        final int layoutResId = isShortcut ? R.layout.apps_customize_widget_shortcut : R.layout.apps_customize_widget;
        for (int i = 0; i < items.size(); ++i) {
            Object rawInfo = items.get(i);
            PendingAddItemInfo createItemInfo = null;
            PagedViewWidget widget = (PagedViewWidget) mLayoutInflater.inflate(
            		layoutResId, layout, false);
            if (!isShortcut && (rawInfo instanceof AppWidgetProviderInfo)) {
                // Fill in the widget information
                AppWidgetProviderInfo info = (AppWidgetProviderInfo) rawInfo;
                createItemInfo = new PendingAddWidgetInfo(info, null, null);

                // Determine the widget spans and min resize spans.
                int[] spanXY = Launcher.getSpanForWidget(mLauncher, info);
                createItemInfo.spanX = spanXY[0];
                createItemInfo.spanY = spanXY[1];
                int[] minSpanXY = Launcher.getMinSpanForWidget(mLauncher, info);
                createItemInfo.minSpanX = minSpanXY[0];
                createItemInfo.minSpanY = minSpanXY[1];

                widget.applyFromAppWidgetProviderInfo(info, -1, spanXY);
                widget.setTag(createItemInfo);
                widget.setShortPressListener(this);
            } else if (rawInfo instanceof ResolveInfo) {
                // Fill in the shortcuts information
                ResolveInfo info = (ResolveInfo) rawInfo;
                createItemInfo = new PendingAddShortcutInfo(info.activityInfo);
                createItemInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
                createItemInfo.componentName = new ComponentName(info.activityInfo.packageName,
                        info.activityInfo.name);
                widget.applyFromResolveInfo(mPackageManager, info);
                widget.setTag(createItemInfo);
            }
            widget.setOnClickListener(this);
            widget.setOnLongClickListener(this);
            widget.setOnTouchListener(this);
            widget.setOnKeyListener(this);

            // Layout each widget
            int ix = i % countX;
            int iy = i / countX;
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(iy, GridLayout.LEFT),
                    GridLayout.spec(ix, GridLayout.TOP));
            lp.width = cellWidth;
            lp.height = cellHeight;
            if(isShortcut)
            	lp.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            else
            	lp.setGravity(Gravity.TOP | Gravity.LEFT);
            if (ix > 0) lp.leftMargin = widthGap;
            if (iy > 0) lp.topMargin = heightGap;
            layout.addView(widget, lp);
        }

        // wait until a call on onLayout to start loading, because
        // PagedViewWidget.getPreviewSize() will return 0 if it hasn't been laid out
        // TODO: can we do a measure/layout immediately?
        layout.setOnLayoutListener(new Runnable() {
            public void run() {
                // Load the widget previews
                int maxPreviewWidth = cellWidth;
                int maxPreviewHeight = cellHeight;
                if (layout.getChildCount() > 0) {
                    PagedViewWidget w = (PagedViewWidget) layout.getChildAt(0);
                    int[] maxSize = w.getPreviewSize();
                    maxPreviewWidth = maxSize[0];
                    maxPreviewHeight = maxSize[1];
                    if ((maxPreviewWidth <= 0) || (maxPreviewHeight <= 0)) {
                        if (LauncherLog.DEBUG) {
                            LauncherLog.d(TAG, "syncWidgetPageItems: maxPreviewWidth = " + maxPreviewWidth 
                                + ", maxPreviewHeight = " + maxPreviewHeight);
                        }
                    }
                }
                if (immediate) {
                    AsyncTaskPageData data = new AsyncTaskPageData(indexToPage(page), items,
                            maxPreviewWidth, maxPreviewHeight, null, null);
                    loadWidgetPreviewsInBackground(null, data);
                    onSyncWidgetPageItems(data);
                } else {
                    if (mInTransition) {
                        mDeferredPrepareLoadWidgetPreviewsTasks.add(this);
                    } else {
                        prepareLoadWidgetPreviewsTask(indexToPage(page), items,
                                maxPreviewWidth, maxPreviewHeight, countX);
                    }
                }
            }
        });
    	
    }

    private void loadWidgetPreviewsInBackground(AppsCustomizeAsyncTask task,
            AsyncTaskPageData data) {
    	
    	if(!isSupportWidget()){
    		return;
    	}
    	
        // loadWidgetPreviewsInBackground can be called without a task to load a set of widget
        // previews synchronously
        if (task != null) {
            // Ensure that this task starts running at the correct priority
            task.syncThreadPriority();
        }

        // Load each of the widget/shortcut previews
        ArrayList<Object> items = data.items;
        ArrayList<Bitmap> images = data.generatedImages;
        int count = items.size();
        for (int i = 0; i < count; ++i) {
            if (task != null) {
                // Ensure we haven't been cancelled yet
                if (task.isCancelled()) break;
                // Before work on each item, ensure that this task is running at the correct
                // priority
                task.syncThreadPriority();
            }

            Object rawInfo = items.get(i);
            if (rawInfo instanceof AppWidgetProviderInfo) {
                AppWidgetProviderInfo info = (AppWidgetProviderInfo) rawInfo;
                int[] cellSpans = Launcher.getSpanForWidget(mLauncher, info);

                int maxWidth = Math.min(data.maxImageWidth,
                        mWidgetSpacingLayout.estimateCellWidth(cellSpans[0]));
                int maxHeight = Math.min(data.maxImageHeight,
                        mWidgetSpacingLayout.estimateCellHeight(cellSpans[1]));
                Bitmap b = getWidgetPreview(info.provider, info.previewImage, info.icon,
                        cellSpans[0], cellSpans[1], maxWidth, maxHeight);
                images.add(b);
            } else if (rawInfo instanceof ResolveInfo) {
                // Fill in the shortcuts information
                ResolveInfo info = (ResolveInfo) rawInfo;
                images.add(getShortcutPreview(info, data.maxImageWidth, data.maxImageHeight));
            }
        }
    }

    private void onSyncWidgetPageItems(AsyncTaskPageData data) {
    	if(!isSupportWidget()){
    		return;
    	}
        if (mInTransition) {
            mDeferredSyncWidgetPageItems.add(data);
            return;
        }
        try {
            int page = data.page;
            PagedViewGridLayout layout = (PagedViewGridLayout) getChildAt(page);

            ArrayList<Object> items = data.items;
            int count = items.size();
            for (int i = 0; i < count; ++i) {
                PagedViewWidget widget = (PagedViewWidget) layout.getChildAt(i);
                if (widget != null) {
                    Bitmap preview = data.generatedImages.get(i);
                    widget.applyPreview(new FastBitmapDrawable(preview), i);
                }
            }

            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "onSyncWidgetPageItems: page = " + page + ", layout = " + layout
                    + ", count = " + count + ", this = " + this);
            }

            layout.createHardwareLayer();
            invalidate();

            // Update all thread priorities
            Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
            while (iter.hasNext()) {
                AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
                int pageIndex = task.page;
                task.setThreadPriority(getThreadPriorityForPage(pageIndex));
            }
        } finally {
            data.cleanup(false);
        }
    }

    @Override
    public void syncPages() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "syncPages: mNumWidgetPages = " + mNumWidgetPages + ", mNumAppsPages = "
                    + mNumAppsPages
                    + ", mNumAppsFreqPages = " + mNumAppsFreqPages
                    + ", mNumAppsDownloadPages = " + mNumAppsDownloadPages
            		);
        }

        removeAllViews();
        cancelAllTasks();

        /// M: notify launcher that apps pages were recreated.
        mLauncher.notifyPagesWereRecreated();

        Context context = getContext();
        
        if(isSupportWidget()){
    		for (int j = 0; j < mNumWidgetPages; ++j) {
	            PagedViewGridLayout layout = new PagedViewGridLayout(context, mWidgetCountX,
	                    mWidgetCountY);
	            setupPage(layout);
	            addView(layout, new ViewGroup.LayoutParams(LayoutParams.FILL_PARENT,
	                    LayoutParams.FILL_PARENT));
	        }
    		
    		for (int i = 0; i < mNumWidgetShortCutPages; ++i) {
    			PagedViewGridLayout layout = new PagedViewGridLayout(context, mCellCountX, mCellCountY);
	            setupPage(layout);
	            addView(layout, new ViewGroup.LayoutParams(LayoutParams.FILL_PARENT,
	                    LayoutParams.FILL_PARENT));
            }
    	}

        for (int i = 0; i < mNumAppsFreqPages; ++i) {
            PagedViewCellLayout layout = new PagedViewCellLayout(context);
            setupPage(layout);
            addView(layout);
			if (LauncherLog.DEBUG) {
				LauncherLog.d(TAG, "syncPages: PagedViewCellLayout layout = " + layout);
			}
        }
        
        for (int i = 0; i < mNumAppsPages; ++i) {
            PagedViewCellLayout layout = new PagedViewCellLayout(context);
            setupPage(layout);
            addView(layout);
        }
        
        for (int i = 0; i < mNumAppsDownloadPages; ++i) {
            PagedViewCellLayout layout = new PagedViewCellLayout(context);
            setupPage(layout);
            addView(layout);
        }
    }

    @Override
    public void syncPageItems(int page, boolean immediate) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "syncPageItems: page = " + page + ", immediate = " + immediate
                    + ", mNumAppsPages = " + mNumAppsPages);
        }
        if(mCurrentContentType == ContentType_Widgets)
        	syncWidgetPageItems(page, immediate, false);
        else if(mCurrentContentType == ContentType_Widgets_ShortCut)
        	syncWidgetPageItems(page, immediate, true);
        else
        	syncAppsPageItems(page, immediate);

    }

    @Override
    protected boolean isValidPageIndex(int page){
    	return (page >= 0 && page < getPageCount());
    }
    
    @Override
    public int getPageCount() {
    	if (mCurrentContentType == ContentType_Apps) {
            return mNumAppsPages;
        } else if(mCurrentContentType == ContentType_Apps_Freq){
        	return mNumAppsFreqPages;
        } else if(mCurrentContentType == ContentType_Apps_Download){
        	return mNumAppsDownloadPages;
        } else if(mCurrentContentType == ContentType_Widgets){
			return mNumWidgetPages;
		} else if(mCurrentContentType == ContentType_Widgets_ShortCut){
			return mNumWidgetShortCutPages;
		}

        return getChildCount();
    }
    
    // We want our pages to be z-ordered such that the further a page is to the left, the higher
    // it is in the z-order. This is important to insure touch events are handled correctly.
    public View getPageAt(int index) {
        return getChildAt(indexToPage(index));
    }

    @Override
    protected int indexToPage(int index) {
        return indexToPage(index, mCurrentContentType);//getChildCount() - index - 1;
    }
    
    protected int indexToPage(int index, int type) {
    	if(this.isSupportWidget()){
    		if (type == ContentType_Apps) {
	            return (index+mNumWidgetPages+mNumWidgetShortCutPages);
	        } 
	    	
	    	if(type == ContentType_Widgets_ShortCut){
	        	return (index+mNumWidgetPages);
	        }
    	} else {
    	
	    	if (type == ContentType_Apps) {
	            return (index+mNumAppsFreqPages);
	        } 
	    	
	    	if(type == ContentType_Apps_Download){
	        	return (index+mNumAppsFreqPages+mNumAppsPages);
	        }
    	}

    	return index;
    }

    // In apps customize, we have a scrolling effect which emulates pulling cards off of a stack.
    @Override
    protected void screenScrolled(int screenCenter) {
        super.screenScrolled(screenCenter);

        if (LauncherLog.DEBUG_LAYOUT) {
            LauncherLog.d(TAG, "screenScrolled: screenCenter = " + screenCenter
                    + ", mOverScrollX = " + mOverScrollX + ", mMaxScrollX = " + mMaxScrollX
                    + ", mScrollX = " + mScrollX + ", this = " + this);
        }
    }

    protected void overScroll(float amount) {
        acceleratedOverScroll(amount);
    }
    
    protected float maxOverScroll(){
    	return 0.0f;
    }
    
    protected int getOffsetScrollX(){
    	int x = 0;
    	if(this.isSupportWidget()){
    		if(mCurrentContentType != ContentType_Widgets){
	    		final int width = super.getWidth();
	    		x += mNumWidgetPages * width;
	    		if(mCurrentContentType == ContentType_Apps){
	            	x += mNumWidgetShortCutPages * width;
	            }
	        }
    	} else {
	    	if(mCurrentContentType != ContentType_Apps_Freq){
	    		final int width = super.getWidth();
	    		x += mNumAppsFreqPages * width;
	    		if(mCurrentContentType == ContentType_Apps_Download){
	            	x += mNumAppsPages * width;
	            }
	        }
    	}
    	
    	return x;
    }
    
    /**
     * Used by the parent to get the content width to set the tab bar to
     * @return
     */
    public int getPageContentWidth() {
        return mContentWidth;
    }

    @Override
    protected void onPageEndMoving() {
        super.onPageEndMoving();
        mForceDrawAllChildrenNextFrame = true;
        // We reset the save index when we change pages so that it will be recalculated on next
        // rotation
        mSaveInstanceStateItemIndex = -1;
    }

    /*
     * AllAppsView implementation
     */
    public void setup(Launcher launcher, DragController dragController) {
        mLauncher = launcher;
        mDragController = dragController;
    }

    /**
     * We should call thise method whenever the core data changes (mApps, mWidgets) so that we can
     * appropriately determine when to invalidate the PagedView page data.  In cases where the data
     * has yet to be set, we can requestLayout() and wait for onDataReady() to be called in the
     * next onMeasure() pass, which will trigger an invalidatePageData() itself.
     */
    private void invalidateOnDataChange() {
    	if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "invalidateOnDataChange : mApps = " + mApps.size() + ", mAppsHasSet = "
                    + mAppsHasSet + ", isDataReady() = " + isDataReady());
        }
    	int count = super.getChildCount();
    	for(int i=0; i<count; i++){
    		View view = super.getChildAt(i);
    		if(view != null)
    			view.setTag("");
    	}
    	
        if (!isDataReady()) {
            // The next layout pass will trigger data-ready if both widgets and apps are set, so
            // request a layout to trigger the page data when ready.
            requestLayout();
        } else {
            cancelAllTasks();
            invalidatePageData();
        }
    }
    
    public static final Comparator<ItemInfo> getAppFreqComparator() {
        final Collator collator = Collator.getInstance();
        return new Comparator<ItemInfo>() {
            public final int compare(ItemInfo a, ItemInfo b) {
                int result = a.launchedFreq - b.launchedFreq;
                if (result == 0) {
                    result = (a.lastLaunchTime > b.lastLaunchTime ? 1 : -1);
                }
                return result;
            }
        };
    }
    
    
    public void setApps(ArrayList<ApplicationInfo> list) {
        mApps = list;
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "setApps : mApps = " + mApps.size() + ", mAppsHasSet = "
                    + mAppsHasSet + ", isDataReady() = " + isDataReady());
        }
        mAppsHasSet = true;
        Collections.sort(mApps, LauncherModel.getAppNameComparator());
        reorderApps();
        
        final boolean supportFreq = ((this.mSupportContentType&ContentType_Apps_Freq) > 0 && mAppsDownload != null);
        final boolean supportDl = ((this.mSupportContentType&ContentType_Apps_Download) > 0 && mAppsFreq != null);
        if(supportFreq || supportDl){
        	if(supportDl)
        		mAppsDownload.clear();
        	if(supportFreq)
        		mAppsFreq.clear();
        	//final ArrayList<ApplicationInfo> listFreq = new ArrayList<ApplicationInfo>();
        	
        	for(ApplicationInfo info : mApps){
            	if(supportDl && info.isDownloadApp())
            		mAppsDownload.add(info);
            	
            	if(supportFreq && info.isFreqApp())
            		mAppsFreq.add(info);
            }
        	
        	if(supportFreq && mAppsFreq.size() > 0){
        		Collections.sort(mAppsFreq, getAppFreqComparator());
        		//mAppsFreq.addAll(listFreq);
        		//listFreq.clear();
        	}
        }

        updatePageCounts();
        invalidateOnDataChange();
    }

    private void addAppsWithoutInvalidate(ArrayList<ApplicationInfo> list) {
        // We add it in place, in alphabetical order
    	final boolean supportFreq = ((this.mSupportContentType&ContentType_Apps_Freq) > 0 && mAppsDownload != null);
        final boolean supportDl = ((this.mSupportContentType&ContentType_Apps_Download) > 0 && mAppsFreq != null);
        
        int count = list.size();
        for (int i = 0; i < count; ++i) {
            ApplicationInfo info = list.get(i);
            int index = Collections.binarySearch(mApps, info, LauncherModel.getAppNameComparator());
            if (index < 0) {
                mApps.add(-(index + 1), info);
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "addAppsWithoutInvalidate: mApps size = " + mApps.size()
                            + ", index = " + index + ", info = " + info + ", this = " + this);
                }
                
                if(supportDl && info.isDownloadApp()){
                	index = Collections.binarySearch(mAppsDownload, info, LauncherModel.getAppNameComparator());
            		mAppsDownload.add(-(index + 1), info);
                }
                
                if(supportFreq && info.isFreqApp()){
                	index = Collections.binarySearch(mAppsFreq, info, getAppFreqComparator());
                	mAppsFreq.add(-(index + 1), info);
                }
            }
        }
    }

    public void addApps(ArrayList<ApplicationInfo> list) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "addApps: list = " + list + ", this = " + this);
        }

        addAppsWithoutInvalidate(list);
        reorderApps();
        updatePageCounts();
        invalidateOnDataChange();
    }

    private int findAppByComponent(List<ApplicationInfo> list, ApplicationInfo item) {
        ComponentName removeComponent = item.intent.getComponent();
        int length = list.size();
        for (int i = 0; i < length; ++i) {
            ApplicationInfo info = list.get(i);
            if (info.intent.getComponent().equals(removeComponent)) {
                return i;
            }
        }
        return -1;
    }
    
    private int findAppByPackage(List<ApplicationInfo> list, String packageName) {
    	return findAppByPackage(list, packageName, null);
    }
    
    private int findAppByPackage(List<ApplicationInfo> list, String packageName, boolean[] attr) {
        int length = list.size();
        for (int i = 0; i < length; ++i) {
            ApplicationInfo info = list.get(i);
            if (ItemInfo.getPackageName(info.intent).equals(packageName)) {
                /// M: we only remove items whose component is in disable state,
                /// this is add to deal the case that there are more than one
                /// activities with LAUNCHER category, and one of them is
                /// disabled may cause all activities removed from app list.
                final boolean isComponentEnabled = Utilities.isComponentEnabled(getContext(),
                        info.intent.getComponent());
                LauncherLog.d(TAG, "findAppByPackage: i = " + i + ",name = "
                        + info.intent.getComponent() + ",isComponentEnabled = "
                        + isComponentEnabled);
                if (!isComponentEnabled) {
                	if(attr != null){
                		attr[0] = info.isDownloadApp();
                		attr[1] = info.isFreqApp();
                	}
                    return i;
                }
            }
        }
        
        if(attr != null){
    		attr[0] = false;
    		attr[1] = false;
    	}
        return -1;
    }

    private void removeAppsWithoutInvalidate(ArrayList<ApplicationInfo> list) {
    	final boolean supportFreq = ((this.mSupportContentType&ContentType_Apps_Freq) > 0 && mAppsDownload != null);
        final boolean supportDl = ((this.mSupportContentType&ContentType_Apps_Download) > 0 && mAppsFreq != null);
        
        // loop through all the apps and remove apps that have the same component
        int length = list.size();
        for (int i = 0; i < length; ++i) {
            ApplicationInfo info = list.get(i);
            int removeIndex = findAppByComponent(mApps, info);
            if (removeIndex > -1) {
                mApps.remove(removeIndex);
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "removeAppsWithoutInvalidate: removeIndex = " + removeIndex
                            + ", ApplicationInfo info = " + info + ", this = " + this);
                }
                
                if(supportDl && info.isDownloadApp()){
                	removeIndex = findAppByComponent(mAppsDownload, info);
                	if(removeIndex > -1)
                		mAppsDownload.remove(removeIndex);
                }
                
                if(supportFreq && info.isFreqApp()){
                	removeIndex = findAppByComponent(mAppsFreq, info);
                	if(removeIndex > -1)
                		mAppsFreq.remove(removeIndex);
                }
            }
        }
    }

    private void removeAppsWithPackageNameWithoutInvalidate(ArrayList<String> packageNames) {
        // loop through all the package names and remove apps that have the same package name
    	final boolean supportFreq = ((this.mSupportContentType&ContentType_Apps_Freq) > 0 && mAppsDownload != null);
        final boolean supportDl = ((this.mSupportContentType&ContentType_Apps_Download) > 0 && mAppsFreq != null);
        boolean[] attr = new boolean[2];
        
        for (String pn : packageNames) {
            int removeIndex = findAppByPackage(mApps, pn, attr);
            while (removeIndex > -1) {

                mApps.remove(removeIndex);
                if(supportDl && attr[0]){
                	final int dlRemoveIndex = findAppByPackage(mAppsDownload, pn);
                	if(dlRemoveIndex > -1)
                		mAppsDownload.remove(dlRemoveIndex);
                }
            	
            	if(supportFreq && attr[1]){
            		final int dlRemoveIndex = findAppByPackage(mAppsFreq, pn);
                	if(dlRemoveIndex > -1)
                		mAppsFreq.remove(dlRemoveIndex);
                }
            	
                removeIndex = findAppByPackage(mApps, pn, attr);
            }            
        }
    }

    public void removeApps(ArrayList<String> packageNames) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "removeApps: packageNames = " + packageNames
                    + ",size = " + mApps.size() + ", this = " + this);
        }

        removeAppsWithPackageNameWithoutInvalidate(packageNames);
        reorderApps();
        updatePageCounts();
        invalidateOnDataChange();
    }

    public void updateApps(ArrayList<ApplicationInfo> list) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updateApps: list = " + list + ", this = " + this);
        }

        // We remove and re-add the updated applications list because it's properties may have
        // changed (ie. the title), and this will ensure that the items will be in their proper
        // place in the list.
        removeAppsWithoutInvalidate(list);
        addAppsWithoutInvalidate(list);
        updatePageCounts();
        reorderApps();
        invalidateOnDataChange();
    }

    public void reset() {
        // If we have reset, then we should not continue to restore the previous state
        mSaveInstanceStateItemIndex = -1;

        boolean isChanged = false;
        AppsCustomizeTabHost tabHost = getTabHost();
        String tag = tabHost.getCurrentTabTag();
        if (tag != null) {
        	if(this.isSupportWidget()){
        		if (!tag.equals(tabHost.getTabTagForContentType(ContentType_Widgets))) {
        			mCurrentContentType = ContentType_Widgets;
        			isChanged = true;
	                tabHost.setCurrentTabFromContent(ContentType_Widgets);
	            }
        	} else {
	            if (!tag.equals(tabHost.getTabTagForContentType(ContentType_Apps))) {
	            	mCurrentContentType = ContentType_Apps;
	            	isChanged = true;
	                tabHost.setCurrentTabFromContent(ContentType_Apps);
	            }
        	}
        }
        
        if(isChanged && getChildCount() > 0){
	        int childCount = getPageCount(); 
	        if (childCount > 0) {
	            mMaxScrollX = getChildOffset(indexToPage(childCount - 1)) - getRelativeChildOffset(indexToPage(childCount - 1));
	        } else {
	            mMaxScrollX = 0;
	        }
	        
	    	int page = Math.min(mContentItemsPageIndexMap.get(mCurrentContentType), (childCount - 1));
	    	
	    	if(mQsWorkspaceCallback != null)
	        	mQsWorkspaceCallback.onPageCountChanged(childCount);
	
	        if (page != 0) {
	            invalidatePageData(0);
	        }
        }
    }

    private AppsCustomizeTabHost getTabHost() {
        return (AppsCustomizeTabHost) mLauncher.findViewById(R.id.apps_customize_pane);
    }

    public void dumpState() {
        // TODO: Dump information related to current list of Applications, Widgets, etc.
        ApplicationInfo.dumpApplicationInfoList(TAG, "mApps", mApps);
        if(mWidgets != null)
        	dumpAppWidgetProviderInfoList(TAG, "mWidgets", mWidgets);
    }

    private void dumpAppWidgetProviderInfoList(String tag, String label,
            ArrayList<Object> list) {
        Log.d(tag, label + " size=" + list.size());
        for (Object i: list) {
            if (i instanceof AppWidgetProviderInfo) {
                AppWidgetProviderInfo info = (AppWidgetProviderInfo) i;
                Log.d(tag, "   label=\"" + info.label + "\" previewImage=" + info.previewImage
                        + " resizeMode=" + info.resizeMode + " configure=" + info.configure
                        + " initialLayout=" + info.initialLayout
                        + " minWidth=" + info.minWidth + " minHeight=" + info.minHeight);
            } else if (i instanceof ResolveInfo) {
                ResolveInfo info = (ResolveInfo) i;
                Log.d(tag, "   label=\"" + info.loadLabel(mPackageManager) + "\" icon="
                        + info.icon);
            }
        }
    }

    public void surrender() {
        // TODO: If we are in the middle of any process (ie. for holographic outlines, etc) we
        // should stop this now.

        // Stop all background tasks
        cancelAllTasks();
    }

    @Override
    public void iconPressed(PagedViewIcon icon) {
        // Reset the previously pressed icon and store a reference to the pressed icon so that
        // we can reset it on return to Launcher (in Launcher.onResume())
        if (mPressedIcon != null) {
            mPressedIcon.resetDrawableState();
        }
        mPressedIcon = icon;
    }

    public void resetDrawableState() {
        if (mPressedIcon != null) {
            mPressedIcon.resetDrawableState();
            mPressedIcon = null;
        }
    }

    /*
     * We load an extra page on each side to prevent flashes from scrolling and loading of the
     * widget previews in the background with the AsyncTasks.
     */
    final static int sLookBehindPageCount = 2;
    final static int sLookAheadPageCount = 2;
    protected int getAssociatedLowerPageBound(int page) {
        final int count = getPageCount();
        int windowSize = Math.min(count, sLookBehindPageCount + sLookAheadPageCount + 1);
        int windowMinIndex = Math.max(Math.min(page - sLookBehindPageCount, count - windowSize), 0);
        return windowMinIndex;
    }
    protected int getAssociatedUpperPageBound(int page) {
        final int count = getPageCount();
        int windowSize = Math.min(count, sLookBehindPageCount + sLookAheadPageCount + 1);
        int windowMaxIndex = Math.min(Math.max(page + sLookAheadPageCount, windowSize - 1),
                count - 1);
        return windowMaxIndex;
    }

    @Override
    protected String getCurrentPageDescription() {
        int page = (mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage;
        int stringId = R.string.default_scroll_format;
        int count = 0;
        
        if (page < mNumAppsPages) {
            stringId = R.string.apps_customize_apps_scroll_format;
            count = mNumAppsPages;
        } else {
            page -= mNumAppsPages;
            stringId = R.string.apps_customize_widgets_scroll_format;
            count = mNumWidgetPages;
        }

        return String.format(getContext().getString(stringId), page + 1, count);
    }

    /**
     * M: Reorder apps in applist.
     */
    public void reorderApps() {
//        if (LauncherLog.DEBUG) {
//            LauncherLog.d(TAG, "reorderApps: mApps = " + mApps + ", this = " + this);
//        }
        if (AllAppsList.sTopPackages == null || mApps == null || mApps.isEmpty()
                || AllAppsList.sTopPackages.isEmpty()) {
            return;
        }

        ArrayList<ApplicationInfo> dataReorder = new ArrayList<ApplicationInfo>(
                AllAppsList.DEFAULT_APPLICATIONS_NUMBER);

        for (AllAppsList.TopPackage tp : AllAppsList.sTopPackages) {
            for (ApplicationInfo ai : mApps) {
                if (ai.componentName.getPackageName().equals(tp.packageName)
                        && ai.componentName.getClassName().equals(tp.className)) {
                    mApps.remove(ai);
                    dataReorder.add(ai);
                    break;
                }
            }
        }

        for (AllAppsList.TopPackage tp : AllAppsList.sTopPackages) {
            int newIndex = 0;
            for (ApplicationInfo ai : dataReorder) {
                if (ai.componentName.getPackageName().equals(tp.packageName)
                        && ai.componentName.getClassName().equals(tp.className)) {
                    newIndex = Math.min(Math.max(tp.order, 0), mApps.size());
                    mApps.add(newIndex, ai);
                    break;
                }
            }
        }
    }

    /**
     * M: Update unread number of the given component in app customize paged view
     * with the given value, first find the icon, and then update the number.
     * NOTES: since maybe not all applications are added in the customize paged
     * view, we should update the apps info at the same time.
     * 
     * @param component
     * @param unreadNum
     */
    public void updateAppsUnreadChanged(ComponentName component, int unreadNum) {
        if (LauncherLog.DEBUG_UNREAD) {
            LauncherLog.d(TAG, "updateAppsUnreadChanged: component = " + component
                    + ",unreadNum = " + unreadNum + ",mNumAppsPages = " + mNumAppsPages);
        }
        updateUnreadNumInAppInfo(component, unreadNum);
        int start = this.isSupportWidget() ? mNumWidgetPages : 0;
        int end = start + (this.isSupportWidget() ? mNumAppsPages : getChildCount());
        for (int i = start; i < end; i++) {
            PagedViewCellLayout cl = (PagedViewCellLayout) getChildAt(i);
            if (cl == null) {
                return;
            }
            updateAppsUnreadChanged(cl, component, unreadNum);
        }
    }
    
    private void updateAppsUnreadChanged(PagedViewCellLayout cl, ComponentName component, int unreadNum){
        if (cl == null) {
            return;
        }
        
        final int count = cl.getPageChildCount();
        MTKAppIcon appIcon = null;
        ApplicationInfo appInfo = null;
        for (int j = 0; j < count; j++) {
            appIcon = (MTKAppIcon) cl.getChildOnPageAt(j);
            appInfo = (ApplicationInfo) appIcon.getTag();
            if (LauncherLog.DEBUG_UNREAD) {
                LauncherLog.d(TAG, "updateAppsUnreadChanged: component = " + component
                        + ", appInfo = " + appInfo.componentName + ", appIcon = " + appIcon);
            }
            if (appInfo != null && appInfo.componentName.equals(component)) {
                appIcon.updateUnreadNum(unreadNum);
            }
        }
    }

    /**
     * M: Update unread number of all application info with data in MTKUnreadLoader.
     */
    public void updateAppsUnread() {
        if (LauncherLog.DEBUG_UNREAD) {
            LauncherLog.d(TAG, "updateAppsUnreadChanged: mNumAppsPages = " + mNumAppsPages);
        }

        updateUnreadNumInAppInfo(mApps);
        // Update apps which already shown in the customized pane.
        int start = this.isSupportWidget() ? mNumWidgetPages : 0;
        int end = start + (this.isSupportWidget() ? mNumAppsPages : getChildCount());
        for (int i = start; i < end; i++) {
            PagedViewCellLayout cl = (PagedViewCellLayout) getChildAt(i);
            if (cl == null) {
                return;
            }
            final int count = cl.getPageChildCount();
            MTKAppIcon appIcon = null;
            ApplicationInfo appInfo = null;
            int unreadNum = 0;
            for (int j = 0; j < count; j++) {
                appIcon = (MTKAppIcon) cl.getChildOnPageAt(j);
                appInfo = (ApplicationInfo) appIcon.getTag();
                unreadNum = MTKUnreadLoader.getUnreadNumberOfComponent(appInfo.componentName);
                appIcon.updateUnreadNum(unreadNum);
                if (LauncherLog.DEBUG_UNREAD) {
                    LauncherLog.d(TAG, "updateAppsUnreadChanged: i = " + i + ", appInfo = "
                            + appInfo.componentName + ", unreadNum = " + unreadNum);
                }
            }
        }
    }
    
    /**
     * M: Update the unread number of the app info with given component.
     * 
     * @param component
     * @param unreadNum
     */
    private void updateUnreadNumInAppInfo(ComponentName component, int unreadNum) {
    	updateUnreadNumInAppInfo(component, unreadNum, mApps);
//        final int size = mApps.size();
//        ApplicationInfo appInfo = null;
//        for (int i = 0; i < size; i++) {
//            appInfo = mApps.get(i);
//            if (appInfo.intent.getComponent().equals(component)) {
//                appInfo.unreadNum = unreadNum;
//            }
//        }
    }
    
    private void updateUnreadNumInAppInfo(ComponentName component, int unreadNum, final ArrayList<ApplicationInfo> list) {
        final int size = list.size();
        ApplicationInfo appInfo = null;
        for (int i = 0; i < size; i++) {
            appInfo = list.get(i);
            if (appInfo.intent.getComponent().equals(component)) {
                appInfo.unreadNum = unreadNum;
            }
        }
    }

    /**
     * M: Update unread number of all application info with data in MTKUnreadLoader.
     * 
     * @param apps
     */
    public static void updateUnreadNumInAppInfo(final ArrayList<ApplicationInfo> apps) {
        final int size = apps.size();
        ApplicationInfo appInfo = null;
        for (int i = 0; i < size; i++) {
            appInfo = apps.get(i);
            appInfo.unreadNum = MTKUnreadLoader.getUnreadNumberOfComponent(appInfo.componentName);
        }
    }
    
    /**
     * M: invalidate app page items.
     */
    void invalidateAppPages(int currentPage, boolean immediateAndOnly) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "invalidateAppPages: currentPage = " + currentPage + ", immediateAndOnly = " + immediateAndOnly);
        }
        invalidatePageData(currentPage, immediateAndOnly);
    }
    
    public void increaseAppFreqInfo(ShortcutInfo sInfo){
    	final boolean supportFreq = ((this.mSupportContentType&ContentType_Apps_Freq) > 0 && mAppsDownload != null);
    	if(!supportFreq || sInfo == null)
    		return;
    	
    	ComponentName cmp = sInfo.getComponentName();
    	if(cmp == null)
    		return;
    	for(ApplicationInfo appInfo : mApps){
    		if(cmp.equals(appInfo.componentName)){
    			increaseAppFreqInfo(appInfo);
    			return;
    		}
    	}
    }
    
    public void increaseAppFreqInfo(ApplicationInfo appInfo){
    	if(appInfo == null)
    		return;
    	
    	appInfo.launchedFreq++;
		appInfo.lastLaunchTime = SystemClock.uptimeMillis();
		if(mAppsChangeFreqAsyncTask != null)
        	mAppsChangeFreqAsyncTask.cancel(false);
        
    	mAppsChangeFreqAsyncTask = new AppsChangeFreqAsyncTask();
    	mAppsChangeFreqAsyncTask.execute(appInfo);
    }
    
    private boolean updateFreqApps(ApplicationInfo appInfo, AsyncTask task){
    	//final boolean supportFreq = (this.mSupportContentType&ContentType_Apps_Freq) > 0;
    	if(appInfo == null)
    		return false;
    	
    	LauncherModel.modifyItemLaunchInfoInDatabase(mLauncher, appInfo);
    	
    	if(appInfo != null && appInfo.isFreqApp()){
    		int size = mAppsFreq.size();
    		for(int i=0; i<size; i++){
    			ApplicationInfo item = mAppsFreq.get(i);
    			if(item.componentName != null && item.componentName.equals(appInfo.componentName)){
    				if(i > 0 && !task.isCancelled()){
	    				mAppsFreq.remove(i);
	    				Comparator<ItemInfo> appComparator = getAppFreqComparator();
	    				for(int j=i-1; j>=0; j--){
	    					
	    					if(task.isCancelled())
	    						return false;
	    					
	    					ApplicationInfo newitem = mAppsFreq.get(j);
	    					int ret = appComparator.compare(newitem, item);
//	    					android.util.Log.i("QsLog", "==curnum:"+item.launchedFreq+"==cmpnum:"+newitem.launchedFreq
//	    							+"==curT:"+item.lastLaunchTime
//	    							+"==newT:"+newitem.lastLaunchTime
//	    							+"==ret:"+ret
//	    							+"==j:"+j
//	    							+"==i:"+i
//	    							);
	    					if(ret > 0){
	    						mAppsFreq.add(j+1, item);
	    						return ((j+1) != i) ? true : false;
	    					}
	    				}
	    				mAppsFreq.add(0, item);
	    				return true;
    				}
    				return false;
    			}
    			
    			if(task.isCancelled())
					return false;
    		}
    		mAppsFreq.add(appInfo);
    		Collections.sort(mAppsFreq, getAppFreqComparator());
    		return true;
    	}
    	
    	return false;
    }

    
    private class AppsChangeFreqAsyncTask extends AsyncTask<ApplicationInfo, Void, Boolean> {
        
        @Override
        protected Boolean doInBackground(ApplicationInfo... params) {
        	return updateFreqApps(params[0], this);
        }
        @Override
        protected void onPostExecute(Boolean result) {
            // All the widget previews are loaded, so we can just callback to inflate the page
        	if(result && !isCancelled()){
        		if(mCurrentContentType == ContentType_Apps_Freq){
        			mDirtyPageContent.set(indexToPage(0, ContentType_Apps_Freq), true);
        			loadAssociatedPages(0, mLauncher.isAllAppsVisible());
        		}
        	}
        }
    }
}
