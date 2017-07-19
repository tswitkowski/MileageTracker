package com.switkows.mileage

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.DialogInterface.OnClickListener
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast

//FIXME - add confirmation when deleting a profile which contains data
//FIXME - upon confirmation (above), delete data associated with profile
class EditProfiles : FragmentActivity() {
    private var mList: ListView? = null
    private var mListViewId: Int = 0

    private inner class Profile internal constructor(val id: Long, val name: String) {
        private var mHasItems: Boolean = false

        init {
            mHasItems = false
        }

        internal fun setHasItems(hasItems: Boolean) {
            mHasItems = hasItems
        }

        override fun toString(): String {
            var result = name
            if (mHasItems)
                result += "*"
            return result
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edit_profiles)
        mListViewId = android.R.layout.simple_list_item_activated_1

        findViewById<View>(R.id.add_profile_button).setOnClickListener {
            val fragment = CreateProfileDialogFragment.newInstance()
            fragment.show(supportFragmentManager, "dialog")
        }
    }

    override fun onResume() {
        val newAdapter = ArrayAdapter<Profile>(this, mListViewId)

        mList = findViewById<View>(android.R.id.list) as ListView
        mList!!.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        mList!!.adapter = newAdapter
        updateList()

        super.onResume()
    }

    private fun updateList() {
        val cursor = contentResolver.query(MileageProvider.CAR_PROFILE_URI, null, null, null, null)
        val adapter = mList!!.adapter as ArrayAdapter<Profile>
        if (cursor == null)
            return
        val content = arrayOfNulls<Profile>(cursor.count)
        val column = cursor.getColumnIndex(MileageProvider.PROFILE_NAME)
        val idColumn = cursor.getColumnIndex("_id")
        var u: Uri
        //      Toast.makeText(this, "Trying to create new ArrayAdapter", Toast.LENGTH_LONG).show();
        for (i in content.indices) {
            cursor.moveToPosition(i)
            content[i] = Profile(cursor.getLong(idColumn), cursor.getString(column))
            u = Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI, content[i]?.name)
            val c = contentResolver.query(u, arrayOf("_id"), null, null, null) ?: continue
            if (c.count > 0)
                content[i]?.setHasItems(true)
            c.close()
        }
        cursor.close()
        adapter.clear()
        content.forEach { item -> adapter.add(item) }
    }

    private fun deleteSelectedProfiles() {
        val sel = mList!!.checkedItemPositions
        var id: Long
        val arrayAdapter = mList!!.adapter as ArrayAdapter<Profile>
        for (index in 0..sel.size() - 1) {
            id = arrayAdapter.getItem(sel.keyAt(sel.indexOfValue(true)))!!.id
            contentResolver.delete(ContentUris.withAppendedId(MileageProvider.CAR_PROFILE_URI, id), null, null)
        }
        updateList()
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private fun renameSelectedProfile(newName: String) {
        val arrayAdapter = mList!!.adapter as ArrayAdapter<Profile>
        //save off the old value, so we can update records appropriately
        val sel = mList!!.checkedItemPositions
        val profile = arrayAdapter.getItem(sel.keyAt(sel.indexOfValue(true)))
        val oldId = profile!!.id
        val oldName = profile.name
        //get the new value
        val values = ContentValues()
        values.put(MileageProvider.PROFILE_NAME, newName)
        //update profile configs
        contentResolver.update(ContentUris.withAppendedId(MileageProvider.CAR_PROFILE_URI, oldId), values, null, null)
        //move all data to new profile name!
        contentResolver.update(MileageProvider.ALL_CONTENT_URI, values, MileageData.ToDBNames[MileageData.CAR] + "=?", arrayOf(oldName))
        Toast.makeText(applicationContext, "Successfully re-named $oldName to $newName", Toast.LENGTH_LONG).show()
        val option = getString(R.string.carSelection)
        val currentCar = PreferenceManager.getDefaultSharedPreferences(applicationContext).getString(option, "Car45")
        if (currentCar!!.compareTo(oldName) == 0)
            PreferenceManager.getDefaultSharedPreferences(applicationContext).edit().putString(option, newName).apply()
        updateList()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.edit_profiles_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var fragment: DialogFragment? = null
        when (item.itemId) {
            R.id.add_item -> fragment = CreateProfileDialogFragment.newInstance()
            R.id.delete_entry -> fragment = DeleteProfileDialogFragment.newInstance(selectedString)
            R.id.change_name -> {
                val sel = mList!!.checkedItemPositions
                val arrayAdapter = mList!!.adapter as ArrayAdapter<Profile>
                val selected = sel.indexOfValue(true)
                val name = if (selected >= 0) arrayAdapter.getItem(sel.keyAt(selected))?.name else null

                fragment = EditProfileDialogFragment.newInstance(name!!)
            }
        }
        if (fragment != null) {
            fragment.show(supportFragmentManager, "dialog")
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private val selectedString: String
        get() {
            val selected = mList!!.checkedItemPositions
            var str = ""
            val arrayAdapter = mList!!.adapter as ArrayAdapter<Profile>
            (0..selected.size() - 1)
                    .asSequence()
                    .filter { selected.valueAt(it) }
                    .map { arrayAdapter.getItem(selected.keyAt(it))?.toString() }
                    .forEach { str = String.format("%s\n%s", str, it) }
            return str
        }

    class CreateProfileDialogFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val activity = activity as EditProfiles
            val acceptListener = OnClickListener { dialog, _ ->
                val box = (dialog as AlertDialog).findViewById<View>(R.id.edit_text_box) as EditText
                val profileName = box.text.toString()
                MileageProvider.addProfile(activity, profileName)
                activity.updateList()
            }
            val editor = EditText(activity)
            editor.id = R.id.edit_text_box
            return AlertDialog.Builder(activity).setTitle("Enter new Profile name:").setView(editor)
                    .setPositiveButton("Add", acceptListener)
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create()
        }

        companion object {
            fun newInstance(): CreateProfileDialogFragment {
                return CreateProfileDialogFragment()
            }
        }
    }

    class DeleteProfileDialogFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val activity = activity as EditProfiles
            val message = arguments.getString("selected")
            return AlertDialog.Builder(activity).setTitle("Are you sure you want to delete the following Profiles:")
                    .setMessage(message)
                    .setPositiveButton("Delete") { _, _ -> activity.deleteSelectedProfiles() }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create()
        }

        companion object {
            fun newInstance(selected: String): DeleteProfileDialogFragment {
                val frag = DeleteProfileDialogFragment()
                val args = Bundle()
                args.putString("selected", selected)
                frag.arguments = args
                return frag
            }
        }
    }

    class EditProfileDialogFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val activity = activity as EditProfiles
            val name = arguments.getString("selected")
            if (name.isEmpty()) {
                return AlertDialog.Builder(activity).setTitle("Error")
                        .setMessage("Select a Profile first").create()
            }
            val editor = EditText(activity)
            editor.id = R.id.edit_text_box
            editor.setText(name)
            return AlertDialog.Builder(activity).setTitle("Enter modified Profile name:").setView(editor)
                    .setPositiveButton("Confirm") { dialog, _ ->
                        val box = (dialog as AlertDialog).findViewById<View>(R.id.edit_text_box) as EditText
                        val profileName = box.text.toString()
                        activity.renameSelectedProfile(profileName)
                    }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create()
        }

        companion object {
            fun newInstance(selected: String): EditProfileDialogFragment {
                val frag = EditProfileDialogFragment()
                val args = Bundle()
                args.putString("selected", selected)
                frag.arguments = args
                return frag
            }
        }
    }
}
