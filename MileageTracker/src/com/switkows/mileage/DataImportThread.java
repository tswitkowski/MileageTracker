package com.switkows.mileage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class DataImportThread extends AsyncTask<File, Integer, Boolean> {
   Context                              mContext;
   private boolean                      mShow;              //set to true to show the dialog box
   String                               mFile;              //short file name (used for toast/log messages only)
   private int                          mMax;               //holds the maximum value of the progress bar
   private boolean                      mShowIndeterminate; //set to TRUE to show progress bar as indeterminate

   private ProgressDialog               mDialog;
   private final EditRecordsListAdapter mAdapter;

   public DataImportThread(Context context, EditRecordsListAdapter adapter) {
      this(context, true, adapter);
   }

   public DataImportThread(boolean showDialog) {
      this(null, showDialog, null);
   }

   public DataImportThread(Context context, boolean showDialog, EditRecordsListAdapter adapter) {
      mContext  = context;
      mShow     = showDialog;
      mAdapter  = adapter;
      mMax      = 100;
   }

   @Override
   protected Boolean doInBackground(File... params) {
      if(params.length != 1)
         return false;
      File in_file = params[0];
      mFile = in_file.getName();
      try {
         BufferedReader reader = new BufferedReader(new FileReader(in_file));
         String line;
         clearDB();
         mMax = 0;
         mShowIndeterminate = false;
         Integer lineCount = 0;
         String currentCar = PreferenceManager.getDefaultSharedPreferences(mContext).getString(mContext.getString(R.string.carSelection), "Car45");
         ArrayList<ContentValues> newEntries = new ArrayList<ContentValues>();
         HashSet<ContentValues> profiles = new HashSet<ContentValues>();
         while(reader.ready()) {
            reader.readLine();
            mMax++;
         }
         reader.close();
         reader = new BufferedReader(new FileReader(in_file));
         while(reader.ready()) {
            line = reader.readLine();
            String[] fields = line.split(",");
            if(fields[0].equals(MileageData.ToDBNames[0])) {
               // Log.d("TJS","Found header in CSV file");
               continue;
            }
            // are we an old format, without the 'cars' column?! if so, add it!
            if(fields.length == 10) {
               String[] newFields = new String[11];
               for(int i = 0; i < fields.length; i++)
                  newFields[i] = fields[i];
               newFields[10] = currentCar;
               fields = newFields;
               Log.v("TJS", "Importing entry from CSV file into current car: " + newFields[10]);
            } else if(fields.length == 11) {
               // Log.v("TJS","Importing entry from CSV file into car: "+fields[10]);
            } else {
               Log.d("TJS", "Skipping import line: only " + fields.length + "elements in CSV line!!");
            }
            // Log.d("TJS","Read line '"+line+"', date = '"+fields[0]+"'...");
            MileageData record = new MileageData(mContext, fields);
            newEntries.add(record.getContent());
            profiles.add(MileageProvider.createProfileContent(record.getCarName()));
            lineCount++;
            publishProgress(lineCount);
         }
         if(newEntries.size() > 0) {
            ContentValues[] additions = new ContentValues[newEntries.size()];
            additions = newEntries.toArray(additions);
            mContext.getContentResolver().bulkInsert(MileageProvider.ALL_CONTENT_URI, additions);
            //push profile names
            additions = new ContentValues[profiles.size()];
            additions = profiles.toArray(additions);
            mContext.getContentResolver().bulkInsert(MileageProvider.CAR_PROFILE_URI, additions);
         }
         reader.close();
      } catch (FileNotFoundException e) {
         Log.e("TJS", e.toString());
      } catch (IOException e) {
         Log.e("TJS", e.toString());
      }
      return true;
   }

   @Override
   protected void onProgressUpdate(Integer... values) {
      super.onProgressUpdate(values);
      if(mShow) {
         mDialog.setProgress(values[0].intValue());
         if(values[0].intValue() >= mMax - 1)
            mShowIndeterminate = true;
         updateProgressConfig();
      }
   }

   @Override
   protected void onPreExecute() {
      super.onPreExecute();
      createDialog();
   }

   @Override
   protected void onPostExecute(Boolean result) {
      super.onPostExecute(result);
      if(mShow) {
         String importMessage;
         if(result)
            importMessage = "Data Successfully imported from\n" + mFile;
         else
            importMessage = "Error! could not access/read " + mFile;

         Log.d("TJS", importMessage);
         Toast.makeText(mContext, importMessage, Toast.LENGTH_LONG).show();
         if(mAdapter != null)
            mAdapter.getCursor().requery();
         if(mShow && mDialog != null)
            mDialog.dismiss();
      } else {
         Log.d("TJS", "Data Successfully imported..");
      }
   }

   public void clearDB() {
      mContext.getContentResolver().delete(MileageProvider.ALL_CONTENT_URI, null, null);
      mContext.getContentResolver().delete(MileageProvider.CAR_PROFILE_URI, null, null);
   }

   private void createDialog() {
      if(mShow) {
         mDialog = new ProgressDialog(mContext);
         mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
         mDialog.setMessage("Importing Data...");
         mDialog.setCancelable(false);
         updateProgressConfig();
         mDialog.show();
      }
   }

   /**
    * Updates the dialog configuration (i.e. can switch back and forth between indeterminate/determinate)
    */
   @TargetApi(11)
   private void updateProgressConfig() {
      if(mShowIndeterminate) {
         mDialog.setIndeterminate(true);
         if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mDialog.setProgressNumberFormat(null);
            mDialog.setProgressPercentFormat(null);
         }
      } else {
         mDialog.setMax(mMax);
      }
   }

   /**
    * Call once your activity is back in the foreground, and the thread can be 'resumed'
    */
   public void restart() {
      createDialog();
   }

   /**
    * Call when you need to 'suspend' the thread, due to activity going to background, orientation change, etc
    */
   public void pause() {
      if(mDialog != null)
         mDialog.dismiss();
      mDialog = null;
   }
}