package com.android.qshtclauncher2;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

public class PagedViewWidgetShortcut extends PagedViewWidget {
	
	public PagedViewWidgetShortcut(Context context) {
        this(context, null);
    }

    public PagedViewWidgetShortcut(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedViewWidgetShortcut(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

    }
    
    @Override
    protected void onDetachedFromWindow() {    
        if (sDeletePreviewsWhenDetachedFromWindow) {
            final TextView name = (TextView) findViewById(R.id.widget_name);
            if (name != null) {
                name.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null); 
            }
        }
        
        super.onDetachedFromWindow();
    }
    
    @Override
    public int[] getPreviewSize() {
        //final ImageView i = (ImageView) findViewById(R.id.widget_preview);
        int[] maxSize = new int[2];
        maxSize[0] = super.getWidth() - mOriginalImagePadding.left - mOriginalImagePadding.right;
        maxSize[1] = super.getHeight() - mOriginalImagePadding.top;
        return maxSize;
    }
    
    @Override
    public void applyPreview(FastBitmapDrawable preview, int index) {
    	super.applyPreview(preview, index);
        if (preview != null) {
        	final TextView name = (TextView) findViewById(R.id.widget_name);
            name.setCompoundDrawablesWithIntrinsicBounds(null, preview, null, null);           
        }
    }
}
