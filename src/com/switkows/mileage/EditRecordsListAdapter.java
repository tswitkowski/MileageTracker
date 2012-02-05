package com.switkows.mileage;

import java.util.HashSet;

import com.switkows.mileage.EditRecordsMenu.EditRecordsMenuFragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
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
   private final int mileageColumn;
   private final int dateColumn;
   private long      mViewedId;
   private final EditRecordsMenuFragment mParent;

   /**
    *
    * @param context
    *           - Render context
    */
   public EditRecordsListAdapter(Context context, EditRecordsMenuFragment parent, Cursor c, String[] from, int[] to) {
      super(context, R.layout.record_list_item, c, from, to);
      mContext = context;
      mParent  = parent;
      mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      idColumn       = c.getColumnIndex("_id");
      dateColumn     = c.getColumnIndex(MileageData.ToDBNames[MileageData.DATE]);
      mileageColumn  = c.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE]);
      mViewedId      = -1;
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
      view.mPosition = position;
//      String date = MileageData.getDateFormatter().format(cursor.getLong(dateColumn));
//      float mpg = cursor.getFloat(mileageColumn);
//      mpg = MileageData.getEconomy(mpg, getPrefs(), mContext);
//      view.setText(String.format("%s (%2.1f %s)", date, mpg, MileageData.getEconomyUnits(getPrefs(), mContext)));
      view.setText(MileageData.getSimpleDescription(cursor, dateColumn, mileageColumn, getPrefs(), mContext));
      view.setChecked(mSelected.contains(Long.valueOf(view.mIDValue)));
      //FIXME - can't the ListView do this automatically?
      //set the 'activated' state, which will cause the UI to add special styling in dualPane mode
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
         view.setActivated(mViewedId >= 0 && view.mIDValue==mViewedId);

      return convertView;
   }
   private SharedPreferences prefs;

   private HashSet<Long> mSelected = new HashSet<Long>();

   public SharedPreferences getPrefs() {
      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      return prefs;
   }

   public void setSelected(long id, int position, boolean isSelected) {
      Long lID = Long.valueOf(id);
      if(isSelected)
         mSelected.add(lID);
      else
         mSelected.remove(lID);
      mParent.handleSelection(mSelected.isEmpty(),position,isSelected);
   }

   public void clearSelected() {
      mSelected.clear();
   }

   public void setViewedItem(long id) {
      mViewedId = id;
   }
}
