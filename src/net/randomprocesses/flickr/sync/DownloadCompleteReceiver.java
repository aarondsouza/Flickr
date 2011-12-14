package net.randomprocesses.flickr.sync;

import java.io.File;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class DownloadCompleteReceiver extends BroadcastReceiver {
  private static final String TAG = "DownloadComplete";

  @Override
  public void onReceive(Context context, Intent intent) {
    final long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
    if (downloadId < 0) {
      if (Log.isLoggable(TAG, Log.ERROR))
        Log.e(TAG, "Received download-completed intent without EXTRA_DOWNLOAD_ID data. Ignoring.");
      return;
    }

    Log.i(TAG, intent.toString() + " Extra-keys: " + intent.getExtras().keySet());

    // Query the DownloadManager to get the filename.
    DownloadManager.Query query = new DownloadManager.Query();
    query.setFilterById(downloadId);
    DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    Cursor c = downloadManager.query(query);
    try {
      if (!c.moveToFirst()) {
        if (Log.isLoggable(TAG, Log.ERROR))
          Log.e(TAG, "No download found with id: " + String.valueOf(downloadId));
        return;
      }

      for (String name : c.getColumnNames()) {
        Log.v(TAG, name + ": " + c.getString(c.getColumnIndex(name)));
      }

      final String localUriString = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
      if (localUriString == null) {
        Log.e(TAG, "Could not get String value of COLUMN_LOCAL_URI. Aborting.");
        return;
      }
      final Uri fileUri = Uri.parse(localUriString);
      final String filePath = fileUri.getPath();
      // Check that the downloaded content corresponds to our Flickr app.
      if (!filePath.contains("net.randomprocesses.flickr")) {
        if (Log.isLoggable(TAG, Log.DEBUG))
          Log.d(TAG, "Ignoring non-Flickr download: " + fileUri.toString());
        return;
      }

      final int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
      if (status != DownloadManager.STATUS_SUCCESSFUL) {
        Log.e(TAG, "Download status something other than STATUS_SUCCESSFUL: " + status);
        if (status == DownloadManager.STATUS_FAILED || status == DownloadManager.STATUS_PAUSED)
          Log.e(TAG, "Reason code: " + c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON)));
        return;
      }
      
      Log.i(TAG, "Checking if exists: " + filePath);
      File file = new File(filePath);
      if (!file.exists() || file.length() == 0) {
        Log.e(TAG, "Received DOWNLOAD_COMPLETE with STATUS_SUCCESSFUL but no file at: " + filePath);
        return;
      } else {
        Log.i(TAG, "File exists with nonzero size.");
      }
      
      // Broadcast the intent to get the MediaScanner to catch our file.
      Log.i(TAG, "Broadcasting ACTION_MEDIA_SCANNER_SCAN_FILE for file: " + fileUri.toString());

      context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, fileUri));
    } finally {
      c.close();
    }
  }
}
