package com.android.qshtclauncher2;

import java.util.ArrayList;


import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;


public class QsPreviewScreen extends LinearLayout{

	private final static int VIEW_INDEX_ROW_0 = 0;
	private final static int VIEW_INDEX_ROW_1 = 1;
	private final static int VIEW_INDEX_ROW_2 = 2;
	//private final static int VIEW_INDEX_ROW_3 = 3;
	private ViewGroup[] mRowsGroup = new ViewGroup[3];
	private ImageView mDropTarget;
	private Workspace mWorkspace;
	
	private final int mPreviewBmpHeight;
	private int mPreviewBmpWidth;
	private Drawable mPreviewBackground;
	
	//ArrayList<Bitmap> mPreviewBitmaps = new ArrayList<Bitmap>();
	//public final static int 
	private Drawable mPreviewBmpForAdd;
	private ImageView mPreviewImageViewForAdd;
	
	//private final Bitmap [] mPreviewBitmaps;// = new Bitmap[7];
	private final ImageView [] mPreviewImageView;// = new ImageView[7];
	
	//private ImageView mPressedImageView;
	
	protected int mDownMotionDelta;
    protected final int mMinScaleMotionDelta;
    protected boolean mStartScaleMotion;
    private boolean mIsTouching;
    //private boolean mIsSecondTouching;
    private int mDownImageViewIndex;
    private long mLastTapTime;
    
    private final long mLongPressTimeout;
    
    private Launcher mLauncher;
    public final static boolean QS_SUPPORT_ADD_SCREEN = true;
    
    private int mLastPointX;
    private int mLastPointY;
    private int mPressLeftSpace;
    private int mPressTopSpace;
    private final int mMinScrollDeltaX;
    private Bitmap mPressPreviewImage;
    
    private final int mStatusBarHeight;
    private final int mImageViewVerGap;
    
    private final static float QS_PRESSED_PREVIEW_SCALE = 1.2f;
    
    private final int mQsMaxScreenCount;
    
    
	public QsPreviewScreen(Context context) {
        this(context, null);
    }

    public QsPreviewScreen(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QsPreviewScreen(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Workspace, defStyle, 0);
        
        mQsMaxScreenCount = a.getInt(R.styleable.QsPreviewScreen_maxScreenCount, 5);
        
        a.recycle();
        
        //mPreviewBitmaps = new Bitmap[mQsMaxScreenCount];
        mPreviewImageView = new ImageView[mQsMaxScreenCount];
        
        mPreviewBackground = context.getResources().getDrawable(R.drawable.preview_bg);
        mPreviewBmpHeight = context.getResources().getDimensionPixelSize(R.dimen.qs_workspace_preview_screen_height);
        mPreviewBmpForAdd = context.getResources().getDrawable(R.drawable.home_icon_add_page);
        
        mImageViewVerGap = context.getResources().getDimensionPixelSize(R.dimen.qs_workspace_preview_hor_margin);
        
        float density = context.getResources().getDisplayMetrics().density;
        mMinScaleMotionDelta = (int)(80 * density);
        
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mLongPressTimeout = configuration.getLongPressTimeout();
        
        mMinScrollDeltaX = (int)(density * 5.0f);
        
        mStatusBarHeight = context.getResources().getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);
                
        if(LauncherLog.DEBUG_QS_I9300){
        	android.util.Log.w("QsLog", "QsPreviewScreen====mImageViewVerGap:"+mImageViewVerGap
        			+"==mStatusBarHeight:"+mStatusBarHeight
        			+"==mMinScrollDeltaX:"+mMinScrollDeltaX);
        }
    }
    
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
     //   android.util.Log.w("QsLog", "QsLockScreen::onFinishInflate====");
        
        mRowsGroup[VIEW_INDEX_ROW_0] = (ViewGroup)this.findViewWithTag("rows1");
        mRowsGroup[VIEW_INDEX_ROW_1] = (ViewGroup)this.findViewWithTag("rows2");
        mRowsGroup[VIEW_INDEX_ROW_2] = (ViewGroup)this.findViewWithTag("rows3");
        
        mDropTarget = (ImageView)this.findViewWithTag("drop");
        if(!QS_SUPPORT_ADD_SCREEN)
        	mDropTarget.setVisibility(View.INVISIBLE);
    }
        
    public void show(Launcher context, Workspace workspace){
    	
    	mLauncher = context;
    	mWorkspace = workspace;
    	if(LauncherLog.DEBUG_QS_I9300){
    		android.util.Log.w("QsLog", "QsPreviewScreen::show() = ");
    	}
    	
    	initScreenPreviewBmp(context);
    	
    	updateScreenPreview();
    	
    	super.setVisibility(View.VISIBLE);
    }
    
    public void hide(){
    	super.setVisibility(View.GONE);
    	
    	mRowsGroup[VIEW_INDEX_ROW_0].removeAllViewsInLayout();
    	mRowsGroup[VIEW_INDEX_ROW_1].removeAllViewsInLayout();
    	mRowsGroup[VIEW_INDEX_ROW_2].removeAllViewsInLayout();
    	
    	if(mPreviewImageView != null){
	    	int size = mPreviewImageView.length;
	    	for(int i=0; i<size; i++){
	    		mPreviewImageView[i] = null;
	    	}
    	}

    	if(mPressPreviewImage != null){
    		mPressPreviewImage.recycle();
    		mPressPreviewImage = null;
    	}
    }

    private void initScreenPreviewBmp(Launcher context){

    	final CellLayout cell = ((CellLayout) mWorkspace.getChildAt(0));
    	final int nPressedIndex = mWorkspace.getCurrentPage();
    	
    	final Rect r = new Rect();
    	mPreviewBackground.getPadding(r);
    	
        int extraH = r.top + r.bottom;
        int width = cell.getWidth();
        int height = cell.getHeight();
        
        int bmpH = mPreviewBmpHeight - extraH;
        
        int x = cell.getPaddingLeft();
        int y = cell.getPaddingTop();
        width -= (x + cell.getPaddingRight());
        height -= (y + cell.getPaddingBottom());

        float scale = (float)bmpH / height;
        
        
        final int sWidth = (int)(width * scale + 0.5f);
        final int sHeight = (int)(height * scale + 0.5f);
        
        mPreviewBmpWidth = sWidth + r.left + r.right;
        final int nCount = mWorkspace.getEditScreenCount();

    	for(int i=0; i<nCount; i++){
    		
    		Bitmap bitmap = /*mPreviewBitmaps[i] = */createPreviewBitmap(((CellLayout) mWorkspace.getChildAt(i)), 
    				scale, -x, -y, sWidth, sHeight);
    		
    		ImageView image = mPreviewImageView[i] = createPreviewImageView(context, bitmap);
    		image.setTag(i);
    		
    		image.setPressed((i == nPressedIndex ? true : false));
    	}
    	
    	if(QS_SUPPORT_ADD_SCREEN){
    		if(mPreviewImageViewForAdd == null){
	    		ImageView image = mPreviewImageViewForAdd = createPreviewImageView(context, mPreviewBmpForAdd);
	    		image.setTag("add");
    		}
    		
    		if(nCount < mWorkspace.getMaxScreenCount())
    			mPreviewImageView[nCount] = mPreviewImageViewForAdd;
    		//image.setOnClickListener(mClickListener);
    	}
    }
    
    private ImageView createPreviewImageView(Context context, Drawable bmp){
    	ImageView image = createPreviewImageView(context);
		
		if(bmp != null)
			image.setImageDrawable(bmp);

		return image;
    }
    
    private ImageView createPreviewImageView(Context context, Bitmap bmp){
    	ImageView image = createPreviewImageView(context);
		
		if(bmp != null)
			image.setImageBitmap(bmp);

		return image;
    }
    
    private ImageView createPreviewImageView(Context context){
    	ImageView image = new QsPreviewImageView(context);
		image.setClickable(false);
		image.setFocusable(false);
		image.setBackgroundDrawable(mPreviewBackground);

		return image;
    }
    
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        
//        if(LauncherLog.DEBUG_QS_I9300){
//    		android.util.Log.w("QsLog", "QsPreviewScreen::dispatchDraw() = mQsLongPressed:"+mQsLongPressed);
//    	}
        
        if(mQsLongPressed && mPressPreviewImage != null){
        	canvas.save();
        	int x = mLastPointX - mPressLeftSpace;
        	int y = mLastPointY - mPressTopSpace - mStatusBarHeight;
        	if(x < 0)
        		x = 0;
        	if(y < 0)
        		y = 0;
        	
        	canvas.translate(x, y);
        	if(mPressPreviewImage != null && !mPressPreviewImage.isRecycled())
        		canvas.drawBitmap(mPressPreviewImage, 0, 0, null);

        	canvas.restore();
        }
    }
    
//    @Override
//    public boolean onInterceptTouchEvent(MotionEvent ev) {
//    	
//    }

    @Override
    //public boolean onInterceptTouchEvent(MotionEvent ev) {
    public boolean onTouchEvent(MotionEvent ev) {
    	//
    	
    	final int action = ev.getAction();
    	
        switch (action & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
        	mStartScaleMotion = false;
        	mDownMotionDelta = 0;
        	mIsTouching = true;
        	mQsLongPressed = false;
        	
        	mLastPointX = (int)ev.getX();
        	mLastPointY = (int)ev.getY();
   		
    		mLastTapTime = SystemClock.uptimeMillis();
    		
    		super.getHandler().removeCallbacks(mQsPreviewLongPress);
    		
    		mDownImageViewIndex = getClickImageView(mLastPointX, mLastPointY);
    		if(QS_SUPPORT_ADD_SCREEN && mDownImageViewIndex >= 0){
    			if(mDownImageViewIndex < mWorkspace.getEditScreenCount()){
    				Rect rc = new Rect();
   		    		getRectInScreen(mPreviewImageView[mDownImageViewIndex], rc);

   		    		mPressLeftSpace = mLastPointX - rc.left - (int)(rc.width() * (QS_PRESSED_PREVIEW_SCALE - 1.0f)/2);
    				mPressTopSpace = mLastPointY - rc.top - (int)(rc.height() * (QS_PRESSED_PREVIEW_SCALE - 1.0f)/2);
    				
    				super.getHandler().postDelayed(mQsPreviewLongPress, mLongPressTimeout);
    			}
    		}
         	
            break;
        case MotionEvent.ACTION_POINTER_DOWN:{
			super.getHandler().removeCallbacks(mQsPreviewLongPress);
		
        	if(!mIsTouching)
        		break;

            if(mDownImageViewIndex >= 0){
                mQsLongPressed = false;
        		mPreviewImageView[mDownImageViewIndex].setVisibility(View.VISIBLE);
        		super.postInvalidate();
        	}
        	
        	mDownImageViewIndex = -1;
    		final float x0 = ev.getX(0);
    		final float y0 = ev.getY(0);
    		
    		final float x1 = ev.getX(1);
    		final float y1 = ev.getY(1);
    		
    		mDownMotionDelta = (int)Math.sqrt(((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)));
        	break;
        }
        case MotionEvent.ACTION_MOVE:{
        	if(!mIsTouching)
        		break;
        	
        	if(QS_SUPPORT_ADD_SCREEN && mQsLongPressed){
        		final int x = (int)ev.getX();
        		final int y = (int)ev.getY();
        		
        		int delta = Math.max(Math.abs(x - mLastPointX), Math.abs(y - mLastPointY));
        		if(delta > mMinScrollDeltaX){
	            	
	        		mLastPointX = x;
	            	mLastPointY = y;
	            	
	            	Rect rc = new Rect();
   		    		getRectInScreen(mDropTarget, rc);
   		    		if(rc.contains(x, y)){
   		    			mDropTarget.setPressed(true);
   		    		} else {
   		    			mDropTarget.setPressed(false);
   		    		}
	            	
	            	super.postInvalidate();
        		}
            	
        	} else {
        		
	        	if(mDownMotionDelta != 0 && !mStartScaleMotion && ev.getPointerCount() > 1){
	        		final float x0 = ev.getX(0);
	        		final float y0 = ev.getY(0);
	        		
	        		final float x1 = ev.getX(1);
	        		final float y1 = ev.getY(1);
	        		
	        		int delta = (int)Math.sqrt(((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)));
	        		
	        		if(Math.abs(delta - mDownMotionDelta) > mMinScaleMotionDelta){
	        			
		        		if((delta - mDownMotionDelta) > 0){
		        			// zoom out
		        			mStartScaleMotion = true;
		        			mLauncher.hidePreiews(true);
		        		}
	        		}
	        	}
        	}
        	break;
        }
        case MotionEvent.ACTION_POINTER_UP:
        	super.getHandler().removeCallbacks(mQsPreviewLongPress);
        	
        	if(mIsTouching && mQsLongPressed){
        		mQsLongPressed = false;
        		mDropTarget.setPressed(false);
                if(mDownImageViewIndex >= 0)
            		mPreviewImageView[mDownImageViewIndex].setVisibility(View.VISIBLE);
        		super.postInvalidate();
        	}
        	break;
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
        	
        	super.getHandler().removeCallbacks(mQsPreviewLongPress);

        	if(mIsTouching && mDownImageViewIndex >= 0){
        	
	        	if(!mStartScaleMotion 
	        			&& (SystemClock.uptimeMillis() - mLastTapTime) < mLongPressTimeout
	        			&& ev.getPointerCount() < 2){
	        		
	        		if(mDownImageViewIndex == getClickImageView((int)ev.getX(), (int)ev.getY() ) ){
	        			
	        			if(mDownImageViewIndex < mWorkspace.getEditScreenCount()){
	        				mLauncher.snapToPage(mDownImageViewIndex);
	        			} else {
		        			View v = mPreviewImageView[mDownImageViewIndex];
		        			if(v != null && "add".equals(v.getTag())){
		        	    		addScreen();
		        	    	}/* else {
		        	    		mLauncher.snapToPage(mDownImageViewIndex);
		        	    	}*/
	        			}
	        		}
	        	} else if(mQsLongPressed){
	        		
	        		mQsLongPressed = false;
	        		Rect rc = new Rect();
   		    		getRectInScreen(mDropTarget, rc);
   		    		if(rc.contains((int)ev.getX(), (int)ev.getY()) && removeScreen(mDownImageViewIndex)){
   		    			
   		    		} else {
   		    			//mDropTarget.setPressed(false);
   		    			
   		    			mPreviewImageView[mDownImageViewIndex].setVisibility(View.VISIBLE);
   		    		}
   		    		
	        		
	        		mDropTarget.setPressed(false);
	        	}
        	}
        	mIsTouching = false;
        	mQsLongPressed = false;
        	super.invalidate();
        	break;
        }

        return mIsTouching || super.onTouchEvent(ev);
        //return mStartScaleMotion || onInterceptTouchEvent(ev);
    }
    
    private int getClickImageView(int x, int y){
    	
    	final int nCount = QS_SUPPORT_ADD_SCREEN ? Math.min((mWorkspace.getEditScreenCount()+1), mWorkspace.getMaxScreenCount()) : mWorkspace.getEditScreenCount();
    	
    	Rect rc = new Rect();
    	for(int i=0; i<nCount; i++){
    		//mPreviewImageView[i].getHitRect(rc);
    		getRectInScreen(mPreviewImageView[i], rc);
    		if(rc.contains(x, y)){
    			return i;
    		}
    	}
    	
    	return -1;
    }
    
    private void getRectInScreen(View v, Rect r){
    	if(v == null)
    		return;
    	
    	v.getHitRect(r);
        int x = r.left;
        int y = r.top + mStatusBarHeight;
        View parent = (View)v.getParent();
        while(parent != null && parent != this){
            x += parent.getLeft();
            y += parent.getTop();

            parent = (View)parent.getParent();
        }

        r.offsetTo(x, y);
    }
    
    private boolean removeScreen(int index){
    	if(mPreviewImageView[index] != null && mWorkspace.removeScreen(index, true)){
    		
    		mPreviewImageView[index].setImageDrawable(null);
    		ViewGroup parent = (ViewGroup)mPreviewImageView[index].getParent();
			parent.removeViewInLayout(mPreviewImageView[index]);
			
			final int nPressedIndex = mWorkspace.getCurrentPage();

			int nCount = mPreviewImageView.length;
			for(int i=index; (i+1)<nCount; i++){
				mPreviewImageView[i] = mPreviewImageView[i+1];
				//android.util.Log.w("QsLog", "removeScreen===i:"+i+"==nPressedIndex:"+nPressedIndex+"==mPreviewImageView:"+(mPreviewImageView[i] != null ? "valid" : "null"));
				if(mPreviewImageView[i] != null)
					mPreviewImageView[i].setPressed((i == nPressedIndex ? true : false));
			}
			mPreviewImageView[nCount - 1] = null;
			
			int ScreenCount = mWorkspace.getEditScreenCount();
			//android.util.Log.w("QsLog", "removeScreen===index:"+index+"==nPressedIndex:"+nPressedIndex+"==ScreenCount:"+ScreenCount);
			if(nPressedIndex < index){
				for(int i=0; i<index; i++){
					mPreviewImageView[i].setPressed((i == nPressedIndex ? true : false));
				}
			} else if(nPressedIndex >= ScreenCount){
				if(mPreviewImageView[ScreenCount-1] != null)
					mPreviewImageView[ScreenCount-1].setPressed(true);
			} 
			//mPreviewImageViewForAdd
			mPreviewImageView[ScreenCount] = mPreviewImageViewForAdd;
			updateScreenPreview();
			
			return true;
    	}
    	
    	return false;
    }
   
    private void addScreen(){
    	View view = mWorkspace.addScreen(true);
    	if(view != null){
    		int index = mWorkspace.indexOfChild(view);//.getChildCount()-1;
    		int count = mWorkspace.getEditScreenCount();
    		
    		//android.util.Log.i("QsLog", "addScreen==index:"+index+"==childcount:"+count);

    		mPreviewImageView[index] = createPreviewImageView(mLauncher/*, mPreviewBitmaps[index]*/);
    		mPreviewImageView[index].setTag(index);

    		if(count < mWorkspace.getMaxScreenCount())
    			mPreviewImageView[count] = mPreviewImageViewForAdd;
    		
			updateScreenPreview();
    	}
    }
    
    private void updateScreenPreview(){
    	ImageView view;
    	//final int nCount = mWorkspace.getChildCount();//Math.min((mWorkspace.getChildCount()+1), mWorkspace.getMaxScreenCount());
    	final int nCount = QS_SUPPORT_ADD_SCREEN ? Math.min((mWorkspace.getEditScreenCount()+1), mWorkspace.getMaxScreenCount()) : mWorkspace.getEditScreenCount();
    	
    	mRowsGroup[VIEW_INDEX_ROW_0].removeAllViewsInLayout();
    	mRowsGroup[VIEW_INDEX_ROW_1].removeAllViewsInLayout();
    	mRowsGroup[VIEW_INDEX_ROW_2].removeAllViewsInLayout();
    	
    	LinearLayout.LayoutParams layutparams = new LinearLayout.LayoutParams(mPreviewBmpWidth, mPreviewBmpHeight, 0.0f);
    	layutparams.leftMargin = mImageViewVerGap;
    	layutparams.rightMargin = mImageViewVerGap;
    	
    	final int nFirstRowCount = 2;
    	int i=0;
    	for(; i<nFirstRowCount; i++){
    		if(mPreviewImageView[i] != null){
	    		mRowsGroup[VIEW_INDEX_ROW_0].addView(mPreviewImageView[i],
	    				layutparams);
    		}
    	}
		
    	final int nLastRowCount = nCount > 3 ? 2 : (nCount - nFirstRowCount);//(nCount == mWorkspace.getMaxScreenCount() ? 2 : 1);
    	final int nMiddleRowCount = nCount - nFirstRowCount - nLastRowCount;
    	//android.util.Log.w("QsLog", "=2==i:"+i+"==nCount:"+nCount+"===nMiddleRowCount:"+nMiddleRowCount+"==nLastRowCount:"+nLastRowCount);
    	if(nMiddleRowCount > 0){

    		for(int j=0; j<nMiddleRowCount; j++){
    			if(mPreviewImageView[i] != null){
	    			mRowsGroup[VIEW_INDEX_ROW_1].addView(mPreviewImageView[i],
	    					layutparams);
    			}
    			i++;
    		}
    		
    		mRowsGroup[VIEW_INDEX_ROW_1].setVisibility(View.VISIBLE);
    	} else {
    		mRowsGroup[VIEW_INDEX_ROW_1].setVisibility(View.GONE);
    	}
    	//android.util.Log.w("QsLog", "=3==i:"+i+"==nCount:"+nCount+"===nMiddleRowCount:"+nMiddleRowCount+"==nLastRowCount:"+nLastRowCount);
    	if(nLastRowCount > 0){
    		
    		for(int j=0; j<nLastRowCount; j++){
    			if(mPreviewImageView[i] != null){
	    			mRowsGroup[VIEW_INDEX_ROW_2].addView(mPreviewImageView[i],
	    					layutparams);
    			}
    			i++;
    		}
    		
    		mRowsGroup[VIEW_INDEX_ROW_2].setVisibility(View.VISIBLE);
    	} else {
    		mRowsGroup[VIEW_INDEX_ROW_2].setVisibility(View.INVISIBLE);
    	}
    }
    
//    private Bitmap createPreviewBitmap(int index){
//    	
//    	final CellLayout cell = ((CellLayout) mWorkspace.getChildAt(index));
//    	
//    	final Rect r = new Rect();
//    	mPreviewBackground.getPadding(r);
//
//        int extraH = r.top + r.bottom;
//        int width = cell.getWidth();
//        int height = cell.getHeight();
//
//        int bmpH = height - extraH;
//
//        int x = cell.getPaddingLeft();
//        int y = cell.getPaddingTop();
//        width -= (x + cell.getPaddingRight());
//        height -= (y + cell.getPaddingBottom());
//
//        float scale = bmpH / height;
//        
//        final int sWidth = (int)(width * scale + 0.5f);
//        final int sHeight = (int)(height * scale + 0.5f);
//        
////		final Bitmap bitmap = Bitmap.createBitmap((int) sWidth, (int) sHeight,
////				Bitmap.Config.ARGB_8888);
////
////		final Canvas c = new Canvas(bitmap);
////		c.scale(scale, scale);
////		c.translate(-cell.getPaddingLeft(), -cell.getPaddingTop());
////		cell.dispatchDraw(c);
//		
//		return createPreviewBitmap(cell, scale, -x, -y, sWidth, sHeight);
//    }
    
    private Bitmap createPreviewBitmap(CellLayout cell, float scale, int x, int y, int width, int height){
    	final Bitmap bitmap = Bitmap.createBitmap((int) width, (int) height,
				Bitmap.Config.ARGB_8888);

		final Canvas c = new Canvas(bitmap);
		c.scale(scale, scale);
		c.translate(x, y);
		cell.dispatchDraw(c);
		
		return bitmap;
    }
    
    private boolean mQsLongPressed = false;
    Runnable mQsPreviewLongPress = new Runnable() {
        public void run() {
        	
        	if(mStartScaleMotion || !mIsTouching || mDownImageViewIndex < 0)
        		return;
        	
        	mQsLongPressed = true;
        	
        	int width = (int)(mPreviewBmpWidth * QS_PRESSED_PREVIEW_SCALE);
        	int height = (int)(mPreviewBmpHeight * QS_PRESSED_PREVIEW_SCALE);
        	
        	final Bitmap bitmap = Bitmap.createBitmap((int) width, (int) height,
    				Bitmap.Config.ARGB_8888);
        	
        	final Canvas c = new Canvas(bitmap);
        	c.scale(QS_PRESSED_PREVIEW_SCALE, QS_PRESSED_PREVIEW_SCALE);
        	
        	if(mDownImageViewIndex  >= 0 && mPreviewImageView[mDownImageViewIndex] != null){
        		mPreviewImageView[mDownImageViewIndex].draw(c);
	        	
	        	if(mPressPreviewImage != null){
	        		mPressPreviewImage.recycle();
	        	}
	        	
	        	mPressPreviewImage = bitmap;
				
				mPreviewImageView[mDownImageViewIndex].setVisibility(View.INVISIBLE);
        	}
        	
        	invalidate();
        }
    };
}
