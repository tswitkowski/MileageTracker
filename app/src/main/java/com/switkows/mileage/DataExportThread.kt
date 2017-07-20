package com.switkows.mileage

import java.io.File
import java.io.FileNotFoundException
import java.io.PrintWriter

import android.app.ProgressDialog
import android.content.Context
import android.os.AsyncTask
import android.util.Log
import android.widget.Toast

internal class DataExportThread @JvmOverloads constructor(private val mContext: Context, private val mShow: Boolean = true              //set to true to show the dialog box
) : AsyncTask<File, Int, Boolean>() {
    private var mFilename: String? = null
    private var mMax: Int = 100                     //holds the maximum value of the progress bar
    private var mShowIndeterminate: Boolean = false //set to TRUE to show progress bar as indeterminate

    private var mDialog: ProgressDialog = ProgressDialog(mContext)

    override fun doInBackground(vararg params: File): Boolean? {
        if (params.size != 1)
            return false
        // Log.d("TJS",Environment.getExternalStorageState());
        val csv_file = params[0]
        mFilename = csv_file.name
        // Log.d("TJS","File exists: " + csv_file.exists());
        // Log.d("TJS","is file: " + csv_file.isFile());
        // Log.d("TJS","is writable: " + csv_file.canWrite());
        try {
            // FileOutputStream stream = new FileOutputStream(csv_file);
            // PrintWriter writer = new PrintWriter(stream);
            val writer = PrintWriter(csv_file)
            writer.println(MileageData.exportCSVTitle())

            val cursor = mContext.contentResolver.query(MileageProvider.ALL_CONTENT_URI, null, null, null, null)!!
            mMax = cursor.count
            var lineCount: Int = 0

            while (cursor.moveToNext()) {
                val data = MileageData(cursor)
                writer.println(data.exportCSV())
                lineCount++
                publishProgress(lineCount)
            }
            writer.close()
            cursor.close()

            return true
        } catch (e: FileNotFoundException) {
            Log.e("TJS", e.toString())
        }

        return false
    }

    override fun onProgressUpdate(values: Array<Int>) {
        super.onProgressUpdate(*values)
        if (mShow) {
            mDialog.progress = values[0]
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
                importMessage = "Data Successfully Saved to\n$mFilename(new)"
            else
                importMessage = "Error! could not access/read " + mFilename!!

            Log.d("TJS", importMessage)
            Toast.makeText(mContext, importMessage, Toast.LENGTH_LONG).show()
            mDialog.dismiss()
        } else {
            Log.d("TJS", "Data Successfully exported..")
        }
    }

    private fun createDialog() {
        if (mShow) {
            mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            mDialog.setMessage("Exporting Data...")
            mDialog.setCancelable(false)
            updateProgressConfig()
            mDialog.show()
        }

    }

    /**
     * Updates the dialog configuration (i.e. can switch back and forth between indeterminate/determinate)
     */
    private fun updateProgressConfig() {
        if (mShowIndeterminate) {
            mDialog.isIndeterminate = true
            mDialog.setProgressNumberFormat(null)
            mDialog.setProgressPercentFormat(null)
        } else {
            mDialog.max = mMax
        }
    }
}
