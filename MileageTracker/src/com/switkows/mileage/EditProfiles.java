package com.switkows.mileage;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class EditProfiles extends Activity {
   private final static int DELETE_PROFILE_DIALOG = 0,
                            ADD_PROFILE_DIALOG    = 1,
                            EDIT_PROFILE_DIALOG   = 2;
   private final static int EDIT_TEXT_BOX         = 45;

   private ListView mList;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.edit_profiles);
      Cursor cursor = getContentResolver().query(MileageProvider.CAR_PROFILE_URI, null, null, null, null);

      SimpleCursorAdapter adapter;
      if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
         adapter = new SimpleCursorAdapter(this,android.R.layout.simple_list_item_multiple_choice,cursor, new String[] {"carName"},new int[] {android.R.id.text1});
      else
         adapter = new SimpleCursorAdapter(this,android.R.layout.simple_list_item_activated_1,cursor, new String[] {"carName"},new int[] {android.R.id.text1},SimpleCursorAdapter.FLAG_AUTO_REQUERY);
      mList = (ListView)findViewById(android.R.id.list);
      mList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
      mList.setAdapter(adapter);
      
      findViewById(R.id.add_profile_button).setOnClickListener(new View.OnClickListener() {
         public void onClick(View v) {
            showDialog(ADD_PROFILE_DIALOG);
         }
      });
   }
   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.edit_profiles_menu, menu);
      return true;
   }
   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
         case R.id.add_item:
            showDialog(ADD_PROFILE_DIALOG);
            return true;
         case R.id.delete_entry:
            showDialog(DELETE_PROFILE_DIALOG);
            return true;
         case R.id.change_name:
            showDialog(EDIT_PROFILE_DIALOG);
            return true;
      }
      return super.onOptionsItemSelected(item);
   }
   @Override
   protected Dialog onCreateDialog(int id) {
      AlertDialog.Builder builder;
      EditText editor;
      switch(id) {
         case DELETE_PROFILE_DIALOG:
            //FIXME - add check to make sure profile has no data. if it does, offer to move data to another profile
            builder = new AlertDialog.Builder(this);
            builder.setTitle("Are you sure you want to delete the following Profiles:");
            builder.setMessage(getSelectedString());
            builder.setPositiveButton("Delete", new OnClickListener() {
               public void onClick(DialogInterface dialog, int which) {
                  long[] items = mList.getCheckedItemIds();
                  for(long id : items)
                     getContentResolver().delete(ContentUris.withAppendedId(MileageProvider.CAR_PROFILE_URI,id),null,null);
               }
            });
            builder.setNegativeButton("Cancel", new OnClickListener() {
               public void onClick(DialogInterface dialog, int which) {
                  dialog.dismiss();
               }
            });
            return builder.create();
         case ADD_PROFILE_DIALOG:
            builder = new AlertDialog.Builder(this);
            editor = new EditText(this);
            editor.setId(EDIT_TEXT_BOX);
            builder.setView(editor);
            builder.setTitle("Enter new Profile name:");
            builder.setPositiveButton("Add", new OnClickListener() {
               public void onClick(DialogInterface dialog, int which) {
                  EditText box = (EditText)((AlertDialog)dialog).findViewById(EDIT_TEXT_BOX);
                  String profileName = box.getText().toString();
                  MileageProvider.addProfile(EditProfiles.this, profileName);
               }
            });
            builder.setNegativeButton("Cancel", new OnClickListener() {
               public void onClick(DialogInterface dialog, int which) {
                  dialog.dismiss();
               }
            });
            return builder.create();
         case EDIT_PROFILE_DIALOG:
            builder = new AlertDialog.Builder(this);
            editor = new EditText(this);
            editor.setId(EDIT_TEXT_BOX);
            SparseBooleanArray sel = mList.getCheckedItemPositions();
            Cursor cursor = ((SimpleCursorAdapter)mList.getAdapter()).getCursor();
            cursor.moveToPosition(sel.keyAt(0));
            editor.setText(cursor.getString(1));
            builder.setView(editor);
            builder.setTitle("Enter modified Profile name:");
            builder.setPositiveButton("Confirm", new OnClickListener() {
               public void onClick(DialogInterface dialog, int which) {
                  //save off the old value, so we can update records appropriately
                  SparseBooleanArray sel = mList.getCheckedItemPositions();
                  Cursor cursor = ((SimpleCursorAdapter)mList.getAdapter()).getCursor();
                  cursor.moveToPosition(sel.keyAt(0));
                  final long oldId = cursor.getLong(0);
                  String oldName   = cursor.getString(1);
                  //get the new value
                  EditText box = (EditText)((AlertDialog)dialog).findViewById(EDIT_TEXT_BOX);
                  String profileName = box.getText().toString();
//                  MileageProvider.addProfile(EditProfiles.this, profileName);
                  ContentValues values = new ContentValues();
                  values.put("carName", profileName);
                  //update profile configs
                  getContentResolver().update(ContentUris.withAppendedId(MileageProvider.CAR_PROFILE_URI, oldId), values, null, null);
                  //move all data to new profile name!
                  getContentResolver().update(MileageProvider.ALL_CONTENT_URI, values, MileageData.ToDBNames[MileageData.CAR]+"=?", new String[] {oldName});
                  Toast.makeText(getApplicationContext(), "Successfully re-named " + oldName + " to " + profileName, Toast.LENGTH_LONG).show();
                  String option = getString(R.string.carSelection);
                  String currentCar = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(option, "Car45");
                  if(currentCar.compareTo(oldName)==0)
                     PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString(option, profileName);
               }
            });
            builder.setNegativeButton("Cancel", new OnClickListener() {
               public void onClick(DialogInterface dialog, int which) {
                  dialog.dismiss();
               }
            });
            return builder.create();
      }
      return super.onCreateDialog(id);
   }
   @Override
   protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
      switch(id) {
         case DELETE_PROFILE_DIALOG:
            View view = dialog.findViewById(android.R.id.message);
            if(view!=null)
               ((TextView)view).setText(getSelectedString());
            break;
         case EDIT_PROFILE_DIALOG:
            SparseBooleanArray sel = mList.getCheckedItemPositions();
            Cursor cursor = ((SimpleCursorAdapter)mList.getAdapter()).getCursor();
            cursor.moveToPosition(sel.keyAt(0));
            EditText box = (EditText)((AlertDialog)dialog).findViewById(EDIT_TEXT_BOX);
            box.setText(cursor.getString(1));
            break;
      }
      super.onPrepareDialog(id, dialog, args);
   }
   
   private String getSelectedString() {
      SparseBooleanArray selected = mList.getCheckedItemPositions();
      String str = "";
      Cursor c = ((SimpleCursorAdapter)mList.getAdapter()).getCursor();
      for(int i=0 ; i < selected.size() ; i++) {
         if(selected.valueAt(i)) {
            c.moveToPosition(selected.keyAt(i));
            String name = c.getString(1);
            str = String.format("%s\n%s", str,name);
         }
      }
      return str;
   }
}
