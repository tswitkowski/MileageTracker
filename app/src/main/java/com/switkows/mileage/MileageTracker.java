package com.switkows.mileage;

import java.util.HashMap;

import com.switkows.mileage.ProfileSelector.ProfileSelectorCallbacks;
import com.switkows.mileage.widgets.StatisticsView;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

//FIXME - merge MileageTracker and EditRecordsMenu into single activity to take advantage of Action Bar enhancements. This will require:
//1. mvoing most code from MileageTracker Activity to a Fragment
//2. removing EditRecordsMenu Activity
//3. correctly replacing new Activity calls with Fragment transactions
public class MileageTracker extends ActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor>, ProfileSelectorCallbacks {
   public static final String       ACTION_INSERT = "com.switkows.mileage.INSERT";
   /** Called when the activity is first created. */
   private static LinearLayout[]    charts;
   private ShowLargeChart[]         chartListeners;
   private MileageChartManager      chartManager;
   private static ListView          mStatsView;
   private static StatisticsAdapter mStatsAdapter;
   protected ProfileSelector        mProfileAdapter;

   private Context                  mContext;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      Log.d("TJS", "Started onCreate...");
      PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
      mContext = this;
      setContentView(R.layout.main);
      Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
      setSupportActionBar(toolbar);
      mStatsAdapter = new StatisticsAdapter(mContext);
      // grab pointers to all my graphical elements
      initalizePointers();
      getSupportLoaderManager().initLoader(45, null, this);
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
         mProfileAdapter = ProfileSelector.setupActionBar(this, null);
      // Log.d("TJS", "Finished opening/creating database");
   }

   @Override
   public void onResume() {
      super.onResume();
      // FIXME - maybe be a bit smarter about when we generate charts!
      getSupportLoaderManager().restartLoader(45, null, this);
      if(mProfileAdapter != null)
         mProfileAdapter.loadActionBarNavItems(this);
   }

   public void generateCharts(Cursor cursor) {
      chartManager = new MileageChartManager(mContext, cursor);
      for(LinearLayout chart : charts)
         chart.removeAllViews();
      LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
      chartManager.addCharts(charts, chartListeners, params);
   }

   public void printStatistics() {
      String ecoUnits = chartManager.getEconomyUnits();
      String distUnits = chartManager.getDistanceUnits();
      mStatsAdapter.setValue(0, chartManager.getAverageMPG(), ecoUnits);
      mStatsAdapter.setValue(1, chartManager.getAverageTrip(), distUnits);
      mStatsAdapter.setValue(2, chartManager.getBestMPG(), ecoUnits);
      mStatsAdapter.setValue(3, chartManager.getBestTrip(), distUnits);
      mStatsAdapter.setValue(4, chartManager.getAverageDiff() * 100, "%");
      mStatsAdapter.notifyDataSetInvalidated();
   }

   public void initalizePointers() {
      // root = (LinearLayout)findViewById(R.id.root);
      charts = new LinearLayout[3];
      charts[0] = (LinearLayout)findViewById(R.id.chart1);
      charts[1] = (LinearLayout)findViewById(R.id.chart2);
      charts[2] = (LinearLayout)findViewById(R.id.chart3);
      chartListeners = new ShowLargeChart[3];
      chartListeners[0] = new ShowLargeChart(mContext, 0);
      chartListeners[1] = new ShowLargeChart(mContext, 0);
      chartListeners[2] = new ShowLargeChart(mContext, 0);
      mStatsView = (ListView)findViewById(R.id.statistics_list);
      mStatsView.setAdapter(mStatsAdapter);
   }

   public Uri getCurrentProfileURI() {
      String car = getCurrentProfile();
      Uri uri = Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI, car);
      return uri;
   }

   public String getCurrentProfile() {
      String option = this.getString(R.string.carSelection);
      String car = PreferenceManager.getDefaultSharedPreferences(mContext).getString(option, "Car45");
      return car;
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.main_menu, menu);
      return true;
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      switch(item.getItemId()) {
         case R.id.add_item:
            startActivity(new Intent(ACTION_INSERT, getCurrentProfileURI()));
            return true;
         case R.id.show_data:
            startActivity(new Intent(mContext, EditRecordsMenu.class));
            return true;
         case R.id.preferences:
            startActivity(new Intent(mContext, EditPreferences.class));
            return true;
      }
      return false;
   }

   private class StatisticsAdapter extends BaseAdapter {

      private final String[]                  mLabels;
      private final LayoutInflater            mInflater;
      private HashMap<String, FloatWithUnits> mList;

      public StatisticsAdapter(Context c) {
         super();
         mLabels = getResources().getStringArray(R.array.StatisticsLabels);
         mList = new HashMap<String, FloatWithUnits>();
         for(String label : mLabels) {
            mList.put(label, new FloatWithUnits(-1, "??"));
         }
         mInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      }

      @Override
      public boolean areAllItemsEnabled() {
         return false;
      }

      @Override
      public boolean isEnabled(int position) {
         return false;
      }

      public int getCount() {
         return mList.size();
      }

      public FloatWithUnits getItem(int position) {
         return mList.get(mLabels[position]);
      }

      public long getItemId(int position) {
         return position;
      }

      public View getView(int position, View convertView, ViewGroup parent) {
         StatisticsView stats;
         if(convertView == null) {
            stats = (StatisticsView)mInflater.inflate(R.layout.statistics_item, null);
         } else {
            stats = (StatisticsView)convertView;
         }
         stats.setValue(getItem(position).getFormattedString());
         stats.setLabel(mLabels[position]);
         return stats;
      }

      public void setValue(int pos, float value, String units) {
         if(!(pos >= 0 && pos < mLabels.length))
            return;
         String label = mLabels[pos];
         if(mList.containsKey(label)) {
            FloatWithUnits val = mList.get(label);
            val.setUnits(units);
            val.setValue(value);
         } else {
            Log.e("TJS", "Should not have gotten here! HashMap not initialized or accessed correctly!!");
         }
      }
   }

   protected class FloatWithUnits {
      private float  mValue;
      private String mUnits;

      public FloatWithUnits(float val, String un) {
         setValue(val);
         setUnits(un);
      }

      public void setValue(float val) {
         mValue = val;
      }

      public void setUnits(String un) {
         mUnits = un;
      }

      public String getFormattedString() {
         return String.format("%.1f %s", mValue, mUnits);
      }
   }

   protected class ShowLargeChart implements OnClickListener {
      private Context mContext;
      private Intent  launcher;
      private int     mID;

      ShowLargeChart(Context c, int id) {
         mContext = c;
         mID = id;
         launcher = new Intent(mContext, ChartViewer.class);
      }

      public void setID(int id) {
         mID = id;
      }

      public void onClick(View v) {
         boolean isUS = MileageData.isMilesGallons(PreferenceManager.getDefaultSharedPreferences(mContext), mContext);
         launcher.putExtra(ChartViewer.UNITS_KEY, isUS);
         launcher.putExtra(ChartViewer.CHART_KEY, mID);
         startActivity(launcher);
      }
   }

   /**
    * Data loader for any cursor's to be used by this activity
    * (only handles querying mileage data for a specific profile)
    */
   public Loader<Cursor> onCreateLoader(int id, Bundle args) {
      CursorLoader loader = new CursorLoader(this, getCurrentProfileURI(), null, null, null, null);
      return loader;
   }

   public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
      generateCharts(cursor);
      printStatistics();
   }

   public void onLoaderReset(Loader<Cursor> cursor) {
      chartManager = null;
   }

   public void onProfileChange(String newProfile) {
      mProfileAdapter.applyPreferenceChange(newProfile);
      getSupportLoaderManager().restartLoader(45, null, this);
   }
}
