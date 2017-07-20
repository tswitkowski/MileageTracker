package com.switkows.mileage

import java.io.File
import java.io.IOException

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.BackupHelper
import android.app.backup.FileBackupHelper
import android.app.backup.SharedPreferencesBackupHelper
import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log

class MileageBackupAgent : BackupAgentHelper() {

    override fun onCreate() {
        val prefBackup = SharedPreferencesBackupHelper(this, mPrefs)
        addHelper(mPrefKey, prefBackup)

        val dataBackup = MyFileBackupHelper(this, mDataFile)
        addHelper(mDataKey, dataBackup)
    }

    @Throws(IOException::class)
    override fun onRestore(data: BackupDataInput, appVersionCode: Int, newState: ParcelFileDescriptor) {
        try {
            super.onRestore(data, appVersionCode, newState)
            DataImportThread(applicationContext, false, null).execute(File(filesDir, mDataFile))
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }

        //FIXME - delete file after import
    }

    // FIXME - this should be 'vararg files: String', but compiler is failing..
    private inner class MyFileBackupHelper internal constructor(internal val mContext: Context, files: String) : FileBackupHelper(mContext, files), BackupHelper {

        override fun performBackup(oldState: ParcelFileDescriptor, data: BackupDataOutput, newState: ParcelFileDescriptor) {
            val exporter = DataExportThread(mContext, false)
            exporter.execute(File(mContext.filesDir, mDataFile)) //write out all data to export.xml
            Log.v("TJS", "MileageTracker backup export complete")
            super.performBackup(oldState, data, newState)
            //FIXME - delete file after backup is complete (will we know when it is?)
        }
    }

    companion object {

        //using com.switkows.mileage_preferences because this is the 'default' naming convention for the preferences file
        private val mPrefs = "com.switkows.mileage_preferences" //name of preferences XML file
        private val mPrefKey = "mileage_prefs"                   //just a label to give the backup data

        private val mDataFile = "export.csv"
        private val mDataKey = "mileage_data"
    }
}
