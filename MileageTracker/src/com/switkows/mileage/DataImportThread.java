package com.switkows.mileage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

public class DataImportThread extends Thread {
   Handler                    mHandler;
   Context                    mContext;
   String                     mFile;
   File                       mDir;

   DataImportThread(Handler h, Context c, File dir, String f) {
      mHandler  = h;
      mContext  = c;
      mFile     = f;
      mDir      = dir;
      // Log.d("TJS","Created ImportThread...");
   }

   public void setHandler(Handler h) {
      mHandler = h;
   }

   @Override
   public void run() {
      // Log.d("TJS","Started ImportThread");
      File in_file = new File(mDir, mFile);
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
            b.putInt(ImportExportProgressHandler.MAX_KEY, totalLineCount);
            msg.setData(b);
            mHandler.sendMessage(msg);
         }

         reader = new BufferedReader(new FileReader(in_file));
         int lineCount = 0;
         String currentCar = PreferenceManager.getDefaultSharedPreferences(mContext).getString(mContext.getString(R.string.carSelection), "Car45");
         ArrayList<ContentValues> newEntries = new ArrayList<ContentValues>();
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
            lineCount++;
            if(mHandler != null) {
               msg = mHandler.obtainMessage();
               b = new Bundle();
               b.putInt(ImportExportProgressHandler.CURRENT_KEY, lineCount);
               b.putInt(ImportExportProgressHandler.MAX_KEY, totalLineCount);
               msg.setData(b);
               mHandler.sendMessage(msg);
            }
         }
         if(newEntries.size()>0) {
            ContentValues[] additions = new ContentValues[newEntries.size()];
            additions = newEntries.toArray(additions);
            mContext.getContentResolver().bulkInsert(MileageProvider.ALL_CONTENT_URI, additions);
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
         b.putString(ImportExportProgressHandler.FINISHED_KEY, importMessage);
         msg.setData(b);
         mHandler.sendMessage(msg);
      }
   }

   public void clearDB(boolean repaint) {
      mContext.getContentResolver().delete(MileageProvider.ALL_CONTENT_URI, null, null);
   }
}