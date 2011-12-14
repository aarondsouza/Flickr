package net.randomprocesses.flickr.sync;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import oauth.signpost.OAuthConsumer;

import net.randomprocesses.flickr.api.FlickrAPI;
import net.randomprocesses.flickr.api.FlickrAPI.FlickrAPIException;
import net.randomprocesses.flickr.api.FlickrPhoto;
import net.randomprocesses.flickr.api.FlickrPhoto.PhotoSize;
import net.randomprocesses.flickr.prefs.PreferenceKeys;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.DownloadManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

public class PhotosSyncAdapter extends AbstractThreadedSyncAdapter {
  private static final String TAG = "PhotosSyncAdapter";
  private Context mContext = null;
  private DownloadManager mDownloadManager = null;
  private ContentResolver mContentResolver = null;

  public PhotosSyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);
    mContext = context;
    mDownloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    mContentResolver = context.getContentResolver();
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority,
                            ContentProviderClient provider, SyncResult syncResult) {
    // Check that we can write to the sdcard space.
    if (!mediaAvailableAndWriteable()) {
      if (Log.isLoggable(TAG, Log.WARN))
        Log.w(TAG, "Media not available/writeable. Skipping sync.");
      return;
    }
    
    final String accountOwnerNsid = AccountManager.get(mContext).getUserData(account, "nsid");
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    final String[] tags =
        TextUtils.split(prefs.getString(PreferenceKeys.SYNC_TAGS(accountOwnerNsid), ""), ",");
    final int allowedNetworkTypes =
        (prefs.getBoolean(PreferenceKeys.SYNC_TAGS_ON_WIFI(accountOwnerNsid), true) ?
         DownloadManager.Request.NETWORK_WIFI :
         (DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI));
    final HashSet<String> validTags = new HashSet<String>();
    final File picturesDir = mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    final AccountManager accountManager = AccountManager.get(mContext);
    try {
      final String authToken =
        accountManager.blockingGetAuthToken(account, "", true /*notifyAuthFailure*/);
      if (authToken == null) {
        Log.e(TAG, "Could not retrieve auth-token. Abandoning photos-sync.");
        return;
      }
      OAuthConsumer consumer = FlickrAPI.getFlickrConsumer();
      consumer.setTokenWithSecret(accountManager.getUserData(account, "token"), authToken);
      // For each tag, get its list of pictures.
      for (String tag : tags) {
        ArrayList<FlickrPhoto> photos = null;
        try {
          photos = FlickrAPI.search(consumer, accountOwnerNsid, tag.trim(), null, 100);
        } catch (FlickrAPIException e) {
          if (FlickrAPI.isAuthenticationError(e)) {
            if (Log.isLoggable(TAG, Log.WARN))
              Log.w(TAG, "Invalidating token " + authToken + " for account: " + account.name);
            accountManager.invalidateAuthToken(account.type, authToken);
          }
          if (Log.isLoggable(TAG, Log.ERROR))
            Log.e(TAG, "Abandoning photos sync for account: " + account.name);
          return;
        }

        validTags.add(tag);
        
        // This subdirectory (under the basepath) will contain the pictures for this
        // account/tag combination.
        final String subDirUnderPictures = accountOwnerNsid + "/" + tag;
        final File destinationDir = new File(picturesDir.getAbsolutePath() + "/" + subDirUnderPictures);
        if (!destinationDir.exists()) {
          if (!destinationDir.mkdirs()) {
            if (Log.isLoggable(TAG, Log.ERROR))
              Log.e(TAG, "Could not create directory: " + destinationDir.getAbsolutePath());
            continue;
          } else {
            if (Log.isLoggable(TAG, Log.DEBUG))
              Log.d(TAG, "Created directory: " + destinationDir.getAbsolutePath());
          }
        }

        // This is the set of photos we should end up with in the directory.
        HashSet<String> photosToHave = new HashSet<String>();
        for (FlickrPhoto photo : photos) {
          photosToHave.add(photo.mId + ".jpg");
        }
        
        // Keep track of photos which we already have.
        String[] existingPhotos = destinationDir.list();
        HashSet<String> existingPhotosSet = new HashSet<String>();
        for (String filename : existingPhotos) {
          if (photosToHave.contains(filename)) {
            existingPhotosSet.add(filename);
            continue;
          }
          // For each photo already in the directory, if we don't need it, delete it.
          final File f = new File(destinationDir.getAbsolutePath() + "/" + filename);
          if (Log.isLoggable(TAG, Log.WARN))
            Log.w(TAG, "Deleting: " + f.getAbsolutePath());
          if (!f.delete() && Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "Could not delete file: " + f.getAbsolutePath());
            continue;
          }
          
          // Delete the row from the Images table so we immediately reflect the deletion in the Gallery.
          mContentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                  MediaStore.Images.Media.DATA + "=?",
                                  new String[] {f.getAbsolutePath()});
          // Seems like the Thumbnails table automatically gets in sync with the Media table
        }
        
        // For each photo that isn't already in the directory, download it.
        for (FlickrPhoto photo : photos) {
          if (photo.mOriginalURL == null) {
            if (Log.isLoggable(TAG, Log.ERROR))
              Log.e(TAG, "Photo doesn't have original URL. Skipping: " + photo.mId);
            continue;
          }
          final String filename = photo.mId + ".jpg";
          // Don't bother downloading if the picture already exists.
          if (existingPhotosSet.contains(filename)) {
            // But make sure that the MediaStore has an entry for this file.
            ensureMediaStoreKnowsAboutFile(destinationDir.getAbsolutePath() + "/" + filename);
            continue;
          }
          
          // Try to retrieve the max allowed photo size to download.
          final PhotoSize photoSize = getBestAvailablePhotoSize(consumer, photo.mId);
          if (photoSize == null) {
            if (Log.isLoggable(TAG, Log.ERROR))
              Log.e(TAG, "Could not get a download size for photo: " + photo.mId);
            continue;
          }
          
          // Otherwise, construct and queue a request.
          final DownloadManager.Request request =
              new DownloadManager.Request(Uri.parse(photoSize.mUrl.toString()));
          request
            .setDestinationInExternalFilesDir(mContext, Environment.DIRECTORY_PICTURES, subDirUnderPictures + "/" + filename)
            .setAllowedNetworkTypes(allowedNetworkTypes)
            .setShowRunningNotification(false)
            .setVisibleInDownloadsUi(false)
            .setAllowedOverRoaming(false);
          final long requestId = mDownloadManager.enqueue(request);
          if (Log.isLoggable(TAG, Log.INFO))
            Log.i(TAG, "Creating download request " + requestId + " for file: " + filename + " from URL: " + photoSize.mUrl);
        }
      }
      
      // TODO: Delete files from unwanted tags (use validTags).
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private PhotoSize getBestAvailablePhotoSize(final OAuthConsumer consumer, final String photoId) {
    HashMap<PhotoSize.Size, PhotoSize> sizes;
    try {
      sizes = FlickrAPI.getPhotoSizes(consumer, photoId);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
    // The sizes are in descending order, so the largest available is returned.
    final PhotoSize.Size[] preferredSizeTypes =
        new PhotoSize.Size[] {PhotoSize.Size.LARGE, PhotoSize.Size.MEDIUM_640,
                              PhotoSize.Size.MEDIUM, PhotoSize.Size.ORIGINAL};
    for (PhotoSize.Size sizeType : preferredSizeTypes)
      if (sizes.containsKey(sizeType)) {
        if (Log.isLoggable(TAG, Log.DEBUG))
          Log.d(TAG, "Picking " + sizeType.name() + " size for photo: " + photoId);
        return sizes.get(sizeType);
      }
    return null;
  }

  private void ensureMediaStoreKnowsAboutFile(final String file) {
    Cursor cursor = mContentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                           new String[] {MediaStore.Images.Media._ID},
                                           MediaStore.Images.Media.DATA + "=?", new String[] {file}, null);
    try {
      // If we actually return a row for this file, then the MediaStore knows about it.
      // Nothing more to do.
      if (cursor.moveToFirst())
        return;
      
      // Scan the file.
      MediaScannerConnection.scanFile(mContext, new String[] {file}, null, new MediaScannerConnection.OnScanCompletedListener() {
        @Override
        public void onScanCompleted(String path, Uri uri) {
          if (uri != null)
            Log.i(TAG, "Successfully scanned " + path + " to URI: " + uri.toString());
          else
            Log.e(TAG, "Failed to scan " + path + ".");
        }
      });
    } finally {
      cursor.close();
    }
  }

  private static boolean mediaAvailableAndWriteable() {
    boolean mExternalStorageAvailable = false;
    boolean mExternalStorageWriteable = false;
    String state = Environment.getExternalStorageState();

    if (Environment.MEDIA_MOUNTED.equals(state)) {
      // We can read and write the media
      mExternalStorageAvailable = mExternalStorageWriteable = true;
    } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
        // We can only read the media
      mExternalStorageAvailable = true;
      mExternalStorageWriteable = false;
    } else {
      // Something else is wrong. It may be one of many other states, but all we need
      //  to know is we can neither read nor write
      mExternalStorageAvailable = mExternalStorageWriteable = false;
    }
    return mExternalStorageAvailable && mExternalStorageWriteable;
  }
}
