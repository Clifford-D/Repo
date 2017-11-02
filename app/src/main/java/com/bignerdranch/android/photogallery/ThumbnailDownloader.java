package com.bignerdranch.android.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


import static android.provider.ContactsContract.CommonDataKinds.Website.URL;

/**
 * Created by Dustin on 10/31/2017.
 */

public class ThumbnailDownloader<T> extends HandlerThread {

    private static final String TAG =
            "ThumbnailDownloader";

    private static final int MESSAGE_DOWNLOAD = 0;

    private boolean mHasQuit = false;

    private Handler mRequestHandler;
    private ConcurrentMap<T,String> mRequestMap = new
            ConcurrentHashMap<>();

    private Handler mResponseHandler;
    private ThumbnailDownloadListener<T>
            mThumbnailDownloadListener;
    public interface ThumbnailDownloadListener<T> {
        void onThumbnailDownloaded(T target, Bitmap
                thumbnail);
    }
    public void
    setThumbnailDownloadListener(ThumbnailDownloadListener<T>
                                         listener) {
        mThumbnailDownloadListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        mResponseHandler = responseHandler;
    }

    @Override
    protected void onLooperPrepared() {
        mRequestHandler = new Handler() {
            @Override
            public void handleMessage(NotificationCompat.MessagingStyle.Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    T target = (T) msg.obj;
                    Log.i(TAG, "Got a request for URL: " + mRequestMap.get(target)); handleRequest(target);
                }
            }
        };
    }

    @Override
    public boolean quit() {
        mHasQuit = true;
        return super.quit();
    }
    public void queueThumbnail(T target, String url)
    {
        Log.i(TAG, "Got a URL: " + url);

        if (url == null) {
            mRequestMap.remove(target);
        } else {
            mRequestMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD,
                    target)
                    .sendToTarget();
        }
    }

    public void clearQueue() {
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
        mRequestMap.clear();
    }

    private void handleRequest(final T target) {
        try {
            final String url =
                    mRequestMap.get(target);
            if (url == null) {
                return;
            }
            byte[] bitmapBytes = new
                    FlickerFetcher().getUrlBytes(url);
            final Bitmap bitmap = BitmapFactory
                    .decodeByteArray(bitmapBytes, 0,
                            bitmapBytes.length);
            Log.i(TAG, "Bitmap created");

            mResponseHandler.post(new Runnable() {
                public void run() {
                    if (mRequestMap.get(target) !=
                            url ||
                            mHasQuit) {
                        return;
                    }
                    mRequestMap.remove(target);
                    mThumbnailDownloadListener.onThumbnailDownloaded(target,
                            bitmap);
                }
            });

        } catch (IOException ioe) {
            Log.e(TAG, "Error downloading image",
                    ioe);
        }
    }
}