package net.randomprocesses.flickr.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import net.randomprocesses.flickr.R;
import net.randomprocesses.flickr.api.FlickrAPI;
import net.randomprocesses.flickr.api.FlickrAPI.FlickrAPIException;
import net.randomprocesses.flickr.api.FlickrAPI.FlickrOAuthException;
import net.randomprocesses.flickr.api.FlickrUser;
import oauth.signpost.OAuthConsumer;

import org.apache.http.ParseException;
import org.json.JSONException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;

public class ContactsSyncAdapter extends AbstractThreadedSyncAdapter {
  private static final String TAG = ContactsSyncAdapter.class.getSimpleName();
  public static final String AUTHORITY = ContactsContract.AUTHORITY;
  public static final String FLICKR_CONTACT_MIMETYPE = "vnd.android.cursor.item/vnd.com.flickr.android.profile";
  // These two URIs are used when writing/updating.
  private static final Uri RAW_CONTACTS_CONTENT_URI = addCallerIsSyncAdapter(RawContacts.CONTENT_URI);
  private static final Uri DATA_CONTENT_URI = addCallerIsSyncAdapter(Data.CONTENT_URI);
  private final Context mContext;
  private final ContentResolver mContentResolver;
  private final AccountManager mAccountManager;
  
  private static Uri addCallerIsSyncAdapter(final Uri uri) {
    return uri.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
  }
  
  public ContactsSyncAdapter(final Context context, boolean autoInitialize) {
    super(context, autoInitialize);
    mContext = context;
    mContentResolver = mContext.getContentResolver();
    mAccountManager = AccountManager.get(mContext);
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority,
                            ContentProviderClient provider, SyncResult syncResult) {
    Log.d(TAG,
        "onPerformSync() called for account: (" + account.name +
        ", " + account.type + "), with authority: " + authority);
    try {
      // Get the authentication token with which all API calls will be made.
      final String authToken =
        mAccountManager.blockingGetAuthToken(account, "", true /*notifyAuthFailure*/);
      if (authToken == null) {
        Log.e(TAG, "Could not retrieve auth-token. Abandoning contacts-sync.");
        return;
      }
      OAuthConsumer consumer = FlickrAPI.getFlickrConsumer();
      consumer.setTokenWithSecret(mAccountManager.getUserData(account, "token"), authToken);
      
      // Fire off an API call to download contact information.
      final ArrayList<FlickrUser> users = getContacts(account, consumer);
      if (users == null) {
        if (Log.isLoggable(TAG, Log.ERROR))
          Log.e(TAG, "Abandoning sync for account: " + account.name);
        return;
      }

      // Reusable Uri that already encodes the account name/type and that we can use
      // to query for whether our contacts already exist in the RawContacts table.
      final Uri rawContactUri =
          RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
            .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
            .build();
      
      // List that will contain all the ops to be executed as part of this sync.
      final ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
      // We'll keep track of all NSIDs we see from the set of contacts we retrieve from Flickr.
      final HashSet<String> nsids = new HashSet<String>();
      for (FlickrUser user : users) {
        if (Log.isLoggable(TAG, Log.DEBUG))
          Log.d(TAG, user.toString());

        nsids.add(user.mNsid);
        
        // Check if this user already exists.
        final long rawContactId = getRawContactId(mContentResolver, rawContactUri, user.mNsid);
        if (rawContactId >= 0) {
          maybeUpdateContact(ops, user, rawContactId);
        } else {
          addNewContact(account, ops, user);
        }
      }
      
      // Now we delete contacts that we currently have, but which weren't returned by
      // Flickr (which means the user deleted them as contacts on the Flickr site).
      Cursor rawContacts =
          mContentResolver.query(rawContactUri, RawContactsNSIDQuery.PROJECTION, null, null, null);
      try {
        while (rawContacts.moveToNext()) {
          final String rawContactNSID = rawContacts.getString(RawContactsNSIDQuery.COLUMN_NSID);
          if (!nsids.contains(rawContactNSID)) {
            if (Log.isLoggable(TAG, Log.WARN))
              Log.w(TAG, "Deleting contact with NSID: " + rawContactNSID);
            deleteContact(ops, rawContacts.getLong(RawContactsNSIDQuery.COLUMN_ID));
          }
        }
      } finally {
        rawContacts.close();
      }
      
      // Apply all the operations in one go. We need to do this so we have all the necessary rows
      // to generate status updates.
      mContentResolver.applyBatch(authority, ops);

      // Update statuses, since we might have changed which contacts are available.
      StatusSync.updateStatuses(mContext, account);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private ArrayList<FlickrUser> getContacts(final Account account, final OAuthConsumer consumer)
      throws ParseException, JSONException, IOException {
    final String accountOwnerNsid = mAccountManager.getUserData(account, "nsid");
    try {
      ArrayList<FlickrUser> users = FlickrAPI.getContacts(consumer);
      users.add(new FlickrUser(FlickrAPI.getUserInfo(consumer, accountOwnerNsid), false));
      return users;
    } catch (FlickrAPIException e) {
      // In case of a failure, check the error code. Some error codes indicate
      // authentication-related failures, in which case we should inform the
      // AccountManager that this authToken should be flushed from its cache.
      if (FlickrAPI.isAuthenticationError(e)) {
        if (Log.isLoggable(TAG, Log.WARN))
          Log.w(TAG, "Invalidating token for account: " + account.name);
        //mAccountManager.invalidateAuthToken(account.type, authToken);
      }
      return null;
    } catch (FlickrOAuthException e) {
      e.printStackTrace();
    }
    return null;
  }

  private void deleteContact(final ArrayList<ContentProviderOperation> ops, final long rawContactId) {
    Cursor dataRows =
        mContentResolver.query(DataQueryId.URI, DataQueryId.PROJECTION, DataQueryId.QUERY,
                               new String[] {String.valueOf(rawContactId)}, null);
    try {
      boolean yield = true;
      while (dataRows.moveToNext()) {
        final long dataRowId = dataRows.getLong(DataQueryId.COLUMN_ID);
        ops.add(ContentProviderOperation.newDelete(
            addCallerIsSyncAdapter(ContentUris.withAppendedId(Data.CONTENT_URI, dataRowId)))
            .withYieldAllowed(yield)
            .build());
        yield = false;
      }
      ops.add(ContentProviderOperation.newDelete(
          addCallerIsSyncAdapter(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId)))
          .withYieldAllowed(yield)
          .build());
    } finally {
      dataRows.close();
    }
  }
  
  private void addNewContact(final Account account,
                             final ArrayList<ContentProviderOperation> ops,
                             final FlickrUser user) {
    if (Log.isLoggable(TAG, Log.WARN))
      Log.w(TAG, "Adding new contact for user: " + user.toString());
    final int backReferenceID = ops.size();
    // This is a new contact. Add the operations to insert it.
    ops.add(ContentProviderOperation.newInsert(RAW_CONTACTS_CONTENT_URI)
        .withValue(RawContacts.ACCOUNT_NAME, account.name)
        .withValue(RawContacts.ACCOUNT_TYPE, account.type)
        .withValue(RawContacts.SOURCE_ID, user.mNsid)
        .withYieldAllowed(true)
        .build());
    ops.add(ContentProviderOperation.newInsert(DATA_CONTENT_URI)
        .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceID)
        .withValue(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, user.mRealname)
        .withYieldAllowed(false)
        .build());
    // TODO Potentially we could also add the NSID into the mix, although it would mean replication
    // since it's already present in the RawContacts SOURCE_ID column. Unclear if this would simplify
    // things in the future since the replication would mean one less lookup into the RawContacts table
    // to get at the NSID.
    ops.add(ContentProviderOperation.newInsert(DATA_CONTENT_URI)
        .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceID)
        .withValue(Data.MIMETYPE, FLICKR_CONTACT_MIMETYPE)
        .withValue(ContactsSyncFields.USERNAME, user.mUsername)
        .withValue(ContactsSyncFields.FRIEND, String.valueOf(user.mFriend))
        .withValue(ContactsSyncFields.FAMILY, String.valueOf(user.mFamily))
        .withValue(ContactsSyncFields.SUMMARY, mContext.getString(R.string.flickr_profile))
        .withYieldAllowed(false)
        .build());
  }

  private void maybeUpdateContact(final ArrayList<ContentProviderOperation> ops,
                                  final FlickrUser user, final long rawContactId) {
    // Contact with this NSID already exists. We need to update it.
    Cursor dataCursor =
        mContentResolver.query(DataQueryAll.URI, DataQueryAll.PROJECTION, DataQueryAll.QUERY,
                               new String[] { String.valueOf(rawContactId) }, null);
    try {
      boolean yield = true;
      while (dataCursor.moveToNext()) {
        final ContentValues values = new ContentValues();
        final String mimeType = dataCursor.getString(DataQueryAll.COLUMN_MIMETYPE);
        if (mimeType.equals(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)) {
          addUpdateIfDifferent(values, dataCursor, DataQueryAll.COLUMN_DISPLAY_NAME, CommonDataKinds.StructuredName.DISPLAY_NAME, user.mRealname);
        } else if (mimeType.equals(FLICKR_CONTACT_MIMETYPE)) {
          addUpdateIfDifferent(values, dataCursor, DataQueryAll.COLUMN_USERNAME, ContactsSyncFields.USERNAME, user.mUsername);
          addUpdateIfDifferent(values, dataCursor, DataQueryAll.COLUMN_FRIEND, ContactsSyncFields.FRIEND, String.valueOf(user.mFriend));
          addUpdateIfDifferent(values, dataCursor, DataQueryAll.COLUMN_FAMILY, ContactsSyncFields.FAMILY, String.valueOf(user.mFamily));
        }
        if (values.size() > 0) {
          if (Log.isLoggable(TAG, Log.WARN))
            Log.w(TAG, "Updating user: " + user.toString());
          final Uri dataUri =
              addCallerIsSyncAdapter(ContentUris.withAppendedId(Data.CONTENT_URI, dataCursor.getLong(DataQueryAll.COLUMN_ID)));
          ops.add(ContentProviderOperation.newUpdate(dataUri)
              .withValues(values)
              .withYieldAllowed(yield)
              .build());
          yield = false;
        }
      }
    } finally {
      dataCursor.close();
    }
  }
  
  private void addUpdateIfDifferent(ContentValues values, Cursor dataCursor,
                                    int columnId, String columnName, String newValue) {
    if (TextUtils.equals(dataCursor.getString(columnId), newValue))
      return;
    values.put(columnName, newValue);
  }

  private long getRawContactId(final ContentResolver contentResolver,
                                final Uri rawContactUri, final String nsid) {
    Cursor rawContactCursor =
        contentResolver.query(rawContactUri, new String[] {RawContacts._ID},
                              RawContacts.SOURCE_ID + "=?", new String[] {nsid}, null);
    try {
      return (rawContactCursor.moveToFirst() ? rawContactCursor.getLong(0) : -1);
    } finally {
      rawContactCursor.close();
    }
  }
  
  private interface DataQueryAll {
    public static final Uri URI = Data.CONTENT_URI;
    public static final String QUERY = Data.RAW_CONTACT_ID + "=?";
    public static final String[] PROJECTION = {
      Data._ID,
      Data.MIMETYPE,
      Data.DATA1,
      Data.DATA2,
      Data.DATA3
    };
    public static final int COLUMN_ID = 0;
    public static final int COLUMN_MIMETYPE = 1;
    public static final int COLUMN_DATA1 = 2;
    public static final int COLUMN_DATA2 = 3;
    public static final int COLUMN_DATA3 = 4;
    
    public static final int COLUMN_DISPLAY_NAME = COLUMN_DATA1;
    
    public static final int COLUMN_USERNAME = COLUMN_DATA1;
    public static final int COLUMN_FRIEND = COLUMN_DATA2;
    public static final int COLUMN_FAMILY = COLUMN_DATA3;
  }
  
  private interface DataQueryId {
    public static final Uri URI = Data.CONTENT_URI;
    public static final String QUERY = Data.RAW_CONTACT_ID + "=?";
    public static final String[] PROJECTION = {Data._ID};
    public static final int COLUMN_ID = 0;
  }

  private interface RawContactsNSIDQuery {
    public static final String[] PROJECTION = {RawContacts._ID, RawContacts.SOURCE_ID};
    public static final int COLUMN_ID = 0;
    public static final int COLUMN_NSID = 1;
  }
}
