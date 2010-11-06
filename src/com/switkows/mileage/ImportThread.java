package com.switkows.mileage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class ImportThread extends Thread {
   public static final String IMPORT_PROGRESS_STR = "linesRead", IMPORT_MAX_STR = "numLines",
         IMPORT_FINISHED_STR = "toast";

   Handler                    mHandler;
   Context                    mContext;
   String                     mFile;

   ImportThread(Handler h, Context c, String f) {
      mHandler = h;
      mContext = c;
      mFile = f;
      // Log.d("TJS","Created ImportThread...");
   }

   public void setHandler(Handler h) {
      mHandler = h;
   }

   @Override
   public void run() {
      // Log.d("TJS","Started ImportThread");
      File in_file = new File(Environment.getExternalStorageDirectory(), mFile);
      String importMessage = "Error! could not access/read " + mFile;
      try {
         BufferedReader reader = new BufferedReader(new FileReader(in_file));
         String line;
         clearDB(false);
         int totalLineCount = 0;
         while(reader.ready()) {
            reader.readLine();
            totalLineCount++;
         }
         Message msg;
         Bundle b;
         if(mHandler != null) {
            msg = mHandler.obtainMessage();
            b = new Bundle();
            b.putInt(IMPORT_MAX_STR, totalLineCount);
            msg.setData(b);
            mHandler.sendMessage(msg);
         }

         reader = new BufferedReader(new FileReader(in_file));
         int lineCount = 0;
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
               newFields[10] = getPrefs().getString(mContext.getString(R.string.carSelection), "Car45");
               fields = newFields;
               Log.v("TJS", "Importing entry from CSV file into current car: " + newFields[10]);
            } else if(fields.length == 11) {
               // Log.v("TJS","Importing entry from CSV file into car: "+fields[10]);
            } else {
               Log.d("TJS", "Skipping import line: only " + fields.length + "elements in CSV line!!");
            }
            // Log.d("TJS","Read line '"+line+"', date = '"+fields[0]+"'...");
            MileageData record = new MileageData(mContext, fields);
            mContext.getContentResolver().insert(MileageProvider.ALL_CONTENT_URI, record.getContent());
            lineCount++;
            if(mHandler != null) {
               msg = mHandler.obtainMessage();
               b = new Bundle();
               b.putInt(IMPORT_PROGRESS_STR, lineCount);
               b.putInt(IMPORT_MAX_STR, totalLineCount);
               msg.setData(b);
               mHandler.sendMessage(msg);
            }
         }
         reader.close();
         importMessage = "Data Successfully imported from " + mFile;
         Log.d("TJS", importMessage);
      } catch (FileNotFoundException e) {
         Log.e("TJS", e.toString());
      } catch (IOException e) {
         Log.e("TJS", e.toString());
      }
      // Toast.makeText(this, importMessage, Toast.LENGTH_LONG);
      if(mHandler != null) {
         Message msg = mHandler.obtainMessage();
         Bundle b = new Bundle();
         b.putString(IMPORT_FINISHED_STR, importMessage);
         msg.setData(b);
         mHandler.sendMessage(msg);
      }
   }

   public void clearDB(boolean repaint) {
      mContext.getContentResolver().delete(MileageProvider.ALL_CONTENT_URI, null, null);
   }

   private SharedPreferences prefs;

   // 'global' fields for handling Import of data within a separate thread
   protected ImportThread    iThread;

   public SharedPreferences getPrefs() {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      return prefs;
   }

   protected String[] getCSVFiles() {
      String state = Environment.getExternalStorageState();
      if(state.equals(Environment.MEDIA_MOUNTED)) {
         File sdcard = Environment.getExternalStorageDirectory();
         String[] files = sdcard.list(new FilenameFilter() {
            public boolean accept(File dir, String filename) {
               return filename.endsWith(".csv");
            }
         });
         return files;
      } else { // we should NEVER enter here, as it is checked before import/export dialogs are shown!
         Toast.makeText(mContext, "Error! SDCARD not accessible!", Toast.LENGTH_LONG).show();
         return null;
      }
   }
}