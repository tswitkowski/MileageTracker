package com.switkows.mileage.Charts;

import java.util.ArrayList;
import java.util.List;

import org.achartengine.chart.PointStyle;

import com.switkows.mileage.MileageData;

import android.content.Context;
import android.graphics.Color;

public class MileageDiffChart extends TimeChartExtension {
   // strings which will return series index
   private static int                AVERAGE_MPG_DIFF = 0;

   private static final String[]     mTitles          = {"Computer Inaccuracy"};
   private static final int[]        colors           = {Color.BLUE};
   private static final PointStyle[] styles           = {PointStyle.SQUARE};

   public MileageDiffChart(Context c, MileageData[] data) {
      super(c, mTitles[0], "% diff", mTitles, colors, styles, data);
   }

   @Override
   protected void appendDataToSeries(long date, float[] values) {
      appendDataToSeries(date, AVERAGE_MPG_DIFF, values[0]);
   }

   @Override
   protected List<double[]> buildValuesList(MileageData[] data) {
      double[] mpg_diff = new double[data.length];
      List<double[]> values = new ArrayList<double[]>();
      for (String ignored : mTitles) {
         for (int row = 0; row < data.length; row++) {
            mpg_diff[row] = data[row].getMileageDiff();
         }
      }
      values.add(mpg_diff);
      return values;
   }
}
