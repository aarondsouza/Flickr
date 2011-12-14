package net.randomprocesses.flickr.sync;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

public class ContactsProfileActivity extends Activity {
  private static final String TAG = ContactsProfileActivity.class.getSimpleName();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (Log.isLoggable(TAG, Log.DEBUG))
      Log.d(TAG, "Redirecting to the browser for contact: " + getIntent().getDataString());
    
    final ContentResolver contentResolver = getContentResolver();
    
    // Grab the data record and pull out the raw-contact id.
    final Cursor dataCursor =
        contentResolver.query(getIntent().getData(), new String[] {Data.RAW_CONTACT_ID}, null, null, null);
    Cursor rawDataCursor = null;
    try {
      if (!dataCursor.moveToFirst())
        return;
      
      // Using the raw-contact id, grab the raw-contact record and pull out the NSID.
      rawDataCursor =
          contentResolver.query(ContentUris.withAppendedId(RawContacts.CONTENT_URI, dataCursor.getLong(0)),
                                new String[] {RawContacts.SOURCE_ID}, null, null, null);
      if (rawDataCursor.moveToFirst()) {
        // Tack the NSID onto the end of the URL and kick off a browser viewing activity.
        final String nsid = rawDataCursor.getString(0);
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://m.flickr.com/#/photos/" + nsid + "/")));
      }
    } finally {
      dataCursor.close();
      if (rawDataCursor != null)
        rawDataCursor.close();
      finish();
    }
  }
}
