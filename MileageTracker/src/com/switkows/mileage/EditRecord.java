package com.switkows.mileage;

import java.util.Calendar;
import java.util.GregorianCalendar;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;

public class EditRecord extends FragmentActivity {

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      String action = getIntent().getAction();
      //FIXME - added an assumption that the ONLY way we will start this activity in landscape is via a 'new' record
      boolean isNew  = MileageTracker.ACTION_INSERT.equals(action);
      if(!isNew && getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE) {
         //if we're in landscape, we'll use a two-pane interface, so dismiss this activity!
         int id = -2;
         if(!isNew)
            id = Integer.parseInt(getIntent().getData().getPathSegments().get(1));
         setResult(id+1);
         finish();
         return;
      } else if(savedInstanceState==null){
         long id = -1;
         if(!isNew)
            id = Long.parseLong(getIntent().getData().getPathSegments().get(1));
         EditRecordFragment fragment = EditRecordFragment.newInstance(id, isNew);
         getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();

         //FIXME - seems like a hack to get the dialog to stretch to the proper width:
//         LayoutParams params = getWindow().getAttributes();
//         params.width = LayoutParams.FILL_PARENT;
//         getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
      }
      setResult(RESULT_CANCELED);   //FIXME - needed?
   }
 
   public static class EditRecordFragment extends Fragment {
      private static final String  TAG         = "TJS - EditRecord";
      private Uri                  mUri;
      private Cursor               mCursor;
      private SharedPreferences    prefs;
      private boolean              isNewRecord;
      private myListAdapter        stationAutocompleteAdapter;
      private LoaderCallbacks      mLoaderCallbacks = new LoaderCallbacks();     

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
      private UpdateCallback       mCallback;
      
      private boolean              viewAttached;//boolean to determine whether we have a View attached (or whether the fragment is in the background)

      public static EditRecordFragment newInstance(long id, boolean isNew) {
         EditRecordFragment result = new EditRecordFragment();
         Bundle args = new Bundle();
         args.putLong("id", id);
         args.putBoolean("new", isNew);
         result.setArguments(args);
         return result;
      }

      //this routine is called when an item is added/updated
      //this allows us to handle the event differently depending
      //on which activity invoked this fragment
      public interface UpdateCallback {
         //return true to indicate the event was handled
         public boolean messageUpdated(long id);
      }

      @Override
      public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
         isNewRecord = false;
         if(container==null)    //if we are not attached to a View
            return null;
         View result = null;

         // Have the system blur any windows behind this one.
   //      getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
   //              WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
         // Do some setup based on the action being performed.

         final boolean newRecord   = getArguments().getBoolean("new");
         final long    recordId    = getArguments().getLong("id");
         if(!newRecord) {
            // Requested to edit: set that state, and the data being edited.
            // mState = STATE_EDIT;
            result =  inflater.inflate(R.layout.edit_record, null);
            //FIXME - seems like a hack to get the dialog to stretch to the proper width:
//            LayoutParams params = getActivity().getWindow().getAttributes();
//            params.width = LayoutParams.FILL_PARENT;
//            getActivity().getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);

            mUri = ContentUris.withAppendedId(MileageProvider.ALL_CONTENT_URI, recordId);
            getLoaderManager().initLoader(LoaderCallbacks.ID_DATA_LOADER, null, mLoaderCallbacks);
         } else {
            isNewRecord = true;
            if(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE)
               result =  inflater.inflate(R.layout.edit_record_landscape_floating, null);
            else
               result =  inflater.inflate(R.layout.edit_record, null);
            //FIXME - seems like a hack to get the dialog to stretch to the proper width:
//            LayoutParams params = getActivity().getWindow().getAttributes();
//            params.width = LayoutParams.FILL_PARENT;
//            getActivity().getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
            // Requested to insert: set that state, and create a new entry
            // in the container.
            // mState = STATE_INSERT;
            // mUri = getContentResolver().insert(intent.getData(), null);
            String car = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(this.getString(R.string.carSelection), "Car45");
            mUri = Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI, car);

            // If we were unable to create a new record, then just finish
            // this activity. A RESULT_CANCELED will be sent back to the
            // original activity if they requested a result.
            if(mUri == null) {
               Log.e(TAG, "Failed to insert new record into " + car);
               handleCompletion(1);
               return null;
            }
         }

         return result;
      }

      @Override
      public void onAttach(Activity activity) {
         super.onAttach(activity);
         try {
            mCallback = (UpdateCallback)activity;
         } catch (ClassCastException e) {}
      }

      @Override
      public void onActivityCreated(Bundle savedInstanceState) {
         super.onActivityCreated(savedInstanceState);
         Button submit = (Button) getActivity().findViewById(R.id.submit);
         //this is in case we don't have a view. for some reason, i'm seeing this called even if onCreateView returns null
         viewAttached = submit!=null;
         if(submit==null)
            return;
         if(isNewRecord)
            submit.setText("Add Entry");
         submit.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
               updateDbRow();
               handleCompletion(1);
            }
         });
         getTextFieldStruct(MileageData.DATE).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
               createDialog().show();
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

      }

      @Override
      public void onResume() {
         super.onResume();

         if(!viewAttached)
            return;
         String indicator = "("+getPrefs().getString(getString(R.string.carSelection), "Car45")+"):";
         if(isNewRecord)
            getActivity().setTitle(getText(R.string.new_record_title)+indicator);
         else
            getActivity().setTitle(getText(R.string.edit_record_title)+indicator);
         //Note : assumes mCursor is not NULL if we are editing a record (so this call doesn't distrub the data..
         if(isNewRecord) {
            Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR), month = c.get(Calendar.MONTH), day = c.get(Calendar.DAY_OF_MONTH);
            getTextFieldStruct(MileageData.DATE).setText(MileageData.getFormattedDate(month, day, year));
         }
         // Try and grab all of the previously entered gas stations, to make data entry easier
         AutoCompleteTextView text = (AutoCompleteTextView) getTextFieldStruct(MileageData.STATION);

         //start us off with a null cursor (Loader will populate later)
         stationAutocompleteAdapter = new myListAdapter(getActivity(), null);
         getLoaderManager().initLoader(LoaderCallbacks.ID_STATION_LOADER, getArguments(), mLoaderCallbacks);
         text.setAdapter(stationAutocompleteAdapter);
      }

      protected void handleCompletion(long id) {
         if(getActivity().getIntent().hasExtra("starter"))
            startActivity(new Intent(getActivity(),MileageTracker.class));
         boolean result = false;
         if(mCallback!=null) {
            result = mCallback.messageUpdated(id);
         }
         if(!result)
            getActivity().finish();
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
               return dateBox == null ? dateBox = (TextView) getActivity().findViewById(R.id.date_reading) : dateBox;
            case MileageData.STATION:
               return stationBox == null ? stationBox = (AutoCompleteTextView) getActivity().findViewById(R.id.gas_station_reading)
                     : stationBox;
            case MileageData.ODOMETER:
               return odoBox == null ? odoBox = (TextView) getActivity().findViewById(R.id.odo_reading) : odoBox;
            case MileageData.TRIP:
               return tripBox == null ? tripBox = (TextView) getActivity().findViewById(R.id.trip_reading) : tripBox;
            case MileageData.GALLONS:
               return gallonsBox == null ? gallonsBox = (TextView) getActivity().findViewById(R.id.gallons_reading) : gallonsBox;
            case MileageData.PRICE:
               return priceBox == null ? priceBox = (TextView) getActivity().findViewById(R.id.price_reading) : priceBox;
            case MileageData.COMPUTER_MILEAGE:
               return compMpgBox == null ? compMpgBox = (TextView) getActivity().findViewById(R.id.comp_mileage_reading) : compMpgBox;
            case MileageData.ACTUAL_MILEAGE:
               return actMpgBox == null ? actMpgBox = (TextView) getActivity().findViewById(R.id.actual_mileage_reading) : actMpgBox;
            case MileageData.TOTAL_PRICE:
               return totPriceBox == null ? totPriceBox = (TextView) getActivity().findViewById(R.id.total_price_reading) : totPriceBox;
            case MileageData.MPG_DIFF:
               return mpgDiffBox == null ? mpgDiffBox = (TextView) getActivity().findViewById(R.id.mpg_diff_reading) : mpgDiffBox;
         }
         return null;
      }

      public SharedPreferences getPrefs() {
         if(prefs == null)
            prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
         return prefs;
      }

      protected void updateDbRow() {
         MileageData data = createDataStructure();
         ContentValues values = data.getContent();
         if(isNewRecord)
            getActivity().getContentResolver().insert(mUri, values);
         else
            getActivity().getContentResolver().update(mUri, values, "_id = " + mCursor.getInt(mCursor.getColumnIndex("_id")), null);
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
         if(!MileageData.isMilesGallons(getPrefs(), getActivity())) {
            odo = odo / MileageData.getDistance(1, getPrefs(), getActivity());
            trip = trip / MileageData.getDistance(1, getPrefs(), getActivity());
            gal = gal / MileageData.getVolume(1, getPrefs(), getActivity());
         }
         return new MileageData(getActivity(), getPrefs().getString(getString(R.string.carSelection), "Car45"),
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
               value = MileageData.getEconomy(value, getPrefs(), getActivity());
               break;
            case MileageData.GALLONS:
               value = MileageData.getVolume(value, getPrefs(), getActivity());
               break;
            case MileageData.ODOMETER:
            case MileageData.TRIP:
               value = MileageData.getDistance(value, getPrefs(), getActivity());
               break;
         }
         return "" + value;
      }

      protected Dialog createDialog() {
         String date = getTextFieldStruct(MileageData.DATE).getText().toString();
         GregorianCalendar cal1 = new GregorianCalendar();
         cal1.setTimeInMillis(MileageData.parseDate(date));
         return new DatePickerDialog(getActivity(), dateListener, cal1.get(Calendar.YEAR), cal1.get(Calendar.MONTH), cal1.get(Calendar.DAY_OF_MONTH));
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

      private static final String STATION_NAME = MileageData.ToDBNames[MileageData.STATION];
      private class LoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
         protected static final int ID_STATION_LOADER = 0;
         protected static final int ID_DATA_LOADER    = 1;
//         protected final String     STATION_NAME      = MileageData.ToDBNames[MileageData.STATION];
         protected final String[]   STATION_PROJ      = { "_id", STATION_NAME };
         protected final String     STATION_SORT      = STATION_NAME + " DESC";

         public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            if(id == ID_STATION_LOADER) {
               Uri uri = MileageProvider.ALL_CONTENT_URI;
               String hint = args.getString("hint");
               String filter = hint == null ? null : STATION_NAME + " like '%" + hint + "%'";
               return new CursorLoader(getActivity(),uri, STATION_PROJ, filter, null, STATION_SORT);
            } else if(id == ID_DATA_LOADER) {
               CursorLoader loader = new CursorLoader(getActivity(),mUri, null, null, null, null);
               return loader;
            } else {
               return null;
            }
         }

         public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if(loader.getId() == ID_STATION_LOADER) {
               stationAutocompleteAdapter.swapCursor(data);
            } else if(loader.getId() == ID_DATA_LOADER) {
               mCursor = data;
               mCursor.moveToFirst();
               setTextFields();
            }
         }

         public void onLoaderReset(Loader<Cursor> loader) {
            if(loader.getId() == ID_STATION_LOADER) {
               stationAutocompleteAdapter.swapCursor(null);
            } else if(loader.getId() == ID_DATA_LOADER) {
               mCursor = null;
            }
         }
         
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
      private class myListAdapter extends SimpleCursorAdapter {
//         private final String   ColumnName = MileageData.ToDBNames[MileageData.STATION];
   
         public myListAdapter(Context context, Cursor c) {
            super(context, android.R.layout.simple_dropdown_item_1line, c, new String[] {STATION_NAME}, new int[] {android.R.id.text1},NO_SELECTION);
         }

         @Override
         public String convertToString(Cursor cursor) {
            int columnIndex = cursor.getColumnIndexOrThrow(STATION_NAME);
            return cursor.getString(columnIndex);
         }

         @Override
         public View newView(Context context, Cursor cursor, ViewGroup parent) {
            if(cursor!=null) {
               TextView view = (TextView)super.newView(context, cursor, parent);
               view.setTextSize(10);
               view.getLayoutParams().width = LayoutParams.WRAP_CONTENT;
               view.setHeight(10); // FIXME - doesn't appear to have any affect
               return view;
            }
            return null;
         }

         @Override
         public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            Bundle args = new Bundle();
            if(constraint != null)
               args.putString("hint", constraint.toString());
            getLoaderManager().restartLoader(LoaderCallbacks.ID_STATION_LOADER, args, mLoaderCallbacks);
            return null;
         }
      }
   }
}