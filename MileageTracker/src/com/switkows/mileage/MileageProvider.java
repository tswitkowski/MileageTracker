package com.switkows.mileage;

import android.app.backup.BackupManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

public class MileageProvider extends ContentProvider {
   private static final String    DB_FILENAME     = "mileage_db";
   private static final String    DB_TABLE        = "mileageInfo";
   private static final String    PROFILE_TABLE   = "mileageProfiles";
   private static final int       DB_VERSION      = 5;
   public static final String     PROFILE_NAME    = "carName";

   public static final String     AUTHORITY       = "com.switkows.mileage.MileageProvider";
   public static final Uri        CAR_CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/car");
   public static final Uri        ALL_CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/all");
   public static final Uri        CAR_PROFILE_URI = Uri.parse("content://" + AUTHORITY + "/profile");

   public static final String     CONTENT_TYPE    = "vnd.android.cursor.dir/vnd.google.mileage";
   public static final String     CONTENT_ITEM    = "vnd.android.cursor.item/vnd.google.mileage";

   public static final int        ALL_CAR         = 0, ONE = 1, SPECIFIC_CAR = 2, PROFILE_SELECT = 3, SINGLE_PROFILE_SELECT = 4;

   private SharedPreferences      prefs;
   private BackupManager          mBackup;
   private boolean                mSuppressBackupUpdate;

   public static final UriMatcher sriMatcher;
   static {
      sriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
      sriMatcher.addURI(AUTHORITY, "all", ALL_CAR);
      sriMatcher.addURI(AUTHORITY, "car/*", SPECIFIC_CAR);
      sriMatcher.addURI(AUTHORITY, "all/#", ONE);
      sriMatcher.addURI(AUTHORITY, "profile", PROFILE_SELECT);
      sriMatcher.addURI(AUTHORITY, "profile/#", SINGLE_PROFILE_SELECT);
   }

   @Override
   public boolean onCreate() {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

      mBackup   = new BackupManager(getContext());
      mDatabase = new MileageDataSet(getContext());
      mSuppressBackupUpdate = false;
      return true;
   }

   private MileageDataSet mDatabase;

   public static CharSequence[] getProfiles(Context context) {
      Cursor cursor = context.getContentResolver().query(CAR_PROFILE_URI, null, null, null, null);
      String[] cars = new String[cursor.getCount()];
      for(int i = 0; i < cursor.getCount(); i++) {
         cursor.moveToPosition(i);
         cars[i] = cursor.getString(1); //FIXME - change to define!
      }
      cursor.close();
      return cars;
   }

   //Convenience method to simplify adding new profiles
   public static void addProfile(Context context, String profile) {
      context.getContentResolver().insert(CAR_PROFILE_URI, createProfileContent(profile));
   }

   public static ContentValues createProfileContent(String name) {
      ContentValues values = new ContentValues();
      values.put(PROFILE_NAME, name);
      return values;
   }

   //returns the default 'sortBy' argument, to reverse-sort by date (i.e. newest record on top)
   public static String defaultSort() {
      return MileageData.ToDBNames[MileageData.DATE] + " desc";
   }

   @Override
   public int delete(Uri uri, String where, String[] whereArgs) {
      SQLiteDatabase db = mDatabase.getWritableDatabase();
      int count;
      String id;
      String extra = where != null && where.length() > 0 ? " AND (" + where + ")" : "";
      switch(sriMatcher.match(uri)) {
         case ALL_CAR:
            count = db.delete(DB_TABLE, where, whereArgs);
            break;
         case SPECIFIC_CAR:
            count = db.delete(DB_TABLE, "carName = '" + uri.getPathSegments().get(1) + "'" + extra, whereArgs);
            break;
         case ONE:
            id = uri.getPathSegments().get(1);
            count = db.delete(DB_TABLE, "_id=" + id + extra, whereArgs);
            break;
         case PROFILE_SELECT:
            count = db.delete(PROFILE_TABLE, where, whereArgs);
            break;
         case SINGLE_PROFILE_SELECT:
            id = uri.getPathSegments().get(1);
            count = db.delete(PROFILE_TABLE, "_id=" + id + extra, whereArgs);
            break;
         default:
            throw new IllegalArgumentException("Unknown URI : " + uri);
      }
      getContext().getContentResolver().notifyChange(uri, null);
      //FIXME - try to get rid of this. it's probably not needed..
      getContext().getContentResolver().notifyChange(ALL_CONTENT_URI, null);
      if(count > 0 && !mSuppressBackupUpdate)
         mBackup.dataChanged();
      return count;
   }

   @Override
   public String getType(Uri uri) {
      switch(sriMatcher.match(uri)) {
         case ALL_CAR:
         case SPECIFIC_CAR:
         case PROFILE_SELECT:
            return CONTENT_TYPE;
         case ONE:
            return CONTENT_ITEM;
         default:
            throw new IllegalArgumentException("Unknown URI : " + uri);
      }
   }

   @Override
   public int bulkInsert(Uri uri, ContentValues[] values) {
      mSuppressBackupUpdate = true;
      int result = super.bulkInsert(uri, values);
      mSuppressBackupUpdate = false;
      return result;
   }

   @Override
   public Uri insert(Uri uri, ContentValues initialValues) {
      boolean isProfile = sriMatcher.match(uri) == PROFILE_SELECT;
      if(sriMatcher.match(uri) != ALL_CAR && sriMatcher.match(uri) != SPECIFIC_CAR && !isProfile) {
         throw new IllegalArgumentException("Unknown URI : '" + uri + "'");
      }
      String table   = isProfile ? PROFILE_TABLE   : DB_TABLE;
      Uri dataseturi = isProfile ? CAR_PROFILE_URI : ALL_CONTENT_URI;

      ContentValues values;
      if(initialValues != null)
         values = new ContentValues(initialValues);
      else
         values = new ContentValues();

      SQLiteDatabase db = mDatabase.getWritableDatabase();
      long rowId = db.insert(table, null, values);
      if(rowId >= 0) {
         Uri noteUri = ContentUris.withAppendedId(dataseturi, rowId);
         getContext().getContentResolver().notifyChange(noteUri, null);
         //FIXME - try to get rid of this. it's probably not needed..
         getContext().getContentResolver().notifyChange(dataseturi, null);
         if(!mSuppressBackupUpdate)
            mBackup.dataChanged();
         return noteUri;
      }
      throw new SQLException("Failed to insert row into " + uri);
   }

   @Override
   public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
      SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
      String groupBy = null;

      switch(sriMatcher.match(uri)) {
         case SPECIFIC_CAR:
            qb.setTables(DB_TABLE);
            // limit to only this car's data
            qb.appendWhere("carName = '" + uri.getPathSegments().get(1) + "'");
            // This will add all of the Columns in 'projection', except for
            // the _id column. This allows for 'uniquifying' the return data
            // (currently, only used for giving suggestions in the gas-station
            // input dialog)
            if(projection != null) // FIXME - do we query with projections when
               // we do NOT want to be unique?
               for(int i = 0; i < projection.length; i++)
                  if(!projection[i].equals("_id")) {
                     if(groupBy == null)
                        groupBy = "" + projection[i];
                     else
                        groupBy += projection[i];
                  }
            break;
         case ALL_CAR:
            qb.setTables(DB_TABLE);
            if(projection != null)
               for(String proj : projection)
                  if(!proj.equals("_id")) {
                     if(groupBy == null)
                        groupBy = "" + proj;
                     else
                        groupBy += proj;
                  }
            break;
         case ONE:
            // return a specific record (used for editing or deleting a single
            // item)
            qb.setTables(DB_TABLE);
            qb.appendWhere("_id = " + uri.getPathSegments().get(1));
            break;
         case PROFILE_SELECT:
            qb.setTables(PROFILE_TABLE);
            if(projection != null)
               for(String proj : projection)
                  if(!proj.equals("_id")) {
                     if(groupBy == null)
                        groupBy = "" + proj;
                     else
                        groupBy += proj;
                  }
            break;
         default:
            throw new IllegalArgumentException("Unknown URI : " + uri);
      }

      SQLiteDatabase db = mDatabase.getReadableDatabase();
      Cursor c = qb.query(db, projection, selection, selectionArgs, groupBy, null, sortOrder);
      c.setNotificationUri(getContext().getContentResolver(), uri);
      return c;
   }

   @Override
   public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
      SQLiteDatabase db = mDatabase.getWritableDatabase();
      boolean isProfile = sriMatcher.match(uri) == PROFILE_SELECT;
      int count;
      String id, where;
      switch(sriMatcher.match(uri)) {
         case ALL_CAR:
         case SPECIFIC_CAR:
            count = db.update(DB_TABLE, values, selection, selectionArgs);
            break;
         case ONE:
            id = uri.getPathSegments().get(1);
            where = selection != null && selection.length() > 0 ? " AND (" + selection + ")" : "";
            count = db.update(DB_TABLE, values, "_id=" + id + where, selectionArgs);
            break;
         case PROFILE_SELECT:
            //FIXME - add check to make sure URI has ID present!
            id = uri.getPathSegments().get(1);
            where = selection != null && selection.length() > 0 ? " AND (" + selection + ")" : "";
            count = db.update(PROFILE_TABLE, values, "_id=" + id + where, selectionArgs);
            break;
         case SINGLE_PROFILE_SELECT:
            id = uri.getPathSegments().get(1);
            where = selection != null && selection.length() > 0 ? " AND (" + selection + ")" : "";
            count = db.update(PROFILE_TABLE, values, "_id=" + id + where, selectionArgs);
            break;
         default:
            throw new IllegalArgumentException("Unknown URI : " + uri);
      }
      getContext().getContentResolver().notifyChange(uri, null);
      //FIXME - try to get rid of this. it's probably not needed..
      getContext().getContentResolver().notifyChange(isProfile ? CAR_PROFILE_URI : ALL_CONTENT_URI, null);
      if(count > 0 && !mSuppressBackupUpdate)
         mBackup.dataChanged();
      return count;
   }

   private class MileageDataSet extends SQLiteOpenHelper {
      private final Context mContext;

      public MileageDataSet(Context context) {
         super(context, DB_FILENAME, null, DB_VERSION);
         mContext = context;
      }

      @Override
      public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
         if(oldVer == 3 && (newVer == 4 || newVer == 5)) {
            Log.d("TJS", "Trying to execute'" + mContext.getString(R.string.upgradeDBfrom3To4) + "'");
            db.execSQL(mContext.getString(R.string.upgradeDBfrom3To4)); // create the new column (supports infinite
            // number of users/cars)
            ContentValues defaults = new ContentValues();
            String carName = prefs.getString(mContext.getString(R.string.carSelection), "Car45");
            defaults.put(MileageData.ToDBNames[MileageData.CAR], carName);
            int rows = db.update(DB_TABLE, defaults, null, null); // add car name to all rows in old database
            Log.d("TJS", "Database successfully upgraded. " + rows + " records were added to " + carName + "'s stats");
         } else if(oldVer == 4 && newVer == 5) {
            db.execSQL(mContext.getString(R.string.initProfileTable));
            ContentValues values = new ContentValues();
            for(int i = 0; i < 3; i++) {
               values.put(PROFILE_NAME, "Car" + (i + 1));
               db.insert(PROFILE_TABLE, "", values);
            }
         } else {
            String[] sql = mContext.getString(R.string.clearDb).split("\n");
            db.beginTransaction();
            try {
               executeMultipleCommands(db, sql);
               db.setTransactionSuccessful();
            } catch (SQLException e) {
               Log.e("Error creating table!", e.toString());
            } finally {
               db.endTransaction();
            }
            onCreate(db);
         }
      }

      @Override
      public void onCreate(SQLiteDatabase db) {
         String[] sql = mContext.getString(R.string.initDb).split("\n");
         db.beginTransaction();
         try {
            executeMultipleCommands(db, sql);
            db.setTransactionSuccessful();
         } catch (SQLException e) {
            Log.e("Error creating table!", e.toString());
         } finally {
            db.endTransaction();
         }

         db.execSQL(mContext.getString(R.string.initProfileTable));
         ContentValues values = new ContentValues();
         for(int i = 0; i < 3; i++) {
            values.put(PROFILE_NAME, "Car" + (i + 1));
            db.insert(PROFILE_TABLE, "", values);
         }
      }

      private void executeMultipleCommands(SQLiteDatabase db, String[] sql) {
         for(String s : sql)
            if(s.trim().length() > 0)
               db.execSQL(s);
      }
   }
}
