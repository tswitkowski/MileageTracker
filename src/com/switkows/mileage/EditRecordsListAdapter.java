package com.switkows.mileage;

import java.util.HashSet;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;

public class EditRecordsListAdapter extends SimpleCursorAdapter {

   /** Remember our context so we can use it when constructing views. */
   private Context mContext;
   private final LayoutInflater mInflater;
   private final int idColumn;
   private final EditRecordsMenu mParent;

   /**
    *
    * @param context
    *           - Render context
    */
   public EditRecordsListAdapter(Context context, EditRecordsMenu parent, Cursor c, String[] from, int[] to) {
      super(context, R.layout.record_list_item, c, from, to);
      mContext = context;
      mParent  = parent;
      mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      idColumn = c.getColumnIndex("_id");
   }


   /**
    * Do not recycle a view if one is already there, if not the data could get corrupted and the checkbox state could be
    * lost.
    *
    * @param convertView
    *           The old view to overwrite
    * @returns a CheckBoxifiedTextView that holds wraps around an CheckBoxifiedText
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
      String date = MileageData.getDateFormatter().format(
         cursor.getLong(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.DATE])));
      float mpg = cursor.getFloat(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE]));
      mpg = MileageData.getEconomy(mpg, getPrefs(), mContext);
      view.setText(String.format("%s (%2.1f %s)", date, mpg, MileageData.getEconomyUnits(getPrefs(), mContext)));
      view.setChecked(mSelected.contains(Long.valueOf(view.mIDValue)));

      return convertView;
   }
   private SharedPreferences prefs;

   private HashSet<Long> mSelected = new HashSet<Long>();

   public SharedPreferences getPrefs() {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      return prefs;
   }

   public void setSelected(EditRecordListItem item, boolean isSelected) {
      Long id = Long.valueOf(item.mIDValue);
      if(isSelected)
         mSelected.add(id);
      else
         mSelected.remove(id);

      mParent.handleSelection(mSelected.isEmpty());
   }

   public void clearSelected() {
      mSelected.clear();
      this.notifyDataSetChanged();
   }

   public HashSet<Long> getSelected() {
      return mSelected;
   }
}
