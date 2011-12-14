package net.randomprocesses.flickr.auth;

import net.randomprocesses.flickr.R;
import net.randomprocesses.flickr.api.FlickrAPI;
import net.randomprocesses.flickr.content.ActivityContentProvider;
import net.randomprocesses.flickr.content.PhotosContentProvider;
import net.randomprocesses.flickr.prefs.PreferenceKeys;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import oauth.signpost.http.HttpParameters;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.util.Log;

// TODO We should probably implement onSaveInstanceState to save away the
// AccountAuthenticatorResponse. In this case we'll probably also have to give
// up inheriting from AccountAuthenticatorActivity and replicate its functionality
// here.
public class AuthenticatorActivity extends AccountAuthenticatorActivity {
  private static final String TAG = AuthenticatorActivity.class.getSimpleName();

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    // Check whether the intent that we're created with was the right one.
    final Intent creationIntent = getIntent();
    if (creationIntent.getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE) == null) {
      Log.e(TAG, "onCreate() called without KEY_ACCOUNT_AUTHENTICATOR_RESPONSE extra. Aborting.");
      Log.e(TAG, creationIntent.toString());
      if (creationIntent.getExtras() != null) {
        Bundle extras = creationIntent.getExtras();
        for (String s : extras.keySet())
          Log.e(TAG, "Key: " + s);
      }
      finish();
      return;
    }

    GetLoginURLTask t = new GetLoginURLTask();
    t.execute();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.i(TAG, "onNewIntent(): " + intent.toString());
    final Uri uri = intent.getData();
    if (uri != null) {
      final String verificationCode = uri.getQueryParameter("oauth_verifier");
      if (verificationCode != null) {
        Log.i(TAG, "Got verificationCode: " + verificationCode);
        LoginTask t = new LoginTask();
        t.execute(verificationCode);
        return;
      }
    }
    // If we reach here, something went wrong.
    finish();
  }
  
  private class GetLoginURLTask extends AsyncTask<Void, Void, String> {
    @Override
    protected String doInBackground(Void... params) {
      try {
        // Create a consumer object we can work with.
        final OAuthConsumer consumer = FlickrAPI.getFlickrConsumer();
        // Getting the login URL also sets a temporary token/secret in the consumer.
        final String url = FlickrAPI.getLoginURL(consumer);
        // Save away the token/secret, so we can exchange it for the auth token/secret later.
        Log.i(TAG, "Saving away token/secret: " + consumer.getToken() + "/" + consumer.getTokenSecret());
        PreferenceManager.getDefaultSharedPreferences(AuthenticatorActivity.this)
          .edit()
          .putString(PreferenceKeys.OAUTH_TEMPORARY_TOKEN, consumer.getToken())
          .putString(PreferenceKeys.OAUTH_TEMPORARY_TOKEN_SECRET, consumer.getTokenSecret())
          .commit();
        return url;
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return null;
    }

    @Override
    protected void onPostExecute(String loginURL) {
      super.onPostExecute(loginURL);
      if (loginURL == null) {
        Log.e(TAG, "Could not OAuth retrieve login URL");
        return;
      }
      // TODO: Save away the temporary token and secret in case this task is killed.

      Uri uri = Uri.parse(loginURL);
      Intent intent = new Intent(Intent.ACTION_VIEW, uri);
      Log.i(TAG, "Redirecting for authentication to: " + loginURL);
      startActivity(intent);
    }
  }
  
  private class LoginTask extends AsyncTask<String, Void, Account> {
    private static final String ACCOUNT_FULLNAME = "fullname";
    private static final String ACCOUNT_NSID = "nsid";
    private static final String ACCOUNT_TOKEN = "token";
    private static final String ACCOUNT_TOKEN_SECRET = "tokenSecret";
    
    // Takes the supplied verification-code and exchanges it for a full authentication token.
    // If this exchange is successful, the method sets up the account and returns
    // an Account object, or null if an error occurred anywhere in the process.
    @Override
    protected Account doInBackground(String... verificationCodes) {
      try {
        final SharedPreferences sharedPreferences =
          PreferenceManager.getDefaultSharedPreferences(AuthenticatorActivity.this);
        final OAuthConsumer consumer = FlickrAPI.getFlickrConsumer();
        // Repopulate with the temporary tokens we got when we asked for a login-URL.
        Log.i(TAG, "Recreating OAuthConsumer with saved temporary token/secret: " +
            sharedPreferences.getString(PreferenceKeys.OAUTH_TEMPORARY_TOKEN, "") + "/" +
            sharedPreferences.getString(PreferenceKeys.OAUTH_TEMPORARY_TOKEN_SECRET, ""));
        consumer.setTokenWithSecret(
            sharedPreferences.getString(PreferenceKeys.OAUTH_TEMPORARY_TOKEN, ""),
            sharedPreferences.getString(PreferenceKeys.OAUTH_TEMPORARY_TOKEN_SECRET, ""));
        // Now exchange the temporary tokens for auth-tokens.
        final OAuthProvider provider = FlickrAPI.getFlickrProvider();
        provider.retrieveAccessToken(consumer, verificationCodes[0]);
        Log.i(TAG, "New token/secret: " + consumer.getToken() + "/" + consumer.getTokenSecret());

        // Grab account owner info from the response.
        final HttpParameters responseParams = provider.getResponseParameters();
        final String fullname = responseParams.getFirst("fullname");
        final String username = responseParams.getFirst("username");
        final String nsid     = responseParams.getFirst("user_nsid");
        if (username == null || nsid == null) {
          Log.e(TAG, "Could not retrieve username or nsid. Abandoning account creation");
          return null;
        }
        Log.i(TAG, "Retrieved auth-tokens for account with username: " + username);
        
        final Account account = new Account(username, getString(R.string.account_type));
        final AccountManager accountManager = AccountManager.get(AuthenticatorActivity.this);
        
        if (accountManager.addAccountExplicitly(account, null, null)) {
          Log.i(TAG, "New Flickr account added successfully for: " + account.name);
          // Set up periodic syncing for all of these authorities.
          String[] authorities = { ContactsContract.AUTHORITY,
                                   ActivityContentProvider.AUTHORITY,
                                   PhotosContentProvider.AUTHORITY };
          for (String a : authorities) {
            // Set up this account to sync periodically.
            ContentResolver.setSyncAutomatically(account, a, false);  // TODO: Change to true
            // Set up the time-interval for the periodic sync.
            ContentResolver.addPeriodicSync(account, a, new Bundle(), PreferenceKeys.defaultSyncIntervalSeconds);
          }
          // Set up default preferences for this account.
          PreferenceKeys.setupDefaultPrefs(AuthenticatorActivity.this, nsid);
        } else {
          Log.w(TAG, "Failed to add account. Perhaps it already exists? Updating tokens.");
        }
        // We'd normally add a Bundle of userdata with the account, but this seems to have
        // stopped working. Just update the account userdata manually.
        accountManager.setUserData(account, ACCOUNT_TOKEN, consumer.getToken());
        accountManager.setUserData(account, ACCOUNT_TOKEN_SECRET, consumer.getTokenSecret());
        accountManager.setUserData(account, ACCOUNT_NSID, nsid);
        accountManager.setUserData(account, ACCOUNT_FULLNAME, fullname);
        
        Log.i(TAG, "Saved tokenSecret: " + accountManager.getUserData(account, "tokenSecret"));
        return account;
      } catch (OAuthMessageSignerException e) {
        e.printStackTrace();
      } catch (OAuthNotAuthorizedException e) {
        e.printStackTrace();
      } catch (OAuthExpectationFailedException e) {
        e.printStackTrace();
      } catch (OAuthCommunicationException e) {
        e.printStackTrace();
      }
      Log.e(TAG, "Failed to add Flickr account");
      return null;
    }

    @Override
    protected void onPostExecute(Account account) {
      super.onPostExecute(account);
      // A non-null account indicates successful account validation/creation.
      // Set up the result Bundle for the authenticator.
      if (account != null) {
        Log.i(TAG, "Returning account: " + account.name);
        Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        setAccountAuthenticatorResult(result);
        // TODO: Spawn off new activity.
      }
      finish();
    }
  }
}
