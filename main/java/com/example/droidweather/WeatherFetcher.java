package com.example.droidweather;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class WeatherFetcher {

    public static class HourlyForecast {
        public final String time;
        public final double temp;
        public final int weatherCode;

        public HourlyForecast(String time, double temp, int weatherCode) {
            this.time = time;
            this.temp = temp;
            this.weatherCode = weatherCode;
        }
    }

    public static class DailyForecast {
        public final String date;
        public final double maxTemp;
        public final double minTemp;
        public final int weatherCode;

        public DailyForecast(String date, double maxTemp, double minTemp, int weatherCode) {
            this.date = date;
            this.maxTemp = maxTemp;
            this.minTemp = minTemp;
            this.weatherCode = weatherCode;
        }
    }

    public static class FullWeatherData {
        public final double currentTemp;
        public final int currentWeatherCode;
        public final List<HourlyForecast> hourlyForecasts;
        public final List<DailyForecast> dailyForecasts;

        public FullWeatherData(double currentTemp, int currentWeatherCode,
                               List<HourlyForecast> hourlyForecasts,
                               List<DailyForecast> dailyForecasts) {
            this.currentTemp = currentTemp;
            this.currentWeatherCode = currentWeatherCode;
            this.hourlyForecasts = hourlyForecasts;
            this.dailyForecasts = dailyForecasts;
        }
    }

    public interface WeatherDataCallback {
        void onSuccess(FullWeatherData data);
        void onError(String errorMessage);
    }

    public interface LocationCallback {
        void onSuccess(String name, String country, double latitude, double longitude);
        void onError(String errorMessage);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static String getWeatherConditionText(int code) {
        switch (code) {
            case 0: return "Clear Sky";
            case 1: return "Mainly Clear";
            case 2: return "Partly Cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Foggy";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing Drizzle";
            case 61: return "Slight Rain";
            case 63: return "Rain";
            case 65: return "Heavy Rain";
            case 66: case 67: return "Freezing Rain";
            case 71: return "Slight Snow";
            case 73: return "Snow";
            case 75: return "Heavy Snow";
            case 77: return "Snow Grains";
            case 80: case 81: case 82: return "Rain Showers";
            case 85: case 86: return "Snow Showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm with Hail";
            default: return "Unknown";
        }
    }

    @android.annotation.SuppressLint({"TrustAllX509TrustManager", "BadHostnameVerifier"})
    private static void trustAllCertificates(HttpsURLConnection connection) {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            connection.setSSLSocketFactory(sc.getSocketFactory());
            connection.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void searchLocation(final String cityName, final LocationCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String urlString = "https://geocoding-api.open-meteo.com/v1/search?name="
                            + URLEncoder.encode(cityName, "UTF-8")
                            + "&count=1&language=en&format=json";

                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    if (connection instanceof HttpsURLConnection) {
                        trustAllCertificates((HttpsURLConnection) connection);
                    }
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;

                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        JSONObject jsonResponse = new JSONObject(response.toString());
                        if (jsonResponse.has("results")) {
                            JSONArray results = jsonResponse.getJSONArray("results");
                            if (results.length() > 0) {
                                JSONObject result = results.getJSONObject(0);
                                final String name = result.optString("name", cityName);
                                final String country = result.optString("country", "");
                                final double lat = result.getDouble("latitude");
                                final double lon = result.getDouble("longitude");

                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onSuccess(name, country, lat, lon);
                                    }
                                });
                                return;
                            }
                        }
                        notifyLocationError("No location found for '" + cityName + "'", callback);

                    } else {
                        notifyLocationError("Server Code: " + responseCode, callback);
                    }

                } catch (final Exception e) {
                    notifyLocationError("Error: " + e.getLocalizedMessage(), callback);
                }
            }
        });
    }

    private static void notifyLocationError(final String message, final LocationCallback callback) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(message);
            }
        });
    }

    public static void fetchFullWeatherData(final double latitude, final double longitude, final WeatherDataCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String urlString = "https://api.open-meteo.com/v1/forecast?latitude="
                            + latitude + "&longitude=" + longitude
                            + "&current_weather=true"
                            + "&hourly=temperature_2m,weathercode"
                            + "&daily=weathercode,temperature_2m_max,temperature_2m_min"
                            + "&timezone=auto";

                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    if (connection instanceof HttpsURLConnection) {
                        trustAllCertificates((HttpsURLConnection) connection);
                    }
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;

                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        JSONObject jsonResponse = new JSONObject(response.toString());

                        // 1. Current weather
                        JSONObject currentWeather = jsonResponse.getJSONObject("current_weather");
                        final double currentTemp = currentWeather.getDouble("temperature");
                        final int currentCode = currentWeather.getInt("weathercode");
                        String currentTime = currentWeather.optString("time", "");

                        // 2. Hourly forecast
                        List<HourlyForecast> hourlyList = new ArrayList<>();
                        if (jsonResponse.has("hourly")) {
                            JSONObject hourlyObj = jsonResponse.getJSONObject("hourly");
                            JSONArray hourlyTimeArr = hourlyObj.getJSONArray("time");
                            JSONArray hourlyTempArr = hourlyObj.getJSONArray("temperature_2m");
                            JSONArray hourlyCodeArr = hourlyObj.getJSONArray("weathercode");

                            int startIndex = 0;
                            for (int i = 0; i < hourlyTimeArr.length(); i++) {
                                if (hourlyTimeArr.getString(i).compareTo(currentTime) >= 0) {
                                    startIndex = i;
                                    break;
                                }
                            }

                            for (int i = startIndex; i < Math.min(startIndex + 24, hourlyTimeArr.length()); i++) {
                                String rawTime = hourlyTimeArr.getString(i);
                                double temp = hourlyTempArr.getDouble(i);
                                int code = hourlyCodeArr.getInt(i);
                                hourlyList.add(new HourlyForecast(rawTime, temp, code));
                            }
                        }

                        // 3. Daily forecast (7 days)
                        List<DailyForecast> dailyList = new ArrayList<>();
                        if (jsonResponse.has("daily")) {
                            JSONObject dailyObj = jsonResponse.getJSONObject("daily");
                            JSONArray dailyTimeArr = dailyObj.getJSONArray("time");
                            JSONArray dailyCodeArr = dailyObj.getJSONArray("weathercode");
                            JSONArray dailyMaxArr = dailyObj.getJSONArray("temperature_2m_max");
                            JSONArray dailyMinArr = dailyObj.getJSONArray("temperature_2m_min");

                            for (int i = 0; i < Math.min(7, dailyTimeArr.length()); i++) {
                                String dateStr = dailyTimeArr.getString(i);
                                double max = dailyMaxArr.getDouble(i);
                                double min = dailyMinArr.getDouble(i);
                                int code = dailyCodeArr.getInt(i);
                                dailyList.add(new DailyForecast(dateStr, max, min, code));
                            }
                        }

                        final FullWeatherData data = new FullWeatherData(currentTemp, currentCode, hourlyList, dailyList);

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onSuccess(data);
                            }
                        });

                    } else {
                        notifyWeatherError("Server Code: " + responseCode, callback);
                    }

                } catch (final Exception e) {
                    notifyWeatherError("Error: " + e.getLocalizedMessage(), callback);
                }
            }
        });
    }

    private static void notifyWeatherError(final String message, final WeatherDataCallback callback) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(message);
            }
        });
    }
}
