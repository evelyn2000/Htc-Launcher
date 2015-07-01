package com.android.qshtclauncher2;

import android.graphics.drawable.Drawable;

public interface QsScreenIndicatorCallback {
	public void onScrollChangedCallback(int l, int t, int oldl, int oldt);
	//public void onScrollBy(int x, int y);
	public void onChangeToScreen(int whichScreen);
	public void onPageCountChanged(int nNewCount);
	
	public final static int CUSTOM_INDICATOR_FIRST = 0;
	public final static int CUSTOM_INDICATOR_LAST = -1;
	
	public void setCustomPageIndicatorIcon(int index, Drawable dr);
}
