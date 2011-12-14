package net.randomprocesses.flickr.sync;

import net.randomprocesses.flickr.prefs.PreferenceKeys;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class ContactsActivityActivity extends Activity {
  private static final String TAG = ContactsProfileActivity.class.getSimpleName();
  public static final String NSID_EXTRA = "nsid";
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    try {
      // Grab the NSID of the account that created the notification.
      final String accountOwnerNsid = getIntent().getStringExtra(NSID_EXTRA);
      if (accountOwnerNsid == null) {
        if (Log.isLoggable(TAG, Log.ERROR))
          Log.e(TAG, "ContactsActivityActivity started with no nsid extra");
        return;
      }
      // Construct the preference-key for that account.
      final String latestStatusPrefKey = PreferenceKeys.LATEST_STATUS(accountOwnerNsid);
      PreferenceManager.getDefaultSharedPreferences(this)
        .edit()
        .putLong(latestStatusPrefKey, System.currentTimeMillis()/1000L)
        .apply();
      // Kick off the activity to view the contacts' activity in the browser.
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://m.flickr.com/#/people/")));
    } finally {
      finish();
    }
  }  
}
