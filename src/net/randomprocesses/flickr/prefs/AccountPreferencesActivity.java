package net.randomprocesses.flickr.prefs;

import net.randomprocesses.flickr.R;
import net.randomprocesses.flickr.content.ActivityContentProvider;
import net.randomprocesses.flickr.content.PhotosContentProvider;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

public class AccountPreferencesActivity extends PreferenceActivity {
  private static final String TAG = "AccountPreferences";
  private String[] mOptions = null;
  private String[] mValues = null;
  
  private String getEntryForValue(String value) {
    int i = 0;
    for (String s : mValues) {
      if (s.equals(value))
        break;
      i++;
    }
    return mOptions[i];
  }
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    mOptions = AccountPreferencesActivity.this.getResources().getStringArray(R.array.sync_intervals);
    mValues = AccountPreferencesActivity.this.getResources().getStringArray(R.array.sync_interval_seconds);

    final Intent intent = getIntent();
    
    final Account account = intent.getParcelableExtra("account");
    final String nsid = AccountManager.get(this).getUserData(account, "nsid");

    // We create the preferences explicitly (versus an XML resource), because the
    // keys for each Preference element need to be account-specific.

    // Simple bar at the top.
    final PreferenceCategory syncCategory = new PreferenceCategory(this);
    syncCategory.setTitle(R.string.sync_preferences);

    // Selection of the time interval between sync.
    final ListPreference syncIntervalList = new ListPreference(this);
    syncIntervalList.setKey(PreferenceKeys.SYNC_INTERVAL(nsid));
    syncIntervalList.setTitle(R.string.sync_interval);
    syncIntervalList.setSummary(
        getEntryForValue(sharedPreferences.getString(PreferenceKeys.SYNC_INTERVAL(nsid), "3600")));
    syncIntervalList.setEntries(R.array.sync_intervals);
    syncIntervalList.setEntryValues(R.array.sync_interval_seconds);
    syncIntervalList.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        try {
          // Change the shown summary.
          preference.setSummary(getEntryForValue((String) newValue));
          
          // Change the sync schedule for all authorities (for this account).
          final Long seconds = Long.valueOf(String.valueOf(newValue));
          ContentResolver.addPeriodicSync(account, ContactsContract.AUTHORITY, new Bundle(), seconds);
          ContentResolver.addPeriodicSync(account, ActivityContentProvider.AUTHORITY, new Bundle(), seconds);
          ContentResolver.addPeriodicSync(account, PhotosContentProvider.AUTHORITY, new Bundle(), seconds);
          if (Log.isLoggable(TAG, Log.DEBUG))
            Log.i(TAG,
                  "Setting sync interval for account (" + account.name + ") to " +
                  String.valueOf(seconds) + " seconds.");
          return true;
        } catch (final NumberFormatException e) {
          if (Log.isLoggable(TAG, Log.ERROR))
            Log.e(TAG, "Error parsing preference value for sync interval: " + String.valueOf(newValue), e);
          return false;
        }
      }
    });
    
    // Set of tags that will be synced.
    final EditTextPreference syncTags = new EditTextPreference(this);
    syncTags.setKey(PreferenceKeys.SYNC_TAGS(nsid));
    syncTags.setTitle(R.string.sync_tags);
    final String tags = sharedPreferences.getString(PreferenceKeys.SYNC_TAGS(nsid), "");
    syncTags.setSummary(TextUtils.isEmpty(tags) ? getString(R.string.no_sync_tags) : tags);
    syncTags.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        String tags = (String) newValue;
        preference.setSummary(TextUtils.isEmpty(tags) ? getString(R.string.no_sync_tags) : tags);
        return true;
      }
    });
    
    final CheckBoxPreference syncTagsOnWifi = new CheckBoxPreference(this);
    syncTagsOnWifi.setKey(PreferenceKeys.SYNC_TAGS_ON_WIFI(nsid));
    syncTagsOnWifi.setTitle(R.string.sync_tags_on_wifi);
    syncTagsOnWifi.setSummary(sharedPreferences.getBoolean(PreferenceKeys.SYNC_TAGS_ON_WIFI(nsid), false) ?
                              R.string.sync_tags_on_wifi_checked_summary :
                              R.string.sync_tags_on_wifi_unchecked_summary);
    syncTagsOnWifi.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        preference.setSummary((Boolean) newValue ?
                              R.string.sync_tags_on_wifi_checked_summary :
                              R.string.sync_tags_on_wifi_unchecked_summary);
        return true;
      }
    });
    
    final PreferenceCategory notificationCategory = new PreferenceCategory(this);
    notificationCategory.setTitle(R.string.notification_preferences);

    final CheckBoxPreference notifyForContactActivity = new CheckBoxPreference(this);
    notifyForContactActivity.setKey(PreferenceKeys.NOTIFY_FOR_CONTACT_ACTIVITY(nsid));
    notifyForContactActivity.setTitle(R.string.notify_for_contact_activity_pref_title);
    notifyForContactActivity.setSummary(sharedPreferences.getBoolean(PreferenceKeys.NOTIFY_FOR_CONTACT_ACTIVITY(nsid), false) ?
                                        R.string.notify_for_contact_activity_pref_checked_summary :
                                        R.string.notify_for_contact_activity_pref_unchecked_summary);
    notifyForContactActivity.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        preference.setSummary((Boolean) newValue ?
                              R.string.notify_for_contact_activity_pref_checked_summary :
                              R.string.notify_for_contact_activity_pref_unchecked_summary);
        return true;
      }
    });

    final PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(this);
    preferenceScreen.setTitle(getString(R.string.preferences_for_account, account.name));
    // Add all the pieces we created before
    preferenceScreen.addPreference(syncCategory);
    preferenceScreen.addPreference(syncIntervalList);
    preferenceScreen.addPreference(syncTags);
    preferenceScreen.addPreference(syncTagsOnWifi);
    preferenceScreen.addPreference(notificationCategory);
    preferenceScreen.addPreference(notifyForContactActivity);
    // Create the screen.
    setPreferenceScreen(preferenceScreen);
  }
}
