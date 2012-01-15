package com.switkows.mileage;


import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

public class EditRecordListItem extends LinearLayout {
   public long        mIDValue;            //Database ID for this list item
   private TextView   mLabel;
   private CheckBox   mCheckBox;
   private Context    mContext;
   private EditRecordsListAdapter mAdapter;   //pointer to parent (so we can tell them we've been selected)

   private final OnClickListener mListener = new OnClickListener() {
      public void onClick(View v) {
         Uri uri = ContentUris.withAppendedId(MileageProvider.ALL_CONTENT_URI, mIDValue);
         mContext.startActivity(new Intent(Intent.ACTION_EDIT, uri));
         // Log.d("TJS","Clicked...");
      }
   };

   public EditRecordListItem(Context context, AttributeSet att) {
      super(context,att);
      mContext = context;
      setOnClickListener(mListener);
   }
   public EditRecordListItem(Context context) {
      super(context);
      mContext = context;
      setOnClickListener(mListener);
   }

   @Override
   public void onFinishInflate() {
      super.onFinishInflate();
      mLabel    = (TextView)findViewById(android.R.id.text1);
      mCheckBox = (CheckBox)findViewById(android.R.id.checkbox);

      mCheckBox.setOnClickListener(new OnClickListener() {
         public void onClick(View v) {
            updateSelected();
         }
      });
   }

   private void updateSelected() {
      if(mAdapter != null)
         mAdapter.setSelected(this,mCheckBox.isChecked());
   }

   public void bindAdapter(EditRecordsListAdapter ad) {
      mAdapter = ad;
   }

   public void setText(String text) {
      mLabel.setText(text);
   }

   public CharSequence getText() {
      return mLabel.getText();
   }

   public void setChecked(boolean checked) {
      // Log.d("TJS","ListViewItem.setChecked called with '"+checked+"'");
      mCheckBox.setChecked(checked);
   }

   public boolean isChecked() {
      return mCheckBox.isChecked();
   }
}
