package com.switkows.mileage;

import android.annotation.TargetApi;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar.OnNavigationListener;

public class ProfileSelector extends SimpleCursorAdapter implements OnNavigationListener {
   private final ActionBarActivity  mContext;
   private ProfileSelectorCallbacks mCallback;

   interface ProfileSelectorCallbacks {
      public void onProfileChange(String newProfile);
   }

   /**
    * Bind Profile names to Action Bar's drop down list
    * 
    * @author Trevor
    */
   public ProfileSelector(ActionBarActivity context, int layout, Cursor c, String[] from, int[] to) {
      super(context, android.R.layout.simple_spinner_dropdown_item, null, new String[] {MileageProvider.PROFILE_NAME}, new int[] {android.R.id.text1},
            NO_SELECTION);

      mContext = context;
      if(context instanceof ProfileSelectorCallbacks)
         mCallback = (ProfileSelectorCallbacks)context;
      updateCursor();
   }

   public void updateCursor() {
      Cursor c = mContext.getContentResolver().query(MileageProvider.CAR_PROFILE_URI, null, null, null, null);
      updateCursor(c);
   }

   public void updateCursor(Cursor c) {
      swapCursor(c);
      //modify the cursor to position at the appropriate point
      getSelectedPosition();
   }

   protected int getSelectedPosition() {
      Cursor cursor = getCursor();
      String currentProfile = getCurrentProfile();
      int columnIndex = cursor.getColumnIndex(MileageProvider.PROFILE_NAME);
      for(int i = 0; i < cursor.getCount(); i++) {
         cursor.moveToPosition(i);
         if(cursor.getString(columnIndex).equalsIgnoreCase(currentProfile))
            break;
      }
      return cursor.getPosition();
   }

   public String getCurrentProfile() {
      String option = mContext.getString(R.string.carSelection);
      String car = PreferenceManager.getDefaultSharedPreferences(mContext).getString(option, "Car45");
      return car;
   }

   /**
    * Handle changes to Action Bar's drop down list change
    * 
    * @author Trevor
    */
   @TargetApi(Build.VERSION_CODES.GINGERBREAD)
   public boolean onNavigationItemSelected(int itemPosition, long itemId) {
      String profile = getItemName(itemPosition);
      if(mCallback instanceof ProfileSelectorCallbacks)
         ((ProfileSelectorCallbacks)mCallback).onProfileChange(profile);
      return true;
   }

   public String getItemName(int position) {
      Cursor c = getCursor();
      c.moveToPosition(position);
      String profile = c.getString(c.getColumnIndex(MileageProvider.PROFILE_NAME));
      return profile;
   }

   @TargetApi(Build.VERSION_CODES.GINGERBREAD)
   public void applyPreferenceChange(String newProfile) {
      Editor prefs = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
      String option = mContext.getString(R.string.carSelection);
      prefs.putString(option, newProfile);
      prefs.apply();
   }

   @TargetApi(Build.VERSION_CODES.HONEYCOMB)
   public static ProfileSelector setupActionBar(ActionBarActivity context, ProfileSelectorCallbacks callbacks) {
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
         ActionBar bar = context.getSupportActionBar();
         if(bar != null) {
            bar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_USE_LOGO);
            bar.setLogo(R.drawable.mileage_tracker_icon);
            ProfileSelector selector = new ProfileSelector(context, 0, null, null, null);
            if(callbacks != null)
               selector.mCallback = callbacks;
            bar.setListNavigationCallbacks(selector, selector);
            bar.setSelectedNavigationItem(selector.getSelectedPosition());
            return selector;
         }
      }
      return null;
   }

   /**
    * Update the Action Bar's navigation list to the correct
    * position (the currently selected Profile name)
    * @param context - The ActionBarActivity containing the Action Bar
    */
   @TargetApi(Build.VERSION_CODES.HONEYCOMB)
   public void updateActionBarSelectorPosition(ActionBarActivity context) {
      ActionBar bar = context.getSupportActionBar();
      bar.setSelectedNavigationItem(getSelectedPosition());
   }

   public void loadActionBarNavItems(ActionBarActivity context) {
      updateCursor();
      updateActionBarSelectorPosition(context);
   }
}
