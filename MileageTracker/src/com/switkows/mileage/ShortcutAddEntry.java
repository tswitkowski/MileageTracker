package com.switkows.mileage;


import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;

public class ShortcutAddEntry extends Activity {

   @Override
   public void onCreate(Bundle icicle) {
       super.onCreate(icicle);

       // Resolve the intent

       final Intent intent = getIntent();
       final String action = intent.getAction();

       // If the intent is a request to create a shortcut, we'll do that and exit

       if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
           setupShortcut();
           finish();
           return;
       }

//       String option = this.getString(R.string.carSelection);
//       String car = PreferenceManager.getDefaultSharedPreferences(this).getString(option, "Car45");
//       Uri uri = Uri.withAppendedPath(MileageProvider.CONTENT_URI, car);
//       startActivity(new Intent(Intent.ACTION_INSERT,uri));
//       finish();
       Log.d("TJS","Error : ShortcutAddEntry called manually!");
   }

   private void setupShortcut() {
      // First, set up the shortcut intent.  For this example, we simply create an intent that
      // will bring us directly back to this activity.  A more typical implementation would use a 
      // data Uri in order to display a more specific result, or a custom action in order to 
      // launch a specific operation.

      String option = this.getString(R.string.carSelection);
      String car = PreferenceManager.getDefaultSharedPreferences(this).getString(option, "Car45");
      Uri uri = Uri.withAppendedPath(MileageProvider.CONTENT_URI, car);

      Intent shortcutIntent = new Intent(Intent.ACTION_INSERT,uri);
//      shortcutIntent.setClassName(this, this.getClass().getName());
      shortcutIntent.putExtra("starter", "MileageTracker Add Entry shortcut");
      shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

      // Then, set up the container intent (the response to the caller)

      Intent intent = new Intent();
      intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
      intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, "Add Entry");
      Parcelable iconResource = Intent.ShortcutIconResource.fromContext(
              this,  R.drawable.mileage_tracker_icon);
      intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

      // Now, return the result to the launcher

      setResult(RESULT_OK, intent);
  }
}
