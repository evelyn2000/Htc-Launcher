package com.android.qshtclauncher2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ViewConfiguration;
import android.widget.ImageView;

public class QsPreviewImageView extends ImageView {
	
//	private Bitmap mCheckOnFlagBmp;
//	private Bitmap mCheckOffFlagBmp;
	private Drawable mCheckDrawable;
	
	public QsPreviewImageView(Context context) {
        this(context, null);
    }

    public QsPreviewImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QsPreviewImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
//        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CellLayoutWithDrag, defStyle, 0);
//
//        a.recycle();
        
        mCheckDrawable = context.getResources().getDrawable(R.drawable.mode_check_state_icon);
        if(mCheckDrawable != null)
        	mCheckDrawable.setBounds(0, 0, mCheckDrawable.getIntrinsicWidth(), mCheckDrawable.getIntrinsicHeight());
//        mPreviewBackground = context.getResources().getDrawable(R.drawable.previewbg);
//        mPreviewBmpHeight = context.getResources().getDimensionPixelSize(R.dimen.qs_workspace_preview_screen_height);
//        mPreviewBmpForAdd = context.getResources().getDrawable(R.drawable.homescreen_quick_view_add);
//        
//        mMinScaleMotionDelta = (int)(50 * context.getResources().getDisplayMetrics().density);
//        
//        final ViewConfiguration configuration = ViewConfiguration.get(context);
//        mLongPressTimeout = configuration.getLongPressTimeout();
//        android.util.Log.w("QsLog", "QsPreviewScreen()==mPreviewBmpHeight:"+mPreviewBmpHeight);
        
        
    }
    
    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        
        Drawable d = mCheckDrawable;
        if (d != null && d.isStateful()) {
            d.setState(getDrawableState());
        }
    }
    
    @Override 
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (mCheckDrawable == null) {
            return; // couldn't resolve the URI
        }
        
        int saveCount = canvas.getSaveCount();
        canvas.save();
        
        float dx = super.getWidth() - super.getPaddingRight()/2 - mCheckDrawable.getIntrinsicWidth();
        float dy = super.getPaddingTop()/2;
        canvas.translate(dx, dy);
        mCheckDrawable.draw(canvas);
        
        canvas.restoreToCount(saveCount);
    }
}
