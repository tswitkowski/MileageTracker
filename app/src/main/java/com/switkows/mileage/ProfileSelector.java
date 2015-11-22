package com.switkows.mileage;

import android.annotation.TargetApi;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.OnNavigationListener;
import android.support.v7.app.AppCompatActivity;

class ProfileSelector extends SimpleCursorAdapter implements OnNavigationListener {
   private final AppCompatActivity  mContext;
   private ProfileSelectorCallbacks mCallback;

   interface ProfileSelectorCallbacks {
      void onProfileChange(String newProfile);
   }

   /**
    * Bind Profile names to Action Bar's drop down list
    * 
    * @author Trevor
    */
   private ProfileSelector(AppCompatActivity context) {
      super(context, android.R.layout.simple_spinner_dropdown_item, null, new String[] {MileageProvider.PROFILE_NAME}, new int[] {android.R.id.text1},
            NO_SELECTION);

      mContext = context;
      if(context instanceof ProfileSelectorCallbacks)
         mCallback = (ProfileSelectorCallbacks)context;
      updateCursor();
   }

   private void updateCursor() {
      Cursor c = mContext.getContentResolver().query(MileageProvider.CAR_PROFILE_URI, null, null, null, null);
      updateCursor(c);
   }

   private void updateCursor(Cursor c) {
      swapCursor(c);
      //modify the cursor to position at the appropriate point
      getSelectedPosition();
   }

   private int getSelectedPosition() {
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

   private String getCurrentProfile() {
      String option = mContext.getString(R.string.carSelection);
      return PreferenceManager.getDefaultSharedPreferences(mContext).getString(option, "Car45");
   }

   /**
    * Handle changes to Action Bar's drop down list change
    * 
    * @author Trevor
    */
   @TargetApi(Build.VERSION_CODES.GINGERBREAD)
   public boolean onNavigationItemSelected(int itemPosition, long itemId) {
      String profile = getItemName(itemPosition);
      if(mCallback != null)
         mCallback.onProfileChange(profile);
      return true;
   }

   private String getItemName(int position) {
      Cursor c = getCursor();
      c.moveToPosition(position);
      return c.getString(c.getColumnIndex(MileageProvider.PROFILE_NAME));
   }

   @TargetApi(Build.VERSION_CODES.GINGERBREAD)
   void applyPreferenceChange(String newProfile) {
      Editor prefs = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
      String option = mContext.getString(R.string.carSelection);
      prefs.putString(option, newProfile);
      prefs.apply();
   }

   @TargetApi(Build.VERSION_CODES.HONEYCOMB)
   static ProfileSelector setupActionBar(AppCompatActivity context, ProfileSelectorCallbacks callbacks) {
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
         ActionBar bar = context.getSupportActionBar();
         if(bar != null) {
            bar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_USE_LOGO);
            bar.setLogo(R.drawable.mileage_tracker_icon);
            ProfileSelector selector = new ProfileSelector(context);
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
    * @param context - The AppCompatActivity containing the Action Bar
    */
   @TargetApi(Build.VERSION_CODES.HONEYCOMB)
   private void updateActionBarSelectorPosition(AppCompatActivity context) {
      ActionBar bar = context.getSupportActionBar();
      if (bar != null) {
         bar.setSelectedNavigationItem(getSelectedPosition());
      }
   }

   public void loadActionBarNavItems(AppCompatActivity context) {
      updateCursor();
      updateActionBarSelectorPosition(context);
   }
}
