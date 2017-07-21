package com.switkows.mileage.Charts

import java.util.ArrayList

import org.achartengine.chart.PointStyle

import com.switkows.mileage.MileageData

import android.content.Context
import android.graphics.Color

class MileageDiffChart(c: Context, data: Array<MileageData>) : TimeChartExtension(c, mTitles[0], "% diff", mTitles, colors, styles, data) {

    override fun appendDataToSeries(date: Long, values: FloatArray) {
        appendDataToSeries(date, AVERAGE_MPG_DIFF, values[0])
    }

    override fun buildValuesList(data: Array<MileageData>): List<DoubleArray> {
        val mpg_diff = DoubleArray(data.size)
        val values = ArrayList<DoubleArray>()
        mTitles?.forEach { _->
            for (row in data.indices) {
                mpg_diff[row] = data[row].mileageDiff.toDouble()
            }
        }
        values.add(mpg_diff)
        return values
    }

    companion object {
        // strings which will return series index
        private val AVERAGE_MPG_DIFF = 0

        private val mTitles = arrayOf("Computer Inaccuracy")
        private val colors = intArrayOf(Color.BLUE)
        private val styles = arrayOf(PointStyle.SQUARE)
    }
}
