package com.example.gps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final String TAG = "2022";
    private static final int MY_PERMISSION_ACCESS_FINE_LOCATION = 1;
    private static final int MY_PERMISSION_ACCESS_COARSE_LOCATION = 2;
    private static final int MY_PERMISSION_WRITE_EXTERNAL_STORAGE = 3;
    private TextView bestprovider;
    private TextView longitude;
    private TextView latitude;
    private TextView archivaldata;
    private LocationManager locationManager;
    private Criteria criteria;
    private Location location;
    private String bp;
    private int amount;

    private MapView osm;
    private IMapController mapController;

    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView text_network;
    private TextView text_gps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        swipeRefreshLayout = findViewById(R.id.refreshlayout);
        text_network = findViewById(R.id.text_network);
        text_gps = findViewById(R.id.text_gps);
        bestprovider = findViewById(R.id.bestprovider);
        longitude = findViewById(R.id.longitude);
        latitude = findViewById(R.id.latitude);
        archivaldata = findViewById(R.id.archival_data);
        osm = findViewById(R.id.osm);

        Context context = getApplicationContext();
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context));

        osm.setTileSource(TileSourceFactory.MAPNIK);
        osm.setBuiltInZoomControls(true);
        osm.setMultiTouchControls(true);

        mapController = (IMapController) osm.getController();
        mapController.setZoom(12);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            swipeRefreshLayout.setRefreshing(false);
            boolean connection = isNetworkAvailable();
            if (connection) {
                text_network.setText("Internet connect");
                text_network.setTextColor(Color.GREEN);
            } else {
                text_network.setText("no internet");
                text_network.setTextColor(Color.RED);
            }
        });

        swipeRefreshLayout.setColorSchemeColors(Color.YELLOW);

        criteria = new Criteria();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        bp = locationManager.getBestProvider(criteria, true);

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            text_gps.setText("GPS: searching...");
            text_gps.setTextColor(Color.YELLOW);
        } else {
            text_gps.setText("GPS: not connected");
            text_gps.setTextColor(Color.RED);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_ACCESS_FINE_LOCATION);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSION_ACCESS_COARSE_LOCATION);
            return;
        }
        locationManager.requestLocationUpdates(bp, 500, 0.5f, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_send_sms) {
            if (location != null) {
                String message = location.getLatitude() + ", " + location.getLongitude();
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"));
                intent.putExtra("sms_body", message);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Lokalizacja nie jest jeszcze dostępna", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (itemId == R.id.menu_save_map) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSION_WRITE_EXTERNAL_STORAGE);
                } else {
                    saveMapScreenshot();
                }
            } else {
                saveMapScreenshot();
            }
            return true;
        } else if (itemId == R.id.menu_share_results) {
            String results = archivaldata.getText().toString();
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, results);
            startActivity(Intent.createChooser(intent, "Udostępnij wyniki"));
            return true;
        } else if (itemId == R.id.menu_weather) {
            Toast.makeText(this, "Pogoda", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveMapScreenshot() {
        osm.setDrawingCacheEnabled(true);
        Bitmap bitmap = osm.getDrawingCache();

        String fileName = "map_screenshot_" + System.currentTimeMillis() + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
        }

        ContentResolver resolver = getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        try {
            if (uri != null) {
                try (OutputStream out = resolver.openOutputStream(uri)) {
                    if (out != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        Toast.makeText(this, "Mapa zapisana w Galerii", Toast.LENGTH_LONG).show();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Błąd podczas zapisu mapy", Toast.LENGTH_SHORT).show();
        } finally {
            osm.setDrawingCacheEnabled(false);
        }
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSION_ACCESS_FINE_LOCATION: {
                if (permissions[0].equalsIgnoreCase(Manifest.permission.ACCESS_FINE_LOCATION)
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("GPSA", "uprawnien" + requestCode + " " + permissions[0] + grantResults[0]);
                    Log.d(TAG, "Permissions ACCESS_FINE_LOCATION was granted");
                    Toast.makeText(this, "Permissions ACCESS_FINE_LOCATION was granted", Toast.LENGTH_SHORT).show();
                    this.recreate();
                } else {
                    Log.d(TAG, "Permissions ACCESS_FINE_LOCATION denied");
                    Toast.makeText(this, "Permissions ACCESS_FINE_LOCATION denied", Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case MY_PERMISSION_ACCESS_COARSE_LOCATION: {
                if (permissions[0].equalsIgnoreCase(Manifest.permission.ACCESS_COARSE_LOCATION)
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("GPSA", "uprawnienia" + requestCode + " " + permissions[0] + grantResults[0]);
                    Log.d(TAG, "Permissions ACCESS_COARSE_LOCATION was granted");
                    Toast.makeText(this, "Permissions ACCESS_COARSE_LOCATION was granted", Toast.LENGTH_SHORT).show();
                    this.recreate();
                } else {
                    Log.d(TAG, "Permissions ACCESS_COARSE_LOCATION denied");
                    Toast.makeText(this, "Permissions ACCESS_COARSE_LOCATION denied", Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case MY_PERMISSION_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permissions WRITE_EXTERNAL_STORAGE was granted");
                    Toast.makeText(this, "Uprawnienie do zapisu przyznane", Toast.LENGTH_SHORT).show();
                    saveMapScreenshot();
                } else {
                    Log.d(TAG, "Permissions WRITE_EXTERNAL_STORAGE denied");
                    Toast.makeText(this, "Odmówiono uprawnienia do zapisu", Toast.LENGTH_SHORT).show();
                }
                break;
            }
            default: Log.d(TAG, "Another permissions");
        }
    }

    @SuppressLint({"SetTextI18n", "MissingPermission"})
    @Override
    public void onLocationChanged(@NonNull Location location) {
        text_gps.setText("GPS: connected");
        text_gps.setTextColor(Color.GREEN);

        this.location = location;
        bp = locationManager.getBestProvider(criteria, true);

        bestprovider.setText("Best provider: " + bp);
        longitude.setText("Longitude: " + this.location.getLongitude());
        latitude.setText("Latitude: " + this.location.getLatitude());
        archivaldata.setText(archivaldata.getText() + " " + this.location.getLongitude() + " : " + this.location.getLatitude() + "\n");
        amount += 1;
        Log.d("GPSA", "msg: " + amount + " pomiar: " + bp + " " + this.location.getLongitude() + " " + this.location.getLatitude());
        GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        mapController.setCenter(geoPoint);
        mapController.animateTo(geoPoint);
        addMarkerToMap(geoPoint);
    }

    public void addMarkerToMap(GeoPoint center) {
        Marker marker = new Marker(osm);
        marker.setPosition(center);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setIcon(getResources().getDrawable(R.drawable.ic_marker));
        osm.getOverlays().clear();
        osm.getOverlays().add(marker);
        osm.invalidate();
        marker.setTitle("My position");
    }
}
