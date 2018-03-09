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
 import android.lease.*;
 import android.os.Bundle;
 import android.os.Handler;
 import android.os.Message;
 import android.support.v7.preference.*;
 import android.util.Log;
 import com.android.settings.R;
 import com.android.settings.SettingsPreferenceFragment;

 import java.util.HashSet;
 import java.util.Set;

 /**
 *
 */
public class LeaseSettings extends SettingsPreferenceFragment implements
         Preference.OnPreferenceChangeListener{
    private static final String TAG = "LeaseSettings";

    private CheckBoxPreference mLeaseEnabledPref;

    private CheckBoxPreference mWakelockLeaseEnabledPref;

    private SettingsHandler mHandler;

     @Override
     public void onCreate(Bundle savedInstanceState) {
         super.onCreate(savedInstanceState);
         Log.d(TAG, "Lease Settings initialized");
         addPreferencesFromResource(R.xml.lease_settings);
         mLeaseEnabledPref = (CheckBoxPreference) findPreference("lease_mode");

         mHandler = new SettingsHandler();
         mHandler.sendEmptyMessage(SettingsHandler.MSG_SYNC_WITH_SECURE_SETTING);

         registerChangeListener();
     }

     @Override
     protected int getMetricsCategory() {
         return 0;
     }

     public void registerChangeListener() {
         mLeaseEnabledPref.setOnPreferenceChangeListener(this);
     }

     @Override
     public boolean onPreferenceChange(Preference preference, Object newValue) {
         if (preference == mLeaseEnabledPref) {
             Log.d(TAG, "Enabled changed to " + newValue);
             boolean value = (Boolean) newValue;
             LeaseSettingsUtils.writeServiceEnabled(value, getContentResolver());
             return true;
         }
         return false;
     }

     private void syncWithSecureSettings() {
         final ContentResolver resolver = getContentResolver();
         android.lease.LeaseSettings settings = LeaseSettingsUtils.readLeaseSettingsLocked(resolver);
         mLeaseEnabledPref.setChecked(settings.serviceEnabled);
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