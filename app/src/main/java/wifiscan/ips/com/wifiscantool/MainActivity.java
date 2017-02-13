package wifiscan.ips.com.wifiscantool;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.widget.ToggleButton;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback, View.OnClickListener {

    WifiManager mWifiManager;
    LocationManager mLocationManager;
    Location mLocation;
    ListView lv;
    TextView textStatus, gpsStatus;
    Button buttonScan, gpsButton, moveDB;
    ToggleButton toggleButton;
    EditText editText;

    int size = 0;
    List<ScanResult> results;
    long TIME = 2000;
    float DIS = 2;
    double longitude = 0;
    double latitude = 0;
    double gps_la = 0;
    double gps_long = 0;

    ProgressDialog pd;

    private GoogleMap mMap;
    MarkerOptions marker, pin;

    String ITEM_KEY = "key";
    ArrayList<HashMap<String, String>> arraylist = new ArrayList<HashMap<String, String>>();
    SimpleAdapter adapter;

    final String TAG = "WifiScanToolLog";
    final String WEATHER = "http://api.wunderground.com/api/5bdfe633506ae4cd/conditions/q/";
    String mWeatherUrl = "";
    private float zoom = 19.5f;

    private DBHelper DH = null;
    String mTime;

    float temp;
    String humidiry;
    String wind_kph;
    String wind_dir;
    String wind_degrees;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textStatus = (TextView) findViewById(R.id.textStatus);
        buttonScan = (Button) findViewById(R.id.buttonScan);
        moveDB = (Button) findViewById(R.id.moveDB);
        gpsStatus = (TextView) findViewById(R.id.gpsStatus);
        buttonScan.setOnClickListener(this);
        moveDB.setOnClickListener(this);
        lv = (ListView) findViewById(R.id.list);
        toggleButton = (ToggleButton) findViewById(R.id.toggleButton);
        gpsButton = (Button) findViewById(R.id.button_gps);
        gpsButton.setOnClickListener(this);
        editText = (EditText) findViewById(R.id.editText);
        editText.setOnClickListener(this);

        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                toggleButton.setChecked(isChecked);
                if (isChecked) {
                    mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                } else {
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                }
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    0x12345);
            //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method
            Log.d(TAG, "request permission");
        } else {
            Log.d(TAG, "permission already granted");
        }

        if (mWifiManager.isWifiEnabled() == false) {
            Toast.makeText(getApplicationContext(), "wifi is disabled..making it enabled", Toast.LENGTH_LONG).show();
            mWifiManager.setWifiEnabled(true);
        }
        if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) == false) {
            Toast.makeText(getApplicationContext(), "gps is disabled..", Toast.LENGTH_LONG).show();
        }

        this.adapter = new SimpleAdapter(this, arraylist, R.layout.row, new String[]{ITEM_KEY}, new int[]{R.id.list_value});
        lv.setAdapter(this.adapter);

        registerReceiver(mWifiScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        updateLocation();
        editText.setText("(" + latitude + ", " + longitude + ")");
        gps_la = latitude;
        gps_long = longitude;

        DH = new DBHelper(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DH.close();
    }

    private final BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) && !buttonScan.isEnabled()) {
                arraylist.clear();
                size = 0;

                String ssid;
                String bssid;
                int freq;
                int level;
                String content;

                addWifiLocationDB(mTime, latitude, longitude, gps_la, gps_long, temp, humidiry,
                        wind_kph, wind_dir, wind_degrees);
                results = mWifiManager.getScanResults();
                size = results.size();
                try {
                    updateLocation();

                    Log.d(TAG, "-- List -- " + size);
                    size = size - 1;
                    while (size >= 0) {
                        HashMap<String, String> item = new HashMap<String, String>();
                        ScanResult scanItem = results.get(size);
                        item.put(ITEM_KEY, scanItem.SSID + " /// " + scanItem.BSSID + " /// " + scanItem.centerFreq0 +
                                " /// " + scanItem.frequency + " /// " + scanItem.level
                                + " /// " + scanItem.capabilities + " /// " + scanItem.is80211mcResponder()
                                + " /// " + scanItem.toString());
                        Log.d(TAG, scanItem.SSID + " " + scanItem.level);

                        ssid = scanItem.SSID;
                        bssid = scanItem.BSSID;
                        freq = scanItem.frequency;
                        level = scanItem.level;
                        content = scanItem.toString();
                        arraylist.add(item);
                        size--;
                        adapter.notifyDataSetChanged();

                        addWifiFingerprintDB(mTime, ssid, bssid, freq, level, content);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "error!! " + e.toString());
                }
                //buttonScan.setClickable(true);
            }
        }
    };

    private void updateLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            longitude = mLocation.getLongitude();
            latitude = mLocation.getLatitude();
            gpsStatus.setText("GPS: (" + latitude + ", " + longitude + ")");
            Log.d(TAG, "gps: " + longitude + ", " + latitude);
            mWeatherUrl = WEATHER + latitude + "," + longitude + ".json";
            new JsonTask().execute(mWeatherUrl);
        }
    }

    public void onClick(View view) {
        arraylist.clear();
        size = 0;
        int vid = view.getId();

        if (vid == buttonScan.getId()) {
            mTime = getTime();
            buttonScan.setEnabled(false);
            mWifiManager.startScan();
        } else if (vid == gpsButton.getId()){
            updateLocation();
            LatLng now = new LatLng(latitude, longitude);
            //mMap.moveCamera(CameraUpdateFactory.newLatLng(now));
            //mMap.animateCamera(CameraUpdateFactory.zoomTo(zoom));
            marker.position(now);
        } else if (vid == editText.getId()) {
            /*
            LatLng now = new LatLng(latitude, longitude);
            editText.setText("(" + latitude + ", " + longitude + ")");
            pin.position(now);
            */
        } else if (vid == moveDB.getId()) {
            updateDBtoSD();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {

            @Override
            public void onMarkerDragStart(Marker marker) {
            }

            @Override
            public void onMarkerDrag(Marker marker) {
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                LatLng temp = marker.getPosition();
                editText.setText("(" + temp.latitude + ", " + temp.longitude + ")");
                gps_la = temp.latitude;
                gps_long = temp.longitude;
            }
        });
        LatLng now = new LatLng(latitude, longitude);
        marker = new MarkerOptions()
                .position(now)
                .title("GPS point");
        pin = new MarkerOptions()
                .position(now)
                .title("Select point")
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
        mMap.addMarker(marker);
        mMap.addMarker(pin);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(now));
        // zoom range 2.0~21.0
        mMap.animateCamera(CameraUpdateFactory.zoomTo(zoom));
        mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
    }


    private class JsonTask extends AsyncTask<String, String, String> {

        protected void onPreExecute() {
            super.onPreExecute();

            pd = new ProgressDialog(MainActivity.this);
            pd.setMessage("Please wait");
            pd.setCancelable(false);
            pd.show();
        }

        protected String doInBackground(String... params) {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(params[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                InputStream stream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(stream,"utf-8"), 16);
                StringBuffer buffer_all = new StringBuffer();
                String line = "";

                while ((line = reader.readLine()) != null) {
                    buffer_all.append(line + "\n");
                }

                JSONObject jsonObj = new JSONObject(buffer_all.toString());
                JSONObject current = jsonObj.getJSONObject("current_observation");
                StringBuffer buffer = new StringBuffer();
                buffer.append(current.getString("temp_c"));
                buffer.append(",");
                buffer.append(current.getString("relative_humidity"));
                buffer.append(",");
                buffer.append(current.getString("wind_kph"));
                buffer.append(",");
                buffer.append(current.getString("wind_dir"));
                buffer.append(",");
                buffer.append(current.getString("wind_degrees"));

                temp = Float.parseFloat(current.getString("temp_c"));
                humidiry = current.getString("relative_humidity");
                wind_kph = current.getString("wind_kph");
                wind_dir = current.getString("wind_dir");
                wind_degrees = current.getString("wind_degrees");

                return buffer.toString();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (pd.isShowing()) {
                pd.dismiss();
            }
            textStatus.setText("Time: " + getTime() + " ||| Weather condition: " + result);
            buttonScan.setEnabled(true);

        }
    }

    private String getTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String currentDateandTime = sdf.format(new Date());
        return currentDateandTime;
    }

    private void addWifiLocationDB(String time, double latitude, double longitude, double gps_lat,
                                   double gps_long, float temp, String humidity, String wind_kph,
                                   String wind_dir, String wind_degree) {
        SQLiteDatabase db = DH.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("_TIME", time);
        values.put("_LATITUDE", latitude + "");
        values.put("_LONGITUDE", longitude + "");
        values.put("_GPSLAT", gps_lat + "");
        values.put("_GPSLONG", gps_long + "");
        values.put("_TEMP", temp);
        values.put("_HUMIDITY", humidity);
        values.put("_WINDKPH", wind_kph);
        values.put("_WINDDIR", wind_dir);
        values.put("_WINDDEGREE", wind_degree);
        db.insert(DBHelper._TableLocation, null, values);
    }

    private void addWifiFingerprintDB(String time, String ssid, String bssid,
                                      int freq, int level, String content) {
        SQLiteDatabase db = DH.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("_TIME", time);
        values.put("_SSID", ssid);
        values.put("_BSSID", bssid);
        values.put("_FREQ", freq);
        values.put("_LEVEL", level);
        values.put("_CONTENT", content);
        db.insert(DBHelper._TableWifi, null, values);
    }

    private void updateDBtoSD() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    0x12345);
            //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method
            Log.d(TAG, "request permission");
        } else {
            Log.d(TAG, "permission already granted");
        }

        File f=new File("/data/data/wifiscan.ips.com.wifiscantool/databases/WifInfo.db");
        FileInputStream fis=null;
        FileOutputStream fos=null;
        try
        {
            fis=new FileInputStream(f);
            fos=new FileOutputStream("/mnt/sdcard/wifi_db_dump.db");
            while(true)
            {
                int i=fis.read();
                if(i!=-1)
                {fos.write(i);}
                else
                {break;}
            }
            fos.flush();
            Toast.makeText(this, "DB dump OK", Toast.LENGTH_LONG).show();
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Toast.makeText(this, "DB dump ERROR", Toast.LENGTH_LONG).show();
        }
        finally
        {
            try
            {
                fos.close();
                fis.close();
            }
            catch(IOException ioe)
            {}
        }
    }
}