package com.switkows.mileage;

import java.util.HashSet;

import com.switkows.mileage.EditRecordsMenu.EditRecordsMenuFragment;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

class EditRecordsListAdapter extends SimpleCursorAdapter {

   /** Remember our context so we can use it when constructing views. */
   private final Context                 mContext;
   private final LayoutInflater          mInflater;
   private int                           idColumn;
   private int                           mileageColumn;
   private int                           dateColumn;
   private long                          mViewedId;
   private final EditRecordsMenuFragment mParent;

   /**
    * @param context
    *           - Render context
    */
   EditRecordsListAdapter(Context context, EditRecordsMenuFragment parent, Cursor c, String[] from, int[] to) {
      super(context, R.layout.record_list_item, c, from, to, NO_SELECTION);
      mContext = context;
      mParent  = parent;
      mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      if(c != null) {
         idColumn       = c.getColumnIndex("_id");
         dateColumn     = c.getColumnIndex(MileageData.ToDBNames[MileageData.DATE]);
         mileageColumn  = c.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE]);
      } else {
         idColumn = dateColumn = mileageColumn = -1;
      }
      mViewedId = -1;
   }

   @Override
   public Cursor swapCursor(Cursor c) {
      if(c != null) {
         idColumn       = c.getColumnIndex("_id");
         dateColumn     = c.getColumnIndex(MileageData.ToDBNames[MileageData.DATE]);
         mileageColumn  = c.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE]);
      }
      return super.swapCursor(c);
   }

   /**
    * Do not recycle a view if one is already there, if not the data could get corrupted and the checkbox state could be
    * lost.
    * 
    * @param convertView
    *           The old view to overwrite
    * @return a EditRecordListItem corresponding with the cursor position
    */

   @Override
   public View getView(int position, View convertView, ViewGroup parent) {
      EditRecordListItem view;
      if(convertView == null) {
         convertView = mInflater.inflate(R.layout.record_list_item, parent, false);
         ((EditRecordListItem)convertView).bindAdapter(this);
      }
      view = (EditRecordListItem)convertView;
      Cursor cursor = getCursor();
      cursor.moveToPosition(position);
      view.mIDValue = cursor.getLong(idColumn);
      view.mPosition = position;
//      String date = MileageData.getDateFormatter().format(cursor.getLong(dateColumn));
//      float mpg = cursor.getFloat(mileageColumn);
//      mpg = MileageData.getEconomy(mpg, getPrefs(), mContext);
//      view.setText(String.format("%s (%2.1f %s)", date, mpg, MileageData.getEconomyUnits(getPrefs(), mContext)));
      view.setText(MileageData.getSimpleDescription(cursor, dateColumn, mileageColumn, getPrefs(), mContext));
      view.setChecked(mSelected.contains(view.mIDValue));
      //FIXME - can't the ListView do this automatically?
      //set the 'activated' state, which will cause the UI to add special styling in dualPane mode
      setListActivatedHoneycomb(view);

      return convertView;
   }

   @TargetApi(Build.VERSION_CODES.HONEYCOMB)
   private void setListActivatedHoneycomb(EditRecordListItem v) {
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
         v.setActivated(mViewedId >= 0 && v.mIDValue == mViewedId);
   }

   private SharedPreferences prefs;

   private final HashSet<Long> mSelected = new HashSet<>();

   SharedPreferences getPrefs() {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      return prefs;
   }

   void setSelected(long id, int position, boolean isSelected) {
      Long lID = id;
      if(isSelected)
         mSelected.add(lID);
      else
         mSelected.remove(lID);
      mParent.handleSelection(mSelected.isEmpty(), position, isSelected);
   }

   void setViewedItem(long id) {
      mViewedId = id;
   }
}
