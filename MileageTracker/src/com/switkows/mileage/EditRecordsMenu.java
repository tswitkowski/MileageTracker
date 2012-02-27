package com.switkows.mileage;

import java.io.File;
import java.io.FilenameFilter;

import com.switkows.mileage.EditRecord.EditRecordFragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView.MultiChoiceModeListener;

public class EditRecordsMenu extends FragmentActivity implements EditRecordFragment.UpdateCallback {

   private static final String LIST_FRAGMENT = "recordList";
   private static final String RECORD_FRAGMENT = "recordViewer";

   // 'global' fields for handling Import of data within a separate thread
   protected DataImportThread   iThread;
   protected long mViewedRecordId;
   private Uri mLastUri;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.edit_record_activity);
      Intent i = getIntent();
      if(i.getData() == null) {
         i.setData(getURI());
      }

      // restore ImportThread pointer, if we got here by way of an orientation change
      if(getLastCustomNonConfigurationInstance() != null) {
         iThread = (DataImportThread) getLastCustomNonConfigurationInstance();
         iThread.restart();
      }
      if(savedInstanceState!=null) {
         mViewedRecordId = savedInstanceState.getLong("currentView");
         mLastUri        = (Uri)savedInstanceState.getParcelable("lastUri");
      }
      else
         mViewedRecordId = -1;
   }

   @Override
   public void onResume() {
      // This might not be the best way to do this, but if we 'resume' this activity,
      // throw away the old cursor, and re-generate the data. This was needed to
      // support a user changing 'profiles' from the preferences screen
      //FIXME - is this okay? should i always reset the data?!
      Uri uri = getURI();
      if(mLastUri==null || uri.compareTo(mLastUri)!=0) {
         getIntent().setData(getURI());

         //This fragment will persist indefinitely.
         ListFragment f = EditRecordsMenuFragment.newInstance(this,getIntent().getData());
         FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
         trans.replace(R.id.record_list_fragment, f, LIST_FRAGMENT);
         trans.commitAllowingStateLoss();
         mViewedRecordId = -1;  //reset this, so we don't display data from the wrong profile
         hideRecordView();
      }
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
         getActionBar().setDisplayHomeAsUpEnabled(true);
      }
      //FIXME - should store the Fragment pointer, so we don't lose state on orientation-switches!!
      if(mViewedRecordId >= 0)
         updateRecordView(mViewedRecordId);
      mLastUri = uri;
      super.onResume();
   }

   // Save the import worker thread, so re-launching the program
   // due to an orientation change will not result in a lockup
   @Override
   public Object onRetainCustomNonConfigurationInstance() {
      if(iThread != null) {
         iThread.pause();
         return iThread;
      }
      return super.onRetainCustomNonConfigurationInstance();
   }

   @Override
   protected void onSaveInstanceState(Bundle outState) {
      super.onSaveInstanceState(outState);
      outState.putLong("currentView", mViewedRecordId);
      outState.putParcelable("lastUri", mLastUri);
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      super.onCreateOptionsMenu(menu);
	  MenuInflater inflater = getMenuInflater();
	  inflater.inflate(R.menu.database_menu, menu);
	  //pre-honeycomb devices do not show CAB, so lets just add it to the menu!
      if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
         inflater.inflate(R.menu.edit_records_cab, menu);
      }
      return true;
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      switch(item.getItemId()) {
         case R.id.add_item:
            Uri uri = getIntent().getData();
            startActivity(new Intent(MileageTracker.ACTION_INSERT, uri));
            return true;
         case R.id.delete_entry:
            showDialog(R.id.delete_entry);
            return true;
         case R.id.move_entry:
            showDialog(R.id.move_entry);
            return true;
         case R.id.select_all:
            selectAll();
            return true;
         case R.id.clear_database:
            clearDB();
            return true;
         case R.id.export_csv: // don't show dialog if the SDcard is not installed.
         case R.id.import_csv: // It'll issue a Toast-based message, though
            checkSDState(item.getItemId());
            return true;
         case R.id.preferences: {
            // Launch preferences activity
            startActivityForResult(new Intent(this, EditPreferences.class), 0);
            return true;
         }
         case android.R.id.home: {
            startActivity(new Intent(this,MileageTracker.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return true;
         }
      }
      return false;
   }

   private void checkSDState(int menuId) {
      String state = Environment.getExternalStorageState();
      if(state.equals(Environment.MEDIA_MOUNTED)) {
         showDialog(menuId);
      } else {
         Toast.makeText(this, "Error! SDCARD not accessible!", Toast.LENGTH_LONG).show();
      }
   }

   @Override
   protected Dialog onCreateDialog(int id) {
      switch(id) {
         case R.id.delete_entry:
            return new DeleteConfirm(this);
         case R.id.move_entry:
            return new MoveDialog(this);
         case R.id.import_csv:
            return new ImportDialog(this);
         case R.id.export_csv:
            return new ExportDialog(this);
      }
      Log.i("TJS", "Got here. should not have!");
      return null;
   }

   @Override
   protected void onPrepareDialog(int id, Dialog dialog) {
      super.onPrepareDialog(id, dialog);
      switch(id) {
         case R.id.delete_entry:
            ((DeleteConfirm) dialog).computeMessage();
            break;
         case R.id.move_entry:
            ((MoveDialog)dialog).computeMessage();
            break;
         case R.id.import_csv:
            dialog = new ImportDialog(this);
            break;
         case R.id.export_csv:
            dialog = new ExportDialog(this);
            break;
      }
   }

   @Override
   protected void onActivityResult(int arg0, int arg1, Intent arg2) {
      super.onActivityResult(arg0, arg1, arg2);
      if(arg0 == 0) {
         if(arg1 == RESULT_CANCELED)
            mViewedRecordId = -1;
      }
   }

   protected EditRecordsMenuFragment getListFragment() {
      Fragment fragment = getSupportFragmentManager().findFragmentByTag(LIST_FRAGMENT);
      if(fragment != null)
         return (EditRecordsMenuFragment)fragment;
      return null;
   }
   protected String getSelectedMessage() {
      EditRecordsMenuFragment fragment = getListFragment();
      if(fragment != null)
         return fragment.getSelectedMessage();
      return "";
   }
   protected void moveSelected(String profile) {
      EditRecordsMenuFragment fragment = getListFragment();
      if(fragment != null)
         if(fragment.moveSelected(mViewedRecordId,profile))
            hideRecordView();
   }
   protected void deleteSelected() {
      EditRecordsMenuFragment fragment = getListFragment();
      if(fragment != null)
         if(fragment.deleteSelected(mViewedRecordId))
            hideRecordView();
   }
   protected void deselectAll() {
      EditRecordsMenuFragment fragment = getListFragment();
      if(fragment != null)
         fragment.deselectAll();
   }
   protected void selectAll() {
      EditRecordsMenuFragment fragment = getListFragment();
      if(fragment != null)
         fragment.selectAll();
   }
   
   protected EditRecordsListAdapter getListAdapter() {
      EditRecordsMenuFragment fragment = getListFragment();
      if(fragment != null)
         return (EditRecordsListAdapter)fragment.getListAdapter();
      return null;
   }
   
   protected void updateRecordView(long id) {
      View view = findViewById(R.id.edit_record_fragment);
      //this is here so we update the 'activated' state of the ListView
      //which allows background state to be updated upon orientation change
      //(between dualPane and singlePane)
      if(view!=null)
         getListFragment().updateRecordView(id);
      //do not restart the EditRecord fragment unless we need to (this
      //ensures that state is kept when this activity goes in teh background)
      if(mViewedRecordId == id && (view != null && view.getVisibility()==View.VISIBLE) )
         return;
      mViewedRecordId = id;
      FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
      EditRecordFragment fragment = EditRecordFragment.newInstance(id, false);
      if(view!=null) {
         view.setVisibility(View.VISIBLE);
         trans.replace(R.id.edit_record_fragment, fragment,RECORD_FRAGMENT);
         trans.commitAllowingStateLoss();

      } else {
         Uri uri = ContentUris.withAppendedId(MileageProvider.ALL_CONTENT_URI, id);
         //wait for result so we know whether the the activity was closed due to a
         //cancel or an orientation-change. This allows us to save the state when
         //we transition back and forth between dualPane & singlePane modes
         startActivityForResult(new Intent(Intent.ACTION_EDIT, uri),0);
      }
   }

   //delete active fragment, and hide view
   protected void hideRecordView() {
      View view = findViewById(R.id.edit_record_fragment);
      if(view!=null) {
         FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
         Fragment fragment = getSupportFragmentManager().findFragmentByTag(RECORD_FRAGMENT);
         if(fragment!=null) {
            trans.remove(fragment);
            trans.commitAllowingStateLoss();
            view.setVisibility(View.GONE);
         }
      }
   }

   //This is not a true database clear...just clears THIS profile's data!!!
   public void clearDB() {
      getContentResolver().delete(getURI(), null, null);
   }

   private Uri getURI() {
      String option = getString(R.string.carSelection);
      String car = PreferenceManager.getDefaultSharedPreferences(this).getString(option, "Car45");
      Uri uri = Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI,car);
      return uri;
   }

   protected String[] getCSVFiles() {
      String state = Environment.getExternalStorageState();
      if(state.equals(Environment.MEDIA_MOUNTED)) {
         File sdcard = Environment.getExternalStorageDirectory();
         String[] files = sdcard.list(new FilenameFilter() {
            public boolean accept(File dir, String filename) {
               return filename.endsWith(".csv");
            }
         });
         return files;
      } else { // we should NEVER enter here, as it is checked before import/export dialogs are shown!
         Toast.makeText(this, "Error! SDCARD not accessible!", Toast.LENGTH_LONG).show();
         return null;
      }
   }

   public static class EditRecordsMenuFragment extends ListFragment {
      private EditRecordsListAdapter mAdapter;
      private boolean mSingleSelection;
      private long mSingleSelectionID;
      private final RecordListLoaderCallbacks mLoaderCallback = new RecordListLoaderCallbacks(); 

      public static EditRecordsMenuFragment newInstance(Context c, Uri uri) {
         EditRecordsMenuFragment result = new EditRecordsMenuFragment();
         Bundle args = new Bundle();
         args.putString("uri", uri.toString());
         result.setArguments(args);
         return result;
      }
      public EditRecordsMenuFragment() {
         super();
         mSingleSelection = false;
         mSingleSelectionID = -1;
      }

      @Override
      public void onActivityCreated(Bundle savedInstanceState) {
         super.onActivityCreated(savedInstanceState);
         getLoaderManager().initLoader(0, getArguments(), mLoaderCallback);
         mAdapter = new EditRecordsListAdapter(getActivity(), this, null, new String[] { MileageData.ToDBNames[MileageData.DATE] },
               new int[] { android.R.id.text1 });
         setListAdapter(mAdapter);
         setEmptyText("No records present");
         if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
            getListView().setMultiChoiceModeListener(new EditRecordModeListener());
         } else
            getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
      }

      @Override
      public void onListItemClick(ListView l, View v, int position, long id) {
         ((EditRecordsMenu)getActivity()).updateRecordView(id);
         super.onListItemClick(l, v, position, id);
      }

      public void handleSelection(boolean hide, int position, boolean isSelected) {
            if(hide && position==-1)
               getListView().clearChoices();
               getListView().setItemChecked(position,isSelected);
      }
      public void updateRecordView(long id) { 
         mAdapter.setViewedItem(id);
         getListView().invalidateViews();
//         getListView().setSelected(true);
      }
      protected boolean deleteSelected(long currentlyViewedId) {
   //      HashSet<Long> checked = mAdapter.getSelected();
         boolean foundIt = false;    //set to true if the crrentlyViewedId was moved/deleted
         SparseBooleanArray checked = getListView().getCheckedItemPositions();
         Uri baseUri = MileageProvider.ALL_CONTENT_URI;
         if(mSingleSelection)
            getActivity().getContentResolver().delete(ContentUris.withAppendedId(baseUri, mSingleSelectionID), null, null);
         long id;
         for(int index=0 ; index<checked.size() ; index++) {
            id = getListAdapter().getItemId(checked.keyAt(index));
            getActivity().getContentResolver().delete(ContentUris.withAppendedId(baseUri, id), null, null);
            if(id == currentlyViewedId)
               foundIt = true;
         }
   //      for(Long id : checked)
   //         getContentResolver().delete(ContentUris.withAppendedId(baseUri, id.longValue()), null, null);
         //FIXME - this is needed, apparently, since i'm not using the same URI as the adapter uses?!
         getLoaderManager().restartLoader(0, getArguments(), mLoaderCallback);
         mAdapter.notifyDataSetChanged();
   //      checked.clear();
         return foundIt;
      }
      //FIXME - merge deleteSelected & moveSelected, since all code except the provider call is different
      protected boolean moveSelected(long currentlyViewedId,String destProfile) {
         boolean foundIt = false;    //set to true if the crrentlyViewedId was moved/deleted
         SparseBooleanArray checked = getListView().getCheckedItemPositions();
         Uri baseUri = MileageProvider.ALL_CONTENT_URI;
         ContentValues values = new ContentValues(1);
         if(mSingleSelection)
            getActivity().getContentResolver().update(ContentUris.withAppendedId(baseUri, mSingleSelectionID), values, null, null);
         values.put(MileageData.ToDBNames[MileageData.CAR], destProfile);
         long id;
         for(int index=0 ; index<checked.size() ; index++) {
            id = getListAdapter().getItemId(checked.keyAt(index));
            getActivity().getContentResolver().update(ContentUris.withAppendedId(baseUri, id), values, null, null);
            if(id == currentlyViewedId)
               foundIt = true;
         }
         //FIXME - this is needed, apparently, since i'm not using the same URI as the adapter uses?!
         getLoaderManager().restartLoader(0, getArguments(), mLoaderCallback);
         mAdapter.notifyDataSetChanged();
         return foundIt;
      }
   
      protected void deselectAll() {
         getListView().clearChoices();
//         mAdapter.clearSelected();
         handleSelection(true,-1,false);
      }
      protected void selectAll() {
         int count = getListView().getCount();
         for(int i=0 ; i < count ; i++)
            getListView().setItemChecked(i, true);
         getListView().requestLayout();   //FIXME - needed?
      }
      //This method queries the Adapter to determine which items are selected.
      //It then walks through the Cursor, adding to the return message a string
      //containing a message for each selected row in the Adapter.
      //FIXME - use getList()'s item's getText() instead of cursor?
      protected String getSelectedMessage() {
         String ret = "";
//         HashSet<Long> mySelected = (HashSet<Long>) mAdapter.getSelected().clone();
         long[] mySelected = getListView().getCheckedItemIds();
         if(mSingleSelection)
            mySelected = new long[] {mSingleSelectionID};
//            mySelected.add(Long.valueOf(mSingleSelectionID));

         Cursor cursor = mAdapter.getCursor();
         if(cursor==null)
            return ret;
         int idColumn   = cursor.getColumnIndex("_id");
         int dateColumn = cursor.getColumnIndex(MileageData.ToDBNames[MileageData.DATE]);
         int mpgColumn  = cursor.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE]);
         SharedPreferences prefs = mAdapter.getPrefs();
         for(int i=0 ; i<cursor.getCount() ; i++) {
            cursor.moveToPosition(i);
            long id = cursor.getLong(idColumn);
            String str;
//            if(mySelected.contains(Long.valueOf(id))) {
            for(long currId : mySelected) {
               if(currId == id) {
                  str = MileageData.getSimpleDescription(cursor, dateColumn, mpgColumn, prefs, getActivity());
                  if(ret.length() > 0)
                     ret = String.format("%s\n%s", ret, str);
                  else
                     ret = str.toString();
                  break;
               }
            }
         }
         return ret;
      }
      protected class EditRecordModeListener implements MultiChoiceModeListener {
         public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.edit_records_cab, menu);
            mode.setTitle("Select Records");
            return true;
         }

         public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
         }

         public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Activity activity = getActivity();
            switch(item.getItemId()) {
               case R.id.delete_entry:
                  activity.showDialog(R.id.delete_entry);
                  return true;
               case R.id.move_entry:
                  activity.showDialog(R.id.move_entry);
                  return true;
               case R.id.select_all:
                  selectAll();
                  return true;
               default:
                  Toast.makeText(activity, "'"+item.getTitle()+"' pressed...", Toast.LENGTH_LONG).show();
                  mode.finish();
                  return true;
            }
         }

         public void onDestroyActionMode(ActionMode mode) {
         }

         public void onItemCheckedStateChanged(ActionMode mode, int position,
               long id, boolean checked) {
         }
      }

      private class RecordListLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {

         public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            Uri uri = Uri.parse(args.getString("uri"));
            return new CursorLoader(getActivity(),uri,null, null, null, MileageProvider.defaultSort());
         }

         public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mAdapter.swapCursor(data);
         }

         public void onLoaderReset(Loader<Cursor> loader) {
            mAdapter.swapCursor(null);
         }
         
      }
   }

   protected class DeleteConfirm extends AlertDialog {
      private final OnClickListener acceptListener;
      public DeleteConfirm(Context context) {
         super(context);
         setTitle(R.string.confirm_delete);
         acceptListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
               deleteSelected();
               deselectAll();
            }
         };
         computeMessage();
         setButton(BUTTON_POSITIVE, "Yes", acceptListener);
         setButton(BUTTON_NEGATIVE, "No", new CancelClickListener());
      }

      public void computeMessage() {
         setMessage(getSelectedMessage());
      }
   }
   
   private class MoveDialog extends AlertDialog {
      private TextView mText;
      public MoveDialog(Context context) {
         super(context);
         setTitle(R.string.confirm_move);
         View view = ((LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.move_dialog, null);
         setView(view);
         mText = (TextView) view.findViewById(android.R.id.text1);
         ArrayAdapter<CharSequence> newAdapter = new ArrayAdapter<CharSequence>(context, android.R.layout.simple_spinner_dropdown_item);
         //replace code with addAll once we drop support for pre-honeycomb devices!
         CharSequence[] cars = MileageProvider.getProfiles(context);
         for(CharSequence car : cars)
            newAdapter.add(car);
//         ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(context, R.array.carValues, android.R.layout.simple_spinner_dropdown_item);
         Spinner s = (Spinner)view.findViewById(R.id.move_to_spinner);
         s.setAdapter(newAdapter);
         OnClickListener acceptListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
               Spinner s = (Spinner)findViewById(R.id.move_to_spinner);
               moveSelected((String)s.getSelectedItem());
               deselectAll();
            }
         };
         setButton(BUTTON_POSITIVE,"Yes", acceptListener);
         setButton(BUTTON_NEGATIVE,"No",  new CancelClickListener());
      }
      
      public void computeMessage() {
         mText.setText(getSelectedMessage());
      }
   }

   protected class ImportDialog extends AlertDialog {
      private final Spinner              filename;
      private final DialogInterface.OnClickListener importListener = new ImportListener();

      // Upon the button being clicked, a new thread will be started, which imports the data into the database,
      // and presents a progress dialog box to the user
      private class ImportListener implements DialogInterface.OnClickListener {
         public void onClick(DialogInterface dialog, int which) {
            String name = (String) filename.getSelectedItem();
            dismiss();
            File file = new File(Environment.getExternalStorageDirectory(), name);
            iThread = new DataImportThread(getContext(),getListAdapter());
            iThread.execute(file);
         }
      }

      public ImportDialog(Context context) {
         super(context);
         String[] files = getCSVFiles();
         if(files != null) {
            setTitle("Select Import File:");
            View view = ((LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.import_dialog, null);
            setView(view);
            filename = (Spinner) view.findViewById(R.id.file_list);
            setButton(BUTTON_POSITIVE,getString(R.string.confirm_import),importListener);
            setButton(BUTTON_NEGATIVE,"Cancel",  new CancelClickListener());
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            filename.setAdapter(adapter);
            for(String file : files)
               adapter.add(file);
         } else {   //FIXME - this will never fire! check files.length, and display an error message!
            filename = null;
            dismiss();
         }
      }
   };

   protected class ExportDialog extends AlertDialog {
      private final AutoCompleteTextView filename;
      private final DialogInterface.OnClickListener exportListener = new ExportListener();

      private class ExportListener implements DialogInterface.OnClickListener {
         public void onClick(DialogInterface dialog, int which) {
            String name = filename.getText().toString();
            dismiss();
            File file = new File(Environment.getExternalStorageDirectory(), name);
            DataExportThread exporter = new DataExportThread(getContext());
            exporter.execute(file);
         }
      }

      public ExportDialog(Context context) {
         super(context);
         setTitle("Select Export File:");
         View view = ((LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.export_dialog, null);
         setView(view);
         filename = (AutoCompleteTextView) view.findViewById(R.id.file_list);
         String[] files = getCSVFiles();
         ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line,
               files);
         filename.setAdapter(adapter);
         setButton(BUTTON_POSITIVE, getString(R.string.confirm_export), exportListener);
         setButton(BUTTON_NEGATIVE,"Cancel",  new CancelClickListener());
      }
   }

   //simple wrapper to make instantiating 'cancel' buttons a bit easier
   private class CancelClickListener implements DialogInterface.OnClickListener {
      public void onClick(DialogInterface dialog, int which) {
         dialog.dismiss();
      }
   }

   public boolean messageUpdated(long id) {
      return true;
   };

//   public void onClick(View v) {
//      switch(v.getId()) {
//         case R.id.button_delete:
//            mSingleSelection = false;
//            showDialog(R.id.delete_entry);
//            break;
//         case R.id.button_deselect:
//            deselectAll();
//            break;
//      }
//   }
}
