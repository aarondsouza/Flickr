package net.randomprocesses.flickr.auth;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AuthenticatorService extends Service {
  private Authenticator mAuthenticator = null;
  
  @Override
  public void onCreate() {
    super.onCreate();
    if (mAuthenticator == null)
      mAuthenticator = new Authenticator(this);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mAuthenticator.getIBinder();
  }
}
