package com.switkows.mileage;

import android.database.Cursor;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Created by Trevor on 7/26/2017.
 * Description: Instrumented Unit tests for the MileageProvider ContentProvider
 */

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ContentProviderTests extends ProviderTestCase2<MileageProvider> {
   private MockContentResolver mMockResolver;

   public ContentProviderTests() {
      super(MileageProvider.class, MileageProvider.Companion.getAUTHORITY());
      Log.d("TJS", "Initializing class");
   }

   @Before
   public void doSetup() throws Exception {
      setUp();
   }

   @Override
   protected void setUp() throws Exception {
      setContext(InstrumentationRegistry.getTargetContext());
      super.setUp();
      Log.d("TJS", "Setting stuff up...");
      setContext(InstrumentationRegistry.getTargetContext());
      mMockResolver = getMockContentResolver();
      MileageProvider.Companion.setMSuppressBackupUpdate(true);
   }

   @Test
   public void newDbTest() {
      Log.d("TJS", "Starting test");
      assertNotNull(mMockResolver);

      final Uri baseQueryAllUri = MileageProvider.Companion.getALL_CONTENT_URI();
      Cursor cursor = mMockResolver.query(baseQueryAllUri, null, null, null, null);
      assertNotNull(cursor);
      assertEquals(0, cursor.getCount());
   }

   @Test
   public void addQueryTest() {
      Log.d("TJS", "Starting add/query test");
      assertNotNull(mMockResolver);

      final Uri baseInsertUri = MileageProvider.Companion.getCAR_CONTENT_URI();
      MileageData dataStruct;
      ArrayList<MileageData> addedData;
      Uri uri;
      addedData = generateData();
      for (MileageData item: addedData) {
         uri = Uri.withAppendedPath(baseInsertUri, item.getCarName());
         assertNotNull(mMockResolver.insert(uri, item.getContent()));
      }

      // Now query & check data
      final Uri baseQueryAllUri = MileageProvider.Companion.getALL_CONTENT_URI();
      Cursor cursor = mMockResolver.query(baseQueryAllUri, null, null, null, null);
      assertNotNull(cursor);
      int matchedCount = 0;
      while(cursor.moveToNext()) {
         dataStruct = new MileageData(cursor);
         assertNotNull(dataStruct);
         assertEquals(dataStruct.exportCSV(), addedData.get(matchedCount).exportCSV());
         matchedCount++;
      }
      assertEquals(matchedCount, addedData.size());
      Log.d("TJS", "Successfully matched "+matchedCount+" adds!");
   }

   public ArrayList<MileageData> generateData() {
      MileageData data;
      ArrayList<MileageData> addedData = new ArrayList<>();

      data = new MileageData( "car45", "11/18/1984", "Dealership",
                              10, 10, 1, 1.199f, 12.1f);
      addedData.add(data);

      data = new MileageData( "car45", "11/18/2007", "Work Chevron",
                              111, 101, 9, 3.029f, 14.3f);
      addedData.add(data);

      return addedData;
   }
}
