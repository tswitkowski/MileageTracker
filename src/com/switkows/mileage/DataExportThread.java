package com.switkows.mileage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class DataExportThread extends Thread {
   private Context  mContext;
   private Handler  mHandler;
   private File     mLocation;
   private String   mFilename;
//   private boolean  mShowToast;
   
   public DataExportThread(Context context, File location, String name,boolean showMessage) {
      super();
      mContext      = context;
      mLocation     = location;
      mFilename     = name;
//      mShowToast    = showMessage; 
   }
   public DataExportThread(Handler handler, Context context, File location, String name) {
      this(context,location,name,true);
      mHandler = handler;
   }

   public void run() {
      // Log.d("TJS",Environment.getExternalStorageState());
      File csv_file = new File(mLocation, mFilename);
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
         Message msg;
         Bundle b = new Bundle();
         if(mHandler!=null) {
            msg = mHandler.obtainMessage();
            b.clear();
            b.putInt(ImportExportProgressHandler.MAX_KEY, numEntries);
            msg.setData(b);
            mHandler.sendMessage(msg);
         }
         int lineCount = 0;
         while(cursor.moveToNext()) {
            MileageData data = new MileageData(mContext.getApplicationContext(),cursor);
            writer.println(data.exportCSV());
            if(mHandler != null) {
               msg = mHandler.obtainMessage();
               b.clear();
               b.putInt(ImportExportProgressHandler.CURRENT_KEY, lineCount);
               b.putInt(ImportExportProgressHandler.MAX_KEY, numEntries);
               msg.setData(b);
               mHandler.sendMessage(msg);
            }
         }
         writer.close();
//         if(mShowToast)
//            Toast.makeText(mContext, "Data Successfully Saved to " + mFilename, Toast.LENGTH_LONG).show();
         if(mHandler != null) {
            String message = "Data Successfully Saved to " + mFilename;
            msg = mHandler.obtainMessage();
            b.clear();
            b.putString(ImportExportProgressHandler.FINISHED_KEY, message);
            msg.setData(b);
            mHandler.sendMessage(msg);
         }
      } catch (FileNotFoundException e) {
         Log.e("TJS", e.toString());
//         Toast.makeText(this, "Error! could not access/write " + filename, Toast.LENGTH_LONG).show();
      }
   }

}
