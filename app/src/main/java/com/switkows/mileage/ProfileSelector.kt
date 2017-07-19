package com.switkows.mileage

import android.database.Cursor
import android.preference.PreferenceManager
import android.support.v4.widget.SimpleCursorAdapter
import android.support.v7.app.ActionBar
import android.support.v7.app.ActionBar.OnNavigationListener
import android.support.v7.app.AppCompatActivity

internal class ProfileSelector
/**
 * Bind Profile names to Action Bar's drop down list

 * @author Trevor
 */
private constructor(private val myContext: AppCompatActivity) : SimpleCursorAdapter(myContext, android.R.layout.simple_spinner_dropdown_item, null, arrayOf(MileageProvider.PROFILE_NAME), intArrayOf(android.R.id.text1), NO_SELECTION), OnNavigationListener {
    private var mCallback: ProfileSelectorCallbacks? = null

    internal interface ProfileSelectorCallbacks {
        fun onProfileChange(newProfile: String)
    }

    init {
        if (myContext is ProfileSelectorCallbacks)
            mCallback = myContext
        updateCursor()
    }

    private fun updateCursor() {
        val c = myContext.contentResolver.query(MileageProvider.CAR_PROFILE_URI, null, null, null, null)
        updateCursor(c)
    }

    private fun updateCursor(c: Cursor) {
        swapCursor(c)
        //modify the cursor to position at the appropriate point
        selectedPosition
    }

    private val selectedPosition: Int
        get() {
            val cursor = cursor
            val currentProfile = currentProfile
            val columnIndex = cursor.getColumnIndex(MileageProvider.PROFILE_NAME)
            for (i in 0..cursor.count - 1) {
                cursor.moveToPosition(i)
                if (cursor.getString(columnIndex).equals(currentProfile, ignoreCase = true))
                    break
            }
            return cursor.position
        }

    private val currentProfile: String
        get() {
            val option = myContext.getString(R.string.carSelection)
            return PreferenceManager.getDefaultSharedPreferences(myContext).getString(option, "Car45")
        }

    /**
     * Handle changes to Action Bar's drop down list change

     * @author Trevor
     */
    override fun onNavigationItemSelected(itemPosition: Int, itemId: Long): Boolean {
        val profile = getItemName(itemPosition)
        if (mCallback != null)
            mCallback!!.onProfileChange(profile)
        return true
    }

    private fun getItemName(position: Int): String {
        val c = cursor
        c.moveToPosition(position)
        return c.getString(c.getColumnIndex(MileageProvider.PROFILE_NAME))
    }

    fun applyPreferenceChange(newProfile: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(myContext).edit()
        val option = myContext.getString(R.string.carSelection)
        prefs.putString(option, newProfile)
        prefs.apply()
    }

    /**
     * Update the Action Bar's navigation list to the correct
     * position (the currently selected Profile name)
     * @param context - The AppCompatActivity containing the Action Bar
     */
    private fun updateActionBarSelectorPosition(context: AppCompatActivity) {
        val bar = context.supportActionBar
        bar?.setSelectedNavigationItem(selectedPosition)
    }

    fun loadActionBarNavItems(context: AppCompatActivity) {
        updateCursor()
        updateActionBarSelectorPosition(context)
    }

    companion object {
        fun setupActionBar(context: AppCompatActivity, callbacks: ProfileSelectorCallbacks?): ProfileSelector? {
            val bar = context.supportActionBar
            if (bar != null) {
                bar.navigationMode = ActionBar.NAVIGATION_MODE_LIST
                bar.displayOptions = ActionBar.DISPLAY_SHOW_HOME or ActionBar.DISPLAY_USE_LOGO
                bar.setLogo(R.drawable.mileage_tracker_icon)
                val selector = ProfileSelector(context)
                if (callbacks != null)
                    selector.mCallback = callbacks
                bar.setListNavigationCallbacks(selector, selector)
                bar.setSelectedNavigationItem(selector.selectedPosition)
                return selector
            }
            return null
        }
    }
}
