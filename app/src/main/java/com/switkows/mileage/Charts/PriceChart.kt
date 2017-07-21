package com.switkows.mileage.Charts

import java.util.ArrayList
import org.achartengine.chart.*

import com.switkows.mileage.MileageData

import android.content.Context
import android.graphics.Color

class PriceChart(c: Context, data: Array<MileageData>, isUS: Boolean) : TimeChartExtension(c, mTitles[if (isUS) 0 else 1], "Price($)", mLineTitles, colors, styles, data) {

    override fun appendDataToSeries(date: Long, values: FloatArray) {
        appendDataToSeries(date, PRICE, values[0])
    }

    override fun buildValuesList(data: Array<MileageData>): List<DoubleArray> {
        val price = DoubleArray(data.size)
        val values = ArrayList<DoubleArray>()
        mTitles?.forEach { _ ->
            for (row in data.indices) {
                price[row] = data[row].price.toDouble()
            }
        }
        values.add(price)
        return values
    }

    companion object {
        // strings which will return series index
        private val PRICE = 0

        private val mTitles = arrayOf("Price Per Gallon vs. Time", "Price Per Liter vs. Time")
        private val mLineTitles = arrayOf("Price")
        private val colors = intArrayOf(Color.BLUE)
        private val styles = arrayOf(PointStyle.SQUARE)
    }
}
