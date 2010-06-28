package com.switkows.mileage;

import java.util.Calendar;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;

public class AddRecordDialog extends Dialog {

	private String date_input;
	private static EditText odometer;
	private static EditText trip;
	private static EditText gallons;
	private static EditText price;
	private static EditText comp_mpg;
	private static EditText gas_station;
	private static EditText date;
	private static Button   submit;
	
	private final Context mContext;
	private static final int DATE_PICKER=45;

	public AddRecordDialog(Context context) {
		super(context);
		mContext = context;
		// TODO Auto-generated constructor stub
		setContentView(R.layout.insert_record);
		setTitle("Add Entry");
		initializePointers();
		Calendar c = Calendar.getInstance();
		int year = c.get(Calendar.YEAR),
			month = c.get(Calendar.MONTH),
			day   = c.get(Calendar.DAY_OF_MONTH);
        setDateDisplay(month, day, year);
        submit.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				MileageData data = new MileageData(mContext,date_input, getStation(), getInputValues());
//        		Log.d("TJS", "Data structure correctly created. adding to database");
				addUpdateDatapoint(data);
				dismiss();
        		//FIXME - clear input elements after 'submit'?
			}
        	
        });

        date.setOnFocusChangeListener(new OnFocusChangeListener() {
        	//FIXME - change to onClick, please..i tried, doesn't seem to work, but keep trying!
			public void onFocusChange(View v, boolean hasFocus) {
				if(hasFocus)
					showDialog(DATE_PICKER);
			}
		});
	}

    public void setDateDisplay(int month, int day, int year) {
    	date_input = String.format("%02d/%02d/%04d",month+1,day,year);
        date.setText(date_input);
    }
    
    private final DatePickerDialog.OnDateSetListener dateListener = new DatePickerDialog.OnDateSetListener() {
    	public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
    		setDateDisplay(monthOfYear, dayOfMonth, year);
    	};
    };

    private void initializePointers() {
    	odometer    = (EditText)findViewById(R.id.odo_reading);
    	trip        = (EditText)findViewById(R.id.trip_reading);
    	gallons     = (EditText)findViewById(R.id.gallons_reading);
    	price       = (EditText)findViewById(R.id.price_reading);
    	comp_mpg    = (EditText)findViewById(R.id.comp_mileage_reading);
    	gas_station = (EditText)findViewById(R.id.gas_station_reading);
    	date        = (EditText)findViewById(R.id.date_reading);
    	submit      = (Button)findViewById(R.id.submit);

    }
    /**
     * This method simply returns the values of all input dialog boxes
     * in an array of floating point numbers
     * @return float[] containing all dialog box contents
     */
    public float[] getInputValues() {
    	float odo_val = Float.parseFloat(odometer.getText().toString());
    	float trip_val = Float.parseFloat(trip.getText().toString());
    	float gal_val = Float.parseFloat(gallons.getText().toString());
    	float price_val = Float.parseFloat(price.getText().toString());
    	float c_mpg_val = Float.parseFloat(comp_mpg.getText().toString());
    	float[] ret = {odo_val,trip_val,gal_val,price_val,c_mpg_val};
    	return ret;
	}
    
    public String getStation() {
    	return gas_station.getText().toString();
    }

}
