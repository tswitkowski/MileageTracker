package com.switkows.mileage;


import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.LinearLayout;
import android.widget.TextView;

public class EditRecordListItem extends LinearLayout implements Checkable {
   public long        mIDValue;            //Database ID for this list item
   public int         mPosition;           //list position
   private TextView   mLabel;
   private CheckBox   mCheckBox;
   private EditRecordsListAdapter mAdapter;   //pointer to parent (so we can tell them we've been selected)

   public EditRecordListItem(Context context, AttributeSet att) {
      super(context,att);
   }
   public EditRecordListItem(Context context) {
      super(context);
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
         mAdapter.setSelected(mIDValue,mPosition,mCheckBox.isChecked());
      mCheckBox.toggle();
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
   public void toggle() {
      setChecked(!isChecked());
   }
}
