package net.randomprocesses.flickr.content;

import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;

public class ActivityContentProvider extends ContentProvider {
  public static final String AUTHORITY  = "net.randomprocesses.flickr.activity";
  private static final int    DB_VERSION = 1;
  private static final String DB_NAME    = "flickr.db";

  private static final int CONTACT_ACTIVITY_DIR_URI_TYPE  = 1;
  private static final int CONTACT_ACTIVITY_ITEM_URI_TYPE = 2;
  
  public static final String CONTACT_ACTIVITY_DIR_MIMETYPE =
    "vnd.android.cursor.dir/vnd.com.flickr.android.contact_activity";
  public static final String CONTACT_ACTIVITY_ITEM_MIMETYPE =
    "vnd.android.cursor.item/vnd.com.flickr.android.contact_activity";
  
  public static class ContactActivity implements BaseColumns {
    private ContactActivity() {}  // This class cannot be instantiated.
    private static final String TABLE_NAME = "contactactivity";
    // Column names
    public static final String _ID          = "_id";
    public static final String ACCOUNT_NSID = "account";   // NSID of the account holder.
    public static final String NSID         = "nsid";      // NSID of the contact publishing the picture.
    public static final String TIMESTAMP    = "timestamp"; // Seconds since epoch (UTC) when the picture was published.
    public static final String TITLE        = "title";     // Title of the picture.
    
    private static final String CREATE_TABLE_QUERY =
      "CREATE TABLE " + TABLE_NAME + " (" +
      _ID          + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
      ACCOUNT_NSID + " TEXT, " +
      NSID         + " TEXT, " +
      TIMESTAMP    + " INTEGER, " +
      TITLE        + " TEXT);";
    
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/contactactivity");
  }
  
  private static final UriMatcher sUriMatcher;
  private static final HashMap<String, String> sContactActivityProjectionMap;
  static {
    sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    sUriMatcher.addURI(AUTHORITY, "contactactivity",   CONTACT_ACTIVITY_DIR_URI_TYPE);
    sUriMatcher.addURI(AUTHORITY, "contactactivity/#", CONTACT_ACTIVITY_ITEM_URI_TYPE);
    
    sContactActivityProjectionMap = new HashMap<String, String>();
    sContactActivityProjectionMap.put(ContactActivity._ID,          ContactActivity._ID);
    sContactActivityProjectionMap.put(ContactActivity.ACCOUNT_NSID, ContactActivity.ACCOUNT_NSID);
    sContactActivityProjectionMap.put(ContactActivity.NSID,         ContactActivity.NSID);
    sContactActivityProjectionMap.put(ContactActivity.TIMESTAMP,    ContactActivity.TIMESTAMP);
    sContactActivityProjectionMap.put(ContactActivity.TITLE,        ContactActivity.TITLE);
  }
  
  private class DatabaseHelper extends SQLiteOpenHelper {
    public DatabaseHelper(Context context) {
      super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(ContactActivity.CREATE_TABLE_QUERY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      db.execSQL("DROP TABLE IF EXISTS " + ContactActivity.TABLE_NAME);
      onCreate(db);
    }
  }
  
  private DatabaseHelper mDatabaseHelper = null;
  
  @Override
  public boolean onCreate() {
    mDatabaseHelper = new DatabaseHelper(getContext());
    return true;
  }
  
  @Override
  public String getType(Uri uri) {
    switch(sUriMatcher.match(uri)) {
    case CONTACT_ACTIVITY_DIR_URI_TYPE:
      return CONTACT_ACTIVITY_DIR_MIMETYPE;
    case CONTACT_ACTIVITY_ITEM_URI_TYPE:
      return CONTACT_ACTIVITY_ITEM_MIMETYPE;
    default:
      throw new IllegalArgumentException("Unknown URI type: " + uri);
    }
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    final SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
    switch(sUriMatcher.match(uri)) {
    case CONTACT_ACTIVITY_DIR_URI_TYPE: {
      getContext().getContentResolver().notifyChange(uri, null);
      if (!values.containsKey(ContactActivity.ACCOUNT_NSID) ||
          !values.containsKey(ContactActivity.NSID) ||
          !values.containsKey(ContactActivity.TIMESTAMP)) {
        throw new IllegalArgumentException("ContentValues missing required data.");
      }
      final long rowId = db.insert(ContactActivity.TABLE_NAME, ContactActivity.ACCOUNT_NSID, values);
      if (rowId < 0)
        return null;
      return ContentUris.withAppendedId(uri, rowId);
    }
    default:
      throw new IllegalArgumentException("Insertion not supported for URI: " + uri);
    }
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    final SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
    switch(sUriMatcher.match(uri)) {
    case CONTACT_ACTIVITY_DIR_URI_TYPE: {
      final int count = db.delete(ContactActivity.TABLE_NAME, selection, selectionArgs);
      if (count > 0)
        getContext().getContentResolver().notifyChange(uri, null);
      return count;
    }
    default:
      throw new IllegalArgumentException("Deletion not supported for URI: " + uri);
    }
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection,
                      String[] selectionArgs, String sortOrder) {
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    switch(sUriMatcher.match(uri)) {
    case CONTACT_ACTIVITY_DIR_URI_TYPE: {
      qb.setTables(ContactActivity.TABLE_NAME);
      qb.setProjectionMap(sContactActivityProjectionMap);
      break;
    }
    default:
      throw new IllegalArgumentException("Deletion not supported for URI: " + uri);
    }
    SQLiteDatabase db = mDatabaseHelper.getReadableDatabase();
    Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
    // Set up the cursor to be notified whenever the URI that it's tracking changes.
    c.setNotificationUri(getContext().getContentResolver(), uri);
    return c;
  }
  
  @Override
  public int update(Uri uri, ContentValues values, String selection,
                    String[] selectionArgs) {
    // TODO Auto-generated method stub
    return 0;
  }
}
