/*
 * Copyright (C) The Android Open Source Project
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
package dk.webbook.scanpilot.barcode;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;

import dk.webbook.scanpilot.R;
import dk.webbook.scanpilot.barcode.ui.camera.CameraSourcePreview;
import dk.webbook.scanpilot.barcode.ui.camera.GraphicOverlay;
import dk.webbook.scanpilot.barcode.ui.camera.CameraSource;


//TODO: Fix front camera tracking
//TODO: Maybe use https://github.com/zxing/zxing to support TC-55

/**
 * Activity for the multi-tracker app.  This app detects barcodes and displays the value with the
 * rear facing camera. During detection overlay graphics are drawn to indicate the position,
 * size, and ID of each barcode.
 */
public final class BarcodeCaptureActivity extends AppCompatActivity {
    private static final String TAG = "Barcode-reader";

    // intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    // constants used to pass extra data in the intent
    public static final String UseAutoDetection = "UseAutoDetection";
    public static final String BarcodeObject = "Barcode";
    public static final String UseFps = "UseFps";

    public static final float FPS = 30.0f;

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;

    // Buttons
    private FloatingActionButton mFlash;
    private FloatingActionButton mFlip;

    // helper objects for detecting taps and pinches.
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;

    /**
     * Initializes the UI and creates the detector pipeline.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        setContentView(R.layout.barcode_capture);

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay<BarcodeGraphic>) findViewById(R.id.graphicOverlay);
        mFlash = (FloatingActionButton) findViewById(R.id.flash);
        mFlip = (FloatingActionButton) findViewById(R.id.flip_camera);

        PackageManager packageManager = getPackageManager();


        // read parameters from the intent and shared preferences used to launch the activity.
        boolean flip              = sharedPref.getBoolean("camera_rear", true);
        boolean useFlash          = sharedPref.getBoolean("camera_flash", false);
        boolean useAutoDetection  = getIntent().getBooleanExtra(UseAutoDetection,
                Boolean.parseBoolean(getString(R.string.app_autodetect)));
        float fps                 = getIntent().getFloatExtra(UseFps, FPS);

        if (!flip) {
            useFlash = false;
            mFlash.setVisibility(View.INVISIBLE);
        } else {
            mFlash.setVisibility(View.VISIBLE);
        }

        if ( !packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA) ||
             !packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) ) {
            mFlip.setVisibility(View.GONE);
        } else {
            mFlip.setImageResource(!flip ?
                    R.drawable.ic_camera_rear_white_24dp : R.drawable.ic_camera_front_white_24dp);
        }

        if ( !packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) ) {
            mFlash.setVisibility(View.GONE);
        } else {
            mFlash.setImageResource(!useFlash ?
                    R.drawable.ic_flash_off_white_24dp : R.drawable.ic_flash_on_white_24dp);
        }

        addButtonListeners(useAutoDetection, fps);

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(true, useFlash, fps, useAutoDetection, flip);
        } else {
            requestCameraPermission();
        }

        gestureDetector      = new GestureDetector(this, new CaptureGestureListener());
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        if (!useAutoDetection) {
            Snackbar.make(mGraphicOverlay, "Tap box around barcode to capture.", Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(mGraphicOverlay, "Auto-detecting: Place barcode in front of camera.", Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Adds listeners to the buttons on the preview
     */
    private void addButtonListeners(final boolean useAutoDetection, final float fps) {
        final Context context = this;
        if (mFlash != null) {
            mFlash.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                    boolean flash = sharedPref.getBoolean("camera_flash", false);
                    String mode = !flash ? Camera.Parameters.FLASH_MODE_TORCH
                                        : Camera.Parameters.FLASH_MODE_OFF;
                    sharedPref.edit().putBoolean("camera_flash", !flash).apply();

                    if (mCameraSource != null) {
                        mFlash.setImageResource(flash ?
                                R.drawable.ic_flash_off_white_24dp : R.drawable.ic_flash_on_white_24dp);
                        mCameraSource.setFlashMode(mode);
                    }
                }
            });
        }
        if (mFlip != null) {
            mFlip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                    boolean flip  = !sharedPref.getBoolean("camera_rear", true);
                    boolean flash = sharedPref.getBoolean("camera_flash", false);

                    sharedPref.edit().putBoolean("camera_rear", flip).apply();

                    if (!flip) {
                        flash = false;
                        mFlash.setVisibility(View.INVISIBLE);
                    } else {
                        mFlash.setVisibility(View.VISIBLE);
                    }

                    if (mCameraSource != null) {
                        mFlip.setImageResource(!flip ?
                                R.drawable.ic_camera_rear_white_24dp : R.drawable.ic_camera_front_white_24dp);
                        mCameraSource.release();
                        mCameraSource = null;
                    }

                    createCameraSource(true, flash, fps, useAutoDetection, flip);
                    startCameraSource();
                }
            });
        }
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean b = scaleGestureDetector.onTouchEvent(e);

        boolean c = gestureDetector.onTouchEvent(e);

        return b || c || super.onTouchEvent(e);
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     *
     * Suppressing InlinedApi since there is a check that the minimum version is met before using
     * the constant.
     */
    @SuppressLint("InlinedApi")
    private void createCameraSource(boolean autoFocus, boolean useFlash, float useFps,
                                    boolean useAutoDetection, boolean flip) {
        Context context = getApplicationContext();

        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context).build();
        BarcodeTrackerFactory barcodeFactory;

        if (useAutoDetection) {
            barcodeFactory = new BarcodeTrackerFactory(mGraphicOverlay, new BarcodeGraphicTracker.BarcodeAutoDetectionListener() {
                @Override
                public void onNewBarcodeDetection(Barcode barcode) {
                    if (barcode != null) {
                        Intent data = new Intent();
                        data.putExtra(BarcodeObject, barcode);
                        if (getParent() == null) {
                            setResult(Activity.RESULT_OK, data);
                        } else {
                            getParent().setResult(Activity.RESULT_OK, data);
                        }
                        finish();
                    }
                }
            });
        } else {
            barcodeFactory = new BarcodeTrackerFactory(mGraphicOverlay);
        }

        barcodeDetector.setProcessor(
                new MultiProcessor.Builder<>(barcodeFactory).build());

        if (!barcodeDetector.isOperational()) {
            // Note: The first time that an app using the barcode or face API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any barcodes
            // and/or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();
                Log.w(TAG, getString(R.string.low_storage_error));
            }
        }

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.
        Log.d(TAG, "FLIP: " + flip);
        CameraSource.Builder builder = new CameraSource.Builder(getApplicationContext(), barcodeDetector)
                .setFacing(flip ? CameraSource.CAMERA_FACING_BACK : CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(useFps);

        // make sure that auto focus is an available option
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            builder = builder.setFocusMode(
                    autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null);
        }

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mCameraSource = builder
                .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
                .setRequestedPreviewSize(Math.max(metrics.widthPixels,metrics.heightPixels),
                        Math.min(metrics.widthPixels, metrics.heightPixels))
                .build();
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (mPreview != null) {
            mPreview.stop();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPreview != null) {
            mPreview.release();
        }
        if (mCameraSource != null) {
            mCameraSource.stop();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            boolean flip              = sharedPref.getBoolean("camera_rear", true);
            boolean useFlash          = sharedPref.getBoolean("camera_flash", false);
            boolean useAutoDetection  = getIntent().getBooleanExtra(UseAutoDetection,
                    Boolean.parseBoolean(getString(R.string.app_autodetect)));
            float fps                 = getIntent().getFloatExtra(UseFps, FPS);

            if (!flip) {
                useFlash = false;
                mFlash.setVisibility(View.INVISIBLE);
            } else {
                mFlash.setVisibility(View.VISIBLE);
            }

            PackageManager packageManager = getPackageManager();
            if ( packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA) &&
                    packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) ) {
                mFlip.setImageResource(!flip ?
                        R.drawable.ic_camera_rear_white_24dp : R.drawable.ic_camera_front_white_24dp);
            }

            if ( packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) ) {
                mFlash.setImageResource(!useFlash ?
                        R.drawable.ic_flash_off_white_24dp : R.drawable.ic_flash_on_white_24dp);
            }

            createCameraSource(true, useFlash, fps, useAutoDetection, flip);
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Multitracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() throws SecurityException {
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    /**
     * onTap is called to capture the oldest barcode currently detected and
     * return it to the caller.
     *
     * @param rawX - the raw position of the tap
     * @param rawY - the raw position of the tap.
     * @return true if the activity is ending.
     */
    private boolean onTap(float rawX, float rawY) {
        BarcodeGraphic graphic = mGraphicOverlay.getGraphic(Math.round(rawX), Math.round(rawY));
        Barcode barcode = null;
        if (graphic != null) {
            barcode = graphic.getBarcode();
            if (barcode != null) {
                Intent data = new Intent();
                data.putExtra(BarcodeObject, barcode);
                setResult(Activity.RESULT_OK, data);
                finish();
            } else {
                Log.d(TAG, "barcode data is null");
            }
        } else {
            Log.d(TAG,"no barcode detected");
        }
        return barcode != null;
    }

    private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {

            return onTap(e.getRawX(), e.getRawY()) || super.onSingleTapConfirmed(e);
        }
    }

    private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {

        /**
         * Responds to scaling events for a gesture in progress.
         * Reported by pointer motion.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should consider this event
         * as handled. If an event was not handled, the detector
         * will continue to accumulate movement until an event is
         * handled. This can be useful if an application, for example,
         * only wants to update scaling factors if the change is
         * greater than 0.01.
         */
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            return false;
        }

        /**
         * Responds to the beginning of a scaling gesture. Reported by
         * new pointers going down.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should continue recognizing
         * this gesture. For example, if a gesture is beginning
         * with a focal point outside of a region where it makes
         * sense, onScaleBegin() may return false to ignore the
         * rest of the gesture.
         */
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        /**
         * Responds to the end of a scale gesture. Reported by existing
         * pointers going up.
         * <p/>
         * Once a scale has ended, {@link ScaleGestureDetector#getFocusX()}
         * and {@link ScaleGestureDetector#getFocusY()} will return focal point
         * of the pointers remaining on the screen.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         */
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mCameraSource.doZoom(detector.getScaleFactor());
        }
    }
}