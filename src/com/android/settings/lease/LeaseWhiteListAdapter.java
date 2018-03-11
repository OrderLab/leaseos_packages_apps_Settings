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

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.android.settings.R;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class LeaseWhiteListAdapter extends BaseAdapter implements SectionIndexer {

    private static final String TAG = "LeaseWhiteListAdapter";

    public static interface OnWhiteListChangeListener {
        public void onWhiteListChanged(LeaseWhiteList.AppInfo appInfo,
                boolean isWhiteListed);
    }

    private LayoutInflater mInflater;
    private PackageManager mPm;

    private List<LeaseWhiteList.AppInfo> mApps;
    private String[] mSections;
    private int[] mPositions;
    private ConcurrentHashMap<String, Drawable> mIcons;
    private Drawable mDefaultImg;

    private OnWhiteListChangeListener mListener;

    //constructor
    public LeaseWhiteListAdapter(Context context, List<LeaseWhiteList.AppInfo> apps,
            List<String> sections, List<Integer> positions,
            OnWhiteListChangeListener listener) {
        mInflater = LayoutInflater.from(context);
        mPm = context.getPackageManager();

        mApps = apps;
        mSections = sections.toArray(new String[sections.size()]);
        mPositions = new int[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            mPositions[i] = positions.get(i);
        }

        // set the default icon till the actual app icon is loaded in async task
        mDefaultImg = context.getResources().getDrawable(android.R.mipmap.sym_def_app_icon);
        mIcons = new ConcurrentHashMap<String, Drawable>();
        new LoadIconsTask().execute(apps.toArray(new LeaseWhiteList.AppInfo[]{}));
        mListener = listener;
    }

    @Override
    public int getCount() {
        return mApps.size();
    }

    @Override
    public Object getItem(int position) {
        return mApps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        WhiteListAppViewHolder appHolder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.leaseos_whitelist_row, null);

            // creates a ViewHolder and children references
            appHolder = new WhiteListAppViewHolder();
            appHolder.title = (TextView) convertView.findViewById(R.id.app_title);
            appHolder.icon = (ImageView) convertView.findViewById(R.id.app_icon);
            appHolder.whitelisted = (CheckBox) convertView.findViewById(R.id.app_whitelisted);
            convertView.setTag(appHolder);
        } else {
            appHolder = (WhiteListAppViewHolder) convertView.getTag();
        }

        final LeaseWhiteList.AppInfo app = mApps.get(position);
        appHolder.title.setText(app.title);
        Drawable icon = mIcons.get(app.packageName);
        appHolder.icon.setImageDrawable(icon != null ? icon : mDefaultImg);
        // Must set the listener to be null first!!
        appHolder.whitelisted.setOnCheckedChangeListener(null);
        appHolder.whitelisted.setChecked(app.whitelisted);
        appHolder.whitelisted.setTag(app);
        appHolder.whitelisted.setOnCheckedChangeListener(mCbListener);
        return convertView;
    }

    private CompoundButton.OnCheckedChangeListener mCbListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            LeaseWhiteList.AppInfo app = (LeaseWhiteList.AppInfo) buttonView.getTag();
            if (app != null) {
                app.whitelisted = isChecked;
                mListener.onWhiteListChanged(app, isChecked);
            }
        }
    };

    @Override
    public Object[] getSections() {
        return mSections;
    }

    @Override
    public int getPositionForSection(int section) {
        if (section < 0 || section >= mSections.length) {
            return -1;
        }

        return mPositions[section];
    }

    @Override
    public int getSectionForPosition(int position) {
        if (position < 0 || position >= getCount()) {
            return -1;
        }

        int index = Arrays.binarySearch(mPositions, position);
        return index >= 0 ? index : -index - 2;
    }

    /**
     * An asynchronous task to load the icons of the installed applications.
     */
    private class LoadIconsTask extends AsyncTask<LeaseWhiteList.AppInfo, Void, Void> {
        @Override
        protected Void doInBackground(LeaseWhiteList.AppInfo... apps) {
            for (LeaseWhiteList.AppInfo app : apps) {
                try {
                    Drawable icon = mPm.getApplicationIcon(app.packageName);
                    mIcons.put(app.packageName, icon);
                    publishProgress();
                } catch (PackageManager.NameNotFoundException e) {
                    // ignored; app will show up with default image
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... progress) {
            notifyDataSetChanged();
        }
    }

    /**
     * App view holder used to reuse the views inside the list.
     */
    public static class WhiteListAppViewHolder {
        TextView title;
        ImageView icon;
        CheckBox whitelisted;
    }
}