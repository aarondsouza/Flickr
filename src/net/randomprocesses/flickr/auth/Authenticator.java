package net.randomprocesses.flickr.auth;

import java.io.IOException;

import oauth.signpost.OAuthConsumer;

import org.apache.http.ParseException;
import org.json.JSONException;

import net.randomprocesses.flickr.api.FlickrAPI;
import net.randomprocesses.flickr.api.FlickrAPI.FlickrAPIException;
import net.randomprocesses.flickr.api.FlickrAPI.FlickrOAuthException;
import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class Authenticator extends AbstractAccountAuthenticator {
  private static final String TAG = Authenticator.class.getSimpleName();
  
  private final Context mContext;
  
  public Authenticator(Context context) {
    super(context);
    mContext = context;
  }

  @Override
  public Bundle addAccount(AccountAuthenticatorResponse response,
                           String accountType, String authTokenType,
                           String[] requiredFeatures, Bundle options)
      throws NetworkErrorException {
    Intent intent = new Intent(mContext, AuthenticatorActivity.class);
    intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
    Bundle bundle = new Bundle();
    bundle.putParcelable(AccountManager.KEY_INTENT, intent);
    return bundle;
  }

  @Override
  public Bundle confirmCredentials(AccountAuthenticatorResponse response,
                                   Account account, Bundle options)
      throws NetworkErrorException {
    final AccountManager accountManager = AccountManager.get(mContext);
    OAuthConsumer consumer = FlickrAPI.getFlickrConsumer();
    consumer.setTokenWithSecret(
        accountManager.getUserData(account, "token"),
        accountManager.getUserData(account, "tokenSecret"));
    boolean validCredentials = false;
    try {
      // TODO Do we need to make this asynchronous?
      validCredentials = FlickrAPI.checkToken(consumer).has("user");
    } catch (ParseException e) {
      e.printStackTrace();
    } catch (JSONException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (FlickrAPIException e) {
      e.printStackTrace();
    } catch (FlickrOAuthException e) {
      e.printStackTrace();
    }
    final Bundle bundle = new Bundle();
    bundle.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, validCredentials);
    return bundle;
  }

  @Override
  public Bundle editProperties(AccountAuthenticatorResponse response,
                               String accountType) {
    // There are no properties of the account that we can edit, so we just return
    // an empty Bundle to signal success.
    return new Bundle();
  }

  @Override
  public Bundle getAuthToken(AccountAuthenticatorResponse response,
                             Account account, String authTokenType,
                             Bundle options) throws NetworkErrorException {
    final AccountManager accountManager = AccountManager.get(mContext);
    final OAuthConsumer consumer = FlickrAPI.getFlickrConsumer();
    consumer.setTokenWithSecret(
        accountManager.getUserData(account, "token"),
        accountManager.getUserData(account, "tokenSecret"));
    try {
      if (FlickrAPI.checkToken(consumer).has("user")) {
        Log.i(TAG, "AuthToken validated.");
        final Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        result.putString(AccountManager.KEY_AUTHTOKEN, consumer.getTokenSecret());
        return result;
      }
    } catch (Exception e) {
      Log.e(TAG, e.toString());
    }
    
    // We've reached here because we couldn't check that the token was valid.
    // get a mini-token from the user and create a new account.
    final Intent intent = new Intent(mContext, AuthenticatorActivity.class);
    intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
    final Bundle bundle = new Bundle();
    bundle.putParcelable(AccountManager.KEY_INTENT, intent);
    return bundle;
  }

  @Override
  public String getAuthTokenLabel(String authTokenType) {
    // TODO Auto-generated method stub
    Log.i(TAG, "getAuthTokenLabel()");
    return null;
  }

  @Override
  public Bundle hasFeatures(AccountAuthenticatorResponse response,
                            Account account, String[] features)
      throws NetworkErrorException {
    // TODO Auto-generated method stub
    Log.i(TAG, "hasFeatures()");
    return null;
  }

  @Override
  public Bundle updateCredentials(AccountAuthenticatorResponse response,
                                  Account account, String authTokenType,
                                  Bundle options) throws NetworkErrorException {
    // TODO Auto-generated method stub
    Log.i(TAG, "updateCredentials()");
    return null;
  }
}
