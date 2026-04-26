/*
 * Copyright (C) 2021-2026 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.quickspace;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;

import com.android.internal.util.crdroid.OmniJawsClient;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuickspaceController implements OmniJawsClient.OmniJawsObserver {

    private static final String TAG = "Launcher3:QuickspaceController";

    private final List<OnDataListener> mListeners =
        Collections.synchronizedList(new ArrayList<>());
    private final Context mContext;
    private final Map<String, Integer> mConditionMap;
    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherInfo;
    private Drawable mConditionImage;
    private boolean mOmniRegistered = false;

    private final Handler mHandler = MAIN_EXECUTOR.getHandler();

    private final Runnable mOnDataUpdatedRunnable = new Runnable() {
            @Override
            public void run() {
                for (OnDataListener list : new ArrayList<>(mListeners)) {
                    list.onDataUpdated();
                }
            }
        };

    private final Runnable mWeatherRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (mWeatherClient == null) return;
                    mWeatherClient.queryWeather(mContext);
                    mWeatherInfo = mWeatherClient.getWeatherInfo();
                    if (mWeatherInfo != null) {
                        mConditionImage = mWeatherClient.getWeatherConditionImage(mContext, mWeatherInfo.conditionCode);
                    }
                    notifyListeners();
                } catch(Exception e) {
                    // Do nothing
                }
            }
        };

    public interface OnDataListener {
        void onDataUpdated();
    }

    public QuickspaceController(Context context) {
        mContext = context;
        mConditionMap = initializeConditionMap();
    }

    private void addOmniJawsIfEnabled() {
        if (!LauncherPrefs.SHOW_QUICKSPACE_WEATHER.get(mContext)) return;
        if (mWeatherClient == null) mWeatherClient = OmniJawsClient.get();
        if (!mOmniRegistered) {
            mWeatherClient.addObserver(mContext, this);
            mOmniRegistered = true;
        }
        queryAndUpdateWeather();
    }

    public void addListener(OnDataListener listener) {
        if (listener == null) return;
        boolean wasEmpty = mListeners.isEmpty();
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
        if (wasEmpty) {
            addOmniJawsIfEnabled();
        }
        listener.onDataUpdated();
    }

    private void removeOmniIfRegistered() {
        if (mOmniRegistered && mWeatherClient != null) {
            mWeatherClient.removeObserver(mContext, this);
            mOmniRegistered = false;
        }
        mWeatherClient = null;
        mWeatherInfo = null;
        mConditionImage = null;
    }

    public void removeListener(OnDataListener listener) {
        if (listener == null) return;
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            removeOmniIfRegistered();
            mHandler.removeCallbacks(mWeatherRunnable);
            mHandler.removeCallbacks(mOnDataUpdatedRunnable);
        }
    }

    public boolean isWeatherAvailable() {
        if (!LauncherPrefs.SHOW_QUICKSPACE_WEATHER.get(mContext)) return false;
        return mWeatherClient != null && mWeatherClient.isOmniJawsEnabled(mContext);
    }

    public void onPrefsChanged() {
        if (mListeners.isEmpty()) return;
        if (LauncherPrefs.SHOW_QUICKSPACE_WEATHER.get(mContext)) {
            addOmniJawsIfEnabled();
        } else {
            removeOmniIfRegistered();
        }
        notifyListeners();
    }

    public Drawable getWeatherIcon() {
        return mConditionImage;
    }

    public String getWeatherTemp() {
        if (mWeatherInfo == null) return null;

        boolean shouldShowCity = LauncherPrefs.SHOW_QUICKSPACE_WEATHER_CITY.get(mContext);
        boolean showWeatherText = LauncherPrefs.SHOW_QUICKSPACE_WEATHER_TEXT.get(mContext);

        StringBuilder weatherTemp = new StringBuilder();
        if (shouldShowCity) {
            weatherTemp.append(mWeatherInfo.city).append(" ");
        }
        weatherTemp.append(mWeatherInfo.temp)
                   .append(mWeatherInfo.tempUnits);

        if (showWeatherText) {
            weatherTemp.append(" • ").append(getConditionText(mWeatherInfo.condition));
        }

        return weatherTemp.toString();
    }

    private String getConditionText(String input) {
        if (input == null || input.isEmpty()) return "";

        Locale locale = mContext.getResources().getConfiguration().getLocales().get(0);
        boolean isEnglish = locale.getLanguage().toLowerCase(Locale.ROOT).startsWith("en");
        String lowerCaseInput = input.toLowerCase();

        if (!isEnglish) {
            for (Map.Entry<String, Integer> entry : mConditionMap.entrySet()) {
                if (lowerCaseInput.contains(entry.getKey())) {
                    return mContext.getResources().getString(entry.getValue());
                }
            }
        }
        return capitalizeWords(lowerCaseInput);
    }

    private Map<String, Integer> initializeConditionMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("clouds", R.string.quick_event_weather_clouds);
        map.put("rain", R.string.quick_event_weather_rain);
        map.put("clear", R.string.quick_event_weather_clear);
        map.put("storm", R.string.quick_event_weather_storm);
        map.put("snow", R.string.quick_event_weather_snow);
        map.put("wind", R.string.quick_event_weather_wind);
        map.put("mist", R.string.quick_event_weather_mist);
        return map;
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) return input;

        String[] words = input.split("\\s+");
        StringBuilder capitalized = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                capitalized.append(Character.toUpperCase(word.charAt(0)))
                           .append(word.substring(1).toLowerCase())
                           .append(" ");
            }
        }
        return capitalized.toString().trim();
    }

    public void onPause() {
        mHandler.removeCallbacks(mWeatherRunnable);
        mHandler.removeCallbacks(mOnDataUpdatedRunnable);
    }

    public void onResume() {
        addOmniJawsIfEnabled();
        notifyListeners();
    }

    public void onDestroy() {
        mHandler.removeCallbacks(mWeatherRunnable);
        mHandler.removeCallbacks(mOnDataUpdatedRunnable);
        for (OnDataListener listener : new ArrayList<>(mListeners)) {
            removeListener(listener);
        }
    }

    @Override
    public void weatherUpdated() {
        queryAndUpdateWeather();
    }

    @Override
    public void weatherError(int errorReason) {
        Log.d(TAG, "weatherError " + errorReason);
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            mWeatherInfo = null;
            notifyListeners();
        }
    }

    @Override
    public void updateSettings() {
        Log.i(TAG, "updateSettings");
        queryAndUpdateWeather();
    }

    private void queryAndUpdateWeather() {
        mHandler.removeCallbacks(mWeatherRunnable);
        mHandler.post(mWeatherRunnable);
    }

    public void notifyListeners() {
        mHandler.removeCallbacks(mOnDataUpdatedRunnable);
        mHandler.post(mOnDataUpdatedRunnable);
    }
}
