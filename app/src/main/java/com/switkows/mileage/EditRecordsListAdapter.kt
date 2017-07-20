package com.switkows.mileage

import java.util.HashSet

import com.switkows.mileage.EditRecordsMenu.EditRecordsMenuFragment

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.preference.PreferenceManager
import android.support.v4.widget.SimpleCursorAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

internal class EditRecordsListAdapter (
        /** Remember our context so we can use it when constructing views.  */
        private val myContext: Context, private val mParent: EditRecordsMenuFragment, c: Cursor?, from: Array<String>, to: IntArray) : SimpleCursorAdapter(myContext, R.layout.record_list_item, c, from, to, NO_SELECTION) {
    private val mInflater = myContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private var idColumn: Int = 0
    private var mileageColumn: Int = 0
    private var dateColumn: Int = 0
    private var mViewedId: Long = -1

    init {
        if (c != null) {
            idColumn = c.getColumnIndex("_id")
            dateColumn = c.getColumnIndex(MileageData.ToDBNames[MileageData.DATE])
            mileageColumn = c.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE])
        } else {
            mileageColumn = -1
            dateColumn = mileageColumn
            idColumn = dateColumn
        }
    }

    override fun swapCursor(c: Cursor?): Cursor? {
        if (c != null) {
            idColumn = c.getColumnIndex("_id")
            dateColumn = c.getColumnIndex(MileageData.ToDBNames[MileageData.DATE])
            mileageColumn = c.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE])
        }
        return super.swapCursor(c)
    }

    /**
     * Do not recycle a view if one is already there, if not the data could get corrupted and the checkbox state could be
     * lost.

     * @param convView
     * *           The old view to overwrite
     * *
     * @return a EditRecordListItem corresponding with the cursor position
     */

    override fun getView(position: Int, convView: View?, parent: ViewGroup): View {
        var convertView = convView
        val view: EditRecordListItem
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.record_list_item, parent, false)
            (convertView as EditRecordListItem).bindAdapter(this)
        }
        view = convertView as EditRecordListItem
        val cursor = cursor
        cursor.moveToPosition(position)
        view.mIDValue = cursor.getLong(idColumn)
        view.mPosition = position
        //      String date = MileageData.getDateFormatter().format(cursor.getLong(dateColumn));
        //      float mpg = cursor.getFloat(mileageColumn);
        //      mpg = MileageData.getEconomy(mpg, getPrefs(), myContext);
        //      view.setText(String.format("%s (%2.1f %s)", date, mpg, MileageData.getEconomyUnits(getPrefs(), myContext)));
        view.setText(MileageData.getSimpleDescription(cursor, dateColumn, mileageColumn, getPrefs(), myContext))
        view.isChecked = mSelected.contains(view.mIDValue)
        //FIXME - can't the ListView do this automatically?
        //set the 'activated' state, which will cause the UI to add special styling in dualPane mode
        setListActivatedHoneycomb(view)

        return convertView
    }

    private fun setListActivatedHoneycomb(v: EditRecordListItem) {
        v.isActivated = mViewedId >= 0 && v.mIDValue == mViewedId
    }

    private var prefs: SharedPreferences? = null

    private val mSelected = HashSet<Long>()

    fun getPrefs(): SharedPreferences {
        if (prefs == null)
            prefs = PreferenceManager.getDefaultSharedPreferences(myContext)
        return prefs!!
    }

    fun setSelected(id: Long, position: Int, isSelected: Boolean) {
        val lID = id
        if (isSelected)
            mSelected.add(lID)
        else
            mSelected.remove(lID)
        mParent.handleSelection(mSelected.isEmpty(), position, isSelected)
    }

    fun setViewedItem(id: Long) {
        mViewedId = id
    }
}
