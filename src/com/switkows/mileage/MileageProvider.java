package com.switkows.mileage;

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
   private static final String    DB_FILENAME  = "mileage_db";
   private static final String    DB_TABLE     = "mileageInfo";
   private static final int       DB_VERSION   = 4;

   public static final String     AUTHORITY    = "com.switkows.mileage.MileageProvider";
   public static final Uri        CONTENT_URI  = Uri.parse("content://" + AUTHORITY + "/records");

   public static final String     CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.mileage";
   public static final String     CONTENT_ITEM = "vnd.android.cursor.item/vnd.google.mileage";

   public static final int        ALL_CAR      = 0, ONE = 1;

   private SharedPreferences      prefs;

   public static final UriMatcher sriMatcher;
   static {
      sriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
      sriMatcher.addURI(AUTHORITY, "records", ALL_CAR);
      sriMatcher.addURI(AUTHORITY, "records/#", ONE);
   }

   @Override
   public boolean onCreate() {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

      mDatabase = new MileageDataSet(getContext());
      return true;
   }

   private MileageDataSet mDatabase;

   @Override
   public int delete(Uri uri, String where, String[] whereArgs) {
      SQLiteDatabase db = mDatabase.getWritableDatabase();
      int count;
      switch(sriMatcher.match(uri)) {
         case ALL_CAR:
            count = db.delete(DB_TABLE, where, whereArgs);
            break;
         case ONE:
            String id = uri.getPathSegments().get(1);
            String extra = where != null && where.length() > 0 ? " AND (" + where + ")" : "";
            count = db.delete(DB_TABLE, "_id=" + id + extra, whereArgs);
            break;
         default:
            throw new IllegalArgumentException("Unknown URI : " + uri);
      }
      getContext().getContentResolver().notifyChange(uri, null);
      return count;
   }

   @Override
   public String getType(Uri uri) {
      switch(sriMatcher.match(uri)) {
         case ALL_CAR:
            return CONTENT_TYPE;
         case ONE:
            return CONTENT_ITEM;
         default:
            throw new IllegalArgumentException("Unknown URI : " + uri);
      }
   }

   @Override
   public Uri insert(Uri uri, ContentValues initialValues) {
      if(sriMatcher.match(uri) != ALL_CAR) {
         throw new IllegalArgumentException("Unknown URI : '" + uri + "'");
      }

      ContentValues values;
      if(initialValues != null)
         values = new ContentValues(initialValues);
      else
         values = new ContentValues();

      SQLiteDatabase db = mDatabase.getWritableDatabase();
      long rowId = db.insert(DB_TABLE, null, values);
      if(rowId > 0) {
         Uri noteUri = ContentUris.withAppendedId(CONTENT_URI, rowId);
         getContext().getContentResolver().notifyChange(noteUri, null);
         return noteUri;
      }
      throw new SQLException("Failed to insert row into " + uri);
   }

   @Override
   public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
      SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
      String groupBy = null;

      switch(sriMatcher.match(uri)) {
         case ALL_CAR:
            qb.setTables(DB_TABLE);
            // limit to only this car's data
            // FIXME - need a 'true ALL' so we can export to CSV!! coming next!!
            qb.appendWhere("carName = '" + prefs.getString(getContext().getString(R.string.carSelection), "Car45")
                  + "'");
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
         case ONE:
            // return a specific record (used for editing or deleting a single
            // item)
            qb.setTables(DB_TABLE);
            qb.appendWhere("_id = " + uri.getPathSegments().get(1));
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
      int count;
      switch(sriMatcher.match(uri)) {
         case ALL_CAR:
            count = db.update(DB_TABLE, values, selection, selectionArgs);
            break;
         case ONE:
            String id = uri.getPathSegments().get(1);
            String where = selection.length() > 0 ? " AND (" + selection + ")" : "";
            count = db.update(DB_TABLE, values, "_id=" + id + where, selectionArgs);
            break;
         default:
            throw new IllegalArgumentException("Unknown URI : " + uri);
      }
      getContext().getContentResolver().notifyChange(uri, null);
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
         // FIXME - add some way to upgrade the dababase, if required
         if(oldVer == 3 && newVer == 4) {
            Log.d("TJS", "Trying to execute'" + mContext.getString(R.string.upgradeDBfrom3To4) + "'");
            db.execSQL(mContext.getString(R.string.upgradeDBfrom3To4)); // create the new column (supports infinite
            // number of users/cars)
            ContentValues defaults = new ContentValues();
            String carName = prefs.getString(mContext.getString(R.string.carSelection), "Car45");
            defaults.put(MileageData.ToDBNames[MileageData.CAR], carName);
            int rows = db.update(DB_TABLE, defaults, null, null); // add car name to all rows in old database
            Log.d("TJS", "Database successfully upgraded. " + rows + " records were added to " + carName + "'s stats");
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

      }

      private void executeMultipleCommands(SQLiteDatabase db, String[] sql) {
         for(String s : sql)
            if(s.trim().length() > 0)
               db.execSQL(s);
      }
   }
}
