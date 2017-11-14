package dk.webbook.scanpilot;

import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

/**
 * WebViewFragment support for android.support.v4.app.Fragment
 * This demo has been tested for SDK 10 and 22.
 *
 * author @juanmendezinfo
 * @see <a href="http://www.juanmendez.info/2015/09/webviewfragment-which-supports.html">details</a>
 */
public class WebViewFragment extends Fragment {
    private static final String TAG = "WebViewFragment";
    private ScanManWebView mWebView;
    private boolean mIsWebViewAvailable;
    private boolean mRotated = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setRetainInstance(true);


        if ( mWebView == null ) {
            mWebView = new ScanManWebView(getActivity());
            mWebView.clearFormData();
            mWebView.setActivity(getActivity());
            mWebView.setFocusable(true);
            mWebView.setFocusableInTouchMode(true);
            mWebView.getSettings().setSaveFormData(false);
            mWebView.requestFocus();
            mWebView.setOnTouchListener(new View.OnTouchListener() {
                private final float MOVE_THRESHOLD_DP = 20 * getResources().getDisplayMetrics().density;
                private boolean mMoveOccured;
                private float mDownPosX;
                private float mDownPosY;

                @Override
                public boolean onTouch(View v, MotionEvent ev) {
                    final int action = ev.getAction();
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                            Log.d(TAG, "ACTION DOWN");
                            mMoveOccured = false;
                            mDownPosX = ev.getX();
                            mDownPosY = ev.getY();
                            break;
                        case MotionEvent.ACTION_UP:
                            Log.d(TAG, "ACTION UP");
                            Log.d(TAG, "Move: " + mMoveOccured);
                            if (!mMoveOccured) {
                                ((MainActivity)getActivity()).hideSoftKeyboard();
                            }
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if ( Math.abs(ev.getX() - mDownPosX) > MOVE_THRESHOLD_DP ||
                                    Math.abs(ev.getY() - mDownPosY) > MOVE_THRESHOLD_DP) {
                                mMoveOccured = true;
                            }
                            break;
                    }
                    return false;
                }
            });
        }

        mIsWebViewAvailable = true;

        return mWebView;
    }


    /**
     * let us know if the webView has been rotated.
     * @return boolean
     */
    public boolean rotated() {
        return mRotated;
    }

    /**
     * Called when the fragment is no longer resumed. Pauses the WebView.
     */
    @Override
    public void onPause() {
        super.onPause();

        if (honeyOrHigher())
            mWebView.onPause();

        mRotated = true;
    }

    /**
     * Called when the fragment is visible to the user and actively running. Resumes the WebView.
     */
    @Override
    public void onResume() {
        mRotated = false;

        if (honeyOrHigher())
            mWebView.onResume();
        mWebView.requestFocus();

        super.onResume();
    }

    private boolean honeyOrHigher(){
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    /**
     * Called when the WebView has been detached from the fragment.
     * The WebView is no longer available after this time.
     */
    @Override
    public void onDestroyView() {
        mIsWebViewAvailable = false;

        if( mWebView != null )
        {
            ViewGroup parentViewGroup = (ViewGroup) mWebView.getParent();
            if (parentViewGroup != null) {
                parentViewGroup.removeView(mWebView);
            }
        }

        super.onDestroyView();
    }

    /**
     * Called when the fragment is no longer in use. Destroys the internal state of the WebView.
     */
    @Override
    public void onDestroy() {
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroy();
    }

    /**
     * Gets the WebView.
     */
    public WebView getWebView() {
        return mIsWebViewAvailable ? mWebView : null;
    }
}
