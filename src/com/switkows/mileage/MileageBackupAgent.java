package com.switkows.mileage;

import java.io.IOException;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupHelper;
import android.app.backup.FileBackupHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.os.ParcelFileDescriptor;

public class MileageBackupAgent extends BackupAgentHelper {

   //using com.switkows.mileage_preferences because this is the 'default' naming convention for the preferences file
   static final String mPrefs     = "com.switkows.mileage_preferences";    //name of preferences XML file
   static final String mPrefKey   = "mileage_pref_key"; //just a label to give the backup data
	
   static final String mDataFile  = "export.csv";
   static final String mDataKey   = "mileage_data";
	
   @Override
   public void onCreate() {
      SharedPreferencesBackupHelper prefBackup = new SharedPreferencesBackupHelper(this, mPrefs);
      addHelper(mPrefKey, prefBackup);

      FileBackupHelper dataBackup = new MyFileBackupHelper(this, mDataFile);
      addHelper(mDataKey, dataBackup);
   }
	
   @Override
   public void onRestore(BackupDataInput data, int appVersionCode,
         ParcelFileDescriptor newState) throws IOException {
      super.onRestore(data, appVersionCode, newState);
      DataImportThread thread = new DataImportThread(null, this, getFilesDir(), mDataFile);
      thread.run();
      //FIXME - delete file after import
   }

   private class MyFileBackupHelper extends FileBackupHelper implements BackupHelper {

      Context mContext;

      public MyFileBackupHelper(Context context, String... files) {
         super(context, files);
         mContext = context;
      }
      
      @Override
      public void performBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) {
         DataExportThread exporter = new DataExportThread(mContext,mContext.getFilesDir(),mDataFile,false);
         exporter.run();  //write out all data to export.xml
         super.performBackup(oldState, data, newState);
         //FIXME - delete file after backup is complete (will we know when it is?)
      }
	}
}
