package com.switkows.mileage;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;

public class Starter extends Activity {
   /** Called when the activity is first created. */
   // private static LinearLayout root;
   private static LinearLayout mileageChart;
   private static LinearLayout diffChart;
   private static LinearLayout priceChart;
   private static Cursor       mCursor;
   private static TextView     aveMpg;
   private static TextView     aveTrip;
   private static TextView     bestMpg;
   private static TextView     bestTrip;
   private static TextView     aveDiff;
   private MileageChartManager chartManager;

   private Context             mContext;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main);
      // grab pointers to all my graphical elements
      initalizePointers();

      Log.d("TJS", "Started onCreate...");

      // Log.d("TJS", "Finished opening/creating database");
      mContext = getApplicationContext();
   }

   @Override
   public void onResume() {
      super.onResume();
      // FIXME - maybe be a bit smarter about when we generate charts!
      mCursor = managedQuery(MileageProvider.CONTENT_URI, null, null, null, null);
      generateCharts();
      printStatistics();
   }

   public void addUpdateDatapoint(MileageData data) {
      getContentResolver().insert(MileageProvider.CONTENT_URI, data.getContent());
      chartManager.appendData(data, true);
   }

   public void generateCharts() {
      chartManager = new MileageChartManager(mContext, mCursor);
      mileageChart.removeAllViews();
      diffChart.removeAllViews();
      priceChart.removeAllViews();
      mileageChart.addView(chartManager.getMileageChart(), new LayoutParams(LayoutParams.FILL_PARENT,
            LayoutParams.FILL_PARENT));
      diffChart.addView(chartManager.getDiffChart(), new LayoutParams(LayoutParams.FILL_PARENT,
            LayoutParams.FILL_PARENT));
      priceChart.addView(chartManager.getPriceChart(), new LayoutParams(LayoutParams.FILL_PARENT,
            LayoutParams.FILL_PARENT));
   }

   public void printStatistics() {
      String ecoUnits = chartManager.getEconomyUnits();
      String distUnits = chartManager.getDistanceUnits();
      aveMpg.setText(String.format("%.1f %s", chartManager.getAverageMPG(), ecoUnits));
      aveTrip.setText(String.format("%.1f %s", chartManager.getAverageTrip(), distUnits));
      bestMpg.setText(String.format("%.1f %s", chartManager.getBestMPG(), ecoUnits));
      bestTrip.setText(String.format("%.1f %s", chartManager.getBestTrip(), distUnits));
      aveDiff.setText(String.format("%.1f%%", chartManager.getAverageDiff() * 100));
   }

   public void initalizePointers() {
      // root = (LinearLayout)findViewById(R.id.root);
      mileageChart = (LinearLayout) findViewById(R.id.mileage_chart);
      diffChart = (LinearLayout) findViewById(R.id.diff_chart);
      priceChart = (LinearLayout) findViewById(R.id.price_chart);
      aveMpg = (TextView) findViewById(R.id.ave_mpg_value);
      aveTrip = (TextView) findViewById(R.id.ave_trip_value);
      bestMpg = (TextView) findViewById(R.id.best_mpg_value);
      bestTrip = (TextView) findViewById(R.id.best_trip_value);
      aveDiff = (TextView) findViewById(R.id.ave_diff_value);

   }

   private static final int MENU_ADD = 0, MENU_SHOWALL = 2, MENU_PREFS = 3;

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      menu.add(0, MENU_ADD, 0, "Add Entry").setIcon(android.R.drawable.ic_menu_add);
      menu.add(0, MENU_SHOWALL, 0, "Modify Data").setIcon(android.R.drawable.ic_menu_edit);
      menu.add(0, MENU_PREFS, 0, "Preferences").setIcon(android.R.drawable.ic_menu_preferences);
      return true;
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      switch(item.getItemId()) {
         case MENU_ADD:
            Uri uri = MileageProvider.CONTENT_URI;
            startActivity(new Intent(Intent.ACTION_INSERT, uri));
            return true;
         case MENU_SHOWALL:
            startActivity(new Intent(mContext, EditRecordsMenu.class));
            return true;
         case MENU_PREFS:
            startActivity(new Intent(mContext, EditPreferences.class));
            return true;
      }
      return false;
   }
}