package net.randomprocesses.flickr.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class ContactsSyncService extends Service {
  private static final Object sSyncAdapterLock = new Object();
  private static ContactsSyncAdapter sSyncAdapter = null;

  @Override
  public void onCreate() {
    synchronized (sSyncAdapterLock) {
      if (sSyncAdapter == null) {
        sSyncAdapter = new ContactsSyncAdapter(getApplicationContext(), true);
      }
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return sSyncAdapter.getSyncAdapterBinder();
  }
}
