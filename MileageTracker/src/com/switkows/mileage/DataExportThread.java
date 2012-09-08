package com.switkows.mileage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

public class DataExportThread extends AsyncTask<File, Integer, Boolean> {
   private Context            mContext;
   private boolean            mShow;                //set to true to show the dialog box
   private String             mFilename;
   private int                mMax;                 //holds the maximum value of the progress bar
   private boolean            mShowIndeterminate;   //set to TRUE to show progress bar as indeterminate

   private ProgressDialog     mDialog;

   public DataExportThread(Context context, boolean showMessage) {
      super();
      mContext  = context;
      mMax      = 100;
      mShow     = showMessage;
   }
   public DataExportThread(Context context) {
      this(context,true);
   }

   @Override
   protected Boolean doInBackground(File... params) {
      if(params.length != 1)
         return false;
      // Log.d("TJS",Environment.getExternalStorageState());
      File csv_file = params[0];
      mFilename = csv_file.getName();
      // Log.d("TJS","File exists: " + csv_file.exists());
      // Log.d("TJS","is file: " + csv_file.isFile());
      // Log.d("TJS","is writeable: " + csv_file.canWrite());
      try {
         // FileOutputStream stream = new FileOutputStream(csv_file);
         // PrintWriter writer = new PrintWriter(stream);
         PrintWriter writer = new PrintWriter(csv_file);
         writer.println(MileageData.exportCSVTitle());

         Cursor cursor = mContext.getContentResolver().query(MileageProvider.ALL_CONTENT_URI, null, null, null, null);
         int numEntries = cursor.getCount();
         mMax = numEntries;
         Integer lineCount = 0;

         while(cursor.moveToNext()) {
            MileageData data = new MileageData(mContext.getApplicationContext(),cursor);
            writer.println(data.exportCSV());
            lineCount++;
            publishProgress(lineCount);
         }
         writer.close();

         return true;
      } catch (FileNotFoundException e) {
         Log.e("TJS", e.toString());
      }
      return false;
   }
   @Override
   protected void onProgressUpdate(Integer... values) {
      super.onProgressUpdate(values);
      if(mShow) {
         mDialog.setProgress(values[0].intValue());
         if(values[0].intValue()>=mMax-1)
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
            importMessage = "Data Successfully Saved to\n" + mFilename + "(new)";
         else
            importMessage = "Error! could not access/read " + mFilename;

         Log.d("TJS", importMessage);
         Toast.makeText(mContext, importMessage, Toast.LENGTH_LONG).show();
         if(mShow && mDialog!=null)
            mDialog.dismiss();
      } else {
         Log.d("TJS", "Data Successfully exported..");
      }
   }
   public void clearDB() {
      mContext.getContentResolver().delete(MileageProvider.ALL_CONTENT_URI, null, null);
   }

   private void createDialog() {
      if(mShow) {
         mDialog = new ProgressDialog(mContext);
         mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
         mDialog.setMessage("Exporting Data...");
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
      if(mDialog!=null)
         mDialog.dismiss();
      mDialog = null;
   }
}
