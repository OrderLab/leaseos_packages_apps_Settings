 /*
  *  @author Yigong Hu <hyigong1@cs.jhu.edu>
  *
  *  The LeaseOS Project
  *
  *  Copyright (c) 2018, Johns Hopkins University - Order Lab.
  *      All rights reserved.
  *
  *  Licensed under the Apache License, Version 2.0 (the "License");
  *  you may not use this file except in compliance with the License.
  *  You may obtain a copy of the License at
  *
  *  http://www.apache.org/licenses/LICENSE-2.0
  *
  *  Unless required by applicable law or agreed to in writing, software
  *  distributed under the License is distributed on an "AS IS" BASIS,
  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  *  See the License for the specific language governing permissions and
  *  limitations under the License.
  */
 package com.android.settings.lease;

 import android.app.Activity;
 import android.content.ContentResolver;
 import android.content.SharedPreferences;
 import android.lease.LeaseSettingsUtils;
 import android.lease.StringUtils;
 import android.os.Bundle;
 import android.os.Handler;
 import android.os.Message;
 import android.support.v7.preference.CheckBoxPreference;
 import android.support.v7.preference.ListPreference;
 import android.support.v7.preference.Preference;
 import android.util.Log;

 import com.android.settings.R;
 import com.android.settings.SettingsPreferenceFragment;

 import java.util.Set;

 /**
  *
  */
 public class LeaseSettings extends SettingsPreferenceFragment implements
         Preference.OnPreferenceChangeListener {
     private static final String TAG = "LeaseSettings";
     private static final int MILLIS_PER_MINUTE = 60 * 1000;

     private CheckBoxPreference mLeaseEnabledPref;
     private SharedPreferences mWhiteListPref;
     private ListPreference mRateLimitWindowPref;
     private ListPreference mGCWindowPref;

     private CheckBoxPreference mWakelockLeaseEnabledPref;
     private CheckBoxPreference mGPSLeaseEnabledPref;
     private CheckBoxPreference mSensorLeaseEnabledPref;

     private SettingsHandler mHandler;

     @Override
     public void onCreate(Bundle savedInstanceState) {
         super.onCreate(savedInstanceState);
         Log.d(TAG, "Lease Settings initialized");
         addPreferencesFromResource(R.xml.lease_settings);
         mLeaseEnabledPref = (CheckBoxPreference) findPreference("lease_mode");
         mWhiteListPref = getActivity().getSharedPreferences("whitelist",
                 Activity.MODE_PRIVATE);
         mRateLimitWindowPref = (ListPreference) findPreference("rate_limit_window");
         mRateLimitWindowPref.setSummary(mRateLimitWindowPref.getEntry());
         mGCWindowPref = (ListPreference) findPreference("gc_window");
         mGCWindowPref.setSummary(mGCWindowPref.getEntry());

         mWakelockLeaseEnabledPref = (CheckBoxPreference) findPreference("enable_wakelock_lease");
         mGPSLeaseEnabledPref = (CheckBoxPreference) findPreference("enable_gps_lease");
         mSensorLeaseEnabledPref = (CheckBoxPreference) findPreference("enable_sensor_lease");

         mHandler = new SettingsHandler();
         mHandler.sendEmptyMessage(SettingsHandler.MSG_SYNC_WITH_SECURE_SETTING);

         registerChangeListener();
     }

     @Override
     protected int getMetricsCategory() {
         return 1;
     }

     public void registerChangeListener() {
         Log.d(TAG, "start to register the listener");
         mLeaseEnabledPref.setOnPreferenceChangeListener(this);
         mRateLimitWindowPref.setOnPreferenceChangeListener(this);
         mGCWindowPref.setOnPreferenceChangeListener(this);

         mWakelockLeaseEnabledPref.setOnPreferenceChangeListener(this);
         mGPSLeaseEnabledPref.setOnPreferenceChangeListener(this);
         mSensorLeaseEnabledPref.setOnPreferenceChangeListener(this);
     }

     private void syncWithSecureSettings() {
         final ContentResolver resolver = getContentResolver();
         Log.d(TAG, "Sync the secure setting");
         android.lease.LeaseSettings settings = LeaseSettingsUtils.readLeaseSettingsLocked(
                 resolver);
         mLeaseEnabledPref.setChecked(settings.serviceEnabled);

         Set<String> whiteListSet = LeaseSettingsUtils.whitelistToSet(settings.whiteList);
         mWhiteListPref.edit().putStringSet(LeaseWhiteList.WL_PKGS_KEY,
                 whiteListSet).commit();
         updateFreqListPref(mRateLimitWindowPref, settings.rateLimitWindow);
         updateFreqListPref(mGCWindowPref, settings.gcWindow);

         mWakelockLeaseEnabledPref.setChecked(settings.wakelockLeaseEnabled);
         mGPSLeaseEnabledPref.setChecked(settings.gpsLeaseEnabled);
         mSensorLeaseEnabledPref.setChecked(settings.sensorLeaseEnabled);
     }

     private void updateFreqListPref(ListPreference preference, long freqInMillis) {
         String minute = millisToMinutes(freqInMillis);
         int index = preference.findIndexOfValue(minute);
         if (index >= 0) {
             preference.setValueIndex(index);
             preference.setSummary(preference.getEntries()[index]);
         }
     }

     private String millisToMinutes(long time) {
         return StringUtils.formatDouble((double) time / MILLIS_PER_MINUTE, 2);
     }

     @Override
     public boolean onPreferenceChange(Preference preference, Object newValue) {
         if (preference == mLeaseEnabledPref) {
             Log.d(TAG, "Enabled changed to " + newValue);
             boolean value = (Boolean) newValue;
             LeaseSettingsUtils.writeServiceEnabled(value, getContentResolver());
             return true;
         } else if (preference == mRateLimitWindowPref) {
             Log.d(TAG, "Rate limit window changed to " + newValue);
             String value = (String) newValue;
             updatePrefSummary(mRateLimitWindowPref, value);

             long window = (long) (Float.parseFloat(value) * MILLIS_PER_MINUTE);
             LeaseSettingsUtils.writeRateLimitWindow(window, getContentResolver());
             return true;
         } else if (preference == mGCWindowPref) {
             Log.d(TAG, "GC window changed to " + newValue);
             String value = (String) newValue;
             updatePrefSummary(mGCWindowPref, value);

             long window = (long) (Float.parseFloat(value) * MILLIS_PER_MINUTE);
             LeaseSettingsUtils.writeGCWindow(window, getContentResolver());
             return true;
         } else if (preference == mWakelockLeaseEnabledPref) {
             Log.d(TAG, "Wakelock lease enabled changed to " + newValue);
             boolean value = (Boolean) newValue;
             LeaseSettingsUtils.writeWakelockLeaseEnabled(value, getContentResolver());
             return true;
         } else if (preference == mGPSLeaseEnabledPref) {
             Log.d(TAG, "GPS lease enabled changed to " + newValue);
             boolean value = (Boolean) newValue;
             LeaseSettingsUtils.writeLocationLeaseEnabled(value, getContentResolver());
             return true;
         } else if (preference == mSensorLeaseEnabledPref) {
             Log.d(TAG, "Sensor lease enabled changed to " + newValue);
             boolean value = (Boolean) newValue;
             LeaseSettingsUtils.writeSensorLeaseEnabled(value, getContentResolver());
             return true;
         }
         Log.d(TAG, "Unrecognized preference change");
         return false;
     }

     private void updatePrefSummary(Preference preference, String newValue) {
         if (preference instanceof ListPreference) {
             // For list preferences, look up the correct display value in
             // the preference's 'entries' list.
             ListPreference listPreference = (ListPreference) preference;
             int index = listPreference.findIndexOfValue(newValue);
             // Set the summary to reflect the new value.
             listPreference.setSummary(
                     index >= 0
                             ? listPreference.getEntries()[index]
                             : null);
         } else {
             // For all other preferences, set the summary to the value's
             // simple string representation.
             if (newValue.length() > 32)
                 preference.setSummary(newValue.substring(0, 32) + "...");
             else
                 preference.setSummary(newValue);
         }
     }


     private class SettingsHandler extends Handler {
         private static final int MSG_SYNC_WITH_SECURE_SETTING = 1;

         @Override
         public void handleMessage(Message msg) {
             switch (msg.what) {
                 case MSG_SYNC_WITH_SECURE_SETTING:
                     syncWithSecureSettings();
                     break;
             }
         }
     }
 }