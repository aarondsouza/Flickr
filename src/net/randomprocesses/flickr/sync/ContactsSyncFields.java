package net.randomprocesses.flickr.sync;

import android.provider.ContactsContract.Data;

public final class ContactsSyncFields {
  public static final String USERNAME   = Data.DATA1;
  public static final String FRIEND     = Data.DATA2;
  public static final String FAMILY     = Data.DATA3;
  
  // This is unfortunately required by the way the contacts XML specification is structured.
  public static final String SUMMARY    = Data.DATA14;
}
