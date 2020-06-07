package io.Pushjet.api;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import io.Pushjet.api.Async.FCMRegistrar;
import io.Pushjet.api.Async.RefreshServiceAsync;
import io.Pushjet.api.PushjetApi.PushjetApi;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String DEFAULT_PUSHJET_SERVER_URL = "https://api.pushjet.io";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Add 'general' preferences.
        addPreferencesFromResource(R.xml.preferences);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        bindPreferenceSummaryToValue(findPreference("server_custom_url"));

        findPreference("notify_channels_settings").setOnPreferenceClickListener(sBindOnPreferenceClickListener);
        findPreference("general_reset").setOnPreferenceClickListener(sBindOnPreferenceClickListener);
        findPreference("server_register").setOnPreferenceClickListener(sBindOnPreferenceClickListener);

        // hide notification channel preferences on Android version < Oreo
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            findPreference("notify_channels_settings").setVisible(false);
        }
    }

    private static Preference.OnPreferenceClickListener sBindOnPreferenceClickListener = new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            String key = preference.getKey();
            final Context context = preference.getContext();
            final DatabaseHandler db = new DatabaseHandler(context);
            if (key.equalsIgnoreCase("general_reset")) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);

                builder.setTitle("Delete all messages");
                builder.setMessage("Are you completely sure you want to do this?");
                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        db.truncateMessages();
                        dialog.dismiss();
                    }
                });

                builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                builder.create().show();

                return true;
            } else if (key.equalsIgnoreCase("server_register")) {

                AlertDialog.Builder builder = new AlertDialog.Builder(context);

                builder.setTitle("Re-register");
                builder.setMessage("Are you completely sure you want to do this? This will delete all your received notifications!");

                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        FCMRegistrar fcm = new FCMRegistrar(context);

                        fcm.forgetRegistration();
                        db.truncateMessages();
                        db.truncateServices();
                        fcm.registerInBackground(true);

                        PushjetApi api = new PushjetApi(context, getRegisterUrl(context));
                        new RefreshServiceAsync(api, db).execute();

                        dialog.dismiss();
                    }
                });

                builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                builder.create().show();
            } else if (key.equalsIgnoreCase("notify_channels_settings") &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                context.startActivity(intent);

                return true;
            }

            return false;
        }
    };
    /**
     * A preference value change subscriptioner that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the subscriptioner to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the subscriptioner immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    public static String getRegisterUrl(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean useCustom = preferences.getBoolean("server_use_custom", false);

        if (useCustom) {
            String url = preferences.getString("server_custom_url", DEFAULT_PUSHJET_SERVER_URL);
            return url.replaceAll("/+$", "");
        } else {
            return DEFAULT_PUSHJET_SERVER_URL;
        }
    }
}
