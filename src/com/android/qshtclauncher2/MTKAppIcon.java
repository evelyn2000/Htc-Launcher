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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewStub;
import android.widget.RelativeLayout;
import android.widget.TextView;


/**
 * M: An icon on a PagedView, specifically for items in the launcher's paged view (with compound
 * drawables on the top).
 */
public class MTKAppIcon extends RelativeLayout {
    private static final String TAG = "MTKAppIcon"; 

    PagedViewIcon mAppIcon;
    private TextView mUnread;
    private ViewStub mUnreadStub;
    private ApplicationInfo mInfo;
    private int mAlpha = 255;
    private int mHolographicAlpha;

    public MTKAppIcon(final Context context) {
        super(context);
    }

    public MTKAppIcon(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public MTKAppIcon(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();  
        
        /* If use the default id, can get the view, but if not, may return null, so 
         * be careful when create the shortcut icon from different layout, make it the same
         * id is very important, like application and boxed_application.
         */
        mAppIcon = (PagedViewIcon)findViewById(R.id.app_customize_application_icon);
        mUnreadStub = (ViewStub)findViewById(R.id.app_customize_unread_stub); 
    }

    /**
     * Update unread message of the shortcut, the number of unread information
     * comes from the list.
     */
    public void updateUnreadNum() {
        if (mInfo.unreadNum <= 0) {
            if (mUnread != null) {
                mUnread.setVisibility(View.GONE);
            }
        } else {
            if (mUnread == null) {
                mUnread = (TextView) mUnreadStub.inflate();
            }
            mUnread.setVisibility(View.VISIBLE);
            if (mInfo.unreadNum > Launcher.MAX_UNREAD_COUNT) {
                mUnread.setText(MTKUnreadLoader.getExceedText());
            } else {
                mUnread.setText(String.valueOf(mInfo.unreadNum));
            }
        }
    }

    /**
     * Update the unread message of the shortcut with the given information.
     * 
     * @param unreadNum the number of the unread message.
     */
    public void updateUnreadNum(final int unreadNum) {
        if (LauncherLog.DEBUG_UNREAD) {
            LauncherLog.d(TAG, "updateUnreadNum: unreadNum = " + unreadNum + ", mInfo = " + mInfo);
        }
        if (unreadNum <= 0) {
            mInfo.unreadNum = 0;
            if (mUnread != null) {
                mUnread.setVisibility(View.GONE);
            }
        } else {
            if (mUnread == null) {
                mUnread = (TextView) mUnreadStub.inflate();
            }
            mInfo.unreadNum = unreadNum;
            mUnread.setVisibility(View.VISIBLE);
            if (unreadNum > Launcher.MAX_UNREAD_COUNT) {
                mUnread.setText(MTKUnreadLoader.getExceedText());
            } else {
                mUnread.setText(String.valueOf(unreadNum));
            }
        }
        setTag(mInfo);
    }

    @Override
    public void setTag(final Object tag) {
        super.setTag(tag);
        mAppIcon.setTag(tag);
        mInfo = (ApplicationInfo) tag;
    }

    public void applyFromApplicationInfo(final ApplicationInfo info, final boolean scaleUp,
            final PagedViewIcon.PressedCallback cb) {
        //LauncherLog.d(TAG, "applyFromApplicationInfo info = " + info + ", mAppIcon = " + mAppIcon);
        mAppIcon.applyFromApplicationInfo(info, scaleUp, cb);
        setTag(info);
        updateUnreadNum();
    }
    
    public void applyFromApplicationInfo(final ItemInfo info, final boolean scaleUp,
            final PagedViewIcon.PressedCallback cb) {
        //LauncherLog.d(TAG, "applyFromApplicationInfo info = " + info + ", mAppIcon = " + mAppIcon);
        mAppIcon.applyFromApplicationInfo(info, scaleUp, cb);
        setTag(info);
        updateUnreadNum();
    }

    @Override
    public void setAlpha(final float alpha) {
        final float viewAlpha = HolographicOutlineHelper.viewAlphaInterpolator(alpha);
        final float holographicAlpha = HolographicOutlineHelper.highlightAlphaInterpolator(alpha);
        int newViewAlpha = (int) (viewAlpha * 255);
        int newHolographicAlpha = (int) (holographicAlpha * 255);
        if ((mAlpha != newViewAlpha) || (mHolographicAlpha != newHolographicAlpha)) {
            mAlpha = newViewAlpha;
            mHolographicAlpha = newHolographicAlpha;
            super.setAlpha(viewAlpha);
        }
    }

    public void invalidateCheckedImage() {
        mAppIcon.invalidate();
    }
}
