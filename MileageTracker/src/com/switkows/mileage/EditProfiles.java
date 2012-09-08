package com.switkows.mileage;

import android.annotation.TargetApi;
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
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
//import android.widget.TextView;
import android.widget.Toast;

//FIXME - add confirmation when deleting a profile which contains data
//FIXME - upon confirmation (above), delete data associated with profile
public class EditProfiles extends FragmentActivity {
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
            DialogFragment fragment = CreateProfileDialogFragment.newInstance();
            fragment.show(getSupportFragmentManager(), "dialog");
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
      int column    = cursor.getColumnIndex(MileageProvider.PROFILE_NAME);
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

   protected void deleteSelectedProfiles() {
      SparseBooleanArray sel = mList.getCheckedItemPositions();
      long id;
      @SuppressWarnings("unchecked")
      final ArrayAdapter<Profile> arrayAdapter = (ArrayAdapter<Profile>)mList.getAdapter();
      for(int index=0 ; index<sel.size() ; index++) {
         id = arrayAdapter.getItem(sel.keyAt(sel.indexOfValue(true))).getId();
         getContentResolver().delete(ContentUris.withAppendedId(MileageProvider.CAR_PROFILE_URI,id),null,null);
      }
      updateList();
   }
   
   @TargetApi(9)
   protected void renameSelectedProfile(String newName) {
      @SuppressWarnings("unchecked")
      final ArrayAdapter<Profile> arrayAdapter = (ArrayAdapter<Profile>)mList.getAdapter();
      //save off the old value, so we can update records appropriately
      SparseBooleanArray sel = mList.getCheckedItemPositions();
      Profile profile = arrayAdapter.getItem(sel.keyAt(sel.indexOfValue(true)));
      final long oldId = profile.getId();
      String oldName   = profile.getName();
      //get the new value
      ContentValues values = new ContentValues();
      values.put(MileageProvider.PROFILE_NAME, newName);
      //update profile configs
      getContentResolver().update(ContentUris.withAppendedId(MileageProvider.CAR_PROFILE_URI, oldId), values, null, null);
      //move all data to new profile name!
      getContentResolver().update(MileageProvider.ALL_CONTENT_URI, values, MileageData.ToDBNames[MileageData.CAR]+"=?", new String[] {oldName});
      Toast.makeText(getApplicationContext(), "Successfully re-named " + oldName + " to " + newName, Toast.LENGTH_LONG).show();
      String option = getString(R.string.carSelection);
      String currentCar = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(option, "Car45");
      if(currentCar.compareTo(oldName)==0)
         PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString(option, newName).apply();
      updateList();
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.edit_profiles_menu, menu);
      return true;
   }
   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      DialogFragment fragment = null;
      switch (item.getItemId()) {
         case R.id.add_item:
            fragment = CreateProfileDialogFragment.newInstance();
            break;
         case R.id.delete_entry:
            fragment = DeleteProfileDialogFragment.newInstance(getSelectedString());
            break;
         case R.id.change_name:
             SparseBooleanArray sel = mList.getCheckedItemPositions();
             @SuppressWarnings("unchecked")
            final ArrayAdapter<Profile> arrayAdapter = (ArrayAdapter<Profile>)mList.getAdapter();
             String name = arrayAdapter.getItem(sel.keyAt(sel.indexOfValue(true))).getName();

            fragment = EditProfileDialogFragment.newInstance(name);
            break;
      }
      if(fragment != null) {
         fragment.show(getSupportFragmentManager(), "dialog");
         return true;
      }
      return super.onOptionsItemSelected(item);
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
   
   public static class CreateProfileDialogFragment extends DialogFragment {
      public static CreateProfileDialogFragment newInstance() {
         CreateProfileDialogFragment frag = new CreateProfileDialogFragment();
         return frag;
     }
      @Override
      public Dialog onCreateDialog(Bundle savedInstanceState) {
         final EditProfiles activity = (EditProfiles)getActivity();
         OnClickListener acceptListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
               EditText box = (EditText)((AlertDialog)dialog).findViewById(EDIT_TEXT_BOX);
               String profileName = box.getText().toString();
               MileageProvider.addProfile(activity, profileName);
               activity.updateList();
            }
         };
         EditText editor = new EditText(activity);
         editor.setId(EDIT_TEXT_BOX);
         return new AlertDialog.Builder(activity)
               .setTitle("Enter new Profile name:")
               .setView(editor)
               .setPositiveButton("Add", acceptListener)
               .setNegativeButton("Cancel", new OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                     dialog.dismiss();
                  }})
               .create();
      }
   }

   public static class DeleteProfileDialogFragment extends DialogFragment {
      public static DeleteProfileDialogFragment newInstance(String selected) {
         DeleteProfileDialogFragment frag = new DeleteProfileDialogFragment();
         Bundle args = new Bundle();
         args.putString("selected", selected);
         frag.setArguments(args);
         return frag;
     }
      @Override
      public Dialog onCreateDialog(Bundle savedInstanceState) {
         final EditProfiles activity = (EditProfiles)getActivity();
         String message = getArguments().getString("selected");
         return new AlertDialog.Builder(activity)
               .setTitle("Are you sure you want to delete the following Profiles:")
               .setMessage(message)
               .setPositiveButton("Delete", new OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                     activity.deleteSelectedProfiles();
                  }})
               .setNegativeButton("Cancel", new OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                     dialog.dismiss();
                  }})
               .create();
      }
   }

   public static class EditProfileDialogFragment extends DialogFragment {
      public static EditProfileDialogFragment newInstance(String selected) {
         EditProfileDialogFragment frag = new EditProfileDialogFragment();
         Bundle args = new Bundle();
         args.putString("selected", selected);
         frag.setArguments(args);
         return frag;
     }
      @Override
      public Dialog onCreateDialog(Bundle savedInstanceState) {
         final EditProfiles activity = (EditProfiles)getActivity();
         String name = getArguments().getString("selected");
         if(name.length()==0) {
            Toast.makeText(activity, "Please select a Profile", Toast.LENGTH_SHORT).show();
            return null;
         }
         EditText editor = new EditText(activity);
         editor.setId(EDIT_TEXT_BOX);
         editor.setText(name);
         return new AlertDialog.Builder(activity)
               .setTitle("Enter modified Profile name:")
               .setView(editor)
               .setPositiveButton("Confirm",  new OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                     EditText box = (EditText)((AlertDialog)dialog).findViewById(EDIT_TEXT_BOX);
                     String profileName = box.getText().toString();
                     activity.renameSelectedProfile(profileName);
                  }})
               .setNegativeButton("Cancel", new OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                     dialog.dismiss();
                  }})
               .create();
      }
   }
}
