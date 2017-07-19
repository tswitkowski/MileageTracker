package com.switkows.mileage

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.FragmentActivity
import android.support.v4.app.LoaderManager
import android.support.v4.content.CursorLoader
import android.support.v4.content.Loader
import android.view.ViewGroup.LayoutParams

/**
 * @author Trevor
 * *         This is a pretty simple activity: display the specified
 * *         chart (with correct units), based on the Intent.
 */
class ChartViewer : FragmentActivity(), LoaderManager.LoaderCallbacks<Cursor> {

    public override fun onResume() {
        super.onResume()
        supportLoaderManager.initLoader(45, null, this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val pref = this.getString(R.string.carSelection)
        val car = PreferenceManager.getDefaultSharedPreferences(this).getString(pref, "Car1")
        val uri = Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI, car)
        return CursorLoader(this, uri, null, null, null, null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        val chartManager = MileageChartManager(this, data)

        val i = intent
        val isUS = i.getBooleanExtra(UNITS_KEY, true)
        val chartID = i.getIntExtra(CHART_KEY, MileageChartManager.NO_CHART)
        val view = chartManager.createChart(chartID, true, isUS)
        addContentView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {}

    companion object {
        val UNITS_KEY = "units"
        val CHART_KEY = "chartID"
    }

}
