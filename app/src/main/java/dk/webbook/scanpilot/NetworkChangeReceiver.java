package dk.webbook.scanpilot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

public abstract class NetworkChangeReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, final Intent intent) {
        debugIntent(intent, "NetworkChangeReceiver");
        switch (intent.getAction()) {
            case ConnectivityManager.CONNECTIVITY_ACTION:
                String offline = String.format("\"%s\"", context.getString(R.string.app_offline_ssid));
                NetworkInfo networkInfo = intent.getParcelableExtra("networkInfo");

                if (networkInfo.isConnected()) {
                    String info = intent.getStringExtra("extraInfo");
                    if (info != null && info.equals(offline)) {
                        refresh(context.getString(R.string.app_offline_url));
                    } else {
                        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                        String online = sharedPref.getString("pref_url", context.getString(R.string.app_url));
                        refresh(online);
                    }
                }
                break;
        }
	}

    protected abstract void refresh(String url);

    private void debugIntent(Intent intent, String tag) {
        Log.v(tag, "action: " + intent.getAction());
        Log.v(tag, "component: " + intent.getComponent());
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key: extras.keySet()) {
                Log.v(tag, "key [" + key + "]: " +
                        extras.get(key));
            }
        }
        else {
            Log.v(tag, "no extras");
        }
    }
}