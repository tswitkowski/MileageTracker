package com.switkows.mileage.Charts;

import java.util.ArrayList;
import java.util.List;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import com.switkows.mileage.MileageData;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

public abstract class TimeChartExtension {
   private XYMultipleSeriesDataset    mDataSet;
   protected XYMultipleSeriesRenderer mRenderer;

   private GraphicalView              mView;
   protected final Context            mContext;
   private final String               mTitle;
   private final String               mYLabel;
   private boolean                    mIsPanZoomable;

   protected String[]                 mTitles;
   protected int[]                    mColors;
   protected PointStyle[]             mStyles;
   protected MileageData[]            mData;
   protected float                    mNormalize = 0;

   /**
    * creates the renderer and GraphicalView
    * 
    * @param titles
    * @param colors
    * @param styles
    */
   public TimeChartExtension(Context c, String title, String ylabel, String[] titles, int[] colors, PointStyle[] styles, MileageData[] data) {
      mContext = c;
      mTitle = title;
      mYLabel = ylabel;
      mTitles = titles;
      mColors = colors;
      mStyles = styles;
      mData = data;
   }

   public void buildChart() {
      mRenderer = buildRenderer(mColors, mStyles);
      List<double[]> x = buildXList(mData);
      List<double[]> values = buildValuesList(mData);
      int length = mRenderer.getSeriesRendererCount();
      for(int i = 0; i < length; i++) {
         ((XYSeriesRenderer)mRenderer.getSeriesRendererAt(i)).setFillPoints(true);
      }
      mDataSet = buildDataset(mTitles, x, values);
      setChartSettings(mRenderer, mTitle, "Date", mYLabel, 0, 100, 0, 100, Color.LTGRAY, Color.GRAY);
      mRenderer.setYLabels(10);
      mRenderer.setFitLegend(true);
      if(mIsPanZoomable) {
         mRenderer.setPanEnabled(true, true);
         mRenderer.setZoomEnabled(true, true);
         Configuration config = mContext.getResources().getConfiguration();
         if(config.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH)
            mRenderer.setZoomButtonsVisible(true);
      } else {
         mRenderer.setPanEnabled(false, false);
         mRenderer.setZoomEnabled(false, false);
         //reduce the number of labels (this should only occur for the
         //main app display, where charts are rendered pretty small)
         mRenderer.setYLabels(7);
      }
      autoFitChart();
      mView = ChartFactory.getTimeChartView(mContext, mDataSet, mRenderer, "MMM yyyy");
   }

   public GraphicalView getChart() {
      if(mView == null)
         buildChart();
      return mView;
   }

   public void setTitles(String[] titles) {
      mTitles = titles;
   }

   public void setColors(int[] colors) {
      mColors = colors;
   }

   public void setStyles(PointStyle[] s) {
      mStyles = s;
   }

   public void setPanZoomable(boolean setting) {
      mIsPanZoomable = setting;
   }

   public void setNormalizedValue(float value) {
      mNormalize = value;
   }

   /**
    * These methods Must be implemented by derived classes!
    * 
    * @param data
    * @return
    */
   protected abstract List<double[]> buildValuesList(MileageData[] data);

   protected List<double[]> buildXList(MileageData[] data) {
      List<double[]> x = new ArrayList<double[]>();
      for(int i = 0; i < mTitles.length; i++) {
         double[] x_row = new double[data.length];
         for(int row = 0; row < data.length; row++) {
            x_row[row] = data[row].getDate();
         }
         x.add(x_row);
      }
      return x;
   }

   protected abstract void appendDataToSeries(long date, float[] values);

   public void clearData() {
      XYSeries[] allSeries = mDataSet.getSeries();
      for(XYSeries series : allSeries)
         mDataSet.removeSeries(series);
      for(String title : mTitles)
         mDataSet.addSeries(new XYSeries(title));
   }

   protected void appendDataToSeries(long date, int ser, float value) {
      XYSeries series = mDataSet.getSeriesAt(ser);
      series.add(date, value);
   }

   /**
    * This is the ONLY public method for appending data to a plot! As is such, it relies on abstract methods, which the
    * subclasses must implement to get the proper behavior
    * 
    * @param date
    *           <code>long</code> representation of the date to be used for this datapoint
    * @param values
    *           one (or more) data values to add to the plot. Depending on the subclass implementation, a certain number
    *           of values should be present
    * @param autofit
    *           a <code>true</code> value will cause the min/max settings for the plot to be re-calculated. Do this
    *           infrequently whenever possible, to help performance
    */
   public void appendDataToSeries(long date, float[] values, boolean autofit) {
      appendDataToSeries(date, values);
      if(autofit)
         autoFitChart();
   }

   /**
    * Builds an XY multiple series renderer.
    * 
    * @param colors
    *           the series rendering colors
    * @param styles
    *           the series point styles
    * @return the XY multiple series renderers
    */
   private static final float LEGEND_FONT_SIZE_PT = 8.0f;

   protected XYMultipleSeriesRenderer buildRenderer(int[] colors, PointStyle[] styles) {
      XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
      int length = colors.length;
      for(int i = 0; i < length; i++) {
         XYSeriesRenderer r = new XYSeriesRenderer();
         r.setColor(colors[i]);
         r.setPointStyle(styles[i]);
         renderer.addSeriesRenderer(r);
      }
      return renderer;
   }

   /**
    * Sets a few of the series renderer settings.
    * 
    * @param renderer
    *           the renderer to set the properties to
    * @param title
    *           the chart title
    * @param xTitle
    *           the title for the X axis
    * @param yTitle
    *           the title for the Y axis
    * @param xMin
    *           the minimum value on the X axis
    * @param xMax
    *           the maximum value on the X axis
    * @param yMin
    *           the minimum value on the Y axis
    * @param yMax
    *           the maximum value on the Y axis
    * @param axesColor
    *           the axes color
    * @param labelsColor
    *           the labels color
    */
   protected void setChartSettings(XYMultipleSeriesRenderer renderer, String title, String xTitle, String yTitle, double xMin, double xMax, double yMin,
         double yMax, int axesColor, int labelsColor) {
      renderer.setChartTitle(title);
      renderer.setXTitle(xTitle);
      renderer.setYTitle(yTitle);
//      renderer.setMarginsColor(Color.TRANSPARENT);
      renderer.setBackgroundColor(Color.BLACK);
      renderer.setApplyBackgroundColor(true);
      renderer.setRange(new double[] {xMin, xMax, yMin, yMax});
      renderer.setAxesColor(axesColor);
      renderer.setLabelsColor(labelsColor);
      renderer.setShowGrid(false);
      final float scale = mContext.getResources().getDisplayMetrics().density;
      final float fontSize = (LEGEND_FONT_SIZE_PT * scale + 0.5f);
      renderer.setLegendTextSize(fontSize);
      renderer.setChartTitleTextSize(fontSize);
      renderer.setAxisTitleTextSize(fontSize);
      renderer.setLabelsTextSize(fontSize);
   }

   protected void autoFitChart() {
      if(mDataSet == null)
         return;
      if(mDataSet.getSeriesCount() == 0)
         return;
      if(mDataSet.getSeriesAt(0).getItemCount() == 0)
         return;
      double minX = mDataSet.getSeriesAt(0).getX(0), minY = mDataSet.getSeriesAt(0).getY(0), maxX = 0, maxY = 0;
      for(XYSeries series : mDataSet.getSeries()) {
         // Log.d("TJS","xmin="+minX+", xmax="+maxX+", ymin="+minY+", ymax="+maxY);
         // for(int i=0 ; i<series.getItemCount() ; i++) {
         // Log.d("TJS","val["+i+"].x = "+series.getX(i));
         // Log.d("TJS","val["+i+"].y = "+series.getY(i));
         // }
         double locMinX = series.getMinX(), locMinY = series.getMinY(), locMaxX = series.getMaxX(), locMaxY = series.getMaxY();
         if(locMinX < minX)
            minX = locMinX;
         if(locMinY < minY)
            minY = locMinY;
         if(locMaxX > maxX)
            maxX = locMaxX;
         if(locMaxY > maxY)
            maxY = locMaxY;
      }
      // Log.d("TJS","xmin="+minX+", xmax="+maxX+", ymin="+minY+", ymax="+maxY);
      double[] range = {minX, maxX, minY, maxY};
      mRenderer.setRange(range);
      mRenderer.setInitialRange(range);
//      mRenderer.setLegendHeight(((int)((mDataSet.getSeriesCount())/3)+3)*15);
   }

   /**
    * Builds an XY multiple dataset using the provided values.
    * 
    * @param titles
    *           the series titles
    * @param xValues
    *           the values for the X axis
    * @param yValues
    *           the values for the Y axis
    * @return the XY multiple dataset
    */
   protected XYMultipleSeriesDataset buildDataset(String[] titles, List<double[]> xValues, List<double[]> yValues) {
      XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
      int length = titles.length;
      for(int i = 0; i < length; i++) {
         XYSeries series = new XYSeries(titles[i]);
         double[] xV = xValues.get(i);
         double[] yV = yValues.get(i);
         int seriesLength = yV.length; //assume x is >= length of y, but y may be less than x!
         for(int k = 0; k < seriesLength; k++) {
            series.add(xV[k], yV[k]);
         }
         dataset.addSeries(series);
      }
      return dataset;
   }

}
