package com.switkows.mileage.Charts;

import java.util.ArrayList;
import java.util.List;
import org.achartengine.chart.*;

import com.switkows.mileage.MileageData;

import android.content.Context;
import android.graphics.Color;

public class MileageChart extends TimeChartExtension {
   // strings which will return series index
   public static final int           COMPUTER_MILEAGE = 0, ACTUAL_MILEAGE = 1;

   private static final String[]     mTitles          = {"MPG over Time", "Km/L over Time"};
   private static final String[]     mUnits           = {"MPG", "Km/L"};
   private static final String[]     mLineTitles      = {"Computer Mileage", "Actual Mileage"};
   private static final int[]        colors           = {Color.BLUE, Color.RED};
   private static final PointStyle[] styles           = {PointStyle.CIRCLE, PointStyle.SQUARE};

   public MileageChart(Context c, MileageData[] data, boolean isUS) {
      super(c, mTitles[isUS ? 0 : 1], mUnits[isUS ? 0 : 1], mLineTitles, colors, styles, data);
   }

   @Override
   protected void appendDataToSeries(long date, float[] values) {
      appendDataToSeries(date, COMPUTER_MILEAGE, values[0]);
      appendDataToSeries(date, ACTUAL_MILEAGE, values[1]);
   }

   @Override
   protected List<double[]> buildValuesList(MileageData[] data) {
      double[] comp_mpg = new double[data.length];
      double[] act_mpg = new double[data.length];
      List<double[]> values = new ArrayList<double[]>();
      for(int i = 0; i < mTitles.length; i++) {
         for(int row = 0; row < data.length; row++) {
            comp_mpg[row] = data[row].getComputerMileage(mContext, null);
            act_mpg[row] = data[row].getActualMileage(mContext, null);
         }
      }
      values.add(comp_mpg);
      values.add(act_mpg);
      return values;
   }
}
