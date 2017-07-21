package com.switkows.mileage.Charts

import java.util.ArrayList

import org.achartengine.GraphicalView
import org.achartengine.chart.*

import com.switkows.mileage.MileageData

import android.content.Context
import android.graphics.Color

class MileageVsStationChart(c: Context, data: Array<MileageData>, isUS: Boolean)
    : TimeChartExtension(c, mChartTitles[if (isUS) 0 else 1], mUnits[if (isUS) 0 else 1], null, null, null, data) {

    private lateinit var mValues: MutableList<DoubleArray>
    private lateinit var mXValues: MutableList<DoubleArray>

    override fun appendDataToSeries(date: Long, values: FloatArray) {
        //      appendDataToSeries(date, COMPUTER_MILEAGE, values[0]);
        //      appendDataToSeries(date, ACTUAL_MILEAGE, values[1]);
    }

    //For this chart, we must analyze all 'data' before we know which titles are present.
    //because of this, we must do a one-short analysis, determining titles as well as data
    override fun buildValuesList(data: Array<MileageData>): List<DoubleArray> {
        return mValues
    }

    override fun buildXList(data: Array<MileageData>): List<DoubleArray> {
        return mXValues
    }

    override fun getChart(): GraphicalView {
        analyzeData()
        return super.getChart()
    }

    private fun analyzeData() {
        mValues = ArrayList<DoubleArray>()
        mXValues = ArrayList<DoubleArray>()
        val titles = ArrayList<String>()
        val colors = ArrayList<Int>()
        val styles = ArrayList<PointStyle>()
        var i = 0
        //search for unique station names
        for (item in mData) {
            val station = item.station
            if (!titles.contains(station)) {
                titles.add(station)
                colors.add(allColors[i % allColors.size])
                styles.add(allStyles[i / allColors.size % allStyles.size])
                i++
            }
        }

        mStyles = styles.toTypedArray()
        mColors = colors.toIntArray()
        mTitles = titles.toTypedArray()

        //build 2-d array of data
        for (title in titles) {
            val valueList = ArrayList<Float>()
            val timeList = ArrayList<Double>()
            //a little tricky here: you must grab the date & mileage from
            //the entry AFTER the entry that matches the station name!
            i = 1
            while (i < mData.size) {
                if (title == mData[i - 1].station) {
                    valueList.add(mData[i].getActualMileage(mContext, null))
                    timeList.add(mData[i].date.toDouble())
                }
                i++
            }

            //convert from List to array
            val theseValues = DoubleArray(valueList.size)
            val theseTimes = DoubleArray(valueList.size)
            i = 0
            while (i < valueList.size) {
                theseValues[i] = (valueList[i] - mNormalize).toDouble()
                theseTimes[i] = timeList[i]
                i++
            }

            //add array to main data list
            mValues.add(theseValues)
            mXValues.add(theseTimes)
        }
    }

    companion object {

        private val mChartTitles = arrayOf("MPG over Time", "Km/L over Time")
        private val mUnits = arrayOf("MPG", "Km/L")
        private val allColors = intArrayOf(Color.BLUE, Color.RED, Color.GREEN, Color.GRAY, Color.WHITE, Color.CYAN, Color.MAGENTA)
        private val allStyles = arrayOf(PointStyle.CIRCLE, PointStyle.SQUARE)
    }
}
