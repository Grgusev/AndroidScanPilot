package dk.webbook.scanpilot;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import dk.webbook.scanpilot.barcode.BarcodeCaptureActivity;


// TODO: Disable keyboard onclick on webview

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int RC_BARCODE_CAPTURE = 9001;


    private static final String SETTINGS = "SETTINGS";
    private static final String WEBVIEW = "WEBVIEW";

    private Context mContext;
    private ScanManWebViewFragment mWebViewFragment;
    private SettingsFragment mSettingsFragment;
    private Menu mMenu;
    private MainActivity mActivity;
    private ProgressDialog mDialog;
    private NetworkChangeReceiver mReceiver;
    private boolean mKeyboardVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = mActivity = this;
        mDialog              = new ProgressDialog(this);
        mKeyboardVisible     = false;

        // Create toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolBar);
        toolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get URL
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
                String appURL = sharedPref.getString("pref_url", getString(R.string.app_url));

                // Get webview
                WebView webView = mWebViewFragment.getWebView();

                // We are probably in settings
                FragmentManager manager = getSupportFragmentManager();
                if (manager.getBackStackEntryCount() > 0) {
                    manager.popBackStack();
                    showCamera();
                    focusWebView();
                    return;
                }

                if (webView != null) {
                    mActivity.loadSite(appURL);
                    showCamera();
                }
            }
        });
        setSupportActionBar(toolbar);

        mReceiver = new NetworkChangeReceiver() {
            @Override
            protected void refresh(String url) {
                if (mWebViewFragment != null) {
                    WebView webView = mWebViewFragment.getWebView();
                    if (webView != null) {
                        loadSite(url);
                    }
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
        registerReceiver(mReceiver, intentFilter);

        // Create WebView fragment
        mWebViewFragment = new ScanManWebViewFragment();
        mSettingsFragment = new SettingsFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.fragment_container, mWebViewFragment, WEBVIEW)
                .commit();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();

        ConnectivityManager manager = (ConnectivityManager)
                getSystemService(MainActivity.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager.getActiveNetworkInfo();

        mKeyboardVisible = false;

        if (info != null && info.isConnectedOrConnecting()) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String appURL = sharedPref.getString("pref_url", getString(R.string.app_url));

            if (getString(R.string.app_offline_url).equals(appURL)) {
                NetworkConnectionManager.connectToHiddenNetwork(this);
            } else {
                WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
                String ssid = String.format("\"%s\"", getString(R.string.app_offline_ssid));
                if ( wifiManager.getConnectionInfo().getSSID().equals(ssid) ) {
                    wifiManager.disconnect();
                    int networkId = NetworkConnectionManager.getNetworkIDFromConfiguredNetworks(this, ssid);
                    if (networkId != -1) {
                        wifiManager.disableNetwork(networkId);
                    }
                    if ( !NetworkConnectionManager.getMobileData(this) ) {
                        Toast.makeText(mContext, "No internet detected!", Toast.LENGTH_SHORT).show();
                        NetworkConnectionManager.setMobileData(this, true);
                    }
                }
            }
        } else {
            Toast.makeText(mContext, "No internet detected!", Toast.LENGTH_SHORT).show();
            NetworkConnectionManager.setMobileData(this, true);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        focusWebView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        mMenu = menu;

        if ( !hasCamera() || !sharedPref.getBoolean("pref_enable", false) ) {
            hideCamera();
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FragmentManager manager = getSupportFragmentManager();
        Fragment fragment = manager.findFragmentByTag(SETTINGS);

        switch (item.getItemId()) {
            // action with ID action_camera was selected
            case R.id.action_camera:
                if (!hasPlayServices()) {
                    new IntentIntegrator(this)
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                            .initiateScan();
                } else {
                    dispatchTakePictureIntent();
                }
                break;
            // action with ID action_wifi was selected
            case R.id.action_wifi:
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                break;
            case R.id.action_keyboard:
                if (mKeyboardVisible) {
                    hideSoftKeyboard();
                } else {
                    showSoftKeyboard();
                }
                break;
            case R.id.action_refresh:
                if (fragment != null && fragment.isVisible()) {
                    if (manager.getBackStackEntryCount() > 0) {
                        manager.popBackStack();
                        showCamera();
                        focusWebView();
                    }
                } else {
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(
                            mContext
                    );
                    String appURL = sharedPref.getString("pref_url", getString(R.string.app_url));

                    loadSite(appURL);
                    showCamera();
                }
                break;
            // action with ID action_settings was selected
            case R.id.action_settings:
                if (fragment != null && fragment.isVisible()) {
                    if (manager.getBackStackEntryCount() > 0) {
                        manager.popBackStack();
                        showCamera();
                        focusWebView();
                    }
                } else {
                    manager.beginTransaction()
                            .replace(R.id.fragment_container, mSettingsFragment, SETTINGS)
                            .addToBackStack(null)
                            .commit();
                }
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    public void onBackPressed() {
        FragmentManager manager = getSupportFragmentManager();
        WebView webView = mWebViewFragment.getWebView();

        mKeyboardVisible = false;

        if (manager.getBackStackEntryCount() > 0) {
            manager.popBackStack();
            showCamera();
            focusWebView();
            return;
        }

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            focusWebView();
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        String barcode = null;
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        // Retrieve image from camera
        if (result != null) {
            barcode = result.getContents();
        } else if (requestCode == RC_BARCODE_CAPTURE && resultCode == RESULT_OK) {
            Barcode bc = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
            if (bc != null) {
                barcode = bc.displayValue;
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        if (barcode != null) {
            Log.d(TAG, "Barcode read: " + barcode);
            submitBarCode(barcode);
        }
    }

    public ProgressDialog getDialog() {
        return mDialog;
    }

    public void loadSite(String url) {
        if (mWebViewFragment != null) {
            WebView webView = mWebViewFragment.getWebView();
            if (webView != null) {
                mDialog.show();
                webView.loadUrl(url);
                focusWebView();
            }
        }
    }

    public boolean hasPlayServices() {
        return GoogleApiAvailability
                .getInstance()
                .isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS;
    }

    public boolean hasCamera() {
        PackageManager packageManager = getPackageManager();
        return (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_EXTERNAL))) &&
               Camera.getNumberOfCameras() > 0;
    }

    public void hideCamera() {
        MenuItem menuItem = mMenu.findItem(R.id.action_camera);
        if (menuItem != null) {
            menuItem.setVisible(false);
        }
    }

    public void showCamera() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        if ( hasCamera() && sharedPref.getBoolean("pref_enable", false) ) {
            MenuItem menuItem = mMenu.findItem(R.id.action_camera);
            if (menuItem != null) {
                menuItem.setVisible(true);
            }
        }
    }

    private void focusWebView() {
        if (mWebViewFragment != null) {
            WebView webView = mWebViewFragment.getWebView();
            if (webView != null) {
                webView.requestFocus();
            }
        }
    }

    private void dispatchTakePictureIntent() {
        Intent intent = new Intent(this, BarcodeCaptureActivity.class);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        Boolean autodetect = sharedPref.getBoolean("pref_autodetect",
                Boolean.parseBoolean(getString(R.string.app_autodetect)));
        float fps;
        try {
            fps = Float.parseFloat(sharedPref.getString("pref_fps", getString(R.string.app_fps)));
        } catch (NumberFormatException e) {
            fps = Float.parseFloat(getString(R.string.app_fps));
        }

        intent.putExtra(BarcodeCaptureActivity.UseFps, fps);
        intent.putExtra(BarcodeCaptureActivity.UseAutoDetection, autodetect);

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, RC_BARCODE_CAPTURE);
        }
    }

    private void submitBarCode(String barcode) {
        WebView webView = mWebViewFragment.getWebView();
        if (webView == null) {
            return;
        }

        // Get map between characters and KeyEvents
        KeyCharacterMap CharMap;
        if ( Build.VERSION.SDK_INT >= 11 ) {// My soft runs until API 5
            CharMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        } else {
            CharMap = KeyCharacterMap.load(KeyCharacterMap.ALPHA);
        }

        // Get KeyEvent of each character in barcode
        KeyEvent[] events = CharMap.getEvents(barcode.toCharArray());

        focusWebView();

        // Write barcode to webview
        for (int i=0; i<events.length; i++) {
            webView.dispatchKeyEvent(events[i]);
        }

        // Submit barcode
        KeyEvent evt_down = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        webView.dispatchKeyEvent(evt_down);
        KeyEvent evt_up = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER);
        webView.dispatchKeyEvent(evt_up);
    }

    /**
     * Hides the soft keyboard
     */
    public void hideSoftKeyboard() {
        WebView webView = mWebViewFragment.getWebView();
        if (webView != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(webView.getWindowToken(), 0);
                mKeyboardVisible = false;
            }
        }
    }

    /**
     * Shows the soft keyboard
     */
    private void showSoftKeyboard() {
        WebView webView = mWebViewFragment.getWebView();
        if (webView != null) {
            focusWebView();
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.showSoftInput(webView, 0);
            mKeyboardVisible = true;
        }
    }

}
