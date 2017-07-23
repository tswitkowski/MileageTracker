package com.switkows.mileage

import java.io.File

import com.switkows.mileage.EditRecord.EditRecordFragment
import com.switkows.mileage.ProfileSelector.ProfileSelectorCallbacks

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.DialogInterface.OnClickListener
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager.OnBackStackChangedListener
import android.support.v4.app.ListFragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.ContextCompat
import android.support.v4.content.CursorLoader
import android.support.v4.content.Loader
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.AbsListView.MultiChoiceModeListener

class EditRecordsMenu : AppCompatActivity(), EditRecordFragment.UpdateCallback, DataImportThread.callbacks, OnBackStackChangedListener, ProfileSelectorCallbacks {

    // 'global' fields for handling Import of data within a separate thread
    private var iThread: DataImportThread? = null
    private var mViewedRecordId: Long = 0
    private var mLastUri: Uri? = null
    private var mProfileAdapter: ProfileSelector? = null

    @SuppressLint("PrivateResource")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edit_record_activity)
        val toolbar = findViewById<Toolbar>(R.id.main_toolbar)
        toolbar.setLogo(R.drawable.mileage_tracker_icon)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        val i = intent
        if (i.data == null) {
            i.data = uri
        }

        mProfileAdapter = ProfileSelector.setupActionBar(this, null)

        // restore ImportThread pointer, if we got here by way of an orientation change
        if (lastCustomNonConfigurationInstance != null) {
            iThread = lastCustomNonConfigurationInstance as DataImportThread
            iThread!!.restart()
        }
        if (savedInstanceState != null) {
            mViewedRecordId = savedInstanceState.getLong("currentView")
            mLastUri = savedInstanceState.getParcelable<Uri>("lastUri")
        } else
            mViewedRecordId = -1
    }

    public override fun onResume() {
        supportFragmentManager.addOnBackStackChangedListener(this)
        // This might not be the best way to do this, but if we 'resume' this activity,
        // throw away the old cursor, and re-generate the data. This was needed to
        // support a user changing 'profiles' from the preferences screen
        //FIXME - is this okay? should i always reset the data?!
        val uri = uri
        if (mLastUri == null || uri.compareTo(mLastUri) != 0) {
            intent.data = uri

            //This fragment will persist indefinitely.
            val f = EditRecordsMenuFragment.newInstance(this, intent.data)
            val trans = supportFragmentManager.beginTransaction()
            trans.replace(R.id.record_list_fragment, f, LIST_FRAGMENT)
            trans.commitAllowingStateLoss()
            hideRecordView()
        }

        setHomeEnabledHoneycomb()
        if (mProfileAdapter != null)
            mProfileAdapter!!.loadActionBarNavItems(this)
        //FIXME - should store the Fragment pointer, so we don't lose state on orientation-switches!!
        if (mViewedRecordId >= 0)
            updateRecordView(mViewedRecordId)
        mLastUri = uri
        super.onResume()
    }

    // SuppressWarnings for call to setDisplayHomeAsUpEnabled, which might return a NPE
    private fun setHomeEnabledHoneycomb() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    // Save the import worker thread, so re-launching the program
    // due to an orientation change will not result in a lockup
    override fun onRetainCustomNonConfigurationInstance(): Any? {
        if (iThread != null && !iThread!!.isCompleted) {
            iThread!!.pause()
            return iThread!!
        }
        return super.onRetainCustomNonConfigurationInstance()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("currentView", mViewedRecordId)
        outState.putParcelable("lastUri", mLastUri)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        //      super.onCreateOptionsMenu(menu);
        val inflater = menuInflater
        inflater.inflate(R.menu.database_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val fragment: DialogFragment
        when (item.itemId) {
            R.id.add_item -> {
                val uri = intent.data
                startActivity(Intent(MileageTracker.ACTION_INSERT, uri))
                return true
            }
            R.id.delete_entry -> {
                fragment = DeleteConfirmFragment.newInstance(selectedMessage)
                fragment.show(supportFragmentManager, "dialog")
                return true
            }
            R.id.move_entry -> {
                fragment = MoveDialogFragment.newInstance(selectedMessage)
                fragment.show(supportFragmentManager, "dialog")
                return true
            }
            R.id.select_all -> {
                selectAll()
                return true
            }
            R.id.clear_database -> {
                clearDB()
                return true
            }
            R.id.export_csv // don't show dialog if the SDCard is not installed.
                , R.id.import_csv // It'll issue a Toast-based message, though
            -> {
                checkSDState(item.itemId)
                return true
            }
            R.id.preferences -> {
                // Launch preferences activity
                startActivityForResult(Intent(this, EditPreferences::class.java), 0)
                return true
            }
            android.R.id.home -> {
                startActivity(Intent(this, MileageTracker::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                return true
            }
        }
        return false
    }

    private fun checkSDState(menuId: Int) {
        val state = Environment.getExternalStorageState()
        if (state == Environment.MEDIA_MOUNTED) {
            var fragment: DialogFragment? = null
            when (menuId) {
                R.id.import_csv -> {
                    val permissionCheck = ContextCompat.checkSelfPermission(this,
                            Manifest.permission.READ_EXTERNAL_STORAGE)
                    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                        // Should we show an explanation?
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                                Manifest.permission.READ_EXTERNAL_STORAGE)) {
                            //show 'why we need this' dialog
                            Log.d("TJS", "Show import reason")
                        }
                        ActivityCompat.requestPermissions(this,
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                SD_READ_REQ)
                    } else
                        fragment = ImportDialogFragment.newInstance()
                }
                R.id.export_csv -> {
                    val readPermission = ContextCompat.checkSelfPermission(this,
                            Manifest.permission.READ_EXTERNAL_STORAGE)
                    val writePermission = ContextCompat.checkSelfPermission(this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    if (readPermission != PackageManager.PERMISSION_GRANTED || writePermission != PackageManager.PERMISSION_GRANTED) {
                        // Should we show an explanation?
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                                Manifest.permission.READ_EXTERNAL_STORAGE) || ActivityCompat.shouldShowRequestPermissionRationale(this,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            //show 'why we need this' dialog
                            Log.d("TJS", "Show why we need write permission dialog")
                        }
                        ActivityCompat.requestPermissions(this,
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                SD_WRITE_REQ)
                    } else
                        fragment = ExportDialogFragment.newInstance()
                }
            }
            if (fragment != null)
                fragment.show(supportFragmentManager, "dialog")
        } else {
            Toast.makeText(this, "Error! SDCARD not accessible!", Toast.LENGTH_LONG).show()
        }
    }

    private fun performImport(filename: String) {
        val file = File(Environment.getExternalStorageDirectory(), filename)
        iThread = DataImportThread(this, listAdapter!!)
        iThread!!.execute(file)
    }

    private fun performExport(filename: String) {
        val file = File(Environment.getExternalStorageDirectory(), filename)
        val exporter = DataExportThread(this)
        exporter.execute(file)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0) {
            if (resultCode == Activity.RESULT_CANCELED)
                mViewedRecordId = -1
            else {
                messageUpdated()
                mViewedRecordId = (resultCode - 1).toLong()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            SD_READ_REQ -> {
                run {
                    // If request is cancelled, the result arrays are empty.
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        // permission was granted, yay! Do the
                        // contacts-related task you need to do.
                        checkSDState(R.id.import_csv)
                    }
                }
                run {
                    // If request is cancelled, the result arrays are empty.
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        // permission was granted, yay! Do the
                        // contacts-related task you need to do.
                        checkSDState(R.id.export_csv)
                    }
                }
            }
            SD_WRITE_REQ -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkSDState(R.id.export_csv)
                }
            }
        }// other 'case' lines to check for other
        // permissions this app might request
    }

    private val listFragment: EditRecordsMenuFragment?
        get() {
            val fragment = supportFragmentManager.findFragmentByTag(LIST_FRAGMENT)
            if (fragment != null)
                return fragment as EditRecordsMenuFragment
            return null
        }

    private val selectedMessage: String
        get() {
            val fragment = listFragment
            if (fragment != null)
                return fragment.selectedMessage
            return ""
        }

    private fun moveSelected(profile: String) {
        val fragment = listFragment
        if (fragment != null)
            if (fragment.moveSelected(mViewedRecordId, profile))
                hideRecordView()
    }

    private fun deleteSelected() {
        val fragment = listFragment
        if (fragment != null)
            if (fragment.deleteSelected(mViewedRecordId))
                hideRecordView()
    }

    private fun deselectAll() {
        val fragment = listFragment
        fragment?.deselectAll()
    }

    private fun selectAll() {
        val fragment = listFragment
        fragment?.selectAll()
    }

    private val listAdapter: EditRecordsListAdapter?
        get() {
            return listFragment?.listAdapter as EditRecordsListAdapter
        }

    private fun updateRecordView(id: Long) {
        val view = findViewById<View>(R.id.edit_record_fragment)
        //this is here so we update the 'activated' state of the ListView
        //which allows background state to be updated upon orientation change
        //(between dualPane and singlePane)
        if (view != null) {
            listFragment?.updateRecordView(id)
        }
        //do not restart the EditRecord fragment unless we need to (this
        //ensures that state is kept when this activity goes in the background)
        if (mViewedRecordId == id && view != null && view.visibility == View.VISIBLE)
            return
        mViewedRecordId = id
        if (view != null) {
            val trans = supportFragmentManager.beginTransaction()
            val fragment = EditRecordFragment.newInstance(id, false)
            //add the fragment to the stack, so 'back' navigation properly hides it (hopefully)
            if (view.visibility != View.VISIBLE)
                trans.addToBackStack(RECORD_FRAGMENT)
            view.visibility = View.VISIBLE
            trans.replace(R.id.edit_record_fragment, fragment, RECORD_FRAGMENT)
            trans.commitAllowingStateLoss()

        } else {
            //pop the back stack
            supportFragmentManager.popBackStack()
            val uri = ContentUris.withAppendedId(MileageProvider.ALL_CONTENT_URI, id)
            //wait for result so we know whether the the activity was closed due to a
            //cancel or an orientation-change. This allows us to save the state when
            //we transition back and forth between dualPane & singlePane modes
            startActivityForResult(Intent(Intent.ACTION_EDIT, uri), 0)
        }
    }

    //delete active fragment, and hide view
    private fun hideRecordView() {
        val view = findViewById<View>(R.id.edit_record_fragment)
        mViewedRecordId = -1   //reset pointer, so we don't get confused later
        if (view != null) {
            val list = listFragment
            list?.updateRecordView(mViewedRecordId)
            val trans = supportFragmentManager.beginTransaction()
            val fragment = supportFragmentManager.findFragmentByTag(RECORD_FRAGMENT)
            if (fragment != null) {
                trans.remove(fragment)
                trans.commitAllowingStateLoss()
            }
            view.visibility = View.GONE
        }
    }

    //This is not a true database clear...just clears THIS profile's data!!!
    private fun clearDB() {
        contentResolver.delete(uri, null, null)
    }

    private val uri: Uri
        get() {
            val option = getString(R.string.carSelection)
            val car = PreferenceManager.getDefaultSharedPreferences(this).getString(option, "Car45")
            return Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI, car)
        }

    private
    val csvFiles: Array<String>?
        get() {
            val state = Environment.getExternalStorageState()
            if (state == Environment.MEDIA_MOUNTED) {
                val sdcard = Environment.getExternalStorageDirectory()
                return sdcard.list { _, filename -> filename.endsWith(".csv") }
            } else { // we should NEVER enter here, as it is checked before import/export dialogs are shown!
                Toast.makeText(this, "Error! SDCARD not accessible!", Toast.LENGTH_LONG).show()
                return null
            }
        }

    //once import task has completed, reset pointer so we don't resurrect dialog box!
    override fun taskCompleted() {
        iThread = null
    }

    class EditRecordsMenuFragment : ListFragment() {
        private var mAdapter: EditRecordsListAdapter? = null
        private val mLoaderCallback = RecordListLoaderCallbacks()

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)
            loaderManager.initLoader(0, arguments, mLoaderCallback)
            mAdapter = EditRecordsListAdapter(activity, this, null, arrayOf(MileageData.ToDBNames[MileageData.DATE]),
                    intArrayOf(android.R.id.text1))
            listAdapter = mAdapter
            setEmptyText("No records present")
            if (setChoiceModeListenerHoneycomb(listView))
                listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        private fun setChoiceModeListenerHoneycomb(v: ListView): Boolean {
            v.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
            v.setMultiChoiceModeListener(EditRecordModeListener())
            return false
        }

        override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
            (activity as EditRecordsMenu).updateRecordView(id)
            super.onListItemClick(l, v, position, id)
        }

        fun handleSelection(hide: Boolean, position: Int, isSelected: Boolean) {
            if (hide && position == -1)
                listView.clearChoices()
            listView.setItemChecked(position, isSelected)
        }

        fun updateRecordView(id: Long) {
            mAdapter!!.setViewedItem(id)
            listView.invalidateViews()
            //         getListView().setSelected(true);
        }

        fun deleteSelected(currentlyViewedId: Long): Boolean {
            var foundIt = false //set to true if the currentlyViewedId was moved/deleted
            val checked = listView.checkedItemPositions
            val baseUri = MileageProvider.ALL_CONTENT_URI
            var id: Long
            for (index in 0..checked.size() - 1) {
                id = listAdapter.getItemId(checked.keyAt(index))
                activity.contentResolver.delete(ContentUris.withAppendedId(baseUri, id), null, null)
                if (id == currentlyViewedId)
                    foundIt = true
            }
            //FIXME - this is needed, apparently, since i'm not using the same URI as the adapter uses?!
            loaderManager.restartLoader(0, arguments, mLoaderCallback)
            mAdapter!!.notifyDataSetChanged()
            return foundIt
        }

        //FIXME - merge deleteSelected & moveSelected, since all code except the provider call is different
        fun moveSelected(currentlyViewedId: Long, destProfile: String): Boolean {
            var foundIt = false //set to true if the currentlyViewedId was moved/deleted
            val checked = listView.checkedItemPositions
            val baseUri = MileageProvider.ALL_CONTENT_URI
            val values = ContentValues(1)
            values.put(MileageData.ToDBNames[MileageData.CAR], destProfile)
            var id: Long
            for (index in 0..checked.size() - 1) {
                id = listAdapter.getItemId(checked.keyAt(index))
                activity.contentResolver.update(ContentUris.withAppendedId(baseUri, id), values, null, null)
                if (id == currentlyViewedId)
                    foundIt = true
            }
            //FIXME - this is needed, apparently, since i'm not using the same URI as the adapter uses?!
            loaderManager.restartLoader(0, arguments, mLoaderCallback)
            mAdapter!!.notifyDataSetChanged()
            return foundIt
        }

        fun messageUpdated(): Boolean {
            loaderManager.restartLoader(0, arguments, mLoaderCallback)
            return true
        }

        internal fun deselectAll() {
            listView.clearChoices()
            handleSelection(true, -1, false)
        }

        internal fun selectAll() {
            val count = listView.count
            for (i in 0..count - 1)
                listView.setItemChecked(i, true)
            listView.requestLayout() //FIXME - needed?
        }

        //This method queries the Adapter to determine which items are selected.
        //It then walks through the Cursor, adding to the return message a string
        //containing a message for each selected row in the Adapter.
        //FIXME - use getList()'s item's getText() instead of cursor?
        internal val selectedMessage: String
            get() {
                var ret = ""
                val mySelected = listView.checkedItemIds

                val cursor = mAdapter!!.cursor ?: return ret
                val idColumn = cursor.getColumnIndex("_id")
                val dateColumn = cursor.getColumnIndex(MileageData.ToDBNames[MileageData.DATE])
                val mpgColumn = cursor.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE])
                val prefs = mAdapter!!.getPrefs()
                for (i in 0..cursor.count - 1) {
                    cursor.moveToPosition(i)
                    val id = cursor.getLong(idColumn)
                    val str: String
                    for (currId in mySelected) {
                        if (currId == id) {
                            str = MileageData.getSimpleDescription(cursor, dateColumn, mpgColumn, prefs, activity)
                            if (ret.isNotEmpty())
                                ret = String.format("%s\n%s", ret, str)
                            else
                                ret = str
                            break
                        }
                    }
                }
                return ret
            }

        private inner class EditRecordModeListener : MultiChoiceModeListener {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val inflater = activity.menuInflater
                inflater.inflate(R.menu.edit_records_cab, menu)
                mode.title = "Select Records"
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                try {
                    val activity = activity as AppCompatActivity
                    val tb = activity.findViewById<View>(R.id.main_toolbar) as Toolbar
                    tb.visibility = View.GONE
                    //               tb.animate().translationY(-tb.getBottom()).setInterpolator(new AccelerateInterpolator()).start();
                } catch (e: ClassCastException) {
                    Log.e("TJS", "Invalid activity class: " + e)
                }

                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val activity = activity
                val fragment: DialogFragment
                when (item.itemId) {
                    R.id.delete_entry -> {
                        fragment = DeleteConfirmFragment.newInstance(selectedMessage)
                        fragment.show(getActivity().supportFragmentManager, "dialog")
                        return true
                    }
                    R.id.move_entry -> {
                        fragment = MoveDialogFragment.newInstance(selectedMessage)
                        fragment.show(getActivity().supportFragmentManager, "dialog")
                        return true
                    }
                    R.id.select_all -> {
                        selectAll()
                        return true
                    }
                    else -> {
                        Toast.makeText(activity, "'" + item.title + "' pressed...", Toast.LENGTH_LONG).show()
                        mode.finish()
                        return true
                    }
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                try {
                    val activity = activity as AppCompatActivity
                    val tb = activity.findViewById<View>(R.id.main_toolbar) as Toolbar
                    tb.visibility = View.VISIBLE
                    //Give a transition from off screen to the normal spot
                    tb.translationY = (-tb.bottom).toFloat()
                    tb.animate().translationY(0f).setInterpolator(AccelerateInterpolator()).start()
                } catch (e: ClassCastException) {
                    Log.e("TJS", "Invalid activity class: " + e)
                }

            }

            override fun onItemCheckedStateChanged(mode: ActionMode, position: Int, id: Long, checked: Boolean) {}
        }

        private inner class RecordListLoaderCallbacks : LoaderManager.LoaderCallbacks<Cursor> {

            override fun onCreateLoader(id: Int, args: Bundle): Loader<Cursor> {
                val uri = Uri.parse(args.getString("uri"))
                return CursorLoader(activity, uri, null, null, null, MileageProvider.defaultSort())
            }

            override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
                mAdapter!!.swapCursor(data)
            }

            override fun onLoaderReset(loader: Loader<Cursor>) {
                mAdapter!!.swapCursor(null)
            }

        }

        companion object {

            fun newInstance(c: Context, uri: Uri): EditRecordsMenuFragment {
                val result = EditRecordsMenuFragment()
                val args = Bundle()
                args.putString("uri", uri.toString())
                result.arguments = args
                return result
            }
        }
    }

    class DeleteConfirmFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val activity = activity as EditRecordsMenu
            val message = arguments.getString("selected")
            val acceptListener = OnClickListener { _, _ ->
                activity.deleteSelected()
                activity.deselectAll()
            }
            return AlertDialog.Builder(getActivity()).setTitle(R.string.confirm_delete).setMessage(message)
                    .setPositiveButton("Yes", acceptListener)
                    .setNegativeButton("No", activity.CancelClickListener())
                    .create()
        }

        companion object {
            fun newInstance(selected: String): DeleteConfirmFragment {
                val frag = DeleteConfirmFragment()
                val args = Bundle()
                args.putString("selected", selected)
                frag.arguments = args
                return frag
            }
        }
    }

    class MoveDialogFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val activity = activity as EditRecordsMenu
            @SuppressLint("InflateParams")
            val view = (activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.move_dialog, null)
            val message = arguments.getString("selected")
            val text = view.findViewById<TextView>(android.R.id.text1)
            text.text = message
            val newAdapter = ArrayAdapter<CharSequence>(activity, android.R.layout.simple_spinner_dropdown_item)
            //replace code with addAll once we drop support for pre-honeycomb devices!
            val cars = MileageProvider.getProfiles(activity)
            cars.forEach { car -> newAdapter.add(car) }
            val s = view.findViewById<Spinner>(R.id.move_to_spinner)
            s.adapter = newAdapter
            val acceptListener = OnClickListener { _, _ ->
                activity.moveSelected(s.selectedItem as String)
                activity.deselectAll()
            }
            return AlertDialog.Builder(getActivity()).setTitle(R.string.confirm_move).setView(view)
                    .setPositiveButton("Yes", acceptListener)
                    .setNegativeButton("No", activity.CancelClickListener())
                    .create()
        }

        companion object {
            fun newInstance(selected: String): MoveDialogFragment {
                val frag = MoveDialogFragment()
                val args = Bundle()
                args.putString("selected", selected)
                frag.arguments = args
                return frag
            }
        }
    }

    class ImportDialogFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val activity = activity as EditRecordsMenu
            val files = activity.csvFiles
            if (files != null) {

                @SuppressLint("InflateParams")
                val view = (activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.import_dialog, null)
                val filename = view.findViewById<Spinner>(R.id.file_list)
                val adapter = ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                filename.adapter = adapter
                for (file in files)
                    adapter.add(file)

                val importListener = OnClickListener { _, _ ->
                    // Upon the button being clicked, a new thread will be started, which imports the data into the database,
                    // and presents a progress dialog box to the user
                    val name = filename.selectedItem as String
                    dismiss()
                    activity.performImport(name)
                }
                return AlertDialog.Builder(activity).setTitle("Select Import File:").setView(view)
                        .setPositiveButton(R.string.confirm_import, importListener)
                        .setNegativeButton("Cancel", activity.CancelClickListener())
                        .create()
            } else { //FIXME - this will never fire! check files.length, and display an error message!
                return null!!
            }
        }

        companion object {
            fun newInstance(): ImportDialogFragment {
                return ImportDialogFragment()
            }
        }
    }

    class ExportDialogFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val filename: AutoCompleteTextView
            val activity = activity as EditRecordsMenu
            @SuppressLint("InflateParams")
            val view = (activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.export_dialog, null)
            filename = view.findViewById<AutoCompleteTextView>(R.id.file_list)
            var files = activity.csvFiles
            if (files == null)
                files = arrayOf<String>()
            val adapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, files)
            filename.setAdapter(adapter)

            val exportListener = OnClickListener { _, _ ->
                val name = filename.text.toString()
                dismiss()
                activity.performExport(name)
            }

            return AlertDialog.Builder(activity).setTitle("Select Export File:").setView(view)
                    .setPositiveButton(R.string.confirm_export, exportListener)
                    .setNegativeButton("Cancel", activity.CancelClickListener())
                    .create()
        }

        companion object {
            fun newInstance(): ExportDialogFragment {
                return ExportDialogFragment()
            }
        }
    }

    //simple wrapper to make instantiating 'cancel' buttons a bit easier
    private inner class CancelClickListener : OnClickListener {
        override fun onClick(dialog: DialogInterface, which: Int) {
            dialog.dismiss()
        }
    }

    override fun messageUpdated(): Boolean {
        var result = false
        mViewedRecordId = -1
        val fragment = supportFragmentManager.findFragmentByTag(LIST_FRAGMENT)
        if (fragment != null && fragment is EditRecordsMenuFragment)
            result = fragment.messageUpdated()
        return result
    }

    override fun onBackStackChanged() {
        val manager = supportFragmentManager
        val count = manager.backStackEntryCount
        if (count == 0)
            hideRecordView()
    }

    override fun onProfileChange(newProfile: String) {
        mProfileAdapter!!.applyPreferenceChange(newProfile)
        val fragment = supportFragmentManager.findFragmentByTag(LIST_FRAGMENT)
        //update fragment's URI, and reload List data
        if (fragment != null && fragment is EditRecordsMenuFragment) {
            fragment.arguments.putString("uri", uri.toString())
            fragment.messageUpdated()
        }
    }

    companion object {

        private val LIST_FRAGMENT = "recordList"
        private val RECORD_FRAGMENT = "recordViewer"

        private val SD_READ_REQ = 45
        private val SD_WRITE_REQ = 46
    }
}
