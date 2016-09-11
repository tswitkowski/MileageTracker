package com.switkows.mileage;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

/**
 * @author Trevor
 *         This is a pretty simple activity: display the specified
 *         chart (with correct units), based on the Intent.
 */
public class ChartViewer extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor> {
   public static final String UNITS_KEY = "units";
   public static final String CHART_KEY = "chartID";

   @Override
   public void onResume() {
      super.onResume();
      getSupportLoaderManager().initLoader(45, null, this);
   }

   public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
      String pref = this.getString(R.string.carSelection);
      String car = PreferenceManager.getDefaultSharedPreferences(this).getString(pref, "Car1");
      Uri uri = Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI, car);
      return new CursorLoader(this, uri, null, null, null, null);
   }

   public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
      MileageChartManager chartManager = new MileageChartManager(this, cursor);

      Intent i = getIntent();
      boolean isUS = i.getBooleanExtra(UNITS_KEY, true);
      int chartID = i.getIntExtra(CHART_KEY, MileageChartManager.NO_CHART);
      View view = chartManager.createChart(chartID, true, isUS);
      addContentView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
   }

   public void onLoaderReset(Loader<Cursor> arg0) {
   }

}
