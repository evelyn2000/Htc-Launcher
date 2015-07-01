/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import com.mediatek.widget.BookmarkAdapter;
import com.mediatek.widget.BookmarkItem;
import com.mediatek.widget.BookmarkView;
import android.widget.BounceCoverFlow;
import android.widget.LinearLayout;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ImageView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class SceneChooser extends Activity implements AdapterView.OnItemSelectedListener{
    private static final String TAG = "SceneChooser";
    static final String SELECTED_SCENE = "selectedScene";
    static final String CURRENT_SCENE_NAME = "currentSceneName";
    static final String CURRENT_SCENE_POSITION = "currentScenePosition";
    
    private static final float IMAGE_RELECTION = 0.18f;
    private static final int MAX_ZOOM_OUT = 120;
    
    private BookmarkAdapter mAdapter;
    private BounceCoverFlow mCoverflow;    
    private ArrayList<BookmarkItem> mBookmarkItems;

    private TextView mSceneNameText;
    
    private Bitmap mStamp;
    
    private int mSelectedPos;
    
    private int mImgHeight;
    private int mImgWidth;
    
    private boolean mVisible = false;
    private boolean mAttached = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scene_chooser);
        initViews();
    }
    
    @Override
    protected void onDestroy() {
    	Bitmap bmp = null;
        for (BookmarkItem item : mBookmarkItems) {
            bmp = item.getContentBitmap();
            if (bmp != null) {
                bmp.recycle();
            }
        }
        super.onDestroy();
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
            	finish();
            	break;
        }
        return true;
    }
    
    private void initViews() {     
    	final Resources res = getResources();
        mSceneNameText = (TextView) findViewById(R.id.scene_name);
        
        mStamp = BitmapFactory.decodeResource(res, R.drawable.current_scene_stamp);

        mBookmarkItems = new ArrayList<BookmarkItem>();

        mImgWidth = res.getDimensionPixelSize(R.dimen.thumb_disp_width);
        mImgHeight = res.getDimensionPixelSize(R.dimen.thumb_disp_height);
                
        mCoverflow = (BounceCoverFlow) findViewById(R.id.bookmarkCoverflow);
        mCoverflow.setGravity(Gravity.CENTER_VERTICAL);
        
        findScene();
        
        mAdapter = new BookmarkAdapter(this, mBookmarkItems);
        
        mAdapter.setImageDispSize(mImgWidth, mImgHeight);
        mAdapter.setImageReflection(IMAGE_RELECTION);
        mCoverflow.setSpacing(res.getDimensionPixelSize(R.dimen.coverflow_space));
        
        mCoverflow.setAdapter(mAdapter);
        mCoverflow.setSelection(Launcher.mCurrentScenePos);
        mCoverflow.setEmptyView(null);
        mCoverflow.setMaxZoomOut(MAX_ZOOM_OUT);
        mCoverflow.setOnItemSelectedListener(this);
    }
    
    private void findScene() {
        final Resources resources = getResources();
        // Context.getPackageName() may return the "original" package name,
        // com.android.launcher2; Resources needs the real package name,
        // com.android.launcher. So we ask Resources for what it thinks the
        // package name should be.
        final String packageName = resources.getResourcePackageName(R.array.scene_preview);

        addScenes(resources, packageName, R.array.scene_preview);
        
        final int size = mBookmarkItems.size();
        final String scene = Launcher.getCurrentScene();
        for (int i = 0; i < size; i++) {
            if (scene.equals(mBookmarkItems.get(i).getTitleString())) {
            	Launcher.mCurrentScenePos = i;
                addStamp(mBookmarkItems.get(i).getContentBitmap(), mStamp);
            }
        }
    }
    
    private void addStamp(Bitmap preview, Bitmap stamp) {
    	final int previewWidth = preview.getWidth();
        final int previewHeight = preview.getHeight();
        final int stampWidth = stamp.getWidth();
        final int stampHeight = stamp.getHeight();

		Bitmap stampBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);

		Canvas canvas = new Canvas(stampBitmap);
		canvas.drawBitmap(preview, 0, 0, null);
		canvas.drawBitmap(stamp, 0, 0, null);

		canvas.save(Canvas.ALL_SAVE_FLAG);
		canvas.restore();
		
		mBookmarkItems.get(Launcher.mCurrentScenePos).setContentBitmap(stampBitmap);
    }

    private void addScenes(Resources resources, String packageName, int list) {
        final String[] extras = resources.getStringArray(list);
        int res = 0;
        Drawable d = null;
        Bitmap previewImage = null;
        String sceneName = "";

        for (String extra : extras) {
            res = resources.getIdentifier(extra, "drawable", packageName);
            if (res != 0) {
                d = resources.getDrawable(res);
                previewImage = ((BitmapDrawable) d).getBitmap();
                sceneName = getSceneName(extra);
                BookmarkItem item = new BookmarkItem(previewImage, sceneName, null);
                mBookmarkItems.add(item);
            }
        }
    }

    static String getSceneName(String extra) {
        // "scene_work_preview" name[1] = work, "work" is the scene name
        String name[] = extra.split("_");
        return name[1];
    }

    public void switchSceneAndBack(View v) {
        final Bundle bundle = new Bundle();
        bundle.putString(SELECTED_SCENE, mBookmarkItems.get(mSelectedPos).getTitleString());
        bundle.putString(CURRENT_SCENE_NAME, Launcher.mSceneNames[mSelectedPos]);
        bundle.putInt(CURRENT_SCENE_POSITION, mSelectedPos);
        final Intent intent = new Intent(LauncherModel.SWITCH_SCENE_ACTION);
        intent.putExtras(bundle);
        sendBroadcast(intent);
        Settings.System.putString(getContentResolver(), "current_scene_name", Launcher.mSceneNames[mSelectedPos]);
        LauncherLog.d(TAG, "switchSceneAndBack current_scene_name = " + Launcher.mSceneNames[Launcher.mCurrentScenePos]);
        finish();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        LauncherLog.d(TAG, "onItemSelected: position = " + position + ", mSelectedPos = "
                + mSelectedPos);
        mSelectedPos = position;
        LauncherLog.d(TAG, "onItemSelected mSceneNames[" + mSelectedPos + "] = " + Launcher.mSceneNames[mSelectedPos]);
        if (mSelectedPos == Launcher.mCurrentScenePos ) {
            mSceneNameText.setText(Launcher.mSceneNames[mSelectedPos] + "(" + getResources().getString(R.string.state) + ")");
        } else {
            mSceneNameText.setText(Launcher.mSceneNames[mSelectedPos]);
        }
    }
    
    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    	mSelectedPos = -1;
    }
}
