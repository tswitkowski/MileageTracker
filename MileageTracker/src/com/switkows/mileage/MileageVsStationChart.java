package com.switkows.mileage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.achartengine.chart.*;
import android.content.Context;
import android.graphics.Color;

public class MileageVsStationChart extends TimeChartExtension {

   private static final int[]        allColors           = { Color.BLUE, Color.RED, Color.GREEN, Color.GRAY, Color.WHITE, Color.CYAN, Color.MAGENTA };
   private static final PointStyle[] allStyles           = { PointStyle.CIRCLE, PointStyle.SQUARE };
   
   private List<double[]> mValues;
   private List<double[]> mXValues;

   public MileageVsStationChart(Context c, MileageData[] data) {
      super(c, "MPG over Time", "MPG", null, null, null, data);
      analyzeData();
   }

   protected void appendDataToSeries(long date, float[] values) {
//      appendDataToSeries(date, COMPUTER_MILEAGE, values[0]);
//      appendDataToSeries(date, ACTUAL_MILEAGE, values[1]);
   }

   //For this chart, we must analyze all 'data' before we know which titles are present.
   //because of this, we must do a one-short analysis, determining titles as well as data
   protected List<double[]> buildValuesList(MileageData[] data) {
      return mValues;
   }
   
   @Override
   protected List<double[]> buildXList(MileageData[] data) {
      return mXValues;
   }
   
   public void analyzeData() {
      mValues = new ArrayList<double[]>();
      mXValues = new ArrayList<double[]>();
      List<String> titles = new ArrayList<String>();
      List<Integer> colors= new ArrayList<Integer>();
      List<PointStyle> styles = new ArrayList<PointStyle>();
      int i = 0;
      //search for unique station names
      for(MileageData item : mData) {
         String station = item.getStation();
         if(!titles.contains(station)) {
            titles.add(station);
            colors.add(allColors[i%allColors.length]);
            styles.add(allStyles[(i/allColors.length) % allStyles.length]);
            i++;
         }
      }
      
      int size = titles.size();
      mStyles = new PointStyle[size];
      mColors = new int[size];
      mTitles = new String[size];

      //convert from List to string array
      Iterator<String> itr = titles.iterator();
      i = 0;
      while(itr.hasNext()) {
         mTitles[i] = itr.next();
         mStyles[i] = styles.get(i);
         mColors[i] = colors.get(i);
         i++;
      }

      //build 2-d array of data
      for(String title : mTitles) {
         List<Float> valueList = new ArrayList<Float>();
         List<Double> timeList = new ArrayList<Double>();
         for(MileageData item : mData) {
            if(title.equals(item.getStation())) {
               valueList.add(item.getActualMileage());
               timeList.add(new Double(item.getDate()));
            }
         }
         
         //convert from List to array
         double theseVals[] = new double[valueList.size()];
         double theseTimes[] = new double[valueList.size()];
         for(i=0 ; i<valueList.size() ; i++) {
            theseVals[i] = valueList.get(i).floatValue();
            theseTimes[i] = timeList.get(i).doubleValue();
         }
         
         //add array to main data list
         mValues.add(theseVals);
         mXValues.add(theseTimes);
      }
   }
}
