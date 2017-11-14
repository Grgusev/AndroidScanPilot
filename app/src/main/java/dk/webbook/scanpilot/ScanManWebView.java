package dk.webbook.scanpilot;

import android.app.Activity;
import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;

/**
 * Created by morten on 8/30/16.
 */
public class ScanManWebView extends WebView {
    private static final String TAG = "ScanManWebView";
    private MainActivity mActivity;

    public ScanManWebView(Context context) {
        super(context);
    }

    public void setActivity(Activity activity) {
        mActivity = ((MainActivity) activity);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return new BaseInputConnection(this, false); //this is needed for #dispatchKeyEvent() to be notified.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean dispatchFirst = super.dispatchKeyEvent(event);
        // Listening here for whatever key events you need
        if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_ENTER:
                    mActivity.hideSoftKeyboard();
                    break;
            }
        }
        return dispatchFirst;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        boolean dispatchFirst = super.onKeyPreIme(keyCode, event);
        // Listening here for whatever key events you need
        if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                    mActivity.hideSoftKeyboard();
                    break;
            }
        }
        return dispatchFirst;
    }

}
