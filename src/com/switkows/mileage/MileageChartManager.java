package com.switkows.mileage;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;

/**
 * This class holds all charts that will be rentered for this application.
 * 
 * @author switkows
 * 
 */
public class MileageChartManager extends DataSetObserver {
   private Context              mContext;
   private Cursor               mCursor;
   private MileageData[]        dataSet;
   private SharedPreferences    prefs;

   public static final int     MPG_CHART = 0, MPG_DIFF_CHART = 1, PRICE_CHART = 2, MPG_STATION_CHART = 3, NO_CHART = 100;

   public MileageChartManager(Context c, Cursor cursor) {
      mContext = c;
      mCursor = cursor;
      loadData();
      mCursor.registerDataSetObserver(this);
   }

   private void loadData() {
      dataSet = new MileageData[mCursor.getCount()];
      for(int i = 0; i < mCursor.getCount(); i++) {
         mCursor.moveToPosition(i);
         dataSet[i] = new MileageData(mContext, mCursor);

      }
   }

   public SharedPreferences getPrefs() {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      return prefs;
   }

   // Utility methods for determining what units to display information in
   public String getDistanceUnits() {
      return MileageData.getDistanceUnits(getPrefs(), mContext);
   }

   public String getEconomyUnits() {
      return MileageData.getEconomyUnits(getPrefs(), mContext);
   }

   // FIXME - this should not go in the chartManager, but it's conventient..
   // should be moved to the content provider, instead
   public float getAverageMPG() {
      if(dataSet == null || dataSet.length == 0)
         return 0;
      return MileageData.getEconomy(getTotal(MileageData.TRIP) / getTotal(MileageData.GALLONS), getPrefs(), mContext);
   }

   public float getAverageTrip() {
      return MileageData.getDistance(getAverage(MileageData.TRIP), getPrefs(), mContext);
   }

   public float getAverageDiff() {
      return getAverage(MileageData.MPG_DIFF);
   }

   public float getBestMPG() {
      return MileageData.getEconomy(getBest(MileageData.ACTUAL_MILEAGE), getPrefs(), mContext);
   }

   public float getBestTrip() {
      return MileageData.getDistance(getBest(MileageData.TRIP), getPrefs(), mContext);
   }

   public float getTotal(int field) {
      float total = 0;
      for(int i = 0; i < dataSet.length; i++)
         total += dataSet[i].getFloatValue(field);
      return total;
   }

   public float getAverage(int field) {
      if(dataSet == null || dataSet.length == 0)
         return 0;
      return getTotal(field) / dataSet.length;
   }

   public float getBest(int field) {
      float best = 0;
      float curVal;
      for(int i = 0; i < dataSet.length; i++) {
         curVal = dataSet[i].getFloatValue(field);
         if(curVal > best)
            best = curVal;
      }
      return best;
   }

   public void addCharts(LinearLayout[] charts, LayoutParams params) {
      SharedPreferences locPrefs = getPrefs();
      if(charts.length > 0) {
         genAddChart(charts[0],locPrefs,R.string.chart1Selection);
         if(charts.length > 1) {
            genAddChart(charts[1],locPrefs,R.string.chart2Selection);
            if(charts.length > 2) {
               genAddChart(charts[2],locPrefs,R.string.chart3Selection);
            }
         }
      }
   }
   
   public void genAddChart(LinearLayout view, SharedPreferences prefs, int key) {
      if(!prefs.contains(mContext.getString(key))) {
         Log.e("TJS","Error. key wasn't found...bad news...");
         return;
      }
      String strVal = prefs.getString(mContext.getString(key), "100");
      int val = NO_CHART;
      try {
         val = Integer.parseInt(strVal);
      } catch(NumberFormatException e) {Log.e("TJS",e.toString());}
      boolean isUS = MileageData.isMilesGallons(prefs, mContext);
      switch(val) {
         case NO_CHART:
            break;
         case MPG_CHART:
            view.addView(new MileageChart(mContext, dataSet, isUS).getChart());
            break;
         case MPG_DIFF_CHART:
            view.addView(new MileageDiffChart(mContext, dataSet, isUS).getChart());
            break;
         case PRICE_CHART:
            view.addView(new PriceChart(mContext, dataSet, isUS).getChart());
            break;
         case MPG_STATION_CHART:
            view.addView(new MileageVsStationChart(mContext, dataSet).getChart());
            break;
      }
      view.setVisibility(val == NO_CHART ? View.GONE : View.VISIBLE);
   }

}
