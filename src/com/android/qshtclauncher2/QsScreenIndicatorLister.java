package com.android.qshtclauncher2;

public interface QsScreenIndicatorLister {
	
	public int getCurrentPage();
	public int getPageCount();
	public void setQsScreenIndicatorCallback(QsScreenIndicatorCallback callback);
	
	//protected QsScreenIndicatorCallback mQsWorkspaceCallback;
//	public void setQsScreenIndicatorCallback(QsScreenIndicatorCallback callback){
//		mQsWorkspaceCallback = callback;
//	}
}
