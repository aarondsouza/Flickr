package net.randomprocesses.flickr.sync;

import java.util.ArrayList;

import net.randomprocesses.flickr.R;
import net.randomprocesses.flickr.content.ActivityContentProvider.ContactActivity;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;

// Utility class that can be called from either the contacts or activity
// sync adapters.
final class StatusSync {
  private static final String TAG = "StatusSync";
  
  private static Uri sContactsStatusUri =
      ContactsContract.StatusUpdates.CONTENT_URI
      .buildUpon()
      .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
      .build();
  
  private interface ActivityQuery {
    public static final String SELECTION = ContactActivity.ACCOUNT_NSID + "=?";
    public static final String[] PROJECTION = {
      ContactActivity.NSID,
      ContactActivity.TIMESTAMP,
      ContactActivity.TITLE
    };
    public static final int COLUMN_NSID      = 0;
    public static final int COLUMN_TIMESTAMP = 1;
    public static final int COLUMN_TITLE     = 2;
  }
  
  public static final void updateStatuses(final Context context, final Account account) {
    final String accountOwnerNsid = AccountManager.get(context).getUserData(account, "nsid");
    
    final ContentResolver contentResolver = context.getContentResolver();
    final Cursor statuses = contentResolver.query(ContactActivity.CONTENT_URI,
                                                  ActivityQuery.PROJECTION, ActivityQuery.SELECTION,
                                                  new String[] {accountOwnerNsid}, null);
    try {
      // Reusable Uri that already encodes the account name/type and that we can use
      // to query for whether our contacts already exist in the RawContacts table.
      final Uri rawContactUri =
          RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
            .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
            .build();
      // List that will contain all the ops to be executed as part of this sync.
      final ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
      while (statuses.moveToNext()) {
        final String nsid      = statuses.getString(ActivityQuery.COLUMN_NSID);
        final long   timestamp = statuses.getLong(ActivityQuery.COLUMN_TIMESTAMP);
        final String title     = statuses.getString(ActivityQuery.COLUMN_TITLE);
        
        updateStatusForPhoto(contentResolver, rawContactUri, ops, nsid, title, timestamp);
      }
      contentResolver.applyBatch(ContactsContract.AUTHORITY, ops);
    } catch (RemoteException e) {
      e.printStackTrace();
    } catch (OperationApplicationException e) {
      e.printStackTrace();
    } finally {
      statuses.close();
    }
  }
  
  private static final void updateStatusForPhoto(final ContentResolver contentResolver,
                                                 final Uri rawContactUri,
                                                 final ArrayList<ContentProviderOperation> ops,
                                                 final String nsid, final String title, final long timestamp) {
    // Create a default title for the picture if the creator didn't.
    final String status = (TextUtils.isEmpty(title) ? "<Untitled>" : title);
    // Find the RawContact for the specified NSID. The rawContactURI already encodes the account.
    final Cursor rawContact =
        contentResolver.query(rawContactUri, new String[] {RawContacts._ID},
                              RawContacts.SOURCE_ID + "=?", new String[] {nsid}, null);
    Cursor dataRow = null;
    try {
      if (!rawContact.moveToFirst()) {
        if (Log.isLoggable(TAG, Log.ERROR))
          Log.e(TAG, "Couldn't find RawContact for status update for NSID: " + nsid);
        return;
      }
      final long rawContactId = rawContact.getLong(0);
      dataRow = contentResolver.query(Data.CONTENT_URI, new String[] {Data._ID},
                                      Data.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
                                      new String[] {String.valueOf(rawContactId),
                                                    ContactsSyncAdapter.FLICKR_CONTACT_MIMETYPE},
                                      null);
      if (!dataRow.moveToFirst()) {
        if (Log.isLoggable(TAG, Log.ERROR))
          Log.e(TAG, "Flickr RawContact has no Data row for NSID: " + nsid);
        return;
      }
      
      ops.add(ContentProviderOperation.newInsert(sContactsStatusUri)
          .withValue(ContactsContract.StatusUpdates.DATA_ID, dataRow.getLong(0))
          .withValue(ContactsContract.StatusUpdates.STATUS, status)
          .withValue(ContactsContract.StatusUpdates.STATUS_TIMESTAMP, timestamp*1000)
          .withValue(ContactsContract.StatusUpdates.STATUS_RES_PACKAGE, "net.randomprocesses.flickr")
          .withValue(ContactsContract.StatusUpdates.STATUS_ICON, R.drawable.icon)
          .withValue(ContactsContract.StatusUpdates.STATUS_LABEL, R.string.app_name)
          .withYieldAllowed(true)
          .build());
    } finally {
      rawContact.close();
      if (dataRow != null)
        dataRow.close();
    }
  }
}
