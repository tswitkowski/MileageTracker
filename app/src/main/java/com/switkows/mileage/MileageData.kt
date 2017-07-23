package com.switkows.mileage

import android.annotation.SuppressLint
import java.text.SimpleDateFormat

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.preference.PreferenceManager
import android.util.Log
import java.util.*

class MileageData {
    var date: Long = 0
        private set
    internal var carName: String? = null
        private set
    private var gas_station: String? = null
    var values: FloatArray
        private set

    // main entry point. takes the 'inputs', computes the 3 computed values
    constructor(car: String,
                dt: String, station: String,
                odo: Float, trip: Float,
                gallons: Float, price: Float,
                comp_mpg: Float) : this(car, 0, station, odo, trip, gallons, price, comp_mpg, trip / gallons, gallons * price, 0f) {
        date = parseDate(dt)
        values[DbColToArrIdx(MPG_DIFF)] = Math.abs(comp_mpg - values[5]) / values[5]
    }

    constructor(dt: String, station: String,
                odo: Float, trip: Float,
                gallons: Float, price: Float,
                comp_mpg: Float, act_mpg: Float,
                total_price: Float, mpg_diff: Float) : this("", parseDate(dt), station, odo, trip, gallons, price, comp_mpg, act_mpg, total_price, mpg_diff)

    /**
     * used for 'importing' a csv file (though the commas will be split above this level)

     * @param strings data to import
     */
    constructor(strings: Array<String>) {
        date = parseDate(strings[DATE])
        gas_station = strings[STATION]
        carName = strings[CAR]
        values = FloatArray(DATA_LEN)
        var i = 0
        while (i < strings.size && i < DATA_LEN) {
            values[i] = java.lang.Float.parseFloat(strings[i + 2])
            i++
        }
        // Log.d("TJS","Creating entry with date = '"+getFormattedDate(date)+"'...");
        // Log.d("TJS","Creating entry for car '"+carName+"'");
    }

    // used as a simple-interface (fewer arguments), rather than specifying a
    // long list of float values
    constructor(car: String, dt: String, station: String, values: FloatArray) : this(car, dt, station, values[0], values[1], values[2], values[3], values[4])

    /**
     * used by MileageData(float[]) and MileageData(float,float,float,float,float)
     */
    constructor(car: String,
                dt: Long, station: String,
                odo: Float, trip: Float,
                gallons: Float, price: Float,
                comp_mpg: Float, act_mpg: Float,
                total_price: Float, mpg_diff: Float) {
        date = dt
        gas_station = station
        carName = car
        values = FloatArray(DATA_LEN)
        setFloatValue(ODOMETER, odo)
        setFloatValue(TRIP, trip)
        setFloatValue(GALLONS, gallons)
        setFloatValue(PRICE, price)
        setFloatValue(COMPUTER_MILEAGE, comp_mpg)
        setFloatValue(ACTUAL_MILEAGE, act_mpg)
        setFloatValue(TOTAL_PRICE, total_price)
        setFloatValue(MPG_DIFF, mpg_diff)
    }

    constructor(cursor: Cursor) : this(cursor.getString(cursor.getColumnIndex(ToDBNames[CAR])),
            cursor.getLong(cursor.getColumnIndex(ToDBNames[DATE])),
            cursor.getString(cursor.getColumnIndex(ToDBNames[STATION])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[ODOMETER])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[TRIP])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[GALLONS])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[PRICE])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[COMPUTER_MILEAGE])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[ACTUAL_MILEAGE])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[TOTAL_PRICE])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[MPG_DIFF])))

    // Log.d("TJS","Attempting to insert record with date = '"+getFormattedDate(getDate())+"'...");
    val content: ContentValues
        get() {
            val cValues = ContentValues()
            cValues.put(ToDBNames[DATE], date)
            cValues.put(ToDBNames[CAR], carName)
            cValues.put(ToDBNames[STATION], station)
            for (i in values.indices)
                cValues.put(ToDBNames[i + 2], getFloatValue(i + 2))
            return cValues
        }

    private fun setFloatValue(idx: Int, value: Float) {
        if (idx > STATION)
            values[DbColToArrIdx(idx)] = value
    }

    internal fun getFloatValue(idx: Int): Float {
        if (idx <= STATION)
            return values[idx]
        return values[DbColToArrIdx(idx)]
    }

    val station: String?
        get() {
            if (gas_station == "")
                return "unknown"
            return gas_station
        }

    private val odometerReading: Float
        get() = getFloatValue(ODOMETER)

    val price: Float
        get() = getFloatValue(PRICE)

    private val computerMileage: Float
        get() = getFloatValue(COMPUTER_MILEAGE)

    private val actualMileage: Float
        get() = getFloatValue(ACTUAL_MILEAGE)

    val mileageDiff: Float
        get() = getFloatValue(MPG_DIFF)

    //These methods return the specific data point with the correct units
    fun getComputerMileage(context: Context, prefs: SharedPreferences?): Float {
        return getEconomy(computerMileage, prefs, context)
    }

    fun getActualMileage(context: Context, prefs: SharedPreferences?): Float {
        return getEconomy(actualMileage, prefs, context)
    }

    fun getOdometerReading(context: Context, prefs: SharedPreferences): Float {
        return getDistance(odometerReading, prefs, context)
    }

    internal fun exportCSV(): String {
        var str = getFormattedDate(date) + "," + gas_station + ","
        for (`val` in values)
            str += java.lang.Float.toString(`val`) + ","
        str += carName!! + ","
        Log.d("TJS", "Exporting line '$str'...")
        return str
    }

    companion object {

        /**
         * This field is used to format the CSV output of the date field as a simple MM/DD/YYYY formatted string A separate
         * formatter is required for each 'thread' that might need to encode/decode, since apparently the functions are NOT
         * thread-safe
         */
        // This one is used to parse a string into an integer/date object
        @SuppressLint("SimpleDateFormat")
        private val dateDecoder = SimpleDateFormat("MM/dd/yyyy")
        // This one is used to generate a string from an integer
        @SuppressLint("SimpleDateFormat")
        private val dateFormatter = SimpleDateFormat("MM/dd/yyyy")
        internal const val DATE = 0
        internal const val STATION = 1
        internal const val ODOMETER = 2
        internal const val TRIP = 3
        internal const val GALLONS = 4
        internal const val PRICE = 5
        internal const val COMPUTER_MILEAGE = 6
        internal const val ACTUAL_MILEAGE = 7
        internal const val TOTAL_PRICE = 8
        internal const val MPG_DIFF = 9
        internal const val CAR = 10

        private fun DbColToArrIdx(idx: Int): Int {
            if (idx > STATION)
                return idx - STATION - 1
            return -1
        }

        private val DATA_LEN = 8

        internal val ToDBNames = arrayOf("date", "station", "odometer", "trip", "gallons", "price", "compMileage", "actMileage", "totalPrice", "mileageDiff", "carName")

        internal fun parseDate(dt: String): Long {
            var loc_dt: Long = 0
            try {
                val loc_date = dateDecoder.parse(dt)
                loc_dt = loc_date.time
            } catch (e: java.text.ParseException) {
                Log.e("TJS", "ERROR : " + e.toString())
            }

            // Log.d("TJS","parseDate("+dt+") = "+getFormattedDate(loc_dt));
            if (!dt.equals(dateDecoder.format(loc_dt), ignoreCase = true)) {
                Log.e("TJS", "Error: parseDate did not output correct results! parseDate(" + dt + ") != " + dateDecoder.format(loc_dt))
            }
            return loc_dt
        }

        internal fun exportCSVTitle(): String {
            var str = ""
            for (name in ToDBNames)
                str += name + ","
            return str
        }

        internal fun getFormattedDate(month: Int, day: Int, year: Int): String {
            val cal: GregorianCalendar = GregorianCalendar(year, month, day)
            return dateFormatter.format(cal.time)
        }
        internal fun getFormattedDate(milli: Long): String {
            return dateFormatter.format(milli)
        }

        internal fun getSimpleDescription(c: Cursor, dateColumn: Int, mpgColumn: Int, prefs: SharedPreferences, context: Context): String {
            val date = MileageData.dateFormatter.format(c.getLong(dateColumn))
            val mpg = MileageData.getEconomy(c.getFloat(mpgColumn), prefs, context)
            return String.format("%s (%2.1f %s)", date, mpg, MileageData.getEconomyUnits(prefs, context))

        }

        // Unit conversion helpers:
        private val US = 0
        private val METRIC = 1
        private val ECONOMY_UNIT_LABELS = arrayOf("mpg", "km/L")
        private val DISTANCE_UNIT_LABELS = arrayOf("mi", "km")
        private val LITER_PER_GALLON = 3.78541178.toFloat()
        private val KM_PER_MILE = 1.609344.toFloat()

        internal fun isMilesGallons(prefs: SharedPreferences, context: Context): Boolean {
            val units = prefs.getString(context.getString(R.string.unitSelection), "mpg")
            return units == "mpg"
        }

        internal fun getDistanceUnits(prefsIn: SharedPreferences?, context: Context): String {
            val prefs = prefsIn ?: PreferenceManager.getDefaultSharedPreferences(context)
            if (isMilesGallons(prefs!!, context))
                return DISTANCE_UNIT_LABELS[US]
            return DISTANCE_UNIT_LABELS[METRIC]
        }

        internal fun getEconomyUnits(prefsIn: SharedPreferences?, context: Context): String {
            val prefs = prefsIn ?: PreferenceManager.getDefaultSharedPreferences(context)
            if (isMilesGallons(prefs!!, context))
                return ECONOMY_UNIT_LABELS[US]
            return ECONOMY_UNIT_LABELS[METRIC]
        }

        internal fun getDistance(miles: Float, prefsIn: SharedPreferences?, context: Context): Float {
            val prefs = prefsIn ?: PreferenceManager.getDefaultSharedPreferences(context)
            if (isMilesGallons(prefs!!, context))
                return miles
            return miles * KM_PER_MILE
        }

        internal fun getVolume(gallons: Float, prefsIn: SharedPreferences?, context: Context): Float {
            val prefs = prefsIn ?: PreferenceManager.getDefaultSharedPreferences(context)
            if (isMilesGallons(prefs!!, context))
                return gallons
            return gallons * LITER_PER_GALLON
        }

        internal fun getEconomy(mpg: Float, prefsIn: SharedPreferences?, context: Context): Float {
            val prefs = prefsIn ?: PreferenceManager.getDefaultSharedPreferences(context)
            if (isMilesGallons(prefs!!, context))
                return mpg
            return getDistance(mpg, prefs, context) / getVolume(1f, prefs, context)
        }
    }

}
