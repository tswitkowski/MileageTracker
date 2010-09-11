package com.switkows.mileage;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class EditRecordsListAdapter extends SimpleCursorAdapter {

   /** Remember our context so we can use it when constructing views. */
   private Context mContext;

   /**
    * 
    * @param context
    *           - Render context
    */
   public EditRecordsListAdapter(Context context, Cursor c, String[] from, int[] to) {
      super(context, R.layout.record_list_item, c, from, to);
      mContext = context;
   }

   // re-generates all displays
   public void invalidate() {
      mDisplays = null;
   }

   @Override
   public void onContentChanged() {
      super.onContentChanged();
      mDisplays = null;
   }

   /**
    * Do not recycle a view if one is already there, if not the data could get corrupted and the checkbox state could be
    * lost.
    * 
    * @param convertView
    *           The old view to overwrite
    * @returns a CheckBoxifiedTextView that holds wraps around an CheckBoxifiedText
    */
   private MyListViewItem[] mDisplays;

   @Override
   public View getView(int position, View convertView, ViewGroup parent) {
      if(mDisplays != null && (mDisplays.length != getCursor().getCount())) // if the array size doesn't match the
         // database, reset everything!
         mDisplays = null;
      if(mDisplays == null) {
         // Log.v("TJS","Creating array...");
         mDisplays = new MyListViewItem[getCursor().getCount()];
         // Log.d("TJS","Creating array with "+mDisplays.length+" entries...");
      }
      // Log.d("TJS","Trying to retrieve list item "+position+" (cursor contains "+getCursor().getCount()+" elements)");
      if(mDisplays[position] == null) {
         Cursor cursor = getCursor();
         cursor.moveToPosition(position);
         String date = MileageData.getDateFormatter().format(
               cursor.getLong(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.DATE])));
         float mpg = cursor.getFloat(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE]));
         mpg = MileageData.getEconomy(mpg, getPrefs(), mContext);
         // Log.v("TJS","creating row "+position);
         mDisplays[position] = new MyListViewItem(mContext, new MyListItem(String.format("%s (%2.1f %s)", date, mpg,
               MileageData.getEconomyUnits(getPrefs(), mContext)), false));
      }
      mDisplays[position].refresh();
      return mDisplays[position];
   }

   private SharedPreferences prefs;

   public SharedPreferences getPrefs() {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      return prefs;
   }

   protected class MyListItem implements Comparable<MyListItem> {

      private String  mLabel;
      private boolean mChecked;

      public MyListItem(String label, boolean checked) {
         mLabel = label;
         mChecked = checked;
      }

      public void setChecked(boolean checked) {
         mChecked = checked;
         // Log.d("TJS","Setting checked state to '"+checked+"'...");
      }

      public boolean getChecked() {
         return mChecked;
      }

      public void setText(String text) {
         mLabel = text;
      }

      public String getText() {
         return mLabel;
      }

      public int compareTo(MyListItem another) {
         if(mLabel != null)
            return mLabel.compareTo(another.getText());
         else
            return 0;
      }
   }

   protected class MyListViewItem extends LinearLayout implements Checkable {
      private TextView   mLabel;
      private CheckBox   mCheckBox;
      private MyListItem data;

      public MyListViewItem(Context context, MyListItem item) {
         super(context);
         mLabel = new TextView(context);
         mCheckBox = new CheckBox(context);
         addView(mLabel);
         addView(mCheckBox);
         data = item;
         mLabel.setText(data.getText());
         LayoutParams params = new LayoutParams(android.view.ViewGroup.LayoutParams.FILL_PARENT,
               android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
         mLabel.setLayoutParams(params);
         mCheckBox.setChecked(data.getChecked());
         mCheckBox.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
               data.setChecked(isChecked());
            }
         });
         params = new LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
               android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 0);
         mCheckBox.setLayoutParams(params);

         setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
               showContextMenu();
               // Log.d("TJS","Clicked...");
            }
         });
      }

      public void setText(String text) {
         mLabel.setText(text);
         data.setText(text);
      }

      public void toggle() {
         // Log.d("TJS","ListviewItem.toggle called...");
         setChecked(!isChecked());
      }

      public void setChecked(boolean checked) {
         // Log.d("TJS","ListViewItem.setChecked called with '"+checked+"'");
         data.setChecked(checked);
         mCheckBox.setChecked(checked);
      }

      public boolean isChecked() {
         return mCheckBox.isChecked();
      }

      public void refresh() {
         // Log.v("TJS","refreshing row. checked="+data.getChecked());
         mCheckBox.setChecked(data.getChecked());
         mCheckBox.postInvalidate();
         // Log.v("TJS","data.checked="+data.getChecked()+", checkbox.checked="+mCheckBox.isChecked());
      }
   }

}
