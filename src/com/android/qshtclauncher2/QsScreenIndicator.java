package com.android.qshtclauncher2;

import java.util.HashMap;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.View.MeasureSpec;


public class QsScreenIndicator extends View implements QsScreenIndicatorCallback{
	
	private int mDirection = 0;
	private Bitmap mHasPrevScreenImg;
	private Bitmap mHasNextScreenImg;
		
	private int mImgPadding = 0;
	//private boolean mIsCreateNum = false;
	private int mTextSize = 0;
	private int mScreenPagesCount = 5;
	private int mCurrentScreen = 2;
	private final TextPaint mTextPaint;
	//private Workspace mWorkspace;
	private Rect mBgPadding = new Rect();
	
	//private Drawable mExtScreenIcon;
	
	//private HashMap<Integer, Drawable> mCustomScreenIcon = new HashMap<Integer, Drawable>();
	
	public QsScreenIndicator(Context context) {
        this(context, null);
    }

    public QsScreenIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QsScreenIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        //mBitmapForegroud = ((BitmapDrawable) getResources().getDrawable(R.drawable.hud_pageturn_foreground)).getBitmap();
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.QsScreenIndicator, defStyle, 0);
        mDirection = a.getInt(R.styleable.QsScreenIndicator_direction, mDirection);
        
        int width = a.getDimensionPixelSize(R.styleable.QsScreenIndicator_iconWidth, 0);
    	int height = a.getDimensionPixelSize(R.styleable.QsScreenIndicator_iconHeight, 0);
        
        Drawable thumb = a.getDrawable(R.styleable.QsScreenIndicator_hasPrevScreenImage);
        if (thumb != null) {
        	mHasPrevScreenImg = drawable2Bitmap(thumb, width, height);
        	thumb.getPadding(mBgPadding);
        }else{
        	mBgPadding.set(0, 0, 0, 0);
        }
        
        thumb = a.getDrawable(R.styleable.QsScreenIndicator_hasNextScreenImage);
        if (thumb != null) {
        	mHasNextScreenImg = drawable2Bitmap(thumb, width, height);
        }
       
        mImgPadding = a.getDimensionPixelSize(R.styleable.QsScreenIndicator_imagePadding, 0);
        mTextPaint = new TextPaint();
    	mTextPaint.setTypeface(Typeface.DEFAULT);
    	int nValue = a.getDimensionPixelSize(R.styleable.QsScreenIndicator_textSize, 12);
    	mTextPaint.setTextSize(nValue);
    	nValue = a.getColor(R.styleable.QsScreenIndicator_textColor, 0xffffffff);
    	mTextPaint.setColor(nValue);
    	mTextPaint.setAntiAlias(true);
    	mTextPaint.setTextAlign(Align.LEFT);
        
        a.recycle();
        
    }
 
    public void initial(Context context, QsScreenIndicatorLister lister){
    	mCurrentScreen = lister.getCurrentPage();
    	mScreenPagesCount = lister.getPageCount();
    	
    	lister.setQsScreenIndicatorCallback(this);
    }
    
    public void onScrollChangedCallback(int l, int t, int oldl, int oldt){
    	//postInvalidate();
    	
    }
    
	public void onChangeToScreen(int whichScreen){
		if(mCurrentScreen != whichScreen)
		{
			mCurrentScreen = whichScreen;
			super.invalidate();
		}
	}
	
	public void onPageCountChanged(int nNewCount){
		
		if(nNewCount != mScreenPagesCount){
			mScreenPagesCount = nNewCount;
			
			if(mCurrentScreen >= mScreenPagesCount){
				mCurrentScreen = mScreenPagesCount - 1;
			}
			
			super.invalidate();
		}
	}
	
	public void setCustomPageIndicatorIcon(int index, Drawable dr){
//		if(dr != null)
//			mCustomScreenIcon.put(index, dr);
//		else
//			mCustomScreenIcon.remove(index);
	}
	
	
//	@Override
//	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//		// TODO Auto-generated method stub
//		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//		
//		final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
//        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
//
//        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
//        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
//        
//        
//	}

	@Override
    protected void onDraw(Canvas canvas) {
		
		Drawable bg = getBackground();
		if(bg != null)
			bg.draw(canvas);
		else
			canvas.drawColor(Color.TRANSPARENT);
		
		if(mDirection > 0)//
        {
        	if(mScreenPagesCount > 0)
        		drawHorizontal(canvas, mScreenPagesCount);
        }
        else
        {
        	if(mScreenPagesCount > 0)
        		drawVertical(canvas, mScreenPagesCount);
        }
		
        //super.onDraw(canvas);
    }
	
	private void drawHorizontal(Canvas canvas, int nScreenCount){
		//int layout = getGravity();
		if(nScreenCount <= 0)
			return;
		
		String drawText = String.valueOf(mCurrentScreen+1) + "/" + String.valueOf(mScreenPagesCount);
		Rect bounds = new Rect();
		mTextPaint.getTextBounds(drawText, 0, drawText.length(), bounds);
		
		int controlWidth = bounds.width();
		if(mHasPrevScreenImg != null){
			controlWidth += mHasPrevScreenImg.getWidth() + mImgPadding;
		}
		
		if(mHasNextScreenImg != null){
			controlWidth += mHasNextScreenImg.getWidth() + mImgPadding;
		}
		
		int nLeft = (super.getWidth() - controlWidth)/2;
		if(mHasPrevScreenImg != null){
			if(mCurrentScreen > 0)
				canvas.drawBitmap(mHasPrevScreenImg, nLeft, (super.getHeight() - mHasPrevScreenImg.getHeight())/2, null);
			
			nLeft += mHasPrevScreenImg.getWidth() + mImgPadding;
		}
		
		//int x = nLeft + (mCurScreenImg.getWidth() - bounds.width() - mBgPadding.left - mBgPadding.right)/2;
		int y = (super.getHeight() + bounds.height())/2;
		canvas.drawText(drawText, nLeft, y, mTextPaint);
		
		nLeft += bounds.width();
		
		if(mHasNextScreenImg != null && (mCurrentScreen+1) < mScreenPagesCount){
			canvas.drawBitmap(mHasNextScreenImg, nLeft + mImgPadding, (super.getHeight() - mHasNextScreenImg.getHeight())/2, null);
		}
		
//		if(mTextPaint != null){
//			
//			String str = String.valueOf(mCurrentScreen+1);
//			
//			int x = nLeft + (mCurScreenImg.getWidth() - bounds.width() - mBgPadding.left - mBgPadding.right)/2;
//			int y = nCurTop + (mCurScreenImg.getHeight() + bounds.height())/2;
//			//QsLog.LogD("drawHorizontal(1)==str:"+str+"=nLeft:"+nLeft+"==nCurTop:"+nCurTop+"==x:"+x+"==y:"+y+"==th:"+bounds.height()+"=ih:"+mCurScreenImg.getHeight());
//			canvas.drawText(str, x, y, mTextPaint);
//		}
		
//		int width = getWidth();
//	//	int nTotalWidth = (nScreenCount - 1) * mDefaultScreenImg.getWidth() + (nScreenCount + 1) * mImgPadding + mCurScreenImg.getWidth();
//		int customIconCount = mCustomScreenIcon.size();
//		Drawable lastIcon = mCustomScreenIcon.get(CUSTOM_INDICATOR_LAST);
//		//if(customIconCount)
//		int nTotalWidth = /*(nScreenCount - 1) * mDefaultScreenImg.getWidth() + */(nScreenCount + 1) * mImgPadding/* + mCurScreenImg.getWidth()*/;
//		if(lastIcon != null){
//			nScreenCount--;
//			nTotalWidth += lastIcon.getIntrinsicWidth();
//		} else {
//			nTotalWidth += mCurScreenImg.getWidth();
//		}
//		nTotalWidth += (nScreenCount - 1) * mDefaultScreenImg.getWidth();
//		
//		//QsLog.LogD("drawHorizontal()==w:"+width+"=nTotalWidth:"+nTotalWidth);
//		boolean bShowMore = false;
//		if(nTotalWidth > width)
//			bShowMore = true;
//		
//		int nLeft = (width - nTotalWidth)/2;
//		int nTop = (getHeight() - mDefaultScreenImg.getHeight())/2;
//		for(int i=0; i<mCurrentScreen; i++){
//			canvas.drawBitmap(mDefaultScreenImg, nLeft, nTop, null);
//			nLeft += mDefaultScreenImg.getWidth() + mImgPadding;
//		}
//		
//		int nCurTop = (getHeight() - mCurScreenImg.getHeight())/2;
//		if(lastIcon != null && mCurrentScreen == nScreenCount){
//			lastIcon.setState(View.ENABLED_SELECTED_STATE_SET);
//		} else {
//			if(lastIcon != null)
//				lastIcon.setState(View.EMPTY_STATE_SET);
//			
//			canvas.drawBitmap(mCurScreenImg, nLeft, nCurTop, null);
//		
//			if(mTextPaint != null){
//				Rect bounds = new Rect();
//				String str = String.valueOf(mCurrentScreen+1);
//				mTextPaint.getTextBounds(str, 0, str.length(), bounds);
//				int x = nLeft + (mCurScreenImg.getWidth() - bounds.width() - mBgPadding.left - mBgPadding.right)/2;
//				int y = nCurTop + (mCurScreenImg.getHeight() + bounds.height())/2;
//				//QsLog.LogD("drawHorizontal(1)==str:"+str+"=nLeft:"+nLeft+"==nCurTop:"+nCurTop+"==x:"+x+"==y:"+y+"==th:"+bounds.height()+"=ih:"+mCurScreenImg.getHeight());
//				canvas.drawText(str, x, y, mTextPaint);
//			}
//			nLeft += mCurScreenImg.getWidth() + mImgPadding;
//		}
//
//		for(int i=mCurrentScreen+1; i<nScreenCount; i++){
//			canvas.drawBitmap(mDefaultScreenImg, nLeft, nTop, null);
//			nLeft += mDefaultScreenImg.getWidth() + mImgPadding;
//		}
//		
//		if(lastIcon != null){
//			nCurTop = (getHeight() - lastIcon.getIntrinsicHeight())/2;
//			lastIcon.setBounds(nLeft, nCurTop, nLeft+lastIcon.getIntrinsicWidth(), nCurTop + lastIcon.getIntrinsicHeight());
//			lastIcon.draw(canvas);
//		}
	}
	
	private void drawVertical(Canvas canvas, int nScreenCount){
		if(nScreenCount <= 0)
			return;
		int height = getHeight();
		
		
		
//		int nTotalHeight = (nScreenCount - 1) * mDefaultScreenImg.getWidth() + (nScreenCount + 1) * mImgPadding + mCurScreenImg.getWidth();
//		//QsLog.LogD("drawHorizontal()==w:"+width+"=nTotalWidth:"+nTotalWidth);
//		boolean bShowMore = false;
//		if(nTotalHeight > height)
//			bShowMore = true;
//		
//		int nTop = (height - nTotalHeight)/2;
//		int nLeft  = (getWidth() - mDefaultScreenImg.getWidth())/2;
//		for(int i=0; i<mCurrentScreen; i++){
//			canvas.drawBitmap(mDefaultScreenImg,nLeft,nTop, null);
//			nTop += mDefaultScreenImg.getHeight() + mImgPadding;
//		}
//        final Resources resources = getResources();
//        //final float gap_x = resources.getDimension(R.dimen.screenindeictor_Vertical_gap_x);
//        //final float gap_y = resources.getDimension(R.dimen.screenindeictor_Vertical_gap_y);
//        
//		int nCurLeft = (getWidth() - mCurScreenImg.getWidth())/2;
//		canvas.drawBitmap(mCurScreenImg,nCurLeft , nTop, null);
//		if(mTextPaint != null){
//			Rect bounds = new Rect();
//			String str = String.valueOf(mCurrentScreen+1);
//			mTextPaint.getTextBounds(str, 0, str.length(), bounds);
//			int y = nTop + (mCurScreenImg.getHeight() - bounds.height() - mBgPadding.left - mBgPadding.right)/2;// + (int)gap_y;
//			int x = nCurLeft + (mCurScreenImg.getWidth() + bounds.width())/2;// - (int)gap_x ;
//			//QsLog.LogD("drawHorizontal(1)==str:"+str+"=nLeft:"+nLeft+"==nCurTop:"+nCurTop+"==x:"+x+"==y:"+y+"==th:"+bounds.height()+"=ih:"+mCurScreenImg.getHeight());
//			canvas.drawText(str, x, y, mTextPaint);
//		}
//		nTop += mCurScreenImg.getHeight() + mImgPadding;
//		
//		for(int i=mCurrentScreen+1; i<nScreenCount; i++){
//			canvas.drawBitmap(mDefaultScreenImg, nLeft, nTop, null);
//			nTop += mDefaultScreenImg.getWidth() + mImgPadding;
//		}
	}
	
	
	   
    private Bitmap drawable2Bitmap(Drawable drawable){  
        if(drawable instanceof BitmapDrawable){  
            return ((BitmapDrawable)drawable).getBitmap();  
        }else if(drawable instanceof NinePatchDrawable){  
            Bitmap bitmap = Bitmap  
                    .createBitmap(  
                            drawable.getIntrinsicWidth(),  
                            drawable.getIntrinsicHeight(),  
                            drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888  
                                    : Bitmap.Config.RGB_565);  
            Canvas canvas = new Canvas(bitmap);  
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(),  
                    drawable.getIntrinsicHeight());  
            drawable.draw(canvas);  
            
            return bitmap;  
        }
        
        return null;
    }
    
    private Bitmap drawable2Bitmap(Drawable drawable, int width, int height){  
        if(drawable instanceof BitmapDrawable){  
            return ((BitmapDrawable)drawable).getBitmap();  
        }else if(drawable instanceof NinePatchDrawable){  
        	
        	if(width <= 0)
        		width = drawable.getIntrinsicWidth();
        	
        	if(height <= 0)
        		height = drawable.getIntrinsicHeight();
        	
            Bitmap bitmap = Bitmap  
                    .createBitmap(  
                    		width,  
                    		height,  
                            drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888  
                                    : Bitmap.Config.RGB_565);  
            Canvas canvas = new Canvas(bitmap);  
            drawable.setBounds(0, 0, width, height);  
            drawable.draw(canvas);  
            
            return bitmap;  
        }
        
        return null;
    }
    
}

