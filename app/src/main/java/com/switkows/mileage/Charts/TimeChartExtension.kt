package com.switkows.mileage.Charts

import java.util.ArrayList

import org.achartengine.ChartFactory
import org.achartengine.GraphicalView
import org.achartengine.chart.PointStyle
import org.achartengine.model.XYMultipleSeriesDataset
import org.achartengine.model.XYSeries
import org.achartengine.renderer.XYMultipleSeriesRenderer
import org.achartengine.renderer.XYSeriesRenderer

import com.switkows.mileage.MileageData

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

abstract class TimeChartExtension
/**
 * creates the renderer and GraphicalView

 */
internal constructor(protected val mContext: Context, private val mTitle: String, private val mYLabel: String, internal var mTitles: Array<String>?,
                     internal var mColors: IntArray?, internal var mStyles: Array<PointStyle>?, internal var mData: Array<MileageData>) {
    private var mDataSet: XYMultipleSeriesDataset? = null
    private lateinit var mRenderer: XYMultipleSeriesRenderer

    private var mView: GraphicalView? = null
    private var mIsPanZoomable: Boolean = false
    internal var mNormalize = 0f

    private fun buildChart() {
        mRenderer = buildRenderer(mColors!!, mStyles!!)
        val x = buildXList(mData)
        val values = buildValuesList(mData)
        val length = mRenderer.seriesRendererCount
        for (i in 0..length - 1) {
            (mRenderer.getSeriesRendererAt(i) as XYSeriesRenderer).isFillPoints = true
        }
        mDataSet = buildDataset(mTitles!!, x, values)
        setChartSettings(mRenderer, mTitle, "Date", mYLabel, 0.0, 100.0, 0.0, 100.0, Color.LTGRAY, Color.GRAY)
        mRenderer.yLabels = 10
        mRenderer.isFitLegend = true
        if (mIsPanZoomable) {
            mRenderer.setPanEnabled(true, true)
            mRenderer.setZoomEnabled(true, true)
            val config = mContext.resources.configuration
            if (config.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH)
                mRenderer.isZoomButtonsVisible = true
        } else {
            mRenderer.setPanEnabled(false, false)
            mRenderer.setZoomEnabled(false, false)
            //reduce the number of labels (this should only occur for the
            //main app display, where charts are rendered pretty small)
            mRenderer.yLabels = 7
        }
        autoFitChart()
        mView = ChartFactory.getTimeChartView(mContext, mDataSet, mRenderer, "MMM yyyy")
    }

    open fun getChart(): GraphicalView {
        if (mView == null)
            buildChart()
        return mView!!
    }

    fun setPanZoomable(setting: Boolean) {
        mIsPanZoomable = setting
    }

    /**
     * These methods Must be implemented by derived classes!

     */
    protected abstract fun buildValuesList(data: Array<MileageData>): List<DoubleArray>

    internal open fun buildXList(data: Array<MileageData>): List<DoubleArray> {
        val x = ArrayList<DoubleArray>()
        mTitles?.forEach { _ ->
            val x_row = DoubleArray(data.size)
            for (row in data.indices) {
                x_row[row] = data[row].date.toDouble()
            }
            x.add(x_row)
        }
        return x
    }

    protected abstract fun appendDataToSeries(date: Long, values: FloatArray)

    internal fun appendDataToSeries(date: Long, ser: Int, value: Float) {
        val series = mDataSet!!.getSeriesAt(ser)
        series.add(date.toDouble(), value.toDouble())
    }

    /**
     * Builds an XY multiple series renderer.

     * @param colors
     * *           the series rendering colors
     * *
     * @param styles
     * *           the series point styles
     * *
     * @return the XY multiple series renderers
     */
    private fun buildRenderer(colors: IntArray, styles: Array<PointStyle>): XYMultipleSeriesRenderer {
        val renderer = XYMultipleSeriesRenderer()
        val length = colors.size
        for (i in 0..length - 1) {
            val r = XYSeriesRenderer()
            r.color = colors[i]
            r.pointStyle = styles[i]
            renderer.addSeriesRenderer(r)
        }
        return renderer
    }

    /**
     * Sets a few of the series renderer settings.

     * @param renderer
     * *           the renderer to set the properties to
     * *
     * @param title
     * *           the chart title
     * *
     * @param xTitle
     * *           the title for the X axis
     * *
     * @param yTitle
     * *           the title for the Y axis
     * *
     * @param xMin
     * *           the minimum value on the X axis
     * *
     * @param xMax
     * *           the maximum value on the X axis
     * *
     * @param yMin
     * *           the minimum value on the Y axis
     * *
     * @param yMax
     * *           the maximum value on the Y axis
     * *
     * @param axesColor
     * *           the axes color
     * *
     * @param labelsColor
     * *           the labels color
     */
    private fun setChartSettings(renderer: XYMultipleSeriesRenderer,
                                 title: String, xTitle: String, yTitle: String,
                                 xMin: Double, xMax: Double, yMin: Double, yMax: Double,
                                 axesColor: Int, labelsColor: Int) {
        renderer.chartTitle = title
        renderer.xTitle = xTitle
        renderer.yTitle = yTitle
        //      renderer.setMarginsColor(Color.TRANSPARENT);
        renderer.backgroundColor = Color.BLACK
        renderer.isApplyBackgroundColor = true
        renderer.setRange(doubleArrayOf(xMin, xMax, yMin, yMax))
        renderer.axesColor = axesColor
        renderer.labelsColor = labelsColor
        renderer.setShowGrid(false)
        val scale = mContext.resources.displayMetrics.density
        val fontSize = LEGEND_FONT_SIZE_PT * scale + 0.5f
        renderer.legendTextSize = fontSize
        renderer.chartTitleTextSize = fontSize
        renderer.axisTitleTextSize = fontSize
        renderer.labelsTextSize = fontSize
    }

    private fun autoFitChart() {
        if (mDataSet == null)
            return
        if (mDataSet!!.seriesCount == 0)
            return
        if (mDataSet!!.getSeriesAt(0).itemCount == 0)
            return
        var minX = mDataSet!!.getSeriesAt(0).getX(0)
        var minY = mDataSet!!.getSeriesAt(0).getY(0)
        var maxX = 0.0
        var maxY = 0.0
        for (series in mDataSet!!.series) {
            // Log.d("TJS","xmin="+minX+", xmax="+maxX+", ymin="+minY+", ymax="+maxY);
            // for(int i=0 ; i<series.getItemCount() ; i++) {
            // Log.d("TJS","val["+i+"].x = "+series.getX(i));
            // Log.d("TJS","val["+i+"].y = "+series.getY(i));
            // }
            val locMinX = series.minX
            val locMinY = series.minY
            val locMaxX = series.maxX
            val locMaxY = series.maxY
            if (locMinX < minX)
                minX = locMinX
            if (locMinY < minY)
                minY = locMinY
            if (locMaxX > maxX)
                maxX = locMaxX
            if (locMaxY > maxY)
                maxY = locMaxY
        }
        // Log.d("TJS","xmin="+minX+", xmax="+maxX+", ymin="+minY+", ymax="+maxY);
        val range = doubleArrayOf(minX, maxX, minY, maxY)
        mRenderer.setRange(range)
        mRenderer.initialRange = range
        //      mRenderer.setLegendHeight(((int)((mDataSet.getSeriesCount())/3)+3)*15);
    }

    /**
     * Builds an XY multiple dataset using the provided values.

     * @param titles
     * *           the series titles
     * *
     * @param xValues
     * *           the values for the X axis
     * *
     * @param yValues
     * *           the values for the Y axis
     * *
     * @return the XY multiple dataset
     */
    private fun buildDataset(titles: Array<String>, xValues: List<DoubleArray>, yValues: List<DoubleArray>): XYMultipleSeriesDataset {
        val dataset = XYMultipleSeriesDataset()
        val length = titles.size
        for (i in 0..length - 1) {
            val series = XYSeries(titles[i])
            val xV = xValues[i]
            val yV = yValues[i]
            val seriesLength = yV.size //assume x is >= length of y, but y may be less than x!
            for (k in 0..seriesLength - 1) {
                series.add(xV[k], yV[k])
            }
            dataset.addSeries(series)
        }
        return dataset
    }

    companion object {

        private val LEGEND_FONT_SIZE_PT = 8.0f
    }

}
