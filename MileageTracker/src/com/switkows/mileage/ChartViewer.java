package com.switkows.mileage;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

/**
 * 
 * @author Trevor
 * This is a pretty simple activity: display the specified
 * chart (with correct units), based on the Intent.
 */
public class ChartViewer extends Activity {
   public static final String UNITS_KEY = "units";
   public static final String CHART_KEY = "chartID";
   private Cursor mCursor;
   private MileageChartManager chartManager;

   @Override
   public void onResume() {
      super.onResume();
      String pref = this.getString(R.string.carSelection);
      String car = PreferenceManager.getDefaultSharedPreferences(this).getString(pref, "Car1");
      Uri uri = Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI, car);
       mCursor = managedQuery(uri, null, null, null, null);
      chartManager = new MileageChartManager(this, mCursor);

       Intent i = getIntent();
       boolean isUS = i.getBooleanExtra(UNITS_KEY, true);
       int chartID = i.getIntExtra(CHART_KEY, MileageChartManager.NO_CHART);
      View view = chartManager.createChart(chartID, true, isUS);
       addContentView(view, new LayoutParams(LayoutParams.FILL_PARENT,LayoutParams.FILL_PARENT));
   }

}