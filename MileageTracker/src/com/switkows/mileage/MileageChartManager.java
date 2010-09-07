package com.switkows.mileage;

import org.achartengine.GraphicalView;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.preference.PreferenceManager;

/**
 * This class holds all charts that will be rentered for this application.
 * 
 * @author switkows
 * 
 */
public class MileageChartManager extends DataSetObserver {
   private Context               mContext;
   private Cursor                mCursor;
   private MileageData[]         dataSet;
   private SharedPreferences     prefs;

   private TimeChartExtension[]  charts;
   private static final int      MPG_CHART            = 0, MPG_DIFF_CHART = 1, PRICE_CHART = 2;

   // FIXME - move to strings/arrays file?
   private static final int      US                   = 0, METRIC = 1;
   private static final String[] ECONOMY_UNIT_LABELS  = { "mpg", "km/L" };
   private static final String[] DISTANCE_UNIT_LABELS = { "mi", "km" };
   private static final float    LITER_PER_GALLON     = (float) 3.78541178;
   private static final float    KM_PER_MILE          = (float) 1.609344;

   public MileageChartManager(Context c, Cursor cursor) {
      mContext = c;
      mCursor = cursor;
      loadData();
      createCharts();
      mCursor.registerDataSetObserver(this);
   }

   private void loadData() {
      dataSet = new MileageData[mCursor.getCount()];
      for(int i = 0; i < mCursor.getCount(); i++) {
         mCursor.moveToPosition(i);
         dataSet[i] = new MileageData(mContext, mCursor);

      }
   }

   // Utility methods for determining what units to display information in
   public boolean isMilesGallons() {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      String units = prefs.getString(mContext.getString(R.string.unitSelection), "mpg");
      return units.equals("mpg");
   }

   public String getDistanceUnits() {
      if(isMilesGallons())
         return DISTANCE_UNIT_LABELS[US];
      return DISTANCE_UNIT_LABELS[METRIC];
   }

   public String getEconomyUnits() {
      if(isMilesGallons())
         return ECONOMY_UNIT_LABELS[US];
      return ECONOMY_UNIT_LABELS[METRIC];
   }

   public float getDistance(float miles) {
      if(isMilesGallons())
         return miles;
      return (miles * KM_PER_MILE);
   }

   public float getVolume(float gallons) {
      if(isMilesGallons())
         return gallons;
      return (gallons * LITER_PER_GALLON);
   }

   public float getEconomy(float mpg) {
      if(isMilesGallons())
         return mpg;
      return (getDistance(mpg) / getVolume(1));
   }

   // FIXME - this should not go in the chartManager, but it's conventient..
   // should be moved to the content provider, instead
   public float getAverageMPG() {
      if(dataSet == null || dataSet.length == 0)
         return 0;
      return getEconomy(getTotal(MileageData.TRIP) / getTotal(MileageData.GALLONS));
   }

   public float getAverageTrip() {
      return getDistance(getAverage(MileageData.TRIP));
   }

   public float getAverageDiff() {
      return getAverage(MileageData.MPG_DIFF);
   }

   public float getBestMPG() {
      return getEconomy(getBest(MileageData.ACTUAL_MILEAGE));
   }

   public float getBestTrip() {
      return getDistance(getBest(MileageData.TRIP));
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

   private void createCharts() {
      boolean isUS = isMilesGallons();
      charts = new TimeChartExtension[3];
      charts[MPG_CHART] = new MileageChart(mContext, dataSet, isUS);
      charts[MPG_DIFF_CHART] = new MileageDiffChart(mContext, dataSet, isUS);
      charts[PRICE_CHART] = new PriceChart(mContext, dataSet, isUS);
   }

   private TimeChartExtension getChartStruct(int idx) {
      return charts[idx];
   }

   private GraphicalView getChartView(int idx) {
      return charts[idx].getChart();
   }

   public GraphicalView getMileageChart() {
      return getChartView(MPG_CHART);
   }

   public GraphicalView getDiffChart() {
      return getChartView(MPG_DIFF_CHART);
   }

   public GraphicalView getPriceChart() {
      return getChartView(PRICE_CHART);
   }

   public void appendData(MileageData data, boolean autofit) {
      long date = data.getDate();
      // passing false as 3rd argument because we will simply iterate over all
      // charts in autoFitCharts
      getChartStruct(MPG_CHART).appendDataToSeries(date,
            new float[] { data.getComputerMileage(), data.getActualMileage() }, false);
      getChartStruct(MPG_DIFF_CHART).appendDataToSeries(date, new float[] { data.getMileageDiff() }, false);
      getChartStruct(PRICE_CHART).appendDataToSeries(date, new float[] { data.getPrice() }, false);
      if(autofit)
         autoFitCharts();
   }

   public void clearData(boolean repaint) {
      for(TimeChartExtension chart : charts)
         chart.clearData();
      if(repaint)
         updateCharts();
   }

   public void autoFitCharts() {
      for(TimeChartExtension chart : charts)
         chart.autoFitChart();
      updateCharts();
   }

   public void updateCharts() {
      for(TimeChartExtension chart : charts)
         chart.getChart().repaint();
   }

   @Override
   public void onChanged() {
      loadData();
      createCharts();
   }

}
