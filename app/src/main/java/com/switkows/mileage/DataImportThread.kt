package com.switkows.mileage

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.ArrayList
import java.util.HashSet

import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Context
import android.os.AsyncTask
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast

internal class DataImportThread(private val mContext: Context, private val mShow: Boolean             //set to true to show the dialog box
                                , private val mAdapter: EditRecordsListAdapter?) : AsyncTask<File, Int, Boolean>() {
    private var mFile: String? = null               //short file name (used for toast/log messages only)
    private var mMax: Int = 100                     //holds the maximum value of the progress bar
    private var mShowIndeterminate: Boolean = false //set to TRUE to show progress bar as indeterminate
    var isCompleted: Boolean = false
        private set

    private var mDialog: ProgressDialog? = null

    //Callbacks so caller can clean up after thread completion
    internal interface callbacks {
        fun taskCompleted()
    }

    constructor(context: Context, adapter: EditRecordsListAdapter) : this(context, true, adapter)

    override fun doInBackground(vararg params: File): Boolean? {
        if (params.size != 1)
            return false
        val in_file = params[0]
        mFile = in_file.name
        try {
            var reader = BufferedReader(FileReader(in_file))
            var line: String
            clearDB()
            mMax = 0
            mShowIndeterminate = false
            var lineCount: Int = 0
            val currentCar = PreferenceManager.getDefaultSharedPreferences(mContext).getString(mContext.getString(R.string.carSelection), "Car45")
            val newEntries = ArrayList<ContentValues>()
            val profiles = HashSet<ContentValues>()
            while (reader.ready()) {
                reader.readLine()
                mMax++
            }
            reader.close()
            reader = BufferedReader(FileReader(in_file))
            while (reader.ready()) {
                line = reader.readLine()
                var fields : Array<String?> = line.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (fields[0] == MileageData.ToDBNames[0]) {
                    // Log.d("TJS","Found header in CSV file");
                    continue
                }
                // are we an old format, without the 'cars' column?! if so, add it!
                if (fields.size == 10) {
                    val newFields = arrayOfNulls<String>(11)
                    System.arraycopy(fields, 0, newFields, 0, fields.size)
                    newFields[10] = currentCar
                    fields = newFields
                    Log.v("TJS", "Importing entry from CSV file into current car: " + newFields[10])
                } else if (fields.size == 11) {
                    Log.v("TJS", "Importing entry from CSV file into car: " + fields[10])
                } else {
                    Log.d("TJS", "Skipping import line: only " + fields.size + "elements in CSV line!!")
                }
                // Log.d("TJS","Read line '"+line+"', date = '"+fields[0]+"'...");
                val record = MileageData(fields)
                newEntries.add(record.content)
                profiles.add(MileageProvider.createProfileContent(record.carName))
                lineCount++
                publishProgress(lineCount)
            }
            if (newEntries.size > 0) {
                var additions : Array<ContentValues> = newEntries.toTypedArray()
                mContext.contentResolver.bulkInsert(MileageProvider.ALL_CONTENT_URI, additions)
                //push profile names
                additions = profiles.toTypedArray()
                mContext.contentResolver.bulkInsert(MileageProvider.CAR_PROFILE_URI, additions)
            }
            reader.close()
        } catch (e: IOException) {
            Log.e("TJS", e.toString())
        }

        return true
    }

    override fun onProgressUpdate(values: Array<Int>) {
        super.onProgressUpdate(*values)
        if (mShow) {
            mDialog?.progress = values[0]
            if (values[0] >= mMax - 1)
                mShowIndeterminate = true
            updateProgressConfig()
        }
    }

    override fun onPreExecute() {
        super.onPreExecute()
        createDialog()
    }

    override fun onPostExecute(result: Boolean?) {
        super.onPostExecute(result)
        if (mShow) {
            val importMessage: String
            if (result!!)
                importMessage = "Data Successfully imported from\n" + mFile!!
            else
                importMessage = "Error! could not access/read " + mFile!!

            Log.d("TJS", importMessage)
            Toast.makeText(mContext, importMessage, Toast.LENGTH_LONG).show()
            mAdapter?.cursor?.requery()
            mDialog?.dismiss()
        } else {
            Log.d("TJS", "Data Successfully imported..")
        }

        isCompleted = true
        val mCallbacks: callbacks
        try {
            mCallbacks = mContext as callbacks
            mCallbacks.taskCompleted()
        } catch (ignored: ClassCastException) {
        }

    }

    private fun clearDB() {
        mContext.contentResolver.delete(MileageProvider.ALL_CONTENT_URI, null, null)
        mContext.contentResolver.delete(MileageProvider.CAR_PROFILE_URI, null, null)
    }

    private fun createDialog() {
        if (mShow) {
            mDialog = ProgressDialog(mContext)
            mDialog?.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            mDialog?.setMessage("Importing Data...")
            mDialog?.setCancelable(false)
            updateProgressConfig()
            mDialog?.show()
        }
    }

    /**
     * Updates the dialog configuration (i.e. can switch back and forth between indeterminate/determinate)
     */
    private fun updateProgressConfig() {
        if (mShowIndeterminate) {
            mDialog?.isIndeterminate = true
            mDialog?.setProgressNumberFormat(null)
            mDialog?.setProgressPercentFormat(null)
        } else {
            mDialog?.max = mMax
        }
    }

    /**
     * Call once your activity is back in the foreground, and the thread can be 'resumed'
     */
    fun restart() {
        createDialog()
    }

    /**
     * Call when you need to 'suspend' the thread, due to activity going to background, orientation change, etc
     */
    fun pause() {
        mDialog?.dismiss()
        mDialog = null
    }
}