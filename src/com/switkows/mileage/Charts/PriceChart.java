package com.switkows.mileage.Charts;

import java.util.ArrayList;
import java.util.List;
import org.achartengine.chart.*;

import com.switkows.mileage.MileageData;

import android.content.Context;
import android.graphics.Color;

public class PriceChart extends TimeChartExtension {
   // strings which will return series index
   public static final int           PRICE       = 0;

   private static final String[]     mTitles     = { "Price Per Gallon vs. Time", "Price Per Liter vs. Time" };
   private static final String[]     mLineTitles = { "Price" };
   private static final int[]        colors      = { Color.BLUE };
   private static final PointStyle[] styles      = { PointStyle.SQUARE };

   public PriceChart(Context c, MileageData[] data, boolean isUS) {
      super(c, mTitles[isUS ? 0 : 1], "Price($)", mLineTitles, colors, styles, data);
   }

   protected void appendDataToSeries(long date, float[] values) {
      appendDataToSeries(date, PRICE, values[0]);
   }

   protected List<double[]> buildValuesList(MileageData[] data) {
      double[] price = new double[data.length];
      List<double[]> values = new ArrayList<double[]>();
      for(int i = 0; i < mTitles.length; i++) {
         for(int row = 0; row < data.length; row++) {
            price[row] = data[row].getPrice();
         }
      }
      values.add(price);
      return values;
   }
}
