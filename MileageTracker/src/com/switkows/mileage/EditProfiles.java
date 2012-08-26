package com.switkows.mileage;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

//FIXME - add confirmation when deleting a profile which contains data
//FIXME - upon confirmation (above), delete data associated with profile
public class EditProfiles extends Activity {
   private final static int DELETE_PROFILE_DIALOG = 0,
                            ADD_PROFILE_DIALOG    = 1,
                            EDIT_PROFILE_DIALOG   = 2;
   private final static int EDIT_TEXT_BOX         = 45;

   private ListView mList;
   private int mListViewId;

   private class Profile {
      private long      mId;
      private String    mName;
      private boolean   mHasItems;
      public Profile(long id, String name) {
         mId        = id;
         mName      = name;
         mHasItems  = false;
      }
      public void setHasItems(boolean hasItems) {
         mHasItems = hasItems;
      }

      public String toString() {
         String result = mName;
         if(mHasItems)
            result += "*";
         return result;
      }
      public long getId() {
         return mId;
      }
      public String getName() {
         return mName;
      }
   }

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.edit_profiles);
      if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
         mListViewId = android.R.layout.simple_list_item_multiple_choice;
      else
         mListViewId = android.R.layout.simple_list_item_activated_1;

      findViewById(R.id.add_profile_button).setOnClickListener(new View.OnClickListener() {
         public void onClick(View v) {
            showDialog(ADD_PROFILE_DIALOG);
         }
      });
   }

   @Override
   protected void onResume() {
      ArrayAdapter<Profile> newAdapter = new ArrayAdapter<Profile>(this, mListViewId);

      mList = (ListView)findViewById(android.R.id.list);
      mList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
      mList.setAdapter(newAdapter);
      updateList();

      super.onResume();
   }

   public void updateList() {
      Cursor cursor = getContentResolver().query(MileageProvider.CAR_PROFILE_URI, null, null, null, null);
      @SuppressWarnings("unchecked")
      ArrayAdapter<Profile> adapter = (ArrayAdapter<Profile>) mList.getAdapter();
      Profile[] content = new Profile[cursor.getCount()];
      int column    = cursor.getColumnIndex("carName");
      int idColumn  = cursor.getColumnIndex("_id");
      Uri u;
//      Toast.makeText(this, "Trying to create new ArrayAdapter", Toast.LENGTH_LONG).show();
      for(int i=0 ; i<content.length ; i++) {
         cursor.moveToPosition(i);
         content[i] = new Profile(cursor.getLong(idColumn),cursor.getString(column));
         u = Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI,content[i].getName());
         Cursor c = getContentResolver().query(u, new String[] {"_id"}, null, null, null);
         if(c.getCount()>0)
            content[i].setHasItems(true);
         c.close();
      }
      cursor.close();
      adapter.clear();
      for (Profile item : content)
         adapter.add(item);
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

   private final OnClickListener mDefaultCancelListener = new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
         dialog.dismiss();
      }};

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
                  updateList();
               }
            });
            builder.setNegativeButton("Cancel", mDefaultCancelListener);
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
                  updateList();
               }
            });
            builder.setNegativeButton("Cancel", mDefaultCancelListener);
            return builder.create();
         case EDIT_PROFILE_DIALOG:
            builder = new AlertDialog.Builder(this);
            editor = new EditText(this);
            editor.setId(EDIT_TEXT_BOX);
            SparseBooleanArray sel = mList.getCheckedItemPositions();
            if(sel.indexOfValue(true)<0) {
               Toast.makeText(this, "Please select a Profile", Toast.LENGTH_SHORT).show();
               return null;
            }
            @SuppressWarnings("unchecked")
            final ArrayAdapter<Profile> arrayAdapter = (ArrayAdapter<Profile>)mList.getAdapter();
            String name = arrayAdapter.getItem(sel.keyAt(sel.indexOfValue(true))).getName();
            editor.setText(name);
            builder.setView(editor);
            builder.setTitle("Enter modified Profile name:");
            builder.setPositiveButton("Confirm", new OnClickListener() {
               @TargetApi(9)
               public void onClick(DialogInterface dialog, int which) {
                  //save off the old value, so we can update records appropriately
                  SparseBooleanArray sel = mList.getCheckedItemPositions();
                  Profile profile = arrayAdapter.getItem(sel.keyAt(sel.indexOfValue(true)));
                  final long oldId = profile.getId();
                  String oldName   = profile.getName();
                  //get the new value
                  EditText box = (EditText)((AlertDialog)dialog).findViewById(EDIT_TEXT_BOX);
                  String profileName = box.getText().toString();
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
                     PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString(option, profileName).apply();
                  updateList();
               }
            });
            builder.setNegativeButton("Cancel", mDefaultCancelListener);
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
            @SuppressWarnings("unchecked")
            ArrayAdapter<Profile> arrayAdapter = (ArrayAdapter<Profile>)mList.getAdapter();
            String name = arrayAdapter.getItem(sel.keyAt(sel.indexOfValue(true))).getName();
            EditText box = (EditText)((AlertDialog)dialog).findViewById(EDIT_TEXT_BOX);
            box.setText(name);
            break;
      }
      super.onPrepareDialog(id, dialog, args);
   }

   private String getSelectedString() {
      SparseBooleanArray selected = mList.getCheckedItemPositions();
      String str = "";
      @SuppressWarnings("unchecked")
      ArrayAdapter<Profile> arrayAdapter = (ArrayAdapter<Profile>)mList.getAdapter();
      for(int i=0 ; i < selected.size() ; i++) {
         if(selected.valueAt(i)) {
            String name = arrayAdapter.getItem(selected.keyAt(i)).toString();
            str = String.format("%s\n%s", str,name);
         }
      }
      return str;
   }
}
