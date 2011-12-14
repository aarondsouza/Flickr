package net.randomprocesses.flickr.api;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import net.randomprocesses.flickr.api.FlickrPhoto.PhotoSize;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.http.AndroidHttpClient;
import android.text.TextUtils;
import android.util.Log;

public class FlickrAPI {
  private static final String TAG = FlickrAPI.class.getSimpleName();
  
  private static final String API_KEY_VALUE = "0e9e1ca1ac8be5729304d19c0046251a";
  private static final String API_SECRET = "1379fbc248610e24";  
  
  private static final String METHOD_KEY = "method";
  private static final String EXTRAS_KEY = "extras";
  
  private static HttpClient sHttpClient;
  private static final int REGISTRATION_TIMEOUT = 30 * 1000; // ms
  
  // These 3 URLs are for the OAuth 3-step handshake.
  private static final String REQUEST_TOKEN_ENDPOINT_URL = "http://www.flickr.com/services/oauth/request_token";
  private static final String ACCESS_TOKEN_ENDPOINT_URL = "http://www.flickr.com/services/oauth/access_token";
  private static final String AUTHORIZE_WEBSITE_URL = "http://www.flickr.com/services/oauth/authorize";
  // This is the callback URL that Flickr sends the user to after they authorize the app. The
  // scheme is "flickr://" which is explicitly handled by this app's AuthenticatorActivity.
  private static final String CALLBACK_URL = "flickr://www.randomprocesses.net/flickr/";
  // Once we've obtained an auth-token, all Flickr API calls are made via this URL.
  private static final String OAUTH_API_URL = "http://api.flickr.com/services/rest";
  
  public static OAuthConsumer getFlickrConsumer() {
    OAuthConsumer consumer = new CommonsHttpOAuthConsumer(API_KEY_VALUE, API_SECRET);
    return consumer;
  }
  
  public static OAuthProvider getFlickrProvider() {
    OAuthProvider provider = new CommonsHttpOAuthProvider(
        REQUEST_TOKEN_ENDPOINT_URL,
        ACCESS_TOKEN_ENDPOINT_URL,
        AUTHORIZE_WEBSITE_URL);
    provider.setOAuth10a(true);
    return provider;
  }
  
  static {
    sHttpClient = AndroidHttpClient.newInstance("Android");
    final HttpParams params = sHttpClient.getParams();
    HttpConnectionParams.setConnectionTimeout(params, REGISTRATION_TIMEOUT);
    HttpConnectionParams.setSoTimeout(params, REGISTRATION_TIMEOUT);
    ConnManagerParams.setTimeout(params, REGISTRATION_TIMEOUT);    
  }
  
  @SuppressWarnings("serial")
  public static class FlickrAPIException extends Exception {
    public final int mErrorCode;
    public FlickrAPIException(final String message, final int code) {
      super(message);
      mErrorCode = code;
    }
  }
  
  @SuppressWarnings("serial")
  public static class FlickrOAuthException extends Exception {
    public FlickrOAuthException(final String message) {
      super(message);
    }
  }
  
  private static ArrayList<BasicNameValuePair> getStandardParameters() {
    final ArrayList<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
    params.add(new BasicNameValuePair("format", "json"));
    params.add(new BasicNameValuePair("nojsoncallback", "1"));
    params.add(new BasicNameValuePair("api_key", API_KEY_VALUE));
    return params;
  }
  
  public static String getLoginURL(final OAuthConsumer consumer) throws OAuthMessageSignerException, OAuthNotAuthorizedException, OAuthExpectationFailedException, OAuthCommunicationException {
    Log.i(TAG, "Current token/secret: " + consumer.getToken() + "/" + consumer.getTokenSecret());
    String url = getFlickrProvider().retrieveRequestToken(consumer, CALLBACK_URL);
    Log.i(TAG, "New token/secret: " + consumer.getToken() + "/" + consumer.getTokenSecret());
    return url + "&perms=read";
  }
  
  public static JSONObject checkToken(final OAuthConsumer consumer)
      throws ParseException, JSONException, IOException, FlickrAPIException, FlickrOAuthException {
    final ArrayList<BasicNameValuePair> params = getStandardParameters();
    params.add(new BasicNameValuePair(METHOD_KEY, "flickr.test.login"));

    return executeSignedRequest(params, consumer);
  }
  
  public static final ArrayList<FlickrUser> getContacts(final OAuthConsumer consumer)
      throws ParseException, JSONException, IOException, FlickrAPIException, FlickrOAuthException {
    final ArrayList<BasicNameValuePair> params = getStandardParameters();
    params.add(new BasicNameValuePair(METHOD_KEY, "flickr.contacts.getList"));

    final JSONArray contactsArray =
      executeSignedRequest(params, consumer).getJSONObject("contacts").getJSONArray("contact");
    if (Log.isLoggable(TAG, Log.VERBOSE))
      Log.v(TAG, contactsArray.toString(2));
    
    final ArrayList<FlickrUser> users = new ArrayList<FlickrUser>();
    for (int i = 0; i < contactsArray.length(); ++i) {
      try {
        users.add(new FlickrUser(contactsArray.getJSONObject(i), true));
      } catch (final JSONException e) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          e.printStackTrace();
          Log.e(TAG, "Skipping contact: " + contactsArray.getJSONObject(i).toString(2));
        }
      }
    }
    
    return users;
  }

  public static final JSONObject getUserInfo(final OAuthConsumer consumer, final String nsid)
      throws ParseException, FlickrAPIException, JSONException, IOException, FlickrOAuthException {
    final ArrayList<BasicNameValuePair> params = getStandardParameters();
    params.add(new BasicNameValuePair(METHOD_KEY, "flickr.people.getInfo"));
    params.add(new BasicNameValuePair("user_id", nsid));
    
    return executeSignedRequest(params, consumer);
  }
  
  public static final ArrayList<FlickrPhoto> getContactsPhotos(final OAuthConsumer consumer, final int perPage)
      throws ParseException, JSONException, IOException, FlickrAPIException, FlickrOAuthException {
    final ArrayList<BasicNameValuePair> params = getStandardParameters();
    params.add(new BasicNameValuePair(METHOD_KEY, "flickr.photos.getContactsPhotos"));
    params.add(new BasicNameValuePair(EXTRAS_KEY, "date_upload"));
    params.add(new BasicNameValuePair("single_photo", "1"));  // TODO: Argument to the method?
    params.add(new BasicNameValuePair("include_self", "1"));
    params.add(new BasicNameValuePair("count", String.valueOf(perPage)));
    
    return parsePhotoList(executeSignedRequest(params, consumer));
  }
  
  public static final JSONObject getActivityOnUserPhotos(final OAuthConsumer consumer)
      throws ParseException, JSONException, IOException, FlickrAPIException, FlickrOAuthException {
    final ArrayList<BasicNameValuePair> params = getStandardParameters();
    params.add(new BasicNameValuePair(METHOD_KEY, "flickr.activity.userPhotos"));
    return executeSignedRequest(params, consumer);
  }

  public static final ArrayList<FlickrPhoto> getUserPhotos(final OAuthConsumer consumer, final String nsid,
                                                           final int perPage)
      throws ParseException, JSONException, IOException, FlickrAPIException, FlickrOAuthException {
    final ArrayList<BasicNameValuePair> params = getStandardParameters();
    params.add(new BasicNameValuePair(METHOD_KEY, "flickr.people.getPhotos"));
    params.add(new BasicNameValuePair(EXTRAS_KEY, "date_upload,url_o"));

    // Add the nsid of the user whose stream we'd like to get photos from.
    params.add(new BasicNameValuePair("user_id", nsid));
    // Add the number of photos we want to sync to.
    params.add(new BasicNameValuePair("per_page", String.valueOf(perPage)));
    return parsePhotoList(executeSignedRequest(params, consumer));
  }
  
  public static final HashMap<PhotoSize.Size, PhotoSize> getPhotoSizes(final OAuthConsumer consumer, final String photoId)
      throws FlickrAPIException, ParseException, JSONException, IOException, FlickrOAuthException {
    final ArrayList<BasicNameValuePair> params = getStandardParameters();
    params.add(new BasicNameValuePair(METHOD_KEY, "flickr.photos.getSizes"));
    params.add(new BasicNameValuePair("photo_id", photoId));

    final HashMap<PhotoSize.Size, PhotoSize> sizes = new HashMap<PhotoSize.Size, PhotoSize>();
    JSONArray sizeArray;
    sizeArray = executeSignedRequest(params, consumer).getJSONObject("sizes").getJSONArray("size");
    for (int i = 0; i < sizeArray.length(); ++i) {
      final JSONObject size = sizeArray.getJSONObject(i);
      final PhotoSize photoSize = new PhotoSize();
      photoSize.mUrl = new URL(size.getString("source"));
      photoSize.mHeight = size.getInt("height");
      photoSize.mWidth = size.getInt("width");
      final String label = size.getString("label");
      if (label.equals("Square"))
        sizes.put(PhotoSize.Size.SQUARE, photoSize);
      else if (label.equals("Thumbnail"))
        sizes.put(PhotoSize.Size.THUMBNAIL, photoSize);
      else if (label.equals("Small"))
        sizes.put(PhotoSize.Size.SMALL, photoSize);
      else if (label.equals("Medium"))
        sizes.put(PhotoSize.Size.MEDIUM, photoSize);
      else if (label.equals("Medium 640"))
        sizes.put(PhotoSize.Size.MEDIUM_640, photoSize);
      else if (label.equals("Large"))
        sizes.put(PhotoSize.Size.LARGE, photoSize);
      else if (label.equals("Original"))
        sizes.put(PhotoSize.Size.ORIGINAL, photoSize);
    }
    return sizes;
  }
  
  public static final ArrayList<FlickrPhoto> search(final OAuthConsumer consumer, final String nsid, final String tags,
                                                    final String group, final int perPage)
      throws FlickrAPIException, ParseException, JSONException, IOException, FlickrOAuthException {
    final ArrayList<BasicNameValuePair> params = getStandardParameters();
    params.add(new BasicNameValuePair(METHOD_KEY, "flickr.photos.search"));
    params.add(new BasicNameValuePair(EXTRAS_KEY, "date_upload,url_o"));
    // Treat specified tags as an AND operation (other acceptable value is "any").
    // TODO: Make this something that's exposed in the UI during stream-creation.
    params.add(new BasicNameValuePair("tag_mode", "all"));
    // Add an nsid (user) specification if we have one.
    if (!TextUtils.isEmpty(nsid))
      params.add(new BasicNameValuePair("user_id", nsid));
    // If we have tags, add those too.
    if (!TextUtils.isEmpty(tags))
      params.add(new BasicNameValuePair("tags", tags));
    // IF we have a group, add that.
    if (!TextUtils.isEmpty(group))
      params.add(new BasicNameValuePair("group_id", group));    
    // Add the number of photos we want to sync to.
    params.add(new BasicNameValuePair("per_page", String.valueOf(perPage)));
    
    return parsePhotoList(executeSignedRequest(params, consumer));
  }
  
  public static final ArrayList<FlickrPhoto> parsePhotoList(final JSONObject response)
      throws JSONException {
    final JSONArray photoArray = response.getJSONObject("photos").getJSONArray("photo");
    final ArrayList<FlickrPhoto> photos = new ArrayList<FlickrPhoto>();
    for (int i = 0; i < photoArray.length(); ++i) {
      try {
        photos.add(new FlickrPhoto(photoArray.getJSONObject(i)));
      } catch (final JSONException e) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          e.printStackTrace();
          Log.e(TAG, "Skipping photo: " + photoArray.getJSONObject(i).toString(2));
        }        
      }
    }
    return photos;
  }
  
  public static JSONObject executeSignedRequest(final ArrayList<BasicNameValuePair> params, final OAuthConsumer consumer)
      throws FlickrAPIException, ParseException, JSONException, IOException, FlickrOAuthException {
    final HttpEntity entity = new UrlEncodedFormEntity(params);
    final HttpPost post = new HttpPost(OAUTH_API_URL);
    post.addHeader(entity.getContentType());
    post.setEntity(entity);
    try {
      consumer.sign(post);
    } catch (OAuthMessageSignerException e) {
      FlickrOAuthException t = new FlickrOAuthException(e.getMessage());
      t.initCause(e);
      throw t;
    } catch (OAuthExpectationFailedException e) {
      FlickrOAuthException t = new FlickrOAuthException(e.getMessage());
      t.initCause(e);
      throw t;
    } catch (OAuthCommunicationException e) {
      FlickrOAuthException t = new FlickrOAuthException(e.getMessage());
      t.initCause(e);
      throw t;
    }

    final HttpResponse resp = sHttpClient.execute(post);
    if (resp.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
      throw new IOException("Received status-code that was not HttpStatus.SC_OK: " + resp.getStatusLine());
    }    
    final JSONObject responseObject = new JSONObject(EntityUtils.toString(resp.getEntity()));
    if (!responseObject.getString("stat").equals("ok")) {
      if (Log.isLoggable(TAG, Log.ERROR))
        Log.e(TAG, "Response indicates failure: " + responseObject.toString(2));
      throw new FlickrAPIException(responseObject.getString("message"),
          responseObject.getInt("code"));
    }
    if (Log.isLoggable(TAG, Log.DEBUG))
      Log.d(TAG, responseObject.toString(2));
    return responseObject;
  }
  
  public static boolean isAuthenticationError(final FlickrAPIException e) {
    return (e.mErrorCode == 98  /* auth token invalid */ ||
            e.mErrorCode == 99  /* user not logged in, insufficient permissions */);
  }
}
