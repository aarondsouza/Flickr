package net.randomprocesses.flickr.api;

import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

import android.text.TextUtils;
import android.util.Log;

public class FlickrPhoto {
  private static final String TAG = FlickrPhoto.class.getSimpleName();
  
  public final String mId;
  public final String mOwner;
  public final int    mFarm;
  public final String mServer;
  public final String mSecret;
  public final long   mDateUpload;
  public final String mTitle;
  public final URL    mOriginalURL;
  
  public static class PhotoSize {
    public enum Size {
      ORIGINAL,
      LARGE,
      MEDIUM_640,
      MEDIUM,
      SMALL,
      THUMBNAIL,
      SQUARE,
    }
    public int mHeight;
    public int mWidth;
    public URL mUrl;
  }
  
  private static URL makeURL(String url) {
    try {
      return new URL(url);
    } catch (MalformedURLException e) {
      return null;
    }
  }
  
  public FlickrPhoto(JSONObject photo) throws JSONException {
    if (Log.isLoggable(TAG, Log.DEBUG))
      Log.d(TAG, "Creating FlickrPhoto from JSON: " + photo.toString(2));
    mId         = photo.getString("id");
    mOwner      = photo.getString("owner");
    mFarm       = photo.getInt("farm");
    mServer     = photo.getString("server");
    mSecret     = photo.getString("secret");
    mDateUpload = Integer.decode(photo.getString("dateupload")).intValue();
    mTitle      = (TextUtils.isEmpty(photo.getString("title")) ? "" : photo.getString("title"));
    mOriginalURL = (!photo.has("url_o") || TextUtils.isEmpty(photo.getString("url_o")) ? null : makeURL(photo.getString("url_o")));
    
    if (Log.isLoggable(TAG, Log.DEBUG))
      Log.d(TAG, "Created: " + this.toString());
  }
  
  @Override
  public String toString() {
    StringBuffer buf = new StringBuffer();
    buf.append("FlickrPhoto: [id: \"").append(mId)
    .append("\", owner: ").append(mOwner)
    .append("\", farm: ").append(mFarm)
    .append(", server: \"").append(mServer)
    .append("\", secret: \"").append(mSecret)
    .append("\", dateupload: ").append(mDateUpload)
    .append(", title: \"").append(mTitle)
    .append("\"]");
    return buf.toString();
  }
  
  public URL generatePhotoURL(final String size /*may be null*/) {
    try {
      return new URL("http://farm" + mFarm + ".static.flickr.com/" + mServer + "/" +
                     mId + "_" + mSecret + (size == null ? "" : "_" + size) + ".jpg");
    } catch (MalformedURLException e) {
      e.printStackTrace();
      return null;
    }
  }
  
  public URL generateFlickrURL(final String photoId) {
    return null;
  }
}
