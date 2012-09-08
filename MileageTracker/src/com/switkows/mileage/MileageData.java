package com.switkows.mileage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.util.Log;

public class MileageData {
   private long                         date;
   private String                       carName;
   private String                       gas_station;
   private float[]                      values;

   /**
    * This field is used to format the CSV output of the date field as a simple MM/DD/YYYY formatted string A separate
    * formatter is required for each 'thread' that might need to encode/decode, since apparently the functions are NOT
    * thread-safe
    */
   // This one is used to parse a string into an integer/date object
   public static final SimpleDateFormat dateDecoder   = new SimpleDateFormat("MM/dd/yyyy");
   // This one is used to generate a string from an integer
   public static final SimpleDateFormat dateFormatter = new SimpleDateFormat("MM/dd/yyyy");
   public static final int              DATE          = 0, STATION = 1, ODOMETER = 2, TRIP = 3, GALLONS = 4, PRICE = 5,
         COMPUTER_MILEAGE = 6, ACTUAL_MILEAGE = 7, TOTAL_PRICE = 8, MPG_DIFF = 9, CAR = 10;

   public static final int DbColToArrIdx(int idx) {
      if(idx > STATION)
         return idx - STATION - 1;
      return -1;
   }

   public static final int ArrIdxToDbCol(int idx) {
      return idx + STATION + 1;
   }

   public static final int      DATA_LEN  = 8;

   public final static String[] ToDBNames = { "date", "station", "odometer", "trip", "gallons", "price", "compMileage",
      "actMileage", "totalPrice", "mileageDiff", "carName" };

   // main entry point. takes the 'inputs', computes the 3 computed values
   public MileageData(Context c, String car, String dt, String station, float odo, float trip, float gallons,
         float price, float comp_mpg) {
      this(c, car, 0, station, odo, trip, gallons, price, comp_mpg, trip / gallons, gallons * price, 0);
      date = parseDate(dt);
      values[DbColToArrIdx(MPG_DIFF)] = Math.abs((comp_mpg - values[5])) / values[5];
   }

   public MileageData(Context c, String dt, String station, float odo, float trip, float gallons, float price,
         float comp_mpg, float act_mpg, float total_price, float mpg_diff) {
      this(c, "", parseDate(dt), station, odo, trip, gallons, price, comp_mpg, act_mpg, total_price, mpg_diff);
   }

   /**
    * used for 'importing' a csv file (though the commas will be split above this level)
    * 
    * @param strings
    */
   public MileageData(Context c, String[] strings) {
      date = parseDate(strings[DATE]);
      gas_station = strings[STATION];
      carName = strings[CAR];
      values = new float[DATA_LEN];
      for(int i = 0; i < strings.length && i < DATA_LEN; i++) {
         values[i] = Float.parseFloat(strings[i + 2]);
      }
      // Log.d("TJS","Creating entry with date = '"+getFormattedDate(date)+"'...");
      // Log.d("TJS","Creating entry for car '"+carName+"'");
   }

   // used as a simple-interface (fewer arguments), rather than specifying a
   // long list of float values
   public MileageData(Context c, String car, String dt, String station, float[] vals) {
      this(c, car, dt, station, vals[0], vals[1], vals[2], vals[3], vals[4]);
   }

   /**
    * used by MileageData(float[]) and MileageData(float,float,float,float,float)
    */
   public MileageData(Context c, String car, long dt, String station, float odo, float trip, float gallons,
         float price, float comp_mpg, float act_mpg, float total_price, float mpg_diff) {
      date = dt;
      gas_station = station;
      carName = car;
      values = new float[DATA_LEN];
      setFloatValue(ODOMETER, odo);
      setFloatValue(TRIP, trip);
      setFloatValue(GALLONS, gallons);
      setFloatValue(PRICE, price);
      setFloatValue(COMPUTER_MILEAGE, comp_mpg);
      setFloatValue(ACTUAL_MILEAGE, act_mpg);
      setFloatValue(TOTAL_PRICE, total_price);
      setFloatValue(MPG_DIFF, mpg_diff);
   }

   public MileageData(Context c, Cursor cursor) {
      this(c, cursor.getString(cursor.getColumnIndex(ToDBNames[CAR])),
            cursor.getLong(cursor.getColumnIndex(ToDBNames[DATE])),
            cursor.getString(cursor.getColumnIndex(ToDBNames[STATION])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[ODOMETER])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[TRIP])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[GALLONS])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[PRICE])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[COMPUTER_MILEAGE])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[ACTUAL_MILEAGE])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[TOTAL_PRICE])),
            cursor.getFloat(cursor.getColumnIndex(ToDBNames[MPG_DIFF])));
   }

   public ContentValues getContent() {
      ContentValues cValues = new ContentValues();
      cValues.put(ToDBNames[DATE], getDate());
      cValues.put(ToDBNames[CAR], getCarName());
      cValues.put(ToDBNames[STATION], getStation());
      for(int i = 0; i < values.length; i++)
         cValues.put(ToDBNames[i + 2], getFloatValue(i + 2));
      // Log.d("TJS","Attempting to insert record with date = '"+getFormattedDate(getDate())+"'...");
      return cValues;
   }

   public static long parseDate(String dt) {
      long loc_dt = 0;
      try {
         Date loc_date = dateDecoder.parse(dt);
         loc_dt = loc_date.getTime();
      } catch (java.text.ParseException e) {
         Log.e("TJS", "ERROR : " + e.toString());
      }
      // Log.d("TJS","parseDate("+dt+") = "+getFormattedDate(loc_dt));
      if(!dt.equalsIgnoreCase(dateDecoder.format(loc_dt))) {
         Log.e("TJS", "Error: parseDate did not output correct results! parseDate(" + dt + ") != "
               + dateDecoder.format(loc_dt));
      }
      return loc_dt;
   }

   private void setFloatValue(int idx, float value) {
      if(idx > STATION)
         values[DbColToArrIdx(idx)] = value;
   }

   public float[] getValues() {
      return values;
   }

   public float getFloatValue(int idx) {
      if(idx <= STATION)
         return values[idx];
      return values[DbColToArrIdx(idx)];
   }

   public long getDate() {
      return date;
   }

   public String getStation() {
      if(gas_station.equals(""))
         return "unknown";
      return gas_station;
   }

   public String getCarName() {
      return carName;
   }

   public float getOdometerReading() {
      return getFloatValue(ODOMETER);
   }

   public float getTrip() {
      return getFloatValue(TRIP);
   }

   public float getGallons() {
      return getFloatValue(GALLONS);
   }

   public float getPrice() {
      return getFloatValue(PRICE);
   }

   public float getComputerMileage() {
      return getFloatValue(COMPUTER_MILEAGE);
   }

   public float getActualMileage() {
      return getFloatValue(ACTUAL_MILEAGE);
   }

   public float getTotalPrice() {
      return getFloatValue(TOTAL_PRICE);
   }

   public float getMileageDiff() {
      return getFloatValue(MPG_DIFF);
   }

   //These methods return the specific data point with the correct units
   public float getComputerMileage(Context context, SharedPreferences prefs) {
      return getEconomy(getComputerMileage(), prefs, context);
   }

   public float getActualMileage(Context context, SharedPreferences prefs) {
      return getEconomy(getActualMileage(), prefs, context);
   }

   public float getOdometerReading(Context context, SharedPreferences prefs) {
      return getDistance(getOdometerReading(), prefs, context);
   }

   public static String exportCSVTitle() {
      String str = "";
      for (String name : ToDBNames)
         str += name.concat(",");
      return str;
   }

   public static SimpleDateFormat getDateFormatter() {
      return dateFormatter;
   }

   public static String getFormattedDate(long milli) {
      return getDateFormatter().format(milli);
   }

   public static String getFormattedDate(int month, int day, int year) {
      GregorianCalendar cal = new GregorianCalendar(year, month, day);
      return getDateFormatter().format(cal.getTime());
   }

   public static String getSimpleDescription(Cursor c, int dateColumn, int mpgColumn, SharedPreferences prefs, Context context) {
      String date = MileageData.getDateFormatter().format(c.getLong(dateColumn));
      float mpg = MileageData.getEconomy(c.getFloat(mpgColumn), prefs, context);
      return String.format("%s (%2.1f %s)", date, mpg, MileageData.getEconomyUnits(prefs, context));

   }

   public String exportCSV() {
      String str = getFormattedDate(date) + "," + gas_station + ",";
      for(float val : values)
         str += Float.toString(val).concat(",");
      str += carName + ",";
      Log.d("TJS", "Exporting line '" + str + "'...");
      return str;
   }

   // Unit conversion helpers:
   public static final int      US                   = 0, METRIC = 1;
   public static final String[] ECONOMY_UNIT_LABELS  = { "mpg", "km/L" };
   public static final String[] DISTANCE_UNIT_LABELS = { "mi", "km" };
   public static final float    LITER_PER_GALLON     = (float) 3.78541178;
   public static final float    KM_PER_MILE          = (float) 1.609344;

   public static final boolean isMilesGallons(SharedPreferences prefs, Context context) {
      String units = prefs.getString(context.getString(R.string.unitSelection), "mpg");
      return units.equals("mpg");
   }

   public static final String getDistanceUnits(SharedPreferences prefs, Context context) {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(context);
      if(isMilesGallons(prefs, context))
         return DISTANCE_UNIT_LABELS[US];
      return DISTANCE_UNIT_LABELS[METRIC];
   }

   public static final String getEconomyUnits(SharedPreferences prefs, Context context) {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(context);
      if(isMilesGallons(prefs, context))
         return ECONOMY_UNIT_LABELS[US];
      return ECONOMY_UNIT_LABELS[METRIC];
   }

   public static final float getDistance(float miles, SharedPreferences prefs, Context context) {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(context);
      if(isMilesGallons(prefs, context))
         return miles;
      return (miles * KM_PER_MILE);
   }

   public static final float getVolume(float gallons, SharedPreferences prefs, Context context) {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(context);
      if(isMilesGallons(prefs, context))
         return gallons;
      return (gallons * LITER_PER_GALLON);
   }

   public static final float getEconomy(float mpg, SharedPreferences prefs, Context context) {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(context);
      if(isMilesGallons(prefs, context))
         return mpg;
      return (getDistance(mpg, prefs, context) / getVolume(1, prefs, context));
   }

}
