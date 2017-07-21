package com.switkows.mileage.Charts

import java.util.ArrayList
import org.achartengine.chart.*

import com.switkows.mileage.MileageData

import android.content.Context
import android.graphics.Color

class MilesChart(c: Context, data: Array<MileageData>, isUS: Boolean) : TimeChartExtension(c, mTitles[if (isUS) 0 else 1], mUnits[if (isUS) 0 else 1], mLineTitles, colors, styles, data) {

    override fun appendDataToSeries(date: Long, values: FloatArray) {
        appendDataToSeries(date, TOTAL_MILES, values[0])
    }

    override fun buildValuesList(data: Array<MileageData>): List<DoubleArray> {
        val miles = DoubleArray(data.size)
        val values = ArrayList<DoubleArray>()
        mTitles?.forEach { _ ->
            for (row in data.indices) {
                miles[row] = data[row].getOdometerReading(mContext, null).toDouble()
            }
        }
        values.add(miles)
        return values
    }

    companion object {
        // strings which will return series index
        private val TOTAL_MILES = 0

        private val mTitles = arrayOf("Miles over time", "Km over Time")
        private val mUnits = arrayOf("MPG", "Km/L")
        private val mLineTitles = arrayOf("Total Distance")
        private val colors = intArrayOf(Color.BLUE)
        private val styles = arrayOf(PointStyle.CIRCLE)
    }
}
