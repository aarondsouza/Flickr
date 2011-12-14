package net.randomprocesses.flickr.sync;

import java.util.ArrayList;

import oauth.signpost.OAuthConsumer;

import net.randomprocesses.flickr.R;
import net.randomprocesses.flickr.api.FlickrAPI;
import net.randomprocesses.flickr.api.FlickrAPI.FlickrAPIException;
import net.randomprocesses.flickr.api.FlickrPhoto;
import net.randomprocesses.flickr.content.ActivityContentProvider;
import net.randomprocesses.flickr.content.ActivityContentProvider.ContactActivity;
import net.randomprocesses.flickr.prefs.PreferenceKeys;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class ActivitySyncAdapter extends AbstractThreadedSyncAdapter {
  private static final String TAG = "FlickrActivitySync";

  private final Context mContext;
  private final ContentResolver mContentResolver;

  public ActivitySyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);
    mContext = context;
    mContentResolver = mContext.getContentResolver();
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority,
                            ContentProviderClient provider, SyncResult syncResult) {
    final String accountOwnerNsid = AccountManager.get(mContext).getUserData(account, "nsid");
    Log.i(TAG, "Activity sync request for account: " + account.name);
    
    final AccountManager accountManager = AccountManager.get(mContext);
    // Get the authentication token with which all API calls will be made.
    try {
      final String authToken =
        accountManager.blockingGetAuthToken(account, "", true /*notifyAuthFailure*/);
      if (authToken == null) {
        Log.e(TAG, "Could not retrieve auth-token. Abandoning activity-sync.");
        return;
      }
      OAuthConsumer consumer = FlickrAPI.getFlickrConsumer();
      consumer.setTokenWithSecret(accountManager.getUserData(account, "token"), authToken);
      
      ArrayList<FlickrPhoto> latestPhotos = null;
      try {
        latestPhotos = FlickrAPI.getContactsPhotos(consumer, 500);
        Log.d(TAG, "Got activity sync resonse with " + latestPhotos.size() + " photos.");
      } catch (FlickrAPIException e) {
        if (FlickrAPI.isAuthenticationError(e)) {
          if (Log.isLoggable(TAG, Log.WARN))
            Log.w(TAG, "Invalidating token " + authToken + " for account: " + account.name);
          accountManager.invalidateAuthToken(account.type, authToken);
        }
        if (Log.isLoggable(TAG, Log.ERROR))
          Log.e(TAG, "Abandoning sync for account: " + account.name);
        return;
      }
            
      // List that will contain all the ops to be executed as part of this sync.
      final ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
      // Delete all previous contact activity that we know about.
      ops.add(ContentProviderOperation.newDelete(ContactActivity.CONTENT_URI)
          .withSelection(ContactActivity.ACCOUNT_NSID + "=?", new String[] {accountOwnerNsid})
          .withYieldAllowed(true)
          .build());
      
      long newestTimestamp = 0;  // Keep track of newest timestamp from contact activity.
      for (FlickrPhoto photo : latestPhotos) {
        newestTimestamp = Math.max(newestTimestamp, photo.mDateUpload);
        ops.add(ContentProviderOperation.newInsert(ActivityContentProvider.ContactActivity.CONTENT_URI)
            .withValue(ContactActivity.ACCOUNT_NSID, accountOwnerNsid)
            .withValue(ContactActivity.NSID, photo.mOwner)
            .withValue(ContactActivity.TIMESTAMP, photo.mDateUpload)
            .withValue(ContactActivity.TITLE, photo.mTitle)
            .withYieldAllowed(false)
            .build());
      }
      mContentResolver.applyBatch(authority, ops);
      
      // Now that we have new contact activity, update statuses viewable in contacts.
      StatusSync.updateStatuses(mContext, account);
      
      // If we found status updates that are newer than what we saw last, post a notification.
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      final String latestStatusPrefKey = PreferenceKeys.LATEST_STATUS(accountOwnerNsid);
      if (prefs.getLong(latestStatusPrefKey, 0) < newestTimestamp) {
        // Update the latest timestamp.
        prefs.edit().putLong(latestStatusPrefKey, newestTimestamp).apply();
        maybeNotifyForContactActivity(prefs, accountOwnerNsid);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private void maybeNotifyForContactActivity(final SharedPreferences prefs, final String accountOwnerNsid) {
    // Check if we need to notify the user.
    if (!prefs.getBoolean(PreferenceKeys.NOTIFY_FOR_CONTACT_ACTIVITY(accountOwnerNsid), false))
      return;
    // Ok, notifications are on for this account.
    final Notification notification =
        new Notification(R.drawable.ic_status, mContext.getString(R.string.new_contacts_activity_ticker),
                         System.currentTimeMillis());
    notification.flags |= Notification.FLAG_AUTO_CANCEL;  // Notification clears when clicked.
    // For now the intent just redirects to the browser to view your contacts.
    final Intent intent = new Intent(mContext, ContactsActivityActivity.class);
    intent.putExtra(ContactsActivityActivity.NSID_EXTRA, accountOwnerNsid);
    final PendingIntent pendingIntent =
        PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    notification.setLatestEventInfo(mContext,
                                    mContext.getString(R.string.new_contacts_activity_ticker),
                                    mContext.getText(R.string.new_contacts_activity_notification_text), pendingIntent);
    final NotificationManager notificationManager =
        (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.notify(0, notification);
  }  
}
