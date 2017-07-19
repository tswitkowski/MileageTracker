package com.switkows.mileage;

import android.app.ActionBar;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

public class EditPreferences extends AppCompatActivity {

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      configureActionBar();
      getFragmentManager().beginTransaction()
            .replace(android.R.id.content, new SettingsFragment())
            .commit();
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
         case android.R.id.home: {
            finish();
         }
      }
      return false;
   }

   private void configureActionBar() {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
   }

   public static class SettingsFragment extends PreferenceFragment {
      @Override
      public void onCreate(Bundle savedInstanceState) {
         super.onCreate(savedInstanceState);
         addPreferencesFromResource(R.xml.preferences);
         // setTitle("MileageTracker Preferences");
      }

      @Override
      public void onResume() {
         super.onResume();
         //get the list of profiles from the Content Provider, and populate preference
         ListPreference list = (ListPreference) findPreference(getString(R.string.carSelection));
         CharSequence[] cars = MileageProvider.Companion.getProfiles(getActivity());
         list.setEntries(cars);
         list.setEntryValues(cars);
      }


   }
}
