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
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.lease.LeaseSettingsUtils;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import com.android.settings.R;

import java.util.*;

/**
 *
 */
public class LeaseWhiteList extends Fragment {

    private static final String TAG = "LeaseWhiteList";
    private TextView mNoUserAppsInstalled;
    private ListView mAppsList;
    private List<AppInfo> mApps;
    private LeaseWhiteListAdapter mAdapter;
    private PackageManager mPm;
    private Activity mActivity;
    private SharedPreferences mPreferences;

    public static final String WL_PKGS_KEY = "whitelist_packages";

    private int mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
    private int mSavedFirstItemOffset;

    // keys for extras and icicles
    private final static String LAST_LIST_POS = "last_list_pos";
    private final static String LAST_LIST_OFFSET = "last_list_offset";

    // holder for package data passed into the adapter
    public static final class AppInfo {
        String title;
        String packageName;
        boolean enabled;
        boolean whitelisted;
        int uid;
    }

    public LeaseWhiteList() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mActivity = getActivity();
        mPm = mActivity.getPackageManager();
        return inflater.inflate(R.layout.leaseos_whitelist, container, false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mNoUserAppsInstalled = (TextView) mActivity.findViewById(R.id.error);

        mAppsList = (ListView) mActivity.findViewById(R.id.apps_list);

        // get shared preference
        mPreferences = mActivity.getSharedPreferences("whitelist", Activity.MODE_PRIVATE);

        if (savedInstanceState != null) {
            mSavedFirstVisiblePosition = savedInstanceState.getInt(LAST_LIST_POS,
                    AdapterView.INVALID_POSITION);
            mSavedFirstItemOffset = savedInstanceState.getInt(LAST_LIST_OFFSET, 0);
        } else {
            mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
            mSavedFirstItemOffset = 0;
        }

        // load apps and construct the list
        loadApps();
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(LAST_LIST_POS, mSavedFirstVisiblePosition);
        outState.putInt(LAST_LIST_OFFSET, mSavedFirstItemOffset);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Remember where the list is scrolled to so we can restore the scroll position
        // when we come back to this activity and *after* we complete querying for the
        // conversations.
        mSavedFirstVisiblePosition = mAppsList.getFirstVisiblePosition();
        View firstChild = mAppsList.getChildAt(0);
        mSavedFirstItemOffset = (firstChild == null) ? 0 : firstChild.getTop();
    }

    @Override
    public void onResume() {
        super.onResume();

        // rebuild the list; the user might have changed settings inbetween
        loadApps();

        if (mSavedFirstVisiblePosition != AdapterView.INVALID_POSITION) {
            mAppsList.setSelectionFromTop(mSavedFirstVisiblePosition, mSavedFirstItemOffset);
            mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
        }
    }

    private void loadApps() {
        mApps = loadInstalledApps();

        // if app list is empty inform the user
        // else go ahead and construct the list
        if (mApps == null || mApps.isEmpty()) {
            mNoUserAppsInstalled.setText(R.string.leaseos_no_user_apps);
            mNoUserAppsInstalled.setVisibility(View.VISIBLE);
            mAppsList.setVisibility(View.GONE);
            mAppsList.setAdapter(null);
        } else {
            mNoUserAppsInstalled.setVisibility(View.GONE);
            mAppsList.setVisibility(View.VISIBLE);
            mAdapter = createAdapter();
            mAppsList.setAdapter(mAdapter);
            mAppsList.setFastScrollEnabled(true);
        }
    }

    private LeaseWhiteListAdapter.OnWhiteListChangeListener mWhitelistListener =
            new LeaseWhiteListAdapter.OnWhiteListChangeListener() {
                @Override
                public void onWhiteListChanged(AppInfo appInfo, boolean isWhiteListed) {
                    Set<String> pkgs = mPreferences.getStringSet(WL_PKGS_KEY, new HashSet<String>());
                    if (isWhiteListed) {
                        Log.d(TAG, "Add " + appInfo.packageName + " to whitelist");
                        pkgs.add(appInfo.packageName);
                        mPreferences.edit().putStringSet(WL_PKGS_KEY, pkgs).commit();
                    } else {
                        Log.d(TAG, "Remove " + appInfo.packageName + " from whitelist");
                        if (pkgs.remove(appInfo.packageName)) {
                            mPreferences.edit().putStringSet(WL_PKGS_KEY, pkgs).commit();
                        }
                    }
                    Log.d(TAG, "New whitelist: " + pkgs);
                    String whitelist = LeaseSettingsUtils.setToWhiteList(pkgs);
                    ContentResolver resolver =  getActivity().getContentResolver();
                    LeaseSettingsUtils.writeWhiteList(whitelist, resolver);
                }
            };

    /**
     * Uses the package manager to query for all currently installed apps
     * for the list.
     *
     * @return the complete List off installed applications (@code PrivacyGuardAppInfo)
     */
    private List<AppInfo> loadInstalledApps() {
        List<AppInfo> apps = new ArrayList<AppInfo>();
        List<PackageInfo> packages = mPm.getInstalledPackages(
                PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);
        boolean showSystemApps = shouldShowSystemApps();
        Signature platformCert;

        Set<String> pkgs = mPreferences.getStringSet(WL_PKGS_KEY, new HashSet<String>());
        try {
            PackageInfo sysInfo = mPm.getPackageInfo("android", PackageManager.GET_SIGNATURES);
            platformCert = sysInfo.signatures[0];
        } catch (PackageManager.NameNotFoundException e) {
            platformCert = null;
        }

        for (PackageInfo info : packages) {
            final ApplicationInfo appInfo = info.applicationInfo;

            // hide apps signed with the platform certificate to avoid the user
            // shooting himself in the foot
            if (platformCert != null && info.signatures != null
                    && platformCert.equals(info.signatures[0])) {
                continue;
            }

            // skip all system apps if they shall not be included
            if (!showSystemApps && (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }

            AppInfo app = new AppInfo();
            app.title = appInfo.loadLabel(mPm).toString();
            app.packageName = info.packageName;
            app.enabled = appInfo.enabled;
            app.uid = info.applicationInfo.uid;
            if (pkgs.contains(app.packageName)) {
                app.whitelisted = true;
            } else {
                app.whitelisted = false;
            }
            apps.add(app);
        }

        // sort the apps by their enabled state, then by title
        Collections.sort(apps, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo lhs, AppInfo rhs) {
                if (lhs.enabled != rhs.enabled) {
                    return lhs.enabled ? -1 : 1;
                }
                return lhs.title.compareToIgnoreCase(rhs.title);
            }
        });

        return apps;
    }

    private boolean shouldShowSystemApps() {
        return false;
        // return mPreferences.getBoolean("show_system_apps", false);
    }

    private LeaseWhiteListAdapter createAdapter() {
        String lastSectionIndex = null;
        ArrayList<String> sections = new ArrayList<String>();
        ArrayList<Integer> positions = new ArrayList<Integer>();
        int count = mApps.size(), offset = 0;

        for (int i = 0; i < count; i++) {
            AppInfo app = mApps.get(i);
            String sectionIndex;

            if (!app.enabled) {
                sectionIndex = "--"; //XXX
            } else if (app.title.isEmpty()) {
                sectionIndex = "";
            } else {
                sectionIndex = app.title.substring(0, 1).toUpperCase();
            }
            if (lastSectionIndex == null) {
                lastSectionIndex = sectionIndex;
            }

            if (!TextUtils.equals(sectionIndex, lastSectionIndex)) {
                sections.add(sectionIndex);
                positions.add(offset);
                lastSectionIndex = sectionIndex;
            }
            offset++;
        }
        return new LeaseWhiteListAdapter(mActivity, mApps, sections,
                positions, mWhitelistListener);
    }

}