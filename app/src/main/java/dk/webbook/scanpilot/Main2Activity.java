package dk.webbook.scanpilot;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import dk.webbook.scanpilot.barcode.BarcodeCaptureActivity;

public class Main2Activity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "Main2Activity";
    private static final int RC_BARCODE_CAPTURE = 9001;

    public TextView txtScan, txtScanPilot;
    public EditText barcodeEditTxt;
    public Boolean isLoggedIn = false;
    public SharedPreferences sharedPreferences;
    public String barcode = null;
    HttpURLConnection client;
    private List<String> stringList;
    List<String> list = new ArrayList<>();
    BarcodeListAdapter adapter;
    ListView lv;

    String urlLogOut = "https://scanmandev.billetten.dk/login/logout";
    String urlLogin = "https://scanmandev.billetten.dk/login";
    String urlBarcode = "https://scanmandev.billetten.dk/service";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setItemIconTintList(null);

        initialize();
        isLoggedIn();

        stringList = new ArrayList<String>(Arrays.asList(barcode));

        if (list != null) {
            stringList.addAll(list);
        }

        adapter = new BarcodeListAdapter(Main2Activity.this);

        lv = (ListView)this.findViewById(R.id.barcode_list);

        if (lv != null) {
            lv.setAdapter(adapter);
        }


        txtScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasPlayServices()) {
                    new IntentIntegrator(Main2Activity.this)
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                            .initiateScan();
                } else {
                    dispatchTakePictureIntent();
                }
            }
        });

        barcodeEditTxt.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    // Perform action on key press
                    //loginScan(barcodeEditTxt.getText().toString())

                    barcode = barcodeEditTxt.getText().toString();
                    InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    barcodeEditTxt.getText().clear();

                    if (isLoggedIn == true) {
                        barcodeScan();
                    } else {
                        loginScan();
                    }

                    return true;
                }
                return false;
            }
        });

        barcodeEditTxt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                barcodeEditTxt.setHint("");
                barcodeEditTxt.setCursorVisible(true);
                return false;
            }
        });
    }

    public void initialize() {
        txtScan = findViewById(R.id.txt_scan);
        barcodeEditTxt = findViewById(R.id.edittxt_for_barcode);
        txtScanPilot = findViewById(R.id.scanpilot_txt);
    }

    public void isLoggedIn() {

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
            if (isLoggedIn == true) {
                barcodeScan();
            } else {
                loginScan();
            }
        }
    }

    private void dispatchTakePictureIntent() {
        Intent intent = new Intent(this, BarcodeCaptureActivity.class);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
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

    public boolean hasPlayServices() {
        return GoogleApiAvailability
                .getInstance()
                .isGooglePlayServicesAvailable(getApplicationContext()) == ConnectionResult.SUCCESS;
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_find_ordre) {
            // Handle the camera action
        } else if (id == R.id.nav_scanning) {

        } else if (id == R.id.nav_historik) {

        } else if (id == R.id.nav_vejledning) {

        } else if (id == R.id.nav_indstillinger) {

        } else if (id == R.id.nav_logout) {
            logOutCalled();
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void loginScan() {

        try {

            TextView outPutView = findViewById(R.id.postOutput);

            URL url = new URL(urlLogin.toString());
            client = (HttpsURLConnection) url.openConnection();

            String postString = "scanner=" + barcode;

            client.setRequestMethod("POST");
            client.setUseCaches(false);
            client.setRequestProperty("Connection", "Keep-Alive");
            client.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            client.setDoInput(true);
            client.setDoOutput(true);



            int SDK_INT = android.os.Build.VERSION.SDK_INT;
            if (SDK_INT > 8)
            {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                        .permitAll().build();
                StrictMode.setThreadPolicy(policy);
                //your codes here

                client.connect();

                DataOutputStream outputPost = new DataOutputStream(client.getOutputStream());
                outputPost.writeBytes(postString);
                outputPost.flush();
                outputPost.close();

            }



            int response = client.getResponseCode();

            String output = "Request URL " + url;
            output += System.getProperty("line.separator") + "Request Parameters" + postString;
            output += System.getProperty("line.separator") + "Response Code" + response;

            BufferedReader br;

            if (200 <= client.getResponseCode() && client.getResponseCode() <= 299) {
                br = new BufferedReader(new InputStreamReader(client.getInputStream()));
            } else {
                br = new BufferedReader(new InputStreamReader(client.getInputStream()));
            }

            String line = "";
            StringBuilder responseOutput = new StringBuilder();

            while ((line = br.readLine()) != null) {
                responseOutput.append(line);
            }
            br.close();

            output += System.getProperty("line.separator") + responseOutput.toString();

            //outPutView.setText(output);

            switch (response)
            {
                case 200:
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoggedIn = true;
                            loginSucceed(barcode);
                        }
                    });
                    break;
                case 401:
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            barcodeRejected();
                        }
                    });
                    break;
                default:
                    break;
            }

        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void barcodeScan() {
        try {

            URL url = new URL(urlBarcode.toString());

            TextView outPutView = findViewById(R.id.postOutput);
            outPutView.setText("");

            client = (HttpsURLConnection) url.openConnection();


            String postString = "action=scan&barcode=" + barcode;
            client.setDoInput(true);
            client.setDoOutput(true);
            client.setUseCaches(true);
            client.setRequestMethod("POST");
            client.setRequestProperty("X-Requested-With", "XMLHttpRequest");



            int SDK_INT = android.os.Build.VERSION.SDK_INT;
            if (SDK_INT > 8)
            {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                        .permitAll().build();
                StrictMode.setThreadPolicy(policy);
                //your codes here
                client.connect();

                DataOutputStream outputPost = new DataOutputStream(client.getOutputStream());
                outputPost.writeBytes(postString);
                outputPost.flush();
                outputPost.close();

            }


            int response = client.getResponseCode();

            String output = "Request URL " + url;
            output += System.getProperty("line.separator") + "Request Parameters " + postString;
            output += System.getProperty("line.separator") + "Response Code " + response;

            BufferedReader br;

            if (200 <= client.getResponseCode() && client.getResponseCode() <= 299) {
                br = new BufferedReader(new InputStreamReader(client.getInputStream()));
            } else {
                br = new BufferedReader(new InputStreamReader(client.getErrorStream()));
            }


            String line = "";
            StringBuilder responseOutput = new StringBuilder();

            while ((line = br.readLine()) != null) {
                responseOutput.append(line);
            }
            br.close();

            output += System.getProperty("line.separator") + responseOutput.toString();

            //outPutView.setText(output);

            JSONObject jsonObject = new JSONObject(String.valueOf(responseOutput));
            int status = jsonObject.getInt("status");
            final String message = jsonObject.getString("message");

            if (status == 1) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        barcodeAccepted();
                    }
                });
            } else if (status == 2) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                    }
                });
            } else if (status == 3) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                    }
                });
            } else  if (status == 4) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                    }
                });
            } else if (status == 5) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        barcodeRejected();
                    }
                });
            }

        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void loginSucceed(String barcode) {
        sharedPreferences.edit().putBoolean("isLoggedIn", true).apply();
        txtScan.setBackground(getResources().getDrawable(R.drawable.mainscreen_accepted_login_color));
        txtScan.setText(getResources().getString(R.string.login_accepted));
        txtScanPilot.setVisibility(View.GONE);

        adapter.addBarcode(barcode, 0);
    }

    public void barcodeAccepted() {
        txtScan.setBackground(getResources().getDrawable(R.drawable.mainscreen_accepted_login_color));
        txtScan.setText(getResources().getString(R.string.login_accepted));

        adapter.addBarcode(barcode, 1);
    }

    public void barcodeRejected() {
        txtScan.setBackground(getResources().getDrawable(R.drawable.mainscreen_rejected_color));
        txtScan.setText(getResources().getString(R.string.rejected));

        adapter.addBarcode(barcode, 2);
    }

    public void logOutCalled() {
        isLoggedIn = false;
        HttpURLConnection urlConnection = null;
        URL url;
        InputStream inStream = null;
        try {
            url = new URL(urlLogOut.toString());
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);

            int SDK_INT = android.os.Build.VERSION.SDK_INT;
            if (SDK_INT > 8)
            {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                        .permitAll().build();
                StrictMode.setThreadPolicy(policy);
                urlConnection.connect();
            }

            inStream = urlConnection.getInputStream();
            BufferedReader bReader = new BufferedReader(new InputStreamReader(inStream));
            String temp, response = null;
            while ((temp = bReader.readLine()) != null) {
                response += temp;
            }
            //object = (JSONObject) new JSONTokener(response).nextValue();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inStream != null) {
                try {
                    // this will close the bReader as well
                    inStream.close();
                } catch (IOException ignored) {
                }
            }
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}
