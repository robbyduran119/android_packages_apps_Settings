/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.settings.fuelgauge;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.View;
import android.widget.Checkable;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.applications.AppInfoBase;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.applications.ApplicationsState.AppEntry;

public class HighPowerDetail extends InstrumentedDialogFragment implements OnClickListener,
        View.OnClickListener {

    private static final String ARG_DEFAULT_ON = "default_on";

    private final PowerWhitelistBackend mBackend = PowerWhitelistBackend.getInstance();

    private String mPackageName;
    private CharSequence mLabel;
    private boolean mDefaultOn;
    private boolean mIsEnabled;
    private Checkable mOptionOn;
    private Checkable mOptionOff;

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.DIALOG_HIGH_POWER_DETAILS;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPackageName = getArguments().getString(AppInfoBase.ARG_PACKAGE_NAME);
        PackageManager pm = getContext().getPackageManager();
        try {
            mLabel = pm.getApplicationInfo(mPackageName, 0).loadLabel(pm);
        } catch (NameNotFoundException e) {
            mLabel = mPackageName;
        }
        mDefaultOn = getArguments().getBoolean(ARG_DEFAULT_ON);
        mIsEnabled = mDefaultOn || mBackend.isWhitelisted(mPackageName);
    }

    public Checkable setup(View view, boolean on) {
        ((TextView) view.findViewById(android.R.id.title)).setText(on
                ? R.string.ignore_optimizations_on : R.string.ignore_optimizations_off);
        ((TextView) view.findViewById(android.R.id.summary)).setText(on
                ? R.string.ignore_optimizations_on_desc : R.string.ignore_optimizations_off_desc);
        view.setClickable(true);
        view.setOnClickListener(this);
        return (Checkable) view;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                .setTitle(mLabel)
                .setNegativeButton(R.string.cancel, null)
                .setView(R.layout.ignore_optimizations_content);
        b.setPositiveButton(R.string.done, this);
        return b.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        mOptionOn = setup(getDialog().findViewById(R.id.ignore_on), true);
        mOptionOff = setup(getDialog().findViewById(R.id.ignore_off), false);
        updateViews();
    }

    private void updateViews() {
        mOptionOn.setChecked(mIsEnabled);
        mOptionOff.setChecked(!mIsEnabled);
    }

    @Override
    public void onClick(View v) {
        if (v == mOptionOn) {
            mIsEnabled = true;
            updateViews();
        } else if (v == mOptionOff) {
            mIsEnabled = false;
            updateViews();
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            boolean newValue = mIsEnabled;
            boolean oldValue = mBackend.isWhitelisted(mPackageName);
            if (newValue != oldValue) {
                logSpecialPermissionChange(newValue, mPackageName, getContext());
                if (newValue) {
                    mBackend.addApp(mPackageName);
                } else {
                    mBackend.removeApp(mPackageName);
                }
            }
        }
    }

    @VisibleForTesting
    static void logSpecialPermissionChange(boolean whitelist, String packageName, Context context) {
        int logCategory = whitelist ? MetricsProto.MetricsEvent.APP_SPECIAL_PERMISSION_BATTERY_DENY
                : MetricsProto.MetricsEvent.APP_SPECIAL_PERMISSION_BATTERY_ALLOW;
        FeatureFactory.getFactory(context).getMetricsFeatureProvider().action(context, logCategory,
                packageName);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        Fragment target = getTargetFragment();
        if (target != null && target.getActivity() != null) {
            target.onActivityResult(getTargetRequestCode(), 0, null);
        }
    }

    public static CharSequence getSummary(Context context, AppEntry entry) {
        return getSummary(context, entry.info.packageName);
    }

    public static CharSequence getSummary(Context context, String pkg) {
        PowerWhitelistBackend powerWhitelist = PowerWhitelistBackend.getInstance();
        return context.getString(powerWhitelist.isWhitelisted(pkg) ? R.string.high_power_on
                : R.string.high_power_off);
    }

    public static void show(Fragment caller, String packageName, int requestCode,
            boolean defaultToOn) {
        HighPowerDetail fragment = new HighPowerDetail();
        Bundle args = new Bundle();
        args.putString(AppInfoBase.ARG_PACKAGE_NAME, packageName);
        args.putBoolean(ARG_DEFAULT_ON, defaultToOn);
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, requestCode);
        if(caller != null){
            FragmentManager fmg =  caller.getFragmentManager();
            if(fmg != null && !fmg.isStateSaved() && !fmg.isDestroyed()){
                fragment.show(fmg, HighPowerDetail.class.getSimpleName());
            }
        }
    }
}
