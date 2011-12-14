package net.randomprocesses.flickr.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class ActivitySyncService extends Service {
  private static final Object sSyncAdapterLock = new Object();
  private static ActivitySyncAdapter sSyncAdapter = null;

  @Override
  public void onCreate() {
    synchronized (sSyncAdapterLock) {
      if (sSyncAdapter == null) {
        sSyncAdapter = new ActivitySyncAdapter(getApplicationContext(), true);
      }
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return sSyncAdapter.getSyncAdapterBinder();
  }
}
