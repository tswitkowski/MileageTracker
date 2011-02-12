package com.switkows.mileage;

import java.util.Calendar;
import java.util.Date;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.DatePicker;
import android.widget.TextView;

public class EditRecord extends Activity {
   private static final String  TAG         = "TJS - EditRecord";
   private static final int     DATE_PICKER = 1;
   private Uri                  mUri;
   private Cursor               mCursor;
   private SharedPreferences    prefs;
   private boolean              isNewRecord;

   private TextView             dateBox;
   private AutoCompleteTextView stationBox;
   private TextView             odoBox;
   private TextView             tripBox;
   private TextView             gallonsBox;
   private TextView             priceBox;
   private TextView             compMpgBox;
   private TextView             actMpgBox;
   private TextView             totPriceBox;
   private TextView             mpgDiffBox;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      isNewRecord = false;
      super.onCreate(savedInstanceState);

      final Intent intent = getIntent();

      // Have the system blur any windows behind this one.
//      getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
//              WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
      // Do some setup based on the action being performed.

      final String action = intent.getAction();
      if(Intent.ACTION_EDIT.equals(action)) {
         // Requested to edit: set that state, and the data being edited.
         // mState = STATE_EDIT;
         mUri = intent.getData();
         mCursor = managedQuery(mUri, null, null, null, null);
      } else if(Intent.ACTION_INSERT.equals(action)) {
         isNewRecord = true;
         // Requested to insert: set that state, and create a new entry
         // in the container.
         // mState = STATE_INSERT;
         // mUri = getContentResolver().insert(intent.getData(), null);
         mUri = intent.getData();

         // If we were unable to create a new note, then just finish
         // this activity. A RESULT_CANCELED will be sent back to the
         // original activity if they requested a result.
         if(mUri == null) {
            Log.e(TAG, "Failed to insert new note into " + getIntent().getData());
            finish();
            return;
         }

         // The new entry was created, so assume all will end well and
         // set the result to be returned.
         // setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

      } else {
         // Whoops, unknown action! Bail.
         Log.e(TAG, "Unknown action, exiting");
         finish();
         return;
      }

      setContentView(R.layout.edit_record);

      Button submit = (Button) findViewById(R.id.submit);
      if(isNewRecord)
         submit.setText("Add Entry");
      submit.setOnClickListener(new Button.OnClickListener() {
         public void onClick(View v) {
            updateDbRow();
            if(getIntent().hasExtra("starter"))
               startActivity(new Intent(getApplicationContext(),MileageTracker.class));
            finish();
         }
      });
      getTextFieldStruct(MileageData.DATE).setOnClickListener(new View.OnClickListener() {
         public void onClick(View v) {
            showDialog(DATE_PICKER);
         }
      });

      // listener for the input boxes which may cause a change to the 'generated'
      // fields in the database.
      OnFocusChangeListener listener = new OnFocusChangeListener() {
         public void onFocusChange(View v, boolean hasFocus) {
            MileageData data = createDataStructure();
            fillFromDataStructure(data);
         }
      };
      getTextFieldStruct(MileageData.TRIP).setOnFocusChangeListener(listener);
      getTextFieldStruct(MileageData.PRICE).setOnFocusChangeListener(listener);
      getTextFieldStruct(MileageData.GALLONS).setOnFocusChangeListener(listener);
      getTextFieldStruct(MileageData.COMPUTER_MILEAGE).setOnFocusChangeListener(listener);

      getTextFieldStruct(MileageData.DATE).setInputType(InputType.TYPE_NULL);
      // getTextFieldStruct(MileageData.ACTUAL_MILEAGE).setEnabled(false);
      // getTextFieldStruct(MileageData.MPG_DIFF).setEnabled(false);
      // getTextFieldStruct(MileageData.TOTAL_PRICE).setEnabled(false);

      // Try and grab all of the previously entered gas stations, to make data entry easier
      Uri uri = MileageProvider.ALL_CONTENT_URI;
      Cursor cursor = managedQuery(uri, new String[] { "_id", MileageData.ToDBNames[MileageData.STATION] }, null, null,
            null);
      AutoCompleteTextView text = (AutoCompleteTextView) getTextFieldStruct(MileageData.STATION);
      // AutoCompleteTextView text =
      // (AutoCompleteTextView)findViewById(R.id.gas_station_reading);
      // text.setAdapter(new AutocompleteListAdapter(this, cursor));
      text.setAdapter(new myListAdapter(this, cursor));
      // for(int i=0 ; i<cursor.getCount() ; i++) {
      // cursor.moveToPosition(i);
      // text.
      // dataSet[i] = new MileageData(mContext,mCursor);
      //
      // }
   }

   @Override
   protected void onResume() {
      super.onResume();

      String indicator = "("+getPrefs().getString(getString(R.string.carSelection), "Car45")+"):";
      if(isNewRecord)
         setTitle(getText(R.string.new_record_title)+indicator);
      else
         setTitle(getText(R.string.edit_record_title)+indicator);
      // If we didn't have any trouble retrieving the data, it is now
      // time to get at the stuff.
      if(mCursor != null) {
         // Make sure we are at the one and only row in the cursor.
         mCursor.moveToFirst();
         setTextFields();
      } else {
         Calendar c = Calendar.getInstance();
         int year = c.get(Calendar.YEAR), month = c.get(Calendar.MONTH), day = c.get(Calendar.DAY_OF_MONTH);
         getTextFieldStruct(MileageData.DATE).setText(MileageData.getFormattedDate(month, day, year));
      }
   }

   protected void setTextFields() {
      for(int i = 0; i <= MileageData.MPG_DIFF; i++)
         getTextFieldStruct(i).setText(colToString(i));
   }

   protected void fillFromDataStructure(MileageData data) {
      ContentValues values = data.getContent();
      for(int i = MileageData.ACTUAL_MILEAGE; i <= MileageData.MPG_DIFF; i++)
         getTextFieldStruct(i).setText(values.getAsString(MileageData.ToDBNames[i]));
   }

   protected TextView getTextFieldStruct(int dbColumn) {
      switch(dbColumn) {
         case MileageData.DATE:
            return dateBox == null ? dateBox = (TextView) findViewById(R.id.date_reading) : dateBox;
         case MileageData.STATION:
            return stationBox == null ? stationBox = (AutoCompleteTextView) findViewById(R.id.gas_station_reading)
                  : stationBox;
         case MileageData.ODOMETER:
            return odoBox == null ? odoBox = (TextView) findViewById(R.id.odo_reading) : odoBox;
         case MileageData.TRIP:
            return tripBox == null ? tripBox = (TextView) findViewById(R.id.trip_reading) : tripBox;
         case MileageData.GALLONS:
            return gallonsBox == null ? gallonsBox = (TextView) findViewById(R.id.gallons_reading) : gallonsBox;
         case MileageData.PRICE:
            return priceBox == null ? priceBox = (TextView) findViewById(R.id.price_reading) : priceBox;
         case MileageData.COMPUTER_MILEAGE:
            return compMpgBox == null ? compMpgBox = (TextView) findViewById(R.id.comp_mileage_reading) : compMpgBox;
         case MileageData.ACTUAL_MILEAGE:
            return actMpgBox == null ? actMpgBox = (TextView) findViewById(R.id.actual_mileage_reading) : actMpgBox;
         case MileageData.TOTAL_PRICE:
            return totPriceBox == null ? totPriceBox = (TextView) findViewById(R.id.total_price_reading) : totPriceBox;
         case MileageData.MPG_DIFF:
            return mpgDiffBox == null ? mpgDiffBox = (TextView) findViewById(R.id.mpg_diff_reading) : mpgDiffBox;
      }
      return null;
   }

   public SharedPreferences getPrefs() {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(this);
      return prefs;
   }

   protected void updateDbRow() {
      MileageData data = createDataStructure();
      ContentValues values = data.getContent();
      if(Intent.ACTION_INSERT.equals(getIntent().getAction()))
         getContentResolver().insert(mUri, values);
      else
         getContentResolver().update(mUri, values, "_id = " + mCursor.getInt(mCursor.getColumnIndex("_id")), null);
   }

   protected MileageData createDataStructure() {
      float odo, trip, gal, price, diff;
      try {
         odo = Float.parseFloat(getTextFieldStruct(MileageData.ODOMETER).getText().toString());
      } catch (NumberFormatException e) {
         odo = 0;
      }
      try {
         trip = Float.parseFloat(getTextFieldStruct(MileageData.TRIP).getText().toString());
      } catch (NumberFormatException e) {
         trip = 0;
      }
      try {
         gal = Float.parseFloat(getTextFieldStruct(MileageData.GALLONS).getText().toString());
      } catch (NumberFormatException e) {
         gal = 0;
      }
      try {
         price = Float.parseFloat(getTextFieldStruct(MileageData.PRICE).getText().toString());
      } catch (NumberFormatException e) {
         price = 0;
      }
      try {
         diff = Float.parseFloat(getTextFieldStruct(MileageData.COMPUTER_MILEAGE).getText().toString());
      } catch (NumberFormatException e) {
         diff = 0;
      }
      // Convert from Metric units (km/L & km) to US units (mpg & mi)
      if(!MileageData.isMilesGallons(getPrefs(), this)) {
         odo = odo / MileageData.getDistance(1, getPrefs(), this);
         trip = trip / MileageData.getDistance(1, getPrefs(), this);
         gal = gal / MileageData.getVolume(1, getPrefs(), this);
      }
      return new MileageData(getApplicationContext(), getPrefs().getString(getString(R.string.carSelection), "Car45"),
            getTextFieldStruct(MileageData.DATE).getText().toString(), getTextFieldStruct(MileageData.STATION)
                  .getText().toString(), odo, trip, gal, price, diff);
   }

   protected String dateToString(long date) {
      return MileageData.getDateFormatter().format(date);
   }

   protected String colToString(int column) {
      if(column == MileageData.DATE)
         return MileageData.getFormattedDate(mCursor.getLong(mCursor.getColumnIndex(MileageData.ToDBNames[column])));
      else if(column == MileageData.STATION)
         return mCursor.getString(mCursor.getColumnIndex(MileageData.ToDBNames[column]));
      float value = mCursor.getFloat(mCursor.getColumnIndex(MileageData.ToDBNames[column]));
      switch(column) {
         case MileageData.ACTUAL_MILEAGE:
         case MileageData.COMPUTER_MILEAGE:
            value = MileageData.getEconomy(value, getPrefs(), this);
            break;
         case MileageData.GALLONS:
            value = MileageData.getVolume(value, getPrefs(), this);
            break;
         case MileageData.ODOMETER:
         case MileageData.TRIP:
            value = MileageData.getDistance(value, getPrefs(), this);
            break;
      }
      return "" + value;
   }

   @Override
   protected Dialog onCreateDialog(int id) {
      switch(id) {
         case DATE_PICKER:
            String date = getTextFieldStruct(MileageData.DATE).getText().toString();
            Date dt = new Date(MileageData.parseDate(date));
            return new DatePickerDialog(this, dateListener, dt.getYear() + 1900, dt.getMonth(), dt.getDate());
         default:
            return null;
      }
   }

   private final DatePickerDialog.OnDateSetListener dateListener = new DatePickerDialog.OnDateSetListener() {
                                                                    public void onDateSet(DatePicker view, int year,
                                                                          int monthOfYear, int dayOfMonth) {
                                                                       setDateDisplay(monthOfYear, dayOfMonth, year);
                                                                    }
                                                                 };

   private void setDateDisplay(int month, int day, int year) {
      getTextFieldStruct(MileageData.DATE).setText(MileageData.getFormattedDate(month, day, year));
   }

   // FIXME - don't delete! this should be investigated as an enhancement to the
   // below implementation!!
   // public static class AutocompleteListAdapter extends CursorAdapter
   // implements Filterable {
   // public AutocompleteListAdapter(Context context, Cursor c) {
   // super(context, c);
   // // mContent = context.getContentResolver();
   // }
   //
   // @Override
   // public View newView(Context context, Cursor cursor, ViewGroup parent) {
   // final LayoutInflater inflater = LayoutInflater.from(context);
   // final TextView view = (TextView) inflater.inflate(
   // android.R.layout.simple_dropdown_item_1line, parent, false);
   // view.setText(cursor.getString(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.STATION])));
   // return view;
   // }
   //
   // @Override
   // public void bindView(View view, Context context, Cursor cursor) {
   // ((TextView)
   // view).setText(cursor.getString(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.STATION])));
   // }
   //
   // @Override
   // public String convertToString(Cursor cursor) {
   // return
   // cursor.getString(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.STATION]));
   // }
   //
   // // @Override
   // // public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
   // // if (getFilterQueryProvider() != null) {
   // // return getFilterQueryProvider().runQuery(constraint);
   // // }
   // //
   // // StringBuilder buffer = null;
   // // String[] args = null;
   // // if (constraint != null) {
   // // buffer = new StringBuilder();
   // // buffer.append("UPPER(");
   // // buffer.append(Contacts.ContactMethods.NAME);
   // // buffer.append(") GLOB ?");
   // // args = new String[] { constraint.toString().toUpperCase() + "*" };
   // // }
   // //
   // // return mContent.query(Contacts.People.CONTENT_URI, PEOPLE_PROJECTION,
   // // buffer == null ? null : buffer.toString(), args,
   // // Contacts.People.DEFAULT_SORT_ORDER);
   // // }
   // //
   // // private ContentResolver mContent;
   // }
   private class myListAdapter extends CursorAdapter {
      private final String   ColumnName = MileageData.ToDBNames[MileageData.STATION];
      private final String[] Columns    = { "_id", ColumnName };

      public myListAdapter(Context context, Cursor c) {
         super(context, c);
      }

      @Override
      public void bindView(View view, Context context, Cursor cursor) {
         int columnIndex = cursor.getColumnIndexOrThrow(ColumnName);
         TextView tview = (TextView) view;
         tview.setText(cursor.getString(columnIndex));
         // FIXME - do i need to adjust sizes here, or just in newView?
         // tview.setTextSize((float) 10);
         // tview.getLayoutParams().width = LayoutParams.WRAP_CONTENT;
         // tview.setHeight(10); //FIXME - doesn't appear to have any affect
      }

      @Override
      public String convertToString(Cursor cursor) {
         int columnIndex = cursor.getColumnIndexOrThrow(ColumnName);
         return cursor.getString(columnIndex);
      }

      @Override
      public View newView(Context context, Cursor cursor, ViewGroup parent) {
         final LayoutInflater inflater = LayoutInflater.from(context);
         final TextView view = (TextView) inflater.inflate(android.R.layout.simple_dropdown_item_1line, parent, false);
         int columnIndex = cursor.getColumnIndexOrThrow(ColumnName);
         TextView tview = view;
         tview.setText(cursor.getString(columnIndex));
         tview.setTextSize(10);
         tview.getLayoutParams().width = LayoutParams.WRAP_CONTENT;
         tview.setHeight(10); // FIXME - doesn't appear to have any affect
         return view;
      }

      @Override
      public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
         if(constraint == null)
            return managedQuery(MileageProvider.ALL_CONTENT_URI, Columns, null, null, null);
         // if (getFilterQueryProvider() != null)
         // return getFilterQueryProvider().runQuery(constraint);
         // return managedQuery(MileageProvider.CONTENT_URI,new String[]
         // {ColumnName},ColumnName+" like '%?%'",new String[]
         // {constraint.toString()},null);
         Cursor cursor = managedQuery(MileageProvider.ALL_CONTENT_URI, Columns, ColumnName + " like '%"
               + constraint.toString() + "%'", null, ColumnName + " DESC");
         return cursor;
      }
   }

}
