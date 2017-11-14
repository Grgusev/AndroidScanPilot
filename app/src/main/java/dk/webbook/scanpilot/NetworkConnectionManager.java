package dk.webbook.scanpilot;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class NetworkConnectionManager {
    private static final String TAG = "NCM";

    public static void connectToHiddenNetwork(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        String ssid = String.format("\"%s\"", context.getString(R.string.app_offline_ssid));
        int networkId;

        Log.d(TAG, "Connecting to hidden network");
        wifiManager.setWifiEnabled(true);

        if ( !isSSIDInConfiguredNetworks(context, ssid) ) {
            WifiConfiguration wifiConfig = new WifiConfiguration();
            wifiConfig.SSID = ssid;
            wifiConfig.preSharedKey = String.format("\"%s\"", "scanning17");
            wifiConfig.hiddenSSID = true;

            networkId = wifiManager.addNetwork(wifiConfig);
        } else {
            networkId = getNetworkIDFromConfiguredNetworks(context, ssid);
        }

        Log.d(TAG, "Network ID: " + networkId);

        if (!wifiManager.getConnectionInfo().getSSID().equals(ssid) && networkId != -1) {
            setMobileData(context, false);
            wifiManager.disconnect();
            wifiManager.enableNetwork(networkId, true);
            wifiManager.saveConfiguration();
            wifiManager.reconnect();
        }
    }

    public static boolean getMobileData(Context context) {
        boolean mobileDataEnabled; // Assume disabled
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            Class cmClass = Class.forName(cm.getClass().getName());
            Method method = cmClass.getDeclaredMethod("getMobileDataEnabled");
            method.setAccessible(true); // Make the method callable
            // get the setting for "mobile data"
            mobileDataEnabled = (Boolean)method.invoke(cm);
        } catch (Exception e) {
            mobileDataEnabled = false;
        }
        return mobileDataEnabled;
    }

    public static void setMobileData(Context context, boolean enable) {
        try {
            final ConnectivityManager conman = (ConnectivityManager)  context.getSystemService(Context.CONNECTIVITY_SERVICE);
            final Class conmanClass = Class.forName(conman.getClass().getName());
            final Field connectivityManagerField = conmanClass.getDeclaredField("mService");
            connectivityManagerField.setAccessible(true);
            final Object connectivityManager = connectivityManagerField.get(conman);
            final Class connectivityManagerClass =  Class.forName(connectivityManager.getClass().getName());
            final Method setMobileDataEnabledMethod = connectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
            setMobileDataEnabledMethod.setAccessible(true);
            setMobileDataEnabledMethod.invoke(connectivityManager, enable);
        } catch (Exception e) {
            if (!enable) {
                Toast.makeText(context, "Disable mobile data to use hidden network",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    public static int getNetworkIDFromConfiguredNetworks(Context context, String ssid) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        List<WifiConfiguration> confs = wifiManager.getConfiguredNetworks();
        if (confs != null) {
            for (WifiConfiguration conf : confs) {
                if (ssid.equals(conf.SSID)) {
                    return conf.networkId;
                }
            }
        }
        return -1;
    }

    public static boolean isSSIDInConfiguredNetworks(Context context, String ssid) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        List<WifiConfiguration> confs = wifiManager.getConfiguredNetworks();
        if (confs != null) {
            for (WifiConfiguration conf : confs) {
                if (ssid.equals(conf.SSID)) {
                    return true;
                }
            }
        }
        return false;
    }
}
