package com.example.droidweather;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.security.ProviderInstaller;
import org.conscrypt.Conscrypt;
import org.json.JSONArray;
import org.json.JSONObject;

import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    public static class SavedLocation {
        public final String name;
        public final double latitude;
        public final double longitude;

        public SavedLocation(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private DrawerLayout drawerLayout;
    private ImageButton btnOpenDrawer;
    private TextView textCity;
    private TextView textTemperature;
    private TextView textCondition;
    private LinearLayout containerHourly;
    private LinearLayout containerDaily;
    private EditText editAddLocation;
    private Button btnAddLocation;
    private LinearLayout containerSavedLocations;

    private List<SavedLocation> savedLocations = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Ensure updated security providers (Conscrypt / ProviderInstaller) are active
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
        } catch (Throwable ignored) {
        }
        try {
            ProviderInstaller.installIfNeeded(getApplicationContext());
        } catch (Throwable ignored) {
        }

        drawerLayout = findViewById(R.id.drawer_layout);
        btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        textCity = findViewById(R.id.text_city);
        textTemperature = findViewById(R.id.text_temperature);
        textCondition = findViewById(R.id.text_condition);
        containerHourly = findViewById(R.id.container_hourly);
        containerDaily = findViewById(R.id.container_daily);
        editAddLocation = findViewById(R.id.edit_add_location);
        btnAddLocation = findViewById(R.id.btn_add_location);
        containerSavedLocations = findViewById(R.id.container_saved_locations);

        btnOpenDrawer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        btnAddLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performAddLocation();
            }
        });

        editAddLocation.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH ||
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    performAddLocation();
                    return true;
                }
                return false;
            }
        });

        savedLocations = loadSavedLocations();
        populateDrawerLocations();

        if (!savedLocations.isEmpty()) {
            selectLocation(savedLocations.get(0));
        }
    }

    private void performAddLocation() {
        String query = editAddLocation.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a city name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (savedLocations.size() >= 5) {
            Toast.makeText(this, "Maximum 5 saved locations reached. Delete one first.", Toast.LENGTH_LONG).show();
            return;
        }

        hideKeyboard();
        Toast.makeText(this, "Searching location...", Toast.LENGTH_SHORT).show();

        WeatherFetcher.searchLocation(query, new WeatherFetcher.LocationCallback() {
            @Override
            public void onSuccess(String name, String country, double latitude, double longitude) {
                String displayName = country.isEmpty() ? name : name + ", " + country;
                SavedLocation newLoc = new SavedLocation(displayName, latitude, longitude);
                savedLocations.add(newLoc);
                saveLocationsToPrefs(savedLocations);
                populateDrawerLocations();
                editAddLocation.setText("");

                selectLocation(newLoc);
                drawerLayout.closeDrawer(GravityCompat.START);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void selectLocation(SavedLocation loc) {
        textCity.setText(loc.name);
        textTemperature.setText("--°C");
        textCondition.setText("Loading...");
        containerHourly.removeAllViews();
        containerDaily.removeAllViews();

        WeatherFetcher.fetchFullWeatherData(loc.latitude, loc.longitude, new WeatherFetcher.WeatherDataCallback() {
            @Override
            public void onSuccess(WeatherFetcher.FullWeatherData data) {
                textTemperature.setText((int) Math.round(data.currentTemp) + "°C");
                textCondition.setText(WeatherFetcher.getWeatherConditionText(data.currentWeatherCode));

                populateHourlyForecast(data.hourlyForecasts);
                populateDailyForecast(data.dailyForecasts);
            }

            @Override
            public void onError(String errorMessage) {
                textCondition.setText("Error loading weather");
                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void populateHourlyForecast(List<WeatherFetcher.HourlyForecast> hourlyList) {
        containerHourly.removeAllViews();
        for (WeatherFetcher.HourlyForecast item : hourlyList) {
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setGravity(Gravity.CENTER);
            itemLayout.setPadding(24, 16, 24, 16);
            itemLayout.setBackgroundColor(0xFF222222);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 12, 0);
            itemLayout.setLayoutParams(lp);

            TextView tvTime = new TextView(this);
            tvTime.setText(formatHourTime(item.time));
            tvTime.setTextColor(0xFF888888);
            tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

            TextView tvCond = new TextView(this);
            tvCond.setText(WeatherFetcher.getWeatherConditionText(item.weatherCode));
            tvCond.setTextColor(0xFF33B5E5);
            tvCond.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvCond.setPadding(0, 4, 0, 4);

            TextView tvTemp = new TextView(this);
            tvTemp.setText((int) Math.round(item.temp) + "°C");
            tvTemp.setTextColor(0xFFFFFFFF);
            tvTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

            itemLayout.addView(tvTime);
            itemLayout.addView(tvCond);
            itemLayout.addView(tvTemp);
            containerHourly.addView(itemLayout);
        }
    }

    private void populateDailyForecast(List<WeatherFetcher.DailyForecast> dailyList) {
        containerDaily.removeAllViews();
        for (WeatherFetcher.DailyForecast item : dailyList) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(20, 20, 20, 20);
            row.setBackgroundColor(0xFF222222);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, 12);
            row.setLayoutParams(lp);

            TextView tvDay = new TextView(this);
            tvDay.setText(formatDayDate(item.date));
            tvDay.setTextColor(0xFFFFFFFF);
            tvDay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            LinearLayout.LayoutParams lpDay = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tvDay.setLayoutParams(lpDay);

            TextView tvCond = new TextView(this);
            tvCond.setText(WeatherFetcher.getWeatherConditionText(item.weatherCode));
            tvCond.setTextColor(0xFFAAAAAA);
            tvCond.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvCond.setPadding(12, 0, 12, 0);

            TextView tvTemps = new TextView(this);
            String tempStr = (int) Math.round(item.minTemp) + "° / " + (int) Math.round(item.maxTemp) + "°C";
            tvTemps.setText(tempStr);
            tvTemps.setTextColor(0xFF33B5E5);
            tvTemps.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);

            row.addView(tvDay);
            row.addView(tvCond);
            row.addView(tvTemps);
            containerDaily.addView(row);
        }
    }

    private String formatHourTime(String isoTime) {
        try {
            if (isoTime.contains("T")) {
                String timePart = isoTime.split("T")[1]; // e.g. "14:00"
                return timePart;
            }
        } catch (Exception ignored) {
        }
        return isoTime;
    }

    private String formatDayDate(String dateStr) {
        try {
            SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat outFormat = new SimpleDateFormat("EEE, MMM d", Locale.US);
            Date date = inFormat.parse(dateStr);
            if (date != null) {
                return outFormat.format(date);
            }
        } catch (Exception ignored) {
        }
        return dateStr;
    }

    private void populateDrawerLocations() {
        containerSavedLocations.removeAllViews();
        for (int i = 0; i < savedLocations.size(); i++) {
            final SavedLocation loc = savedLocations.get(i);
            final int index = i;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(20, 24, 20, 24);

            TextView tv = new TextView(this);
            tv.setText(loc.name);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            LinearLayout.LayoutParams lpTv = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tv.setLayoutParams(lpTv);

            TextView btnDelete = new TextView(this);
            btnDelete.setText("✕");
            btnDelete.setTextColor(0xFFFF6666);
            btnDelete.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            btnDelete.setPadding(16, 8, 16, 8);

            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    savedLocations.remove(index);
                    saveLocationsToPrefs(savedLocations);
                    populateDrawerLocations();
                }
            });

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectLocation(loc);
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
            });

            row.addView(tv);
            row.addView(btnDelete);
            containerSavedLocations.addView(row);
        }
    }

    private List<SavedLocation> loadSavedLocations() {
        SharedPreferences prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE);
        String json = prefs.getString("saved_locations", null);
        List<SavedLocation> list = new ArrayList<>();
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    list.add(new SavedLocation(
                            obj.getString("name"),
                            obj.getDouble("latitude"),
                            obj.getDouble("longitude")
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (list.isEmpty()) {
            list.add(new SavedLocation("London", 51.5074, -0.1278));
            list.add(new SavedLocation("New York", 40.7128, -74.0060));
            list.add(new SavedLocation("Tokyo", 35.6895, 139.6917));
            list.add(new SavedLocation("Paris", 48.8566, 2.3522));
            list.add(new SavedLocation("Sydney", -33.8688, 151.2093));
            saveLocationsToPrefs(list);
        }
        return list;
    }

    private void saveLocationsToPrefs(List<SavedLocation> list) {
        try {
            JSONArray arr = new JSONArray();
            for (SavedLocation loc : list) {
                JSONObject obj = new JSONObject();
                obj.put("name", loc.name);
                obj.put("latitude", loc.latitude);
                obj.put("longitude", loc.longitude);
                arr.put(obj);
            }
            getSharedPreferences("weather_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("saved_locations", arr.toString())
                    .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }
}
