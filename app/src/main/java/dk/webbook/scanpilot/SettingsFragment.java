package dk.webbook.scanpilot;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.Map;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "SettingsFragment";

    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getContext().setTheme(R.style.PreferenceTheme);

        // Get preferences from XML
        addPreferencesFromResource(R.xml.preferences);

        MainActivity activity = ((MainActivity)getActivity());
        PreferenceCategory pref_camera = (PreferenceCategory) getPreferenceManager().findPreference("pref_camera");
        PackageManager packageManager = activity.getPackageManager();

        if ( !activity.hasCamera() ) {
            // Remove all camera settings
            getPreferenceScreen().removePreference(pref_camera);
        } else if ( !activity.hasPlayServices() ) {
            // Remove settings that belong to play services solution
            pref_camera.removePreference(pref_camera.findPreference("pref_autodetect"));
            pref_camera.removePreference(pref_camera.findPreference("pref_fps"));
        }

        // Add current values to summary
        SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
        Map<String,?> keys = prefs.getAll();
        for(Map.Entry<String,?> entry : keys.entrySet()){
            onSharedPreferenceChanged(prefs, entry.getKey());
        }

        activity.hideCamera();
    }

    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        MainActivity activity = (MainActivity)getActivity();

        if (pref instanceof ListPreference) {
            String value = ((ListPreference) pref).getValue();

            if (key.equals("pref_url") && value.equals(getString(R.string.app_offline_url))) {
                // If we choose offline, we try to connect to offline WIFI
                NetworkConnectionManager.connectToHiddenNetwork(activity);
            } else {
                // Else we check whether internet is available
                ConnectivityManager manager = (ConnectivityManager)
                        activity.getSystemService(MainActivity.CONNECTIVITY_SERVICE);
                NetworkInfo info = manager.getActiveNetworkInfo();

                if (info != null && info.isConnectedOrConnecting()) {
                    Log.d(TAG, "Is Connected");
                    WifiManager wifiManager =
                            (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
                    String offline = String.format("\"%s\"", getString(R.string.app_offline_ssid));

                    Log.d(TAG, "WIFI Connection: " + wifiManager.getConnectionInfo().getSSID());
                    if ( wifiManager.getConnectionInfo().getSSID().equals(offline) ) {
                        wifiManager.disconnect();
                        int networkId = NetworkConnectionManager.getNetworkIDFromConfiguredNetworks(activity, offline);
                        if (networkId != -1) {
                            Log.d(TAG, "Disabling hidden network");
                            wifiManager.disableNetwork(networkId);
                        }
                        if ( !NetworkConnectionManager.getMobileData(activity) ) {
                            Toast.makeText(activity, "No internet detected!", Toast.LENGTH_SHORT).show();
                            NetworkConnectionManager.setMobileData(activity, true);
                        }
                    }
                } else {
                    Toast.makeText(activity, "No internet detected!", Toast.LENGTH_SHORT).show();
                    NetworkConnectionManager.setMobileData(activity, true);
                }
            }
            pref.setSummary(((ListPreference) pref).getEntry());
        } else if (pref instanceof EditTextPreference) {
            pref.setSummary(((EditTextPreference) pref).getText());
        } else if (pref instanceof CheckBoxPreference) {
            if (((CheckBoxPreference)pref).isChecked()) {
                pref.setSummary("true");
            } else {
                pref.setSummary("false");
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
}
