package dk.webbook.scanpilot;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Checks that we only visit allowed URLs in our webview
 */
public class ScanManWebViewClient extends WebViewClient {
    private static final String TAG = "ScanManWebViewClient";

    private Context mContext;
    private WebView mWebView;
    private boolean mTimeout;
    private Handler mHandler;
    private Runnable mRun;

    protected ProgressDialog mDialog;

    ScanManWebViewClient(MainActivity activity) {
        mContext   = activity;
        mTimeout   = false;
        mHandler   = new Handler();
        mDialog    = activity.getDialog();
        mRun       = new Runnable() {
            public void run() {

                if (mTimeout) {
                    if (mWebView != null) {
                        mWebView.stopLoading();
                        handleError(mWebView);
                    }
                }
            }
        };
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        int timeout;

        mTimeout = true;
        mWebView = view;

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        try {
            timeout = Integer.parseInt(
                    sharedPref.getString("pref_timeout", mContext.getString(R.string.app_timeout)),
                    10
            );
        } catch (NumberFormatException e) {
            timeout = Integer.parseInt(
                    mContext.getString(R.string.app_timeout),
                    10
            );
        }

        mHandler.postDelayed(mRun, timeout);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        mTimeout = false;
        mHandler.removeCallbacks(mRun);
        mDialog.dismiss();
        view.requestFocus();
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        final Uri uri = Uri.parse(url);
        return this.handleUri(uri);
    }

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        final Uri uri = request.getUrl();
        return this.handleUri(uri);
    }

    private boolean handleUri(final Uri uri) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        String appURL = sharedPref.getString("pref_url", mContext.getString(R.string.app_url));
        Uri appURI = Uri.parse(appURL);

        if (uri.getHost().equals(appURI.getHost())) {
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        this.mContext.startActivity(intent);
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        this.handleError(view);
    }

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        this.handleError(view);
    }

    // Treat all errors as timeout errors
    private void handleError(WebView view) {
        String timedOutHtml = "<!DOCTYPE html>" +
                "<html lang='en'>" +
                "<head>" +
                "<meta charset='utf-8' />" +
                "<meta http-equiv='X-UA-Compatible' content='IE=edge' />" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'/>" +
                "<title>Connection Timed Out</title>" +
                "</head>" +
                "<body>" +
                "<h1>The connection timed out.</h1>" +
                "<h2>Please check your internet connection</h2>" +
                "</body>" +
                "</html>";

        Log.d(TAG, "WebView encountered error while loading site");
        mWebView.loadData(timedOutHtml, "text/html", "utf-8");
    }
}
