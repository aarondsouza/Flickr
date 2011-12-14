package net.randomprocesses.flickr.prefs;

import net.randomprocesses.flickr.R;
import android.content.Context;
import android.preference.PreferenceManager;

// Manages preference-keys that might have to be specialized for different
// account instances.
public class PreferenceKeys {
  // Interval between periodic syncs.
  public static final long defaultSyncIntervalSeconds = 7200;
  private static final String SYNC_INTERVAL = "sync_interval_pref_key";
  // Timestamp of the latest contact-activity (to decide whether to notify user of new activity).
  private static final String LATEST_STATUS = "latest_status_pref_key";
  // Whether new contact activity should generate a status-bar notification.
  private static final String NOTIFY_FOR_CONTACT_ACTIVITY = "notify_for_contact_activity_pref_key";
  // Type of contacts for which we generate notifications.
  private static final String NOTIFY_CONTACT_TYPE = "notify_contact_type_pref_key";
  // Comma-separated list of tags of pictures we should sync to the phone.
  private static final String SYNC_TAGS = "sync_tags_pref_key";
  // Whether the pictures should only be downloaded when on a Wifi connection.
  private static final String SYNC_TAGS_ON_WIFI = "sync_tags_on_wifi_pref_key";
  // OAuth temporary token
  public static final String OAUTH_TEMPORARY_TOKEN = "oauth_temporary_token";
  // OAuth temporary token-secret
  public static final String OAUTH_TEMPORARY_TOKEN_SECRET = "oauth_temporary_token_secret";
  
  private static String appendNsid(final String key, final String nsid) {
    return key + "_" + nsid;
  }
  
  public static String SYNC_INTERVAL(final String nsid) {
    return appendNsid(SYNC_INTERVAL, nsid);
  }
  public static String LATEST_STATUS(final String nsid) {
    return appendNsid(LATEST_STATUS, nsid);
  }
  public static String NOTIFY_FOR_CONTACT_ACTIVITY(final String nsid) {
    return appendNsid(NOTIFY_FOR_CONTACT_ACTIVITY, nsid);
  }
  public static String NOTIFY_CONTACT_TYPE(final String nsid) {
    return appendNsid(NOTIFY_CONTACT_TYPE, nsid);
  }
  public static String SYNC_TAGS(final String nsid) {
    return appendNsid(SYNC_TAGS, nsid);
  }
  public static String SYNC_TAGS_ON_WIFI(final String nsid) {
    return appendNsid(SYNC_TAGS_ON_WIFI, nsid);
  }
  
  public static void setupDefaultPrefs(final Context context, final String nsid) {
    PreferenceManager.getDefaultSharedPreferences(context)
      .edit()
      // Set the default update time interval in preferences.
      .putString(SYNC_INTERVAL(nsid), String.valueOf(defaultSyncIntervalSeconds))
      // Also initialize the latest activity time seen from our contacts.
      .putLong(LATEST_STATUS(nsid), 0)
      // Notify in the status-bar for new contact activity.
      .putBoolean(NOTIFY_FOR_CONTACT_ACTIVITY(nsid), true)
      // Notify for any contact.
      .putString(NOTIFY_CONTACT_TYPE(nsid), context.getResources().getStringArray(R.array.contact_activity_type_constant)[0])
      // Tags to sync/download.
      .putString(SYNC_TAGS(nsid), "")
      // Only download photos on Wifi.
      .putBoolean(SYNC_TAGS_ON_WIFI(nsid), true)
      .commit();
  }
}
