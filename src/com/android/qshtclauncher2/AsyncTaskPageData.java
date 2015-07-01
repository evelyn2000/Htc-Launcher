package com.android.qshtclauncher2;

import java.util.ArrayList;

import android.graphics.Bitmap;

public class AsyncTaskPageData {
    enum Type {
        LoadWidgetPreviewData
    }

    AsyncTaskPageData(int p, ArrayList<Object> l, ArrayList<Bitmap> si, AsyncTaskCallback bgR,
            AsyncTaskCallback postR) {
        page = p;
        items = l;
        sourceImages = si;
        generatedImages = new ArrayList<Bitmap>();
        maxImageWidth = maxImageHeight = -1;
        doInBackgroundCallback = bgR;
        postExecuteCallback = postR;
    }
    AsyncTaskPageData(int p, ArrayList<Object> l, int cw, int ch, AsyncTaskCallback bgR,
            AsyncTaskCallback postR) {
        page = p;
        items = l;
        generatedImages = new ArrayList<Bitmap>();
        maxImageWidth = cw;
        maxImageHeight = ch;
        doInBackgroundCallback = bgR;
        postExecuteCallback = postR;
    }
    void cleanup(boolean cancelled) {
        // Clean up any references to source/generated bitmaps
        if (sourceImages != null) {
            if (cancelled) {
                for (Bitmap b : sourceImages) {
                    b.recycle();
                }
            }
            sourceImages.clear();
        }
        if (generatedImages != null) {
            if (cancelled) {
                for (Bitmap b : generatedImages) {
                    b.recycle();
                }
            }
            generatedImages.clear();
        }
    }
    int page;
    ArrayList<Object> items;
    ArrayList<Bitmap> sourceImages;
    ArrayList<Bitmap> generatedImages;
    int maxImageWidth;
    int maxImageHeight;
    AsyncTaskCallback doInBackgroundCallback;
    AsyncTaskCallback postExecuteCallback;
}