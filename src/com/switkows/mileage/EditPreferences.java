package com.switkows.mileage;

import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

public class EditPreferences extends PreferenceActivity {
   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preferences);
      // setTitle("MileageTracker Preferences");
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
         getActionBar().setDisplayHomeAsUpEnabled(true);
      }
   }
   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      switch(item.getItemId()) {
         case android.R.id.home: {
            finish();
         }
      }
      return false;
   }
   @Override
   protected void onResume() {
      super.onResume();
      //get the list of profiles from the Content Provider, and populate preference
      ListPreference list = (ListPreference)findPreference(getString(R.string.carSelection));
      CharSequence[] cars = MileageProvider.getProfiles(this);
      list.setEntries(cars);
      list.setEntryValues(cars);
   }
}
