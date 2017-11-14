package dk.webbook.scanpilot;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

public class ScanManWebViewFragment extends WebViewFragment {
    private ScanManWebAppInterface mInterface;
    private ScanManWebViewClient mClient;
    private View.OnLongClickListener mLongClickListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        WebView webView = (WebView) super.onCreateView(inflater, container, savedInstanceState);
        Bundle bundle = getArguments();
        if (webView != null) {
            FragmentActivity activity = getActivity();
            MainActivity mainActivity = ((MainActivity)getActivity());
            // Get URL
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
            final String appURL = sharedPref.getString("pref_url", getString(R.string.app_url));

            // Enable javascript
            webView.getSettings().setJavaScriptEnabled(true);
            if (mInterface == null) {
                mInterface = new ScanManWebAppInterface(activity);
                webView.addJavascriptInterface(mInterface, "Android");
            }

            // Set new WebViewClient
            if (mClient == null) {
                mClient = new ScanManWebViewClient(mainActivity);
                webView.setWebViewClient(mClient);
            }

            if (mLongClickListener == null) {
                mLongClickListener = new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        return true;
                    }
                };
                webView.setOnLongClickListener(mLongClickListener);
            }

            mainActivity.loadSite(appURL);
            webView.clearHistory();
            webView.clearCache(true);
        }

        return webView;
    }

    @Override
    public void onDestroyView() {
        if (mInterface != null) {
            mInterface.releaseSoundPool();
        }
        super.onDestroyView();
    }
}
