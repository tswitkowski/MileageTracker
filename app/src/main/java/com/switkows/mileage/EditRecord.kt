package com.switkows.mileage

import java.util.Calendar
import java.util.GregorianCalendar

import com.switkows.mileage.ProfileSelector.ProfileSelectorCallbacks

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.CursorLoader
import android.support.v4.content.Loader
import android.support.v4.widget.SimpleCursorAdapter
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup.LayoutParams
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView

class EditRecord : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        val action = intent.action
        //FIXME - added an assumption that the ONLY way we will start this activity in landscape is via a 'new' record
        val isNew = MileageTracker.ACTION_INSERT == action
        if (!isNew && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            //if we're in landscape, we'll use a two-pane interface, so dismiss this activity!
            val id = Integer.parseInt(intent.data!!.pathSegments[1])
            setResult(id + 1)
            finish()
        } else if (savedInstanceState == null) {
            var id: Long = -1
            if (!isNew)
                id = java.lang.Long.parseLong(intent.data!!.pathSegments[1])
            val fragment = EditRecordFragment.newInstance(id, isNew)
            supportFragmentManager.beginTransaction().replace(android.R.id.content, fragment).commit()
            setResult(RESULT_CANCELED)   //If we hit this case, we want to tell caller that this activity was canceled (thus the caller should not attempt to add this record's view back to the application)

            // Update metrics
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val shortcutManager = getSystemService(ShortcutManager::class.java)
                shortcutManager!!.reportShortcutUsed("stop_for_gas")
            }

            //FIXME - seems like a hack to get the dialog to stretch to the proper width:
            //         LayoutParams params = getWindow().getAttributes();
            //         params.width = LayoutParams.FILL_PARENT;
            //         getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
        } else {
            setResult(RESULT_CANCELED) //FIXME - needed?
        }
    }

    class EditRecordFragment : Fragment(), ProfileSelectorCallbacks {
        private var mUri: Uri? = null
        private var mId: Long = 0
        private var mProfileName: String? = null
        private var mCursor: Cursor? = null
        private var prefs: SharedPreferences? = null
        private var isNewRecord: Boolean = false
        private var mDualPane: Boolean = false
        private var stationAutocompleteAdapter: myListAdapter? = null
        private val mLoaderCallbacks = LoaderCallbacks()
        private var mProfileAdapter: ProfileSelector? = null

        private var dateBox: TextView? = null
        private var stationBox: AutoCompleteTextView? = null
        private var odoBox: TextView? = null
        private var tripBox: TextView? = null
        private var gallonsBox: TextView? = null
        private var priceBox: TextView? = null
        private var compMpgBox: TextView? = null
        private var actMpgBox: TextView? = null
        private var totPriceBox: TextView? = null
        private var mpgDiffBox: TextView? = null
        private var mCallback: UpdateCallback? = null

        private var viewAttached: Boolean = false                            //boolean to determine whether we have a View attached (or whether the fragment is in the background)

        //this routine is called when an item is added/updated
        //this allows us to handle the event differently depending
        //on which activity invoked this fragment
        interface UpdateCallback {
            //return true to indicate the event was handled
            fun messageUpdated(): Boolean
        }

        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            isNewRecord = false
            if (container == null)
            //if we are not attached to a View
                return null
            val result: View

            // Have the system blur any windows behind this one.
            //         getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
            //                 WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            // Do some setup based on the action being performed.

            val newRecord = arguments.getBoolean("new")
            mId = arguments.getLong("id")
            mDualPane = activity !is EditRecord
            mProfileName = PreferenceManager.getDefaultSharedPreferences(activity).getString(this.getString(R.string.carSelection), "Car45")
            if (!newRecord) {
                // Requested to edit: set that state, and the data being edited.
                // mState = STATE_EDIT;
                result = inflater!!.inflate(if (mDualPane) R.layout.edit_record_fragment else R.layout.edit_record, null)

                mUri = ContentUris.withAppendedId(MileageProvider.ALL_CONTENT_URI, mId)
                loaderManager.initLoader(LoaderCallbacks().ID_DATA_LOADER, null, mLoaderCallbacks)
            } else {
                isNewRecord = true
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                    result = inflater!!.inflate(R.layout.edit_record_landscape_floating, null)
                else
                    result = inflater!!.inflate(if (mDualPane) R.layout.edit_record_fragment else R.layout.edit_record, null)
                // Requested to insert: set that state, and create a new entry
                // in the container.
                // mState = STATE_INSERT;
                // mUri = getContentResolver().insert(intent.getData(), null);
            }

            return result
        }

        override fun onAttach(context: Context?) {
            super.onAttach(context)
            try {
                mCallback = context as UpdateCallback?
            } catch (ignored: ClassCastException) {
            }

        }

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)
            val submit : Button? = activity.findViewById<View>(R.id.submit) as Button?
            //this is in case we don't have a view. for some reason, i'm seeing this called even if onCreateView returns null
            viewAttached = submit != null
            if (submit == null)
                return
            if (isNewRecord)
                submit.setText(R.string.add_entry_label)
            submit.setOnClickListener(View.OnClickListener {
                updateDbRow()
                handleCompletion(mId)
            })
            val tf = getTextFieldStruct(MileageData.DATE)
            if (tf != null) {
                tf.setOnClickListener { createDialog().show() }
                tf.inputType = InputType.TYPE_NULL
            }

            // listener for the input boxes which may cause a change to the 'generated'
            // fields in the database.
            val listener = OnFocusChangeListener { _, _ ->
                val data = createDataStructure()
                fillFromDataStructure(data!!)
            }
            intArrayOf( MileageData.TRIP,       MileageData.PRICE,
                        MileageData.GALLONS,    MileageData.COMPUTER_MILEAGE).forEach {
                field -> getTextFieldStruct(field)?.onFocusChangeListener = listener
            }

            getTextFieldStruct(MileageData.DATE)?.inputType = InputType.TYPE_NULL
            // getTextFieldStruct(MileageData.ACTUAL_MILEAGE).setEnabled(false);
            // getTextFieldStruct(MileageData.MPG_DIFF).setEnabled(false);
            // getTextFieldStruct(MileageData.TOTAL_PRICE).setEnabled(false);
            getTextFieldStruct(MileageData.STATION)//grab pointer

            val activity = activity as AppCompatActivity
            if (!mDualPane && activity.supportActionBar == null) {
                val toolbar : Toolbar? = activity.findViewById<View>(R.id.main_toolbar) as Toolbar
                if (toolbar != null) {
                    activity.setSupportActionBar(toolbar)
                    //match the parent width    (ugly!!)
                    //            LayoutParams params = new LayoutParams(getActivity().getWindow().getAttributes().width, toolbar.getHeight());
                    //            toolbar.setLayoutParams(params);
                    //               toolbar.setMinimumWidth(activity.getWindow().getAttributes().width);
                }
            }

        }

        override fun onResume() {
            super.onResume()

            //         Button submit = (Button)getActivity().findViewById(R.id.submit);
            //         //this is in case we don't have a view. for some reason, i'm seeing this called even if onCreateView returns null
            //         viewAttached = submit != null;
            if (!viewAttached)
                return
            if (isNewRecord) {
                activity.title = getText(R.string.new_record_title)
                if (mProfileAdapter == null)
                    mProfileAdapter = ProfileSelector.setupActionBar(activity as AppCompatActivity, this)
            } else {
                val indicator = "(" + getPrefs()?.getString(getString(R.string.carSelection), "Car45") + "):"
                activity.title = getText(R.string.edit_record_title).toString() + indicator
            }
            //Note : assumes mCursor is not NULL if we are editing a record (so this call doesn't disturb the data..)
            if (isNewRecord) {
                val c = Calendar.getInstance()
                val year = c.get(Calendar.YEAR)
                val month = c.get(Calendar.MONTH)
                val day = c.get(Calendar.DAY_OF_MONTH)
                val tf = getTextFieldStruct(MileageData.DATE)
                tf?.text = MileageData.getFormattedDate(month, day, year)
            }
            // Try and grab all of the previously entered gas stations, to make data entry easier
            val text = getTextFieldStruct(MileageData.STATION) as AutoCompleteTextView?

            //start us off with a null cursor (Loader will populate later)
            stationAutocompleteAdapter = myListAdapter(activity, null)
            loaderManager.initLoader(LoaderCallbacks().ID_STATION_LOADER, arguments, mLoaderCallbacks)
            text?.setAdapter<myListAdapter>(stationAutocompleteAdapter)
        }

        fun setInsertProfile(newProfile: String) {
            mProfileName = newProfile
        }

        private fun handleCompletion(id: Long) {
            if (activity.intent.hasExtra("starter"))
                startActivity(Intent(activity, MileageTracker::class.java))
            val result = mCallback?.messageUpdated() ?: false
            activity.setResult((id + 1).toInt())
            if (!result)
                activity.finish()
        }

        private fun setTextFields() {
            for (i in 0..MileageData.MPG_DIFF) {
                getTextFieldStruct(i)?.text = colToString(i)
            }
        }

        private fun fillFromDataStructure(data: MileageData) {
            val values = data.content
            for (i in MileageData.ACTUAL_MILEAGE..MileageData.MPG_DIFF) {
                getTextFieldStruct(i)?.text = values.getAsString(MileageData.ToDBNames[i])
            }
        }

        private fun getTextFieldStruct(dbColumn: Int): TextView? {
            when (dbColumn) {
                MileageData.DATE              -> {if (dateBox      == null) dateBox      = activity.findViewById<View>(R.id.date_reading           ) as TextView;              return dateBox}
                MileageData.STATION           -> {if (stationBox   == null) stationBox   = activity.findViewById<View>(R.id.gas_station_reading    ) as AutoCompleteTextView;  return stationBox}
                MileageData.ODOMETER          -> {if (odoBox       == null) odoBox       = activity.findViewById<View>(R.id.odo_reading            ) as TextView;              return odoBox}
                MileageData.TRIP              -> {if (tripBox      == null) tripBox      = activity.findViewById<View>(R.id.trip_reading           ) as TextView;              return tripBox}
                MileageData.GALLONS           -> {if (gallonsBox   == null) gallonsBox   = activity.findViewById<View>(R.id.gallons_reading        ) as TextView;              return gallonsBox}
                MileageData.PRICE             -> {if (priceBox     == null) priceBox     = activity.findViewById<View>(R.id.price_reading          ) as TextView;              return priceBox}
                MileageData.COMPUTER_MILEAGE  -> {if (compMpgBox   == null) compMpgBox   = activity.findViewById<View>(R.id.comp_mileage_reading   ) as TextView;              return compMpgBox}
                MileageData.ACTUAL_MILEAGE    -> {if (actMpgBox    == null) actMpgBox    = activity.findViewById<View>(R.id.actual_mileage_reading ) as TextView;              return actMpgBox}
                MileageData.TOTAL_PRICE       -> {if (totPriceBox  == null) totPriceBox  = activity.findViewById<View>(R.id.total_price_reading    ) as TextView;              return totPriceBox}
                MileageData.MPG_DIFF          -> {if (mpgDiffBox   == null) mpgDiffBox   = activity.findViewById<View>(R.id.mpg_diff_reading       ) as TextView;              return mpgDiffBox}
            }
            return null
        }

        fun getPrefs(): SharedPreferences? {
            if (prefs == null)
                prefs = PreferenceManager.getDefaultSharedPreferences(activity)
            return prefs
        }

        private fun updateDbRow() {
            val data = createDataStructure()
            val values = data?.content
            if (isNewRecord) {
                mUri = Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI, mProfileName)
                activity.contentResolver.insert(mUri!!, values)
            } else
                activity.contentResolver.update(mUri!!, values, "_id = " + mCursor!!.getInt(mCursor!!.getColumnIndex("_id")), null)
        }

        private fun createDataStructure(): MileageData? {
            var odo = 0f
            var trip = 0f
            var gal = 0f
            var price = 0f
            var diff = 0f
            var tf: TextView?
            if (mProfileName!!.isEmpty())
                mProfileName = getPrefs()?.getString(getString(R.string.carSelection), "Car45")
            try {
                tf = getTextFieldStruct(MileageData.ODOMETER)
                if (tf != null)
                    odo = java.lang.Float.parseFloat(tf.text.toString())
            } catch (e: NumberFormatException) {
                odo = 0f
            }

            try {
                tf = getTextFieldStruct(MileageData.TRIP)
                if (tf != null)
                    trip = java.lang.Float.parseFloat(tf.text.toString())
            } catch (e: NumberFormatException) {
                trip = 0f
            }

            try {
                tf = getTextFieldStruct(MileageData.GALLONS)
                if (tf != null)
                    gal = java.lang.Float.parseFloat(tf.text.toString())
            } catch (e: NumberFormatException) {
                gal = 0f
            }

            try {
                tf = getTextFieldStruct(MileageData.PRICE)
                if (tf != null)
                    price = java.lang.Float.parseFloat(tf.text.toString())
            } catch (e: NumberFormatException) {
                price = 0f
            }

            try {
                tf = getTextFieldStruct(MileageData.COMPUTER_MILEAGE)
                if (tf != null)
                    diff = java.lang.Float.parseFloat(tf.text.toString())
            } catch (e: NumberFormatException) {
                diff = 0f
            }

            // Convert from Metric units (km/L & km) to US units (mpg & mi)
            if (!MileageData.isMilesGallons(getPrefs()!!, activity)) {
                odo /= MileageData.getDistance(1f, getPrefs(), activity)
                trip /= MileageData.getDistance(1f, getPrefs(), activity)
                gal /= MileageData.getVolume(1f, getPrefs(), activity)
            }
            tf = getTextFieldStruct(MileageData.DATE)
            val st = getTextFieldStruct(MileageData.STATION)
            if (tf != null && st != null)
                return MileageData(mProfileName!!, tf.text.toString(),
                        st.text.toString(), odo, trip, gal, price, diff)
            return null
        }

        private fun colToString(column: Int): String {
            if (column == MileageData.DATE)
                return MileageData.getFormattedDate(mCursor!!.getLong(mCursor!!.getColumnIndex(MileageData.ToDBNames[column])))
            else if (column == MileageData.STATION)
                return mCursor!!.getString(mCursor!!.getColumnIndex(MileageData.ToDBNames[column]))
            var value = mCursor!!.getFloat(mCursor!!.getColumnIndex(MileageData.ToDBNames[column]))
            when (column) {
                MileageData.ACTUAL_MILEAGE, MileageData.COMPUTER_MILEAGE -> value = MileageData.getEconomy(value, getPrefs(), activity)
                MileageData.GALLONS -> value = MileageData.getVolume(value, getPrefs(), activity)
                MileageData.ODOMETER, MileageData.TRIP -> value = MileageData.getDistance(value, getPrefs(), activity)
            }
            return "" + value
        }

        private fun createDialog(): Dialog {
            val tf = getTextFieldStruct(MileageData.DATE)
            val date = tf?.text?.toString() ?: ""
            val cal1 = GregorianCalendar()
            cal1.timeInMillis = MileageData.parseDate(date)
            return DatePickerDialog(activity, dateListener, cal1.get(Calendar.YEAR), cal1.get(Calendar.MONTH), cal1.get(Calendar.DAY_OF_MONTH))
        }

        private val dateListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth -> setDateDisplay(monthOfYear, dayOfMonth, year) }

        private fun setDateDisplay(month: Int, day: Int, year: Int) {
            val tf = getTextFieldStruct(MileageData.DATE)
            tf?.text = MileageData.getFormattedDate(month, day, year)
        }

        override fun onProfileChange(newProfile: String) {
            setInsertProfile(newProfile)
        }

        private inner class LoaderCallbacks : LoaderManager.LoaderCallbacks<Cursor> {
            internal val ID_STATION_LOADER = 0
            internal val ID_DATA_LOADER = 1
            //         protected final String     STATION_NAME      = MileageData.ToDBNames[MileageData.STATION];
            internal val STATION_PROJ = arrayOf("_id", STATION_NAME)
            internal val STATION_SORT = STATION_NAME + " DESC"

            override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
                if (id == ID_STATION_LOADER) {
                    val uri = MileageProvider.ALL_CONTENT_URI
                    val hint = args?.getString("hint")
                    val filter = if (hint == null) null else "$STATION_NAME like '%$hint%'"
                    return CursorLoader(activity, uri, STATION_PROJ, filter, null, STATION_SORT)
                } else if (id == ID_DATA_LOADER) {
                    return CursorLoader(activity, mUri, null, null, null, null)
                } else {
                    return null
                }
            }

            override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
                if (loader.id == ID_STATION_LOADER) {
                    stationAutocompleteAdapter!!.swapCursor(data)
                } else if (loader.id == ID_DATA_LOADER) {
                    mCursor = data
                    mCursor!!.moveToFirst()
                    setTextFields()
                }
            }

            override fun onLoaderReset(loader: Loader<Cursor>) {
                if (loader.id == ID_STATION_LOADER) {
                    stationAutocompleteAdapter!!.swapCursor(null)
                } else if (loader.id == ID_DATA_LOADER) {
                    mCursor = null
                }
            }
        }

        // FIXME - don't delete! this should be investigated as an enhancement to the
        // below implementation!!
        // public static class AutocompleteListAdapter extends CursorAdapter
        // implements Filterable {
        // public AutocompleteListAdapter(Context context, Cursor c) {
        // super(context, c);
        // // mContent = context.getContentResolver();
        // }
        //
        // @Override
        // public View newView(Context context, Cursor cursor, ViewGroup parent) {
        // final LayoutInflater inflater = LayoutInflater.from(context);
        // final TextView view = (TextView) inflater.inflate(
        // android.R.layout.simple_dropdown_item_1line, parent, false);
        // view.setText(cursor.getString(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.STATION])));
        // return view;
        // }
        //
        // @Override
        // public void bindView(View view, Context context, Cursor cursor) {
        // ((TextView)
        // view).setText(cursor.getString(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.STATION])));
        // }
        //
        // @Override
        // public String convertToString(Cursor cursor) {
        // return
        // cursor.getString(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.STATION]));
        // }
        //
        // // @Override
        // // public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        // // if (getFilterQueryProvider() != null) {
        // // return getFilterQueryProvider().runQuery(constraint);
        // // }
        // //
        // // StringBuilder buffer = null;
        // // String[] args = null;
        // // if (constraint != null) {
        // // buffer = new StringBuilder();
        // // buffer.append("UPPER(");
        // // buffer.append(Contacts.ContactMethods.NAME);
        // // buffer.append(") GLOB ?");
        // // args = new String[] { constraint.toString().toUpperCase() + "*" };
        // // }
        // //
        // // return mContent.query(Contacts.People.CONTENT_URI, PEOPLE_PROJECTION,
        // // buffer == null ? null : buffer.toString(), args,
        // // Contacts.People.DEFAULT_SORT_ORDER);
        // // }
        // //
        // // private ContentResolver mContent;
        // }
        private inner class myListAdapter
        //         private final String   ColumnName = MileageData.ToDBNames[MileageData.STATION];

        internal constructor(context: Context, c: Cursor?) : SimpleCursorAdapter(context, android.R.layout.simple_dropdown_item_1line, c, arrayOf<String>(STATION_NAME), intArrayOf(android.R.id.text1), NO_SELECTION) {

            override fun convertToString(cursor: Cursor?): String {
                val columnIndex = cursor!!.getColumnIndexOrThrow(STATION_NAME)
                return cursor.getString(columnIndex)
            }

            override fun newView(context: Context?, cursor: Cursor?, parent: ViewGroup): View? {
                if (cursor != null) {
                    val view = super.newView(context, cursor, parent) as TextView
                    view.textSize = 12f
                    view.layoutParams.width = LayoutParams.WRAP_CONTENT
                    view.height = 10 // FIXME - doesn't appear to have any affect
                    return view
                }
                return null
            }

            override fun runQueryOnBackgroundThread(constraint: CharSequence?): Cursor? {
                val args = Bundle()
                if (constraint != null)
                    args.putString("hint", constraint.toString())
                loaderManager.restartLoader(LoaderCallbacks().ID_STATION_LOADER, args, mLoaderCallbacks)
                return null
            }
        }

        companion object {

            fun newInstance(id: Long, isNew: Boolean): EditRecordFragment {
                val result = EditRecordFragment()
                val args = Bundle()
                args.putLong("id", id)
                args.putBoolean("new", isNew)
                result.arguments = args
                return result
            }

            private val STATION_NAME = MileageData.ToDBNames[MileageData.STATION]
        }
    }
}
