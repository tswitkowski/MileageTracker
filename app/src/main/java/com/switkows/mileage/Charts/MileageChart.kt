package com.switkows.mileage.Charts

import java.util.ArrayList
import org.achartengine.chart.*

import com.switkows.mileage.MileageData

import android.content.Context
import android.graphics.Color

class MileageChart(c: Context, data: Array<MileageData>, isUS: Boolean) : TimeChartExtension(c, mTitles[if (isUS) 0 else 1], mUnits[if (isUS) 0 else 1], mLineTitles, colors, styles, data) {

    override fun appendDataToSeries(date: Long, values: FloatArray) {
        appendDataToSeries(date, COMPUTER_MILEAGE, values[0])
        appendDataToSeries(date, ACTUAL_MILEAGE, values[1])
    }

    override fun buildValuesList(data: Array<MileageData>): List<DoubleArray> {
        val comp_mpg = DoubleArray(data.size)
        val act_mpg = DoubleArray(data.size)
        val values = ArrayList<DoubleArray>()
        mTitles?.forEach { _ ->
            for (row in data.indices) {
                comp_mpg[row] = data[row].getComputerMileage(mContext, null).toDouble()
                act_mpg[row] = data[row].getActualMileage(mContext, null).toDouble()
            }
        }
        values.add(comp_mpg)
        values.add(act_mpg)
        return values
    }

    companion object {
        // strings which will return series index
        private val COMPUTER_MILEAGE = 0
        private val ACTUAL_MILEAGE = 1

        private val mTitles = arrayOf("MPG over Time", "Km/L over Time")
        private val mUnits = arrayOf("MPG", "Km/L")
        private val mLineTitles = arrayOf("Computer Mileage", "Actual Mileage")
        private val colors = intArrayOf(Color.BLUE, Color.RED)
        private val styles = arrayOf(PointStyle.CIRCLE, PointStyle.SQUARE)
    }
}
