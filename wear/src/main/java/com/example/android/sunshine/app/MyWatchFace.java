/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;


import com.example.android.sunshine.app.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService{
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final String TEMP_HIGH_LOW = "/temp-detail";
    private static final String IMAGE_KEY = "photo";
    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private GoogleApiClient mGoogleApiClient;
    private static final int MSG_UPDATE_TIME = 0;
    private static final String IMAGE_PATH = "/image";
    private static final String TAG ="MyWatchFace" ;
    private Bitmap image;
    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }



    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener
            ,MessageApi.MessageListener,
    DataApi.DataListener
    {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint,mDatePaint,mTempPaint;
        boolean mAmbient;
        Calendar mCalendar;
        private String temp="MAX 14";
        private String templow="MIN  10";
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;
        float mYdateoffset;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            Log.d("djbd","jdbh");
            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mYdateoffset=resources.getDimension(R.dimen.date_y_offset);
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));
            mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();
            mTextPaint = new Paint();
            mDatePaint= new Paint();
            mTempPaint=new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_text));
            mTempPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mCalendar = Calendar.getInstance();
            image= BitmapFactory.decodeResource(getApplicationContext().getResources(),R.drawable.ic_clear);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mGoogleApiClient.connect();
                Wearable.DataApi.addListener(mGoogleApiClient, this);
                Wearable.MessageApi.addListener(mGoogleApiClient, this);
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
                Wearable.DataApi.removeListener(mGoogleApiClient, this);
                Wearable.MessageApi.removeListener(mGoogleApiClient, this);
                mGoogleApiClient.disconnect();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            mYOffset= resources.getDimension(!isRound
                    ? R.dimen.digital_y_offset : R.dimen.digital_y_offset_round);
            mYdateoffset=resources.getDimension(!isRound
                    ? R.dimen.date_y_offset : R.dimen.date_y_offset_round);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float dateSize = resources.getDimension(isRound
                    ? R.dimen.date_text_size_round : R.dimen.date_text_size);
            mTextPaint.setTextSize(textSize);
            mDatePaint.setTextSize(dateSize);
            mTempPaint.setTextSize(dateSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
               // mTextPaint.setColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE));
            //mXOffset=bounds.width()/4;
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd yyyy");
            String currentDateandTime = sdf.format(new Date());
            String date=currentDateandTime.toUpperCase();
            if(mAmbient) {
                canvas.drawText(text, mXOffset, mYOffset, mTextPaint);
                canvas.drawText(date, mXOffset, mYdateoffset, mDatePaint);
            }
            else {
                canvas.drawText(text, mXOffset, mYOffset, mTextPaint);
                canvas.drawText(date, mXOffset, mYdateoffset, mDatePaint);

                canvas.drawBitmap(image, (bounds.width()/2-image.getWidth()), bounds.height() / ((float) 1.65), null);
                canvas.drawText(temp, bounds.width() / 2, bounds.height() / ((float) 1.35), mTempPaint);
                canvas.drawText(templow, bounds.width() / 2, bounds.height() / ((float) 1.20), mTempPaint);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
          //  mGoogleApiClient.connect();
            LOGD(TAG, "onDataChanged(): " + dataEvents);
            Log.d("recieveing","recieving");
            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    String path = event.getDataItem().getUri().getPath();
                    if (IMAGE_PATH.equals(path)) {
                        DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                        Asset photoAsset = dataMapItem.getDataMap()
                                .getAsset(IMAGE_KEY);
                        // Loads image on background thread.
                        new LoadBitmapAsyncTask().execute(photoAsset);

                    }
                }
                else {
                    LOGD(TAG, "Unknown data event type" + "Type = " + event.getType());
                }
            }

        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d("kn","kj");

            Wearable.DataApi.addListener(mGoogleApiClient, this);
            Wearable.MessageApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        }

        private class LoadBitmapAsyncTask extends AsyncTask<Asset, Void, Bitmap> {

            @Override
            protected Bitmap doInBackground(Asset... params) {

                if (params.length > 0) {

                    Asset asset = params[0];

                    InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                            mGoogleApiClient, asset).await().getInputStream();

                    if (assetInputStream == null) {
                        Log.w(TAG, "Requested an unknown Asset.");
                        return null;
                    }
                    return BitmapFactory.decodeStream(assetInputStream);

                } else {
                    Log.e(TAG, "Asset must be non-null");
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {

                if (bitmap != null) {
                    LOGD(TAG, "Setting background image on second page..");
                    image=bitmap;
                    invalidate();

                }
                else{
                    LOGD(TAG, "Setting background image on second lol..");
                }
            }
        }
        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            Log.d("recieveing","recievinggmessage");
            if(messageEvent.getPath().equals(TEMP_HIGH_LOW)){
                String suffix="\u00B0";
                String data=new String(messageEvent.getData(), Charset.defaultCharset());
                StringTokenizer s = new StringTokenizer(data," ");
                temp= "MAX "+s.nextToken();
                templow="MIN "+s.nextToken();
              //  templow=data;
                invalidate();
            }
        }


    }
    public static void LOGD(final String tag, String message) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }

}
