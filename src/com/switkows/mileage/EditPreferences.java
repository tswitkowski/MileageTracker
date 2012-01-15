package com.switkows.mileage;

import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

public class EditPreferences extends PreferenceActivity {
   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preferences);
      // setTitle("MileageTracker Preferences");
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
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
}
