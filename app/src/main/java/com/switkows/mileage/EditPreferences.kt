package com.switkows.mileage

import android.os.Bundle
import android.preference.ListPreference
import android.preference.PreferenceFragment
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem

class EditPreferences : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureActionBar()
        fragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }
        }
        return false
    }

    private fun configureActionBar() {
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.preferences)
            // setTitle("MileageTracker Preferences");
        }

        override fun onResume() {
            super.onResume()
            //get the list of profiles from the Content Provider, and populate preference
            val list = findPreference(getString(R.string.carSelection)) as ListPreference
            val cars = MileageProvider.getProfiles(activity)
            list.entries = cars
            list.entryValues = cars
        }


    }
}
