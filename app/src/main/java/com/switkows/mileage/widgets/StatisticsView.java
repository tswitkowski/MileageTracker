package com.switkows.mileage.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
* Created by Trevor on 1/1/2015.
*/
public class StatisticsView extends RelativeLayout {

   private TextView mLabel;
   private TextView mValue;

   public StatisticsView(Context context, AttributeSet set) {
      super(context, set);
   }

   @Override
   public void onFinishInflate() {
      super.onFinishInflate();
      mLabel = (TextView)findViewById(android.R.id.text1);
      mValue = (TextView)findViewById(android.R.id.text2);
   }

   public void setLabel(String label) {
      mLabel.setText(label);
   }

   public void setValue(float value, String units) {
      setValue(String.format("%.1f %s", value, units));
   }

   public void setValuePercent(float value, String units) {
      setValue(String.format("%.2f%s", value, units));
   }

   public void setValue(String str) {
      mValue.setText(str);
   }
}
