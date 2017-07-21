package com.switkows.mileage

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.LinearLayout

import com.switkows.mileage.Charts.MileageChart
import com.switkows.mileage.Charts.MileageDiffChart
import com.switkows.mileage.Charts.MileageVsStationChart
import com.switkows.mileage.Charts.MilesChart
import com.switkows.mileage.Charts.PriceChart
import com.switkows.mileage.Charts.TimeChartExtension
import com.switkows.mileage.MileageTracker.ShowLargeChart

/**
 * This class holds all charts that will be rendered for this application.

 * @author switkows
 */
internal class MileageChartManager(private val mContext: Context, cursor: Cursor) {
    private lateinit var dataSet: Array<MileageData>
    private var prefs: SharedPreferences? = null

    init {
        loadData(cursor)
    }

    private fun loadData(cursor: Cursor) {
        val dataSetList = ArrayList<MileageData>()
        for (i in 0..cursor.count - 1) {
            cursor.moveToPosition(i)
            dataSetList.add(MileageData(cursor))
        }
        dataSet = dataSetList.toTypedArray()
    }

    private fun getPrefs(): SharedPreferences {
        if (prefs == null)
            prefs = PreferenceManager.getDefaultSharedPreferences(mContext)
        return prefs!!
    }

    // Utility methods for determining what units to display information in
    val distanceUnits: String
        get() = MileageData.getDistanceUnits(getPrefs(), mContext)

    val economyUnits: String
        get() = MileageData.getEconomyUnits(getPrefs(), mContext)

    // FIXME - this should not go in the chartManager, but it's convenient..
    // should be moved to the content provider, instead
    val averageMPG: Float
        get() {
            if (dataSet.isEmpty())
                return 0f
            return MileageData.getEconomy(getTotal(MileageData.TRIP) / getTotal(MileageData.GALLONS), getPrefs(), mContext)
        }

    val averageTrip: Float
        get() = MileageData.getDistance(getAverage(MileageData.TRIP), getPrefs(), mContext)

    val averageDiff: Float
        get() = getAverage(MileageData.MPG_DIFF)

    val bestMPG: Float
        get() = MileageData.getEconomy(getBest(MileageData.ACTUAL_MILEAGE), getPrefs(), mContext)

    val bestTrip: Float
        get() = MileageData.getDistance(getBest(MileageData.TRIP), getPrefs(), mContext)

    private fun getTotal(field: Int): Float {
        var total = 0f
        dataSet.forEach { item -> total += item.getFloatValue(field)}
        return total
    }

    private fun getAverage(field: Int): Float {
        if (dataSet.isEmpty())
            return 0f
        return getTotal(field) / dataSet.size
    }

    private fun getBest(field: Int): Float {
        var best = 0f
        var curVal: Float
        for (item in dataSet) {
            curVal = item.getFloatValue(field)
            if (curVal > best)
                best = curVal
        }
        return best
    }

    fun addCharts(charts: Array<LinearLayout>, listeners: Array<ShowLargeChart>) {
        val locPrefs = getPrefs()
        if (charts.isNotEmpty())
            genAddChart(charts[0], listeners[0], locPrefs, R.string.chart1Selection)
        if (charts.size > 1)
            genAddChart(charts[1], listeners[1], locPrefs, R.string.chart2Selection)
        if (charts.size > 2)
            genAddChart(charts[2], listeners[2], locPrefs, R.string.chart3Selection)
    }

    private fun genAddChart(view: LinearLayout, listener: ShowLargeChart, prefs: SharedPreferences, key: Int) {
        if (!prefs.contains(mContext.getString(key))) {
            Log.e("TJS", "Error. key wasn't found...bad news...")
            return
        }
        val strVal = prefs.getString(mContext.getString(key), "100")
        var chartType = NO_CHART
        try {
            chartType = Integer.parseInt(strVal)
        } catch (e: NumberFormatException) {
            Log.e("TJS", e.toString())
        }

        val isUS = MileageData.isMilesGallons(prefs, mContext)
        when (chartType) {
            NO_CHART -> {
            }
            MPG_CHART, MPG_DIFF_CHART, PRICE_CHART, MPG_STATION_CHART, ODO_CHART -> {
                listener.setID(chartType)
                val chart = createChart(chartType, false, isUS)
                chart!!.setOnClickListener(listener)
                view.addView(chart)
            }
        }
        view.visibility = if (chartType == NO_CHART) View.GONE else View.VISIBLE
    }

    fun createChart(chartID: Int, isPanZoomable: Boolean, isUS: Boolean): View? {
        var chart: TimeChartExtension? = null
        //      float average = 0;
        when (chartID) {
            MPG_CHART -> chart = MileageChart(mContext, dataSet, isUS)
            MPG_DIFF_CHART -> chart = MileageDiffChart(mContext, dataSet)
            PRICE_CHART -> chart = PriceChart(mContext, dataSet, isUS)
            MPG_STATION_CHART -> chart = MileageVsStationChart(mContext, dataSet, isUS)
            ODO_CHART -> chart = MilesChart(mContext, dataSet, isUS)
        }//           average = getAverageMPG();
        //           average = getAverageDiff();
        //           average = getAverage(MileageData.PRICE);
        //           average = getAverageMPG();
        if (chart != null) {
            chart.setPanZoomable(isPanZoomable)
            return chart.getChart()
        }
        return null
    }

    companion object {

        private const val MPG_CHART = 0
        private const val MPG_DIFF_CHART = 1
        private const val PRICE_CHART = 2
        private const val MPG_STATION_CHART = 3
        private const val ODO_CHART = 4
        const val NO_CHART = 100
    }

}
