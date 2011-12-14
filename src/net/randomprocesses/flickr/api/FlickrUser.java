package net.randomprocesses.flickr.api;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class FlickrUser {
  private static final String TAG = FlickrUser.class.getSimpleName();
  
  public final String mNsid;
  public final String mUsername;
  public final String mRealname;

  public final boolean mFriend;
  public final boolean mFamily;
  
  public FlickrUser(JSONObject object, boolean fromContact) throws JSONException {
    if (Log.isLoggable(TAG, Log.DEBUG))
      Log.d(TAG, "Attempting to create FlickrUser from JSON: " + object.toString(2));
    if (fromContact) {
      mNsid      = object.getString("nsid");
      mUsername  = object.getString("username");
      mRealname  = object.getString("realname").length() > 0 ? object.getString("realname") : mUsername;
  
      mFriend = object.getString("friend").equals("1");
      mFamily = object.getString("family").equals("1");
    } else {
      // TODO This needs cleaning up.
      JSONObject person = object.getJSONObject("person");
      mNsid = person.getString("nsid");
      mUsername = person.getJSONObject("username").getString("_content");
      mRealname = person.getJSONObject("realname").getString("_content");
      mFriend = false;
      mFamily = false;
    }
    if (Log.isLoggable(TAG, Log.DEBUG))
      Log.d(TAG, "Created: " + this.toString());
  }
  
  @Override
  public String toString() {
    StringBuffer buf = new StringBuffer();
    buf.append("FlickrUser: [nsid: \"").append(mNsid)
    .append("\", username: \"").append(mUsername)
    .append("\", realname: \"").append(mRealname)
    .append("\"]");
    return buf.toString();
  }
}
