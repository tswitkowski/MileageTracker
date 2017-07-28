package com.switkows.mileage;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

/**
 * Created by Trevor on 7/26/2017.
 * Description: Instrumented Unit tests for testing the Sqlite db upgrade
 * paths. Use ContentProviderTests for all other db related testing!
 */

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SqliteUpgradeTests {
   private MileageProvider.Companion.MileageDataSet mDb;
   private final String filename = "test."+MileageProvider.Companion.getDB_FILENAME();

   public SqliteUpgradeTests() {
      Log.d("TJS", "Initializing class");
   }

   @Before
   public void doSetup() throws Exception {
      setUp();
   }

   private void setUp() throws Exception {
      Log.d("TJS", "Setting stuff up...");
   }


   // Idea taken from https://riggaroo.co.za/automated-testing-sqlite-database-upgrades-android/
   // And from https://stackoverflow.com/questions/8499554/android-junit-test-for-sqliteopenhelper
   @Test
   public void dbUpgrade3to5Test() throws IOException {
      Log.d("TJS", "Starting upgrade 3 to 5 test");
      copyDatabase(3);

      mDb = new MileageProvider.Companion.MileageDataSet(InstrumentationRegistry.getTargetContext(),
            filename,5);
      Cursor cursor = mDb.getReadableDatabase().query(MileageProvider.Companion.getPROFILE_TABLE(), null, null, null, null, null, null);
      assertNotNull(cursor);
      assertEquals(4, cursor.getCount());
      int matchCount = 0;
      // Default behavior is to add profiles Car1-3 to db
      matchCount += checkDefaultProfiles(cursor);

      // Now check the default name I assign to all records in onUpgrade
      cursor.moveToPosition(3);
      String val = getProfileName(cursor);
      assertEquals("Car45", val);
      matchCount++;

      // Check final match count
      assertEquals(4, matchCount);
   }

   @Test
   public void dbUpgrade4to5Test() throws IOException {
      Log.d("TJS", "Starting upgrade 4 to 5 test");
      copyDatabase(4);
      // Now check that we have a profiles table (it's busted currently!!)
      mDb = new MileageProvider.Companion.MileageDataSet(InstrumentationRegistry.getTargetContext(),
            filename,5);
      Cursor cursor = mDb.getReadableDatabase().query(MileageProvider.Companion.getPROFILE_TABLE(), null, null, null, null, null, null);
      assertNotNull(cursor);
      int matchCount = 0;
      // Default behavior is to add profiles Car1-3 to db
      matchCount += checkDefaultProfiles(cursor);
      // Check remaining ones..
      cursor.moveToPosition(matchCount++);
      assertEquals("is250", getProfileName(cursor));
      cursor.moveToPosition(matchCount++);
      assertEquals("Silverado", getProfileName(cursor));
      cursor.moveToPosition(matchCount++);
      assertEquals("myNewRide", getProfileName(cursor));
      cursor.moveToPosition(matchCount++);
      assertEquals("blah 45", getProfileName(cursor));
      // In case there are extra items...matchCount will be higher than expected!
      while (cursor.moveToNext()) {
         getProfileName(cursor);
         matchCount++;
      }

      assertEquals(7, cursor.getCount());
      assertEquals(7, matchCount);
   }

   private String getProfileName(Cursor cursor) {
      int column = cursor.getColumnIndex(MileageProvider.Companion.getPROFILE_NAME());
      String val = cursor.getString(column);
      Log.d("TJS", "Data: " + val);
      return val;
   }

   private int checkDefaultProfiles(Cursor cursor) {
      int matchCount = 0;
      for(int i=0 ; i < 3 ; i++) {
         cursor.moveToPosition(i);
         assertEquals("Car" + (matchCount+1), getProfileName(cursor));
         matchCount++;
      }
      return matchCount;
   }

   private void copyDatabase(int version) throws IOException {
      String dbPath = InstrumentationRegistry.getTargetContext().getDatabasePath(filename).getAbsolutePath();

      @SuppressLint("DefaultLocale")
      String dbName = String.format("mileage_db_v%d.db", version);
      InputStream mInput = InstrumentationRegistry.getContext().getAssets().open(dbName);

      File db = new File(dbPath);
      if (!db.exists()){
         boolean ret;
         ret = db.createNewFile();
         assertTrue(ret);
      }
      OutputStream mOutput = new FileOutputStream(dbPath);
      byte[] mBuffer = new byte[1024];
      int mLength;
      while ((mLength = mInput.read(mBuffer)) > 0) {
         mOutput.write(mBuffer, 0, mLength);
      }
      mOutput.flush();
      mOutput.close();
      mInput.close();
   }
}
