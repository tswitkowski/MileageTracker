package com.switkows.mileage.widgets;

import android.content.Context;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;

import com.switkows.mileage.R;

/**
 * Created by Trevor on 12/28/2014.
 */
public class MainActionBar extends Toolbar {
   public MainActionBar(Context context, AttributeSet attr) {
      super(context, attr);
   }

   public MainActionBar(Context context) {
      super(context);
   }

   public MainActionBar(Context context, int menuResource) {
      super(context);
      if(menuResource >= 0)
         inflateMenu(menuResource);
//      setNavigationIcon(R.drawable.mileage_tracker_icon);
   }

//   public void createLayout(Context context) {
//      inflate(context, )
//   }

   public void setup(Context context) {
      setNavigationIcon(R.drawable.mileage_tracker_icon);
   }
}
