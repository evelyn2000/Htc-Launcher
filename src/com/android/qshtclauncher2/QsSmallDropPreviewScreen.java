package com.android.qshtclauncher2;

import java.util.ArrayList;


import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;


public class QsSmallDropPreviewScreen extends LinearLayout implements DropTarget, View.OnClickListener{

	private Workspace mWorkspace;
	
   
    private Launcher mLauncher;
    public final static boolean QS_SUPPORT_ADD_SCREEN = true;

    
    private final int mQsMaxScreenCount;
    private final ArrayList<Integer> mRefreshPageCache;// = new ArrayList<Integer>();
    private final int DOUBLE_TAP_TIMEOUT;
    private long mFirstClickTime;
    private int mAutoScrollToIndex = -1;
    private int mDragingChildIndex = -1;
    private final static int LONG_PRESS_TIME_OUT = 1000;
    
    
	public QsSmallDropPreviewScreen(Context context) {
        this(context, null);
    }

    public QsSmallDropPreviewScreen(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QsSmallDropPreviewScreen(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Workspace, defStyle, 0);
        
        mQsMaxScreenCount = a.getInt(R.styleable.QsPreviewScreen_maxScreenCount, 7);
        
        a.recycle();
        
        mRefreshPageCache = new ArrayList<Integer>(mQsMaxScreenCount);
        
        DOUBLE_TAP_TIMEOUT = ViewConfiguration.getDoubleTapTimeout();
    }
    
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }
        
    public void init(Launcher context, Workspace workspace){
    	
    	mLauncher = context;
    	mWorkspace = workspace;
    	if(LauncherLog.DEBUG_QS_HTC){
    		android.util.Log.w("QsLog", "QsSmallDropPreviewScreen::show() = height:"+super.getHeight()+"==MeasuredHeight:"+super.getMeasuredHeight());
    	}
    }
    
    public void onWindowVisibleChanged(boolean isShow) {
    	if(isShow){
    		initScreenPreviewBmp();
    	} else {
    		super.removeCallbacks(mRefreshPage);
    		mRefreshPageCache.clear();
    		super.removeAllViews();
    	}
    }
    
	public void updateScreenPreviewScreen(int index){
		if (super.getHeight() == 0) {
			if (LauncherLog.DEBUG_QS_HTC) {
				android.util.Log.w("QsLog",
						"QsSmallDropPreviewScreen::initScreenPreviewBmp() = height:"
								+ super.getHeight() + "==MeasuredHeight:"
								+ super.getMeasuredHeight());
			}
			return;
		}

		if (index < 0) {
			initScreenPreviewBmp();
			return;
		} else if (index >= mWorkspace.getPageCount())
			return;

		final CellLayout cell = ((CellLayout) mWorkspace.getPageAt(index));
		cell.invalidate();
		
		if(!mRefreshPageCache.contains(index))
			mRefreshPageCache.add(index);
		
		super.removeCallbacks(mRefreshPage);
		super.postDelayed(mRefreshPage, 200);
    }
    
    private void initScreenPreviewBmp(){
    	if(super.getHeight() == 0){
    		if(LauncherLog.DEBUG_QS_HTC){
        		android.util.Log.w("QsLog", "QsSmallDropPreviewScreen::initScreenPreviewBmp() = height:"+super.getHeight()
        				+"==MeasuredHeight:"+super.getMeasuredHeight());
        	}
    		return;
    	}
    	
    	final CellLayout cell = ((CellLayout) mWorkspace.getChildAt(0));
    	final int nPressedIndex = mWorkspace.getCurrentPage();
    	
    	LayoutInflater inflater = LayoutInflater.from(mLauncher);
    	View imgPrev = inflater.inflate(R.layout.small_drop_preview_image, null);
//    	final Rect r = new Rect();
//    	imgPrev.getb
//    	mPreviewBackground.getPadding(r);
    	
        //int extraH = r.top + r.bottom;
        int width = cell.getWidth();
        int height = cell.getHeight();
        
        int bmpH = super.getHeight() - super.getPaddingBottom() - super.getPaddingTop() - imgPrev.getPaddingBottom() - imgPrev.getPaddingTop();
        
        int x = cell.getPaddingLeft();
        int y = cell.getPaddingTop();
        width -= (x + cell.getPaddingRight());
        height -= (y + cell.getPaddingBottom());

        float scale = (float)bmpH / height;
        
        
        final int sWidth = (int)(width * scale + 0.5f);
        final int sHeight = (int)(height * scale + 0.5f);

        super.removeAllViewsInLayout();
        
        final int nCount = mWorkspace.getPageCount();
    	for(int i=0; i<nCount; i++){
    		
    		Bitmap bitmap = createPreviewBitmap(((CellLayout) mWorkspace.getPageAt(i)), 
    				scale, -x, -y, sWidth, sHeight);
    		if(bitmap == null)
    			continue;
    		
    		ImageView image = (ImageView)inflater.inflate(R.layout.small_drop_preview_image, this, false);
    		image.setTag(i);
    		image.setImageBitmap(bitmap);
    		image.setSelected((i == nPressedIndex ? true : false));
    		image.setOnClickListener(this);
    		//image.set
    		
    		super.addViewInLayout(image, -1, image.getLayoutParams());
    	}
    	
    	mAutoScrollToIndex = nPressedIndex;
    	super.requestLayout();
    }

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		// TODO Auto-generated method stub
		super.onLayout(changed, l, t, r, b);
		
		//android.util.Log.i("QsLog", "onLayout==mAutoScrollToIndex:"+mAutoScrollToIndex+"===w:"+super.getWidth()+"==w1:"+(r - l));
		if(mAutoScrollToIndex > 0){
			View selView = super.getChildAt(mAutoScrollToIndex);
	    	if(selView != null){
	    		//android.util.Log.i("QsLog", "onLayout==nPressedIndex:"+mAutoScrollToIndex+"===w:"+super.getWidth()+"==right:"+selView.getRight());
		    	HorizontalScrollView horscroll = (HorizontalScrollView)super.getParent();
		    	if(horscroll != null){
		    		int space = selView.getRight() - horscroll.getWidth();
		    		if(space > 0){
		    			selView = super.getChildAt(mAutoScrollToIndex+1);
		    			if(selView != null)
		    				space = selView.getLeft() - horscroll.getWidth();
		    			else 
		    				space = super.getWidth() - horscroll.getWidth();
		    			horscroll.scrollBy(space, 0);
		    		}
		    	}
	    	}
	    	
	    	mAutoScrollToIndex = -1;
		}
	}

	private boolean switchSelectStatus(final View selView){
    	if(selView.isSelected())
    		return false;
    	final int index = super.indexOfChild(selView);
    	if(index >= 0){
	    	int size = super.getChildCount();
	    	for(int i=0; i<size; i++){
	    		View view = super.getChildAt(i);
	    		if(view.isSelected()){
	    			view.setSelected(false);
	    			break;
	    		}
	    	}
    	
	    	selView.setSelected(true);
	    	mWorkspace.setCurrentPage(index);
	    	super.post(new Runnable() {
	            public void run() {
	            	mWorkspace.snapToPage(index);
	            }
	        });
	    	return true;
    	}
    	return false;
    }
    
    @Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
    	long time = SystemClock.uptimeMillis();
    	//android.util.Log.i("QsLog", "=======double time:"+((time - mFirstClickTime) < DOUBLE_TAP_TIMEOUT)+"==sel:"+v.isSelected());
    	if((time - mFirstClickTime) < DOUBLE_TAP_TIMEOUT && v.isSelected()){
    		super.post(new Runnable() {
                public void run() {
                	mLauncher.showWorkspace(true);
                }
            });
    		return;
    	}
    	mFirstClickTime = time;
    	if(switchSelectStatus(v))
    		invalidate();
	}
 
    private Bitmap createPreviewBitmap(CellLayout cell, float scale, int x, int y, int width, int height){
    	final Bitmap bitmap = Bitmap.createBitmap((int) width, (int) height,
				Bitmap.Config.ARGB_8888);

		final Canvas c = new Canvas(bitmap);
		c.scale(scale, scale);
		c.translate(x, y);
		cell.draw(c);//.dispatchDraw(c);
		
		return bitmap;
    }
    
    public boolean acceptDrop(DragObject d) {
        return true;
    }
    
    public void onDrop(DragObject d) {
    	ItemInfo info = (ItemInfo) d.dragInfo;
    	int index = getChildViewIndex(d.x);
    	//android.util.Log.i("QsLog", "onDrop===x:"+d.x+"==y:"+d.y+"==index:"+index);
    	super.removeCallbacks(mEnterSpringModeRunnable);
    	if(index >= 0 && index < super.getChildCount()){
    		bindSelectedItem(d, index);
    	}
    }

    public void onFlingToDelete(DragObject d, int x, int y, PointF vec) {
        // Do nothing
    }

    public void onDragEnter(DragObject d) {
        //d.dragView.setColor(mHoverColor);
    	//int index = getChildViewIndex(d.x);    	
    }

    public void onDragOver(DragObject d) {
        // Do nothing
    	int index = getChildViewIndex(d.x);
    	//android.util.Log.i("QsLog", "onDragOver===x:"+d.x+"==y:"+d.y+"==index:"+index+"==old:"+mDragingChildIndex);
    	if(mDragingChildIndex != index){
			super.removeCallbacks(mEnterSpringModeRunnable);
			if(index >= 0 && index < super.getChildCount()){
				super.postDelayed(mEnterSpringModeRunnable, LONG_PRESS_TIME_OUT);
	    	}
			mDragingChildIndex = index;
		}
    }

    public void onDragExit(DragObject d) {
    	mDragingChildIndex = -1;
    	super.removeCallbacks(mEnterSpringModeRunnable);
    }

    public void onDragStart(DragSource source, Object info, int dragAction) {
        // Do nothing
    }

    public boolean isDropEnabled() {
        return super.getChildCount() > 0;
    }

    public void onDragEnd() {
        // Do nothing
    }

    @Override
    public void getHitRect(android.graphics.Rect outRect) {
        super.getHitRect(outRect);
    }

    @Override
    public DropTarget getDropTargetDelegate(DragObject d) {
        return null;
    }

    public void getLocationInDragLayer(int[] loc) {
        mLauncher.getDragLayer().getLocationInDragLayer(this, loc);
    }
    
    public int getChildViewIndex(int x){
    	if(super.getChildCount() > 0 && super.getWidth() > 0){
    		return (int)(x * super.getChildCount() / super.getWidth());
    	}
    	return -1;
    }
    
    private void bindSelectedItem(DragObject d, final int screen){
    	final ItemInfo item = (ItemInfo)d.dragInfo;
    	Runnable onAnimationCompleteRunnable = null;
    	if(item != null && screen >= 0){

	    	final Workspace workspace = mLauncher.getWorkspace();
	    	final CellLayout cellLayout = (CellLayout)workspace.getPageAt(screen);
	    	
	    	//android.util.Log.i("QsLog", "bindSelectedItem==type:"+item.itemType+"==x:"+item.cellX+"=y:"+item.cellY+"==spanx:"+item.spanX+"==spany:"+item.spanY);
	    	final int[] targetCell = new int[2];
	    	final int container = LauncherSettings.Favorites.CONTAINER_DESKTOP;

	    	if (item instanceof PendingAddItemInfo) {
	    		final PendingAddItemInfo pendingInfo = (PendingAddItemInfo) item;
	    		View finalView = pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
	                    ? ((PendingAddWidgetInfo) pendingInfo).boundWidget : null;
	                    
	            if (finalView != null && finalView instanceof AppWidgetHostView) {
	                mLauncher.getDragLayer().removeView(finalView);
	            }
	            
	    		if(!cellLayout.findCellForSpan(targetCell, item.spanX, item.spanY)){
	            	Toast.makeText(mLauncher, mLauncher.getString(R.string.completely_out_of_space),
	                        Toast.LENGTH_SHORT).show();
	            } else {
		    		onAnimationCompleteRunnable = new Runnable() {
		                @Override
		                public void run() {
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
		                }
		    		};
	            }
	
	    	} else {
	            if(!cellLayout.findCellForSpan(targetCell, 1, 1)){
	            	Toast.makeText(mLauncher, mLauncher.getString(R.string.completely_out_of_space),
	                        Toast.LENGTH_SHORT).show();
	            } else {            
		            onAnimationCompleteRunnable = new Runnable() {
		                @Override
		                public void run() {
		                	mLauncher.completeAddApplication(((ApplicationInfo) item).intent, container, screen, targetCell[0], targetCell[0]);
		                }
		            };
	            }
	    	}
    	}
    	
    	if (d.dragView != null) {
    		Rect r = new Rect();
    		mLauncher.getDragLayer().getViewRectRelativeToSelf(d.dragView, r);
            
            mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, r.left, r.top, r.left, r.top, 1, 1, 1, 1.0f, 1.0f,
            		onAnimationCompleteRunnable, DragLayer.ANIMATION_END_DISAPPEAR, -1, null);
        }
    }
    
    public void refreshScreenPreviewScreen(int index){
    	final CellLayout cell = ((CellLayout) mWorkspace.getPageAt(index));
		
		LayoutInflater inflater = LayoutInflater.from(mLauncher);
		ImageView imgview = (ImageView)super.getChildAt(index);
		if (imgview == null)
			return;

		int width = cell.getWidth();
		int height = cell.getHeight();

		int bmpH = super.getHeight() - super.getPaddingBottom()
				- super.getPaddingTop() - imgview.getPaddingBottom()
				- imgview.getPaddingTop();

		int x = cell.getPaddingLeft();
		int y = cell.getPaddingTop();
		width -= (x + cell.getPaddingRight());
		height -= (y + cell.getPaddingBottom());

		float scale = (float) bmpH / height;

		final int sWidth = (int) (width * scale + 0.5f);
		final int sHeight = (int) (height * scale + 0.5f);

		Bitmap bitmap = createPreviewBitmap(cell, scale, -x, -y, sWidth,
				sHeight);
		if (bitmap != null){			
			imgview.setImageBitmap(bitmap);
			//super.invalidate();
		}
    }
    
    Runnable mRefreshPage = new Runnable() {
        public void run() {
            while(!mRefreshPageCache.isEmpty()){
	            int index = mRefreshPageCache.remove(0);
	            refreshScreenPreviewScreen(index);
            }
        }
    };
    
    Runnable mEnterSpringModeRunnable = new Runnable() {
        public void run() {
        	if(mDragingChildIndex >= 0){
        		mWorkspace.snapToPage(mDragingChildIndex);
        		mLauncher.enterSpringLoadedDragMode(true);
        	}
        }
    };
   
}
