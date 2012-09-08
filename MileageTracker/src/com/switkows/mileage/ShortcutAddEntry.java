package com.switkows.mileage;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
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

      if(Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
         setupShortcut();
         finish();
         return;
      }

//       String option = this.getString(R.string.carSelection);
//       String car = PreferenceManager.getDefaultSharedPreferences(this).getString(option, "Car45");
//       Uri uri = Uri.withAppendedPath(MileageProvider.CONTENT_URI, car);
//       startActivity(new Intent(Intent.ACTION_INSERT,uri));
//       finish();
      Log.e("TJS", "Error : ShortcutAddEntry called manually!");
   }

   private void setupShortcut() {
      // First, set up the shortcut intent.  For this example, we simply create an intent that
      // will bring us directly back to this activity.  A more typical implementation would use a
      // data Uri in order to display a more specific result, or a custom action in order to
      // launch a specific operation.

      String option = this.getString(R.string.carSelection);
      String car = PreferenceManager.getDefaultSharedPreferences(this).getString(option, "Car45");
      Uri uri = Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI, car);

      Intent shortcutIntent = new Intent(MileageTracker.ACTION_INSERT, uri);
      shortcutIntent.putExtra("starter", "MileageTracker Add Entry shortcut"); //value is don't care. this tells the EditRecord activity to return to the main MileageTracker activity
      shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);  //don't put this action in the 'recent apps' menu

      // Then, set up the container intent (the response to the caller)
      Intent intent = new Intent();
      intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
      intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, "Stop 4 gas");

      intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, overlay(R.drawable.mileage_tracker_icon,android.R.drawable.ic_menu_add));

      // Now, return the result to the launcher

      setResult(RESULT_OK, intent);
   }

   private Bitmap overlay(int main, int overlay) {
      Bitmap origIcon = BitmapFactory.decodeResource(getResources(), R.drawable.mileage_tracker_icon);
      //create a copy, so we can overlay the + sign
      Bitmap newIcon = origIcon.copy(origIcon.getConfig(), true);
      //assumes the app's icon is > 35x35, and is square
      float scale = ((float)35) / origIcon.getWidth(); //FIXME - do not hard-code?
      Bitmap origPlus = BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_menu_add);

      Canvas canvas = new Canvas(newIcon);
      Matrix m = new Matrix();
      m.reset();
      m.postScale(scale, scale);
      //put overlay icon on lower right-hand corner of main icon
      m.postTranslate(origIcon.getWidth() - 30, origIcon.getHeight() - 30);

      canvas.drawBitmap(origPlus, m, null);

      return newIcon;
   }
}
