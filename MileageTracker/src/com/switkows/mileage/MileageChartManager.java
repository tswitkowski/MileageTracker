package com.switkows.mileage;

import com.switkows.mileage.MileageTracker.ShowLargeChart;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import com.switkows.mileage.Charts.*;

/**
 * This class holds all charts that will be rentered for this application.
 * 
 * @author switkows
 */
public class MileageChartManager {
   private Context           mContext;
   private MileageData[]     dataSet;
   private SharedPreferences prefs;

   public static final int   MPG_CHART = 0, MPG_DIFF_CHART = 1, PRICE_CHART = 2, MPG_STATION_CHART = 3, ODO_CHART = 4, NO_CHART = 100;

   public MileageChartManager(Context c, Cursor cursor) {
      mContext = c;
      loadData(cursor);
   }

   private void loadData(Cursor cursor) {
      dataSet = new MileageData[cursor.getCount()];
      for(int i = 0; i < cursor.getCount(); i++) {
         cursor.moveToPosition(i);
         dataSet[i] = new MileageData(mContext, cursor);

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

   // FIXME - this should not go in the chartManager, but it's convenient..
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
      for(MileageData item : dataSet)
         total += item.getFloatValue(field);
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
      for(MileageData item : dataSet) {
         curVal = item.getFloatValue(field);
         if(curVal > best)
            best = curVal;
      }
      return best;
   }

   public void addCharts(LinearLayout[] charts, ShowLargeChart[] listeners, LayoutParams params) {
      SharedPreferences locPrefs = getPrefs();
      if(charts.length > 0) {
         genAddChart(charts[0], listeners[0], locPrefs, R.string.chart1Selection);
         if(charts.length > 1) {
            genAddChart(charts[1], listeners[1], locPrefs, R.string.chart2Selection);
            if(charts.length > 2) {
               genAddChart(charts[2], listeners[2], locPrefs, R.string.chart3Selection);
            }
         }
      }
   }

   public void genAddChart(LinearLayout view, ShowLargeChart listener, SharedPreferences prefs, int key) {
      if(!prefs.contains(mContext.getString(key))) {
         Log.e("TJS", "Error. key wasn't found...bad news...");
         return;
      }
      String strVal = prefs.getString(mContext.getString(key), "100");
      int val = NO_CHART;
      try {
         val = Integer.parseInt(strVal);
      } catch (NumberFormatException e) {
         Log.e("TJS", e.toString());
      }
      boolean isUS = MileageData.isMilesGallons(prefs, mContext);
      switch(val) {
         case NO_CHART:
            break;
         case MPG_CHART:
         case MPG_DIFF_CHART:
         case PRICE_CHART:
         case MPG_STATION_CHART:
         case ODO_CHART:
            listener.setID(val);
            View chart = createChart(val, false, isUS);
            chart.setOnClickListener(listener);
            view.addView(chart);
            break;
      }
      view.setVisibility(val == NO_CHART ? View.GONE : View.VISIBLE);
   }

   public View createChart(int chartID, boolean isPanZoomable, boolean isUS) {
      TimeChartExtension chart = null;
//      float average = 0;
      switch(chartID) {
         case MPG_CHART:
            chart = new MileageChart(mContext, dataSet, isUS);
//           average = getAverageMPG();
            break;
         case MPG_DIFF_CHART:
            chart = new MileageDiffChart(mContext, dataSet, isUS);
//           average = getAverageDiff();
            break;
         case PRICE_CHART:
            chart = new PriceChart(mContext, dataSet, isUS);
//           average = getAverage(MileageData.PRICE);
            break;
         case MPG_STATION_CHART:
            chart = new MileageVsStationChart(mContext, dataSet, isUS);
//           average = getAverageMPG();
            break;
         case ODO_CHART:
            chart = new MilesChart(mContext, dataSet, isUS);
            break;
      }
      if(chart != null) {
         chart.setPanZoomable(isPanZoomable);
//         Log.d("TJS","Setting Normalization value to "+average);
//         chart.setNormalizedValue(average);
         return chart.getChart();
      }
      return null;
   }

}
