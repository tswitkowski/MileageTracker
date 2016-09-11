package com.switkows.mileage.Charts;

import java.util.ArrayList;
import java.util.List;
import org.achartengine.chart.*;

import com.switkows.mileage.MileageData;

import android.content.Context;
import android.graphics.Color;

public class MilesChart extends TimeChartExtension {
   // strings which will return series index
   private static final int          TOTAL_MILES = 0;

   private static final String[]     mTitles     = {"Miles over time", "Km over Time"};
   private static final String[]     mUnits      = {"MPG", "Km/L"};
   private static final String[]     mLineTitles = {"Total Distance"};
   private static final int[]        colors      = {Color.BLUE};
   private static final PointStyle[] styles      = {PointStyle.CIRCLE};

   public MilesChart(Context c, MileageData[] data, boolean isUS) {
      super(c, mTitles[isUS ? 0 : 1], mUnits[isUS ? 0 : 1], mLineTitles, colors, styles, data);
   }

   @Override
   protected void appendDataToSeries(long date, float[] values) {
      appendDataToSeries(date, TOTAL_MILES, values[0]);
   }

   @Override
   protected List<double[]> buildValuesList(MileageData[] data) {
      double[] miles = new double[data.length];
      List<double[]> values = new ArrayList<double[]>();
      for (String ignored : mTitles) {
         for (int row = 0; row < data.length; row++) {
            miles[row] = data[row].getOdometerReading(mContext, null);
         }
      }
      values.add(miles);
      return values;
   }
}
