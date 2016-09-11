package com.switkows.mileage;

import java.io.File;
import java.io.FilenameFilter;

import com.switkows.mileage.EditRecord.EditRecordFragment;
import com.switkows.mileage.ProfileSelector.ProfileSelectorCallbacks;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView.MultiChoiceModeListener;

public class EditRecordsMenu extends AppCompatActivity implements EditRecordFragment.UpdateCallback, DataImportThread.callbacks, OnBackStackChangedListener, ProfileSelectorCallbacks {

   private static final String LIST_FRAGMENT   = "recordList";
   private static final String RECORD_FRAGMENT = "recordViewer";

   // 'global' fields for handling Import of data within a separate thread
   protected DataImportThread  iThread;
   protected long              mViewedRecordId;
   private Uri                 mLastUri;
   protected ProfileSelector   mProfileAdapter;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.edit_record_activity);
      Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
      toolbar.setLogo(R.drawable.mileage_tracker_icon);
      setSupportActionBar(toolbar);
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      Intent i = getIntent();
      if(i.getData() == null) {
         i.setData(getURI());
      }

      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
         mProfileAdapter = ProfileSelector.setupActionBar(this, null);

      // restore ImportThread pointer, if we got here by way of an orientation change
      if(getLastCustomNonConfigurationInstance() != null) {
         iThread = (DataImportThread)getLastCustomNonConfigurationInstance();
         iThread.restart();
      }
      if(savedInstanceState != null) {
         mViewedRecordId = savedInstanceState.getLong("currentView");
         mLastUri        = savedInstanceState.getParcelable("lastUri");
      } else
         mViewedRecordId = -1;
   }

   @Override
   public void onResume() {
      getSupportFragmentManager().addOnBackStackChangedListener(this);
      // This might not be the best way to do this, but if we 'resume' this activity,
      // throw away the old cursor, and re-generate the data. This was needed to
      // support a user changing 'profiles' from the preferences screen
      //FIXME - is this okay? should i always reset the data?!
      Uri uri = getURI();
      if(mLastUri == null || uri.compareTo(mLastUri) != 0) {
         getIntent().setData(uri);

         //This fragment will persist indefinitely.
         ListFragment f = EditRecordsMenuFragment.newInstance(this, getIntent().getData());
         FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
         trans.replace(R.id.record_list_fragment, f, LIST_FRAGMENT);
         trans.commitAllowingStateLoss();
         hideRecordView();
      }

      setHomeEnabledHoneycomb();
      if(mProfileAdapter != null)
         mProfileAdapter.loadActionBarNavItems(this);
      //FIXME - should store the Fragment pointer, so we don't lose state on orientation-switches!!
      if(mViewedRecordId >= 0)
         updateRecordView(mViewedRecordId);
      mLastUri = uri;
      super.onResume();
   }

   // SuppressWarnings for call to setDisplayHomeAsUpEnabled, which might return a NPE
   @SuppressWarnings("ConstantConditions")
   @TargetApi(Build.VERSION_CODES.HONEYCOMB)
   private void setHomeEnabledHoneycomb() {
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
         getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      }
   }

   // Save the import worker thread, so re-launching the program
   // due to an orientation change will not result in a lockup
   @Override
   public Object onRetainCustomNonConfigurationInstance() {
      if(iThread != null && !iThread.isCompleted()) {
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
//      super.onCreateOptionsMenu(menu);
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
      DialogFragment fragment;
      switch(item.getItemId()) {
         case R.id.add_item:
            Uri uri = getIntent().getData();
            startActivity(new Intent(MileageTracker.ACTION_INSERT, uri));
            return true;
         case R.id.delete_entry:
            fragment = DeleteConfirmFragment.newInstance(getSelectedMessage());
            fragment.show(getSupportFragmentManager(), "dialog");
            return true;
         case R.id.move_entry:
            fragment = MoveDialogFragment.newInstance(getSelectedMessage());
            fragment.show(getSupportFragmentManager(), "dialog");
            return true;
         case R.id.select_all:
            selectAll();
            return true;
         case R.id.clear_database:
            clearDB();
            return true;
         case R.id.export_csv: // don't show dialog if the SDCard is not installed.
         case R.id.import_csv: // It'll issue a Toast-based message, though
            checkSDState(item.getItemId());
            return true;
         case R.id.preferences: {
            // Launch preferences activity
            startActivityForResult(new Intent(this, EditPreferences.class), 0);
            return true;
         }
         case android.R.id.home: {
            startActivity(new Intent(this, MileageTracker.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return true;
         }
      }
      return false;
   }

   private void checkSDState(int menuId) {
      String state = Environment.getExternalStorageState();
      if(state.equals(Environment.MEDIA_MOUNTED)) {
         DialogFragment fragment = null;
         switch(menuId) {
            case R.id.import_csv:
               fragment = ImportDialogFragment.newInstance();
               break;
            case R.id.export_csv:
               fragment = ExportDialogFragment.newInstance();
               break;
         }
         if(fragment != null)
            fragment.show(getSupportFragmentManager(), "dialog");
      } else {
         Toast.makeText(this, "Error! SDCARD not accessible!", Toast.LENGTH_LONG).show();
      }
   }

   protected void performImport(String filename) {
      File file = new File(Environment.getExternalStorageDirectory(), filename);
      iThread = new DataImportThread(this, getListAdapter());
      iThread.execute(file);
   }

   protected void performExport(String filename) {
      File file = new File(Environment.getExternalStorageDirectory(), filename);
      DataExportThread exporter = new DataExportThread(this);
      exporter.execute(file);
   }

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent arg2) {
      super.onActivityResult(requestCode, resultCode, arg2);
      if(requestCode == 0) {
         if(resultCode == RESULT_CANCELED)
            mViewedRecordId = -1;
         else {
            messageUpdated(mViewedRecordId);
            mViewedRecordId = resultCode - 1;
         }
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
         if(fragment.moveSelected(mViewedRecordId, profile))
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
      if(view != null)
         getListFragment().updateRecordView(id);
      //do not restart the EditRecord fragment unless we need to (this
      //ensures that state is kept when this activity goes in teh background)
      if(mViewedRecordId == id && (view != null && view.getVisibility() == View.VISIBLE))
         return;
      mViewedRecordId = id;
      if(view != null) {
         FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
         EditRecordFragment fragment = EditRecordFragment.newInstance(id, false);
         //add the fragment to the stack, so 'back' navigation properly hides it (hopefully)
         if(view.getVisibility() != View.VISIBLE)
            trans.addToBackStack(RECORD_FRAGMENT);
         view.setVisibility(View.VISIBLE);
         trans.replace(R.id.edit_record_fragment, fragment, RECORD_FRAGMENT);
         trans.commitAllowingStateLoss();

      } else {
         //pop the back stack
         getSupportFragmentManager().popBackStack();
         Uri uri = ContentUris.withAppendedId(MileageProvider.ALL_CONTENT_URI, id);
         //wait for result so we know whether the the activity was closed due to a
         //cancel or an orientation-change. This allows us to save the state when
         //we transition back and forth between dualPane & singlePane modes
         startActivityForResult(new Intent(Intent.ACTION_EDIT, uri), 0);
      }
   }

   //delete active fragment, and hide view
   protected void hideRecordView() {
      View view = findViewById(R.id.edit_record_fragment);
      mViewedRecordId = -1;   //reset pointer, so we don't get confused later
      if(view != null) {
         EditRecordsMenuFragment list = getListFragment();
         if(list != null)
            list.updateRecordView(mViewedRecordId);
         FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
         Fragment fragment = getSupportFragmentManager().findFragmentByTag(RECORD_FRAGMENT);
         if(fragment != null) {
            trans.remove(fragment);
            trans.commitAllowingStateLoss();
         }
         view.setVisibility(View.GONE);
      }
   }

   //This is not a true database clear...just clears THIS profile's data!!!
   public void clearDB() {
      getContentResolver().delete(getURI(), null, null);
   }

   private Uri getURI() {
      String option = getString(R.string.carSelection);
      String car = PreferenceManager.getDefaultSharedPreferences(this).getString(option, "Car45");
      return Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI, car);
   }

   protected String[] getCSVFiles() {
      String state = Environment.getExternalStorageState();
      if(state.equals(Environment.MEDIA_MOUNTED)) {
         File sdcard = Environment.getExternalStorageDirectory();
         return sdcard.list(new FilenameFilter() {
            public boolean accept(File dir, String filename) {
               return filename.endsWith(".csv");
            }
         });
      } else { // we should NEVER enter here, as it is checked before import/export dialogs are shown!
         Toast.makeText(this, "Error! SDCARD not accessible!", Toast.LENGTH_LONG).show();
         return null;
      }
   }

   //once import task has completed, reset pointer so we don't resurrect dialog box!
   public void taskCompleted() {
      iThread = null;
   }

   public static class EditRecordsMenuFragment extends ListFragment {
      private EditRecordsListAdapter          mAdapter;
      private final RecordListLoaderCallbacks mLoaderCallback = new RecordListLoaderCallbacks();

      @SuppressWarnings("UnusedParameters")
      public static EditRecordsMenuFragment newInstance(Context c, Uri uri) {
         EditRecordsMenuFragment result = new EditRecordsMenuFragment();
         Bundle args = new Bundle();
         args.putString("uri", uri.toString());
         result.setArguments(args);
         return result;
      }

      @Override
      public void onActivityCreated(Bundle savedInstanceState) {
         super.onActivityCreated(savedInstanceState);
         getLoaderManager().initLoader(0, getArguments(), mLoaderCallback);
         mAdapter = new EditRecordsListAdapter(getActivity(), this, null, new String[] { MileageData.ToDBNames[MileageData.DATE] },
               new int[] { android.R.id.text1 });
         setListAdapter(mAdapter);
         setEmptyText("No records present");
         if(setChoiceModeListenerHoneycomb(getListView()))
            getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
      }

      @TargetApi(Build.VERSION_CODES.HONEYCOMB)
      private boolean setChoiceModeListenerHoneycomb(ListView v) {
         if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            v.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
            v.setMultiChoiceModeListener(new EditRecordModeListener());
            return false;
         }
         return true;
      }

      @Override
      public void onListItemClick(ListView l, View v, int position, long id) {
         ((EditRecordsMenu)getActivity()).updateRecordView(id);
         super.onListItemClick(l, v, position, id);
      }

      public void handleSelection(boolean hide, int position, boolean isSelected) {
         if(hide && position == -1)
            getListView().clearChoices();
         getListView().setItemChecked(position, isSelected);
      }

      public void updateRecordView(long id) {
         mAdapter.setViewedItem(id);
         getListView().invalidateViews();
//         getListView().setSelected(true);
      }

      protected boolean deleteSelected(long currentlyViewedId) {
         boolean foundIt = false; //set to true if the currentlyViewedId was moved/deleted
         SparseBooleanArray checked = getListView().getCheckedItemPositions();
         Uri baseUri = MileageProvider.ALL_CONTENT_URI;
         long id;
         for(int index = 0; index < checked.size(); index++) {
            id = getListAdapter().getItemId(checked.keyAt(index));
            getActivity().getContentResolver().delete(ContentUris.withAppendedId(baseUri, id), null, null);
            if(id == currentlyViewedId)
               foundIt = true;
         }
         //FIXME - this is needed, apparently, since i'm not using the same URI as the adapter uses?!
         getLoaderManager().restartLoader(0, getArguments(), mLoaderCallback);
         mAdapter.notifyDataSetChanged();
         return foundIt;
      }

      //FIXME - merge deleteSelected & moveSelected, since all code except the provider call is different
      protected boolean moveSelected(long currentlyViewedId, String destProfile) {
         boolean foundIt = false; //set to true if the currentlyViewedId was moved/deleted
         SparseBooleanArray checked = getListView().getCheckedItemPositions();
         Uri baseUri = MileageProvider.ALL_CONTENT_URI;
         ContentValues values = new ContentValues(1);
         values.put(MileageData.ToDBNames[MileageData.CAR], destProfile);
         long id;
         for(int index = 0; index < checked.size(); index++) {
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

      protected boolean messageUpdated() {
         getLoaderManager().restartLoader(0, getArguments(), mLoaderCallback);
         return true;
      }
      protected void deselectAll() {
         getListView().clearChoices();
         handleSelection(true, -1, false);
      }

      protected void selectAll() {
         int count = getListView().getCount();
         for(int i = 0; i < count; i++)
            getListView().setItemChecked(i, true);
         getListView().requestLayout(); //FIXME - needed?
      }

      //This method queries the Adapter to determine which items are selected.
      //It then walks through the Cursor, adding to the return message a string
      //containing a message for each selected row in the Adapter.
      //FIXME - use getList()'s item's getText() instead of cursor?
      protected String getSelectedMessage() {
         String ret = "";
         long[] mySelected = getListView().getCheckedItemIds();

         Cursor cursor = mAdapter.getCursor();
         if(cursor == null)
            return ret;
         int idColumn   = cursor.getColumnIndex("_id");
         int dateColumn = cursor.getColumnIndex(MileageData.ToDBNames[MileageData.DATE]);
         int mpgColumn  = cursor.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE]);
         SharedPreferences prefs = mAdapter.getPrefs();
         for(int i = 0; i < cursor.getCount(); i++) {
            cursor.moveToPosition(i);
            long id = cursor.getLong(idColumn);
            String str;
            for(long currId : mySelected) {
               if(currId == id) {
                  str = MileageData.getSimpleDescription(cursor, dateColumn, mpgColumn, prefs, getActivity());
                  if(ret.length() > 0)
                     ret = String.format("%s\n%s", ret, str);
                  else
                     ret = str;
                  break;
               }
            }
         }
         return ret;
      }

      @TargetApi(Build.VERSION_CODES.HONEYCOMB)
      protected class EditRecordModeListener implements MultiChoiceModeListener {
         public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.edit_records_cab, menu);
            mode.setTitle("Select Records");
            return true;
         }

         public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            try {
               AppCompatActivity activity = (AppCompatActivity) getActivity();
               Toolbar tb = (Toolbar) activity.findViewById(R.id.main_toolbar);
               tb.setVisibility(View.GONE);
//               tb.animate().translationY(-tb.getBottom()).setInterpolator(new AccelerateInterpolator()).start();
            } catch (ClassCastException e) {
               Log.e("TJS", "Invalid activity class: " + e);
            }
            return true;
         }

         public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Activity activity = getActivity();
            DialogFragment fragment;
            switch(item.getItemId()) {
               case R.id.delete_entry:
                  fragment = DeleteConfirmFragment.newInstance(getSelectedMessage());
                  fragment.show(getActivity().getSupportFragmentManager(), "dialog");
                  return true;
               case R.id.move_entry:
                  fragment = MoveDialogFragment.newInstance(getSelectedMessage());
                  fragment.show(getActivity().getSupportFragmentManager(), "dialog");
                  return true;
               case R.id.select_all:
                  selectAll();
                  return true;
               default:
                  Toast.makeText(activity, "'" + item.getTitle() + "' pressed...", Toast.LENGTH_LONG).show();
                  mode.finish();
                  return true;
            }
         }

         public void onDestroyActionMode(ActionMode mode) {
            try {
               AppCompatActivity activity = (AppCompatActivity) getActivity();
               Toolbar tb = (Toolbar) activity.findViewById(R.id.main_toolbar);
               tb.setVisibility(View.VISIBLE);
               //Give a transition from off screen to the normal spot
               tb.setTranslationY(-tb.getBottom());
               tb.animate().translationY(0).setInterpolator(new AccelerateInterpolator()).start();
            } catch (ClassCastException e) {
               Log.e("TJS", "Invalid activity class: " + e);
            }
         }

         public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
         }
      }

      private class RecordListLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {

         public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            Uri uri = Uri.parse(args.getString("uri"));
            return new CursorLoader(getActivity(), uri, null, null, null, MileageProvider.defaultSort());
         }

         public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mAdapter.swapCursor(data);
         }

         public void onLoaderReset(Loader<Cursor> loader) {
            mAdapter.swapCursor(null);
         }

      }
   }

   public static class DeleteConfirmFragment extends DialogFragment {
      public static DeleteConfirmFragment newInstance(String selected) {
         DeleteConfirmFragment frag = new DeleteConfirmFragment();
         Bundle args = new Bundle();
         args.putString("selected", selected);
         frag.setArguments(args);
         return frag;
      }

      @NonNull
      @Override
      public Dialog onCreateDialog(Bundle savedInstanceState) {
         final EditRecordsMenu activity = (EditRecordsMenu)getActivity();
         String message = getArguments().getString("selected");
         OnClickListener acceptListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
               activity.deleteSelected();
               activity.deselectAll();
            }
         };
         return new AlertDialog.Builder(getActivity()).setTitle(R.string.confirm_delete).setMessage(message)
                                                      .setPositiveButton("Yes", acceptListener)
                                                      .setNegativeButton("No", activity.new CancelClickListener())
                                                      .create();
      }
   }

   public static class MoveDialogFragment extends DialogFragment {
      public static MoveDialogFragment newInstance(String selected) {
         MoveDialogFragment frag = new MoveDialogFragment();
         Bundle args = new Bundle();
         args.putString("selected", selected);
         frag.setArguments(args);
         return frag;
      }

      @NonNull
      @Override
      public Dialog onCreateDialog(Bundle savedInstanceState) {
         final EditRecordsMenu activity = (EditRecordsMenu)getActivity();
         @SuppressLint("InflateParams")
         final View view = ((LayoutInflater)activity.getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.move_dialog, null);
         String message = getArguments().getString("selected");
         TextView text = (TextView)view.findViewById(android.R.id.text1);
         text.setText(message);
         ArrayAdapter<CharSequence> newAdapter = new ArrayAdapter<CharSequence>(activity, android.R.layout.simple_spinner_dropdown_item);
         //replace code with addAll once we drop support for pre-honeycomb devices!
         CharSequence[] cars = MileageProvider.getProfiles(activity);
         for(CharSequence car : cars)
            newAdapter.add(car);
         Spinner s = (Spinner)view.findViewById(R.id.move_to_spinner);
         s.setAdapter(newAdapter);
         OnClickListener acceptListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
               Spinner s = (Spinner)view.findViewById(R.id.move_to_spinner);
               activity.moveSelected((String)s.getSelectedItem());
               activity.deselectAll();
            }
         };
         return new AlertDialog.Builder(getActivity()).setTitle(R.string.confirm_move).setView(view)
                                                      .setPositiveButton("Yes", acceptListener)
                                                      .setNegativeButton("No", activity.new CancelClickListener())
                                                      .create();
      }
   }

   public static class ImportDialogFragment extends DialogFragment {
      public static ImportDialogFragment newInstance() {
         return new ImportDialogFragment();
      }

      @NonNull
      @Override
      public Dialog onCreateDialog(Bundle savedInstanceState) {
         final EditRecordsMenu activity = (EditRecordsMenu)getActivity();
         String[] files = activity.getCSVFiles();
         if(files != null) {

            @SuppressLint("InflateParams")
            View view = ((LayoutInflater)activity.getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.import_dialog, null);
            final Spinner filename = (Spinner)view.findViewById(R.id.file_list);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            filename.setAdapter(adapter);
            for(String file : files)
               adapter.add(file);

            DialogInterface.OnClickListener importListener = new DialogInterface.OnClickListener() {
               // Upon the button being clicked, a new thread will be started, which imports the data into the database,
               // and presents a progress dialog box to the user
               public void onClick(DialogInterface dialog, int which) {
                  String name = (String)filename.getSelectedItem();
                  dismiss();
                  activity.performImport(name);
               }
            };
            return new AlertDialog.Builder(activity).setTitle("Select Import File:").setView(view)
                                                    .setPositiveButton(R.string.confirm_import, importListener)
                                                    .setNegativeButton("Cancel", activity.new CancelClickListener())
                                                    .create();
         } else { //FIXME - this will never fire! check files.length, and display an error message!
            return null;
         }
      }
   }

   public static class ExportDialogFragment extends DialogFragment {
      public static ExportDialogFragment newInstance() {
         return new ExportDialogFragment();
      }

      @NonNull
      @Override
      public Dialog onCreateDialog(Bundle savedInstanceState) {
         final AutoCompleteTextView filename;
         final EditRecordsMenu activity = (EditRecordsMenu)getActivity();
         @SuppressLint("InflateParams")
         View view = ((LayoutInflater)activity.getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.export_dialog, null);
         filename = (AutoCompleteTextView)view.findViewById(R.id.file_list);
         String[] files = activity.getCSVFiles();
         ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, android.R.layout.simple_dropdown_item_1line, files);
         filename.setAdapter(adapter);

         DialogInterface.OnClickListener exportListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
               String name = filename.getText().toString();
               dismiss();
               activity.performExport(name);
            }
         };

         return new AlertDialog.Builder(activity).setTitle("Select Export File:").setView(view)
                                                 .setPositiveButton(R.string.confirm_export, exportListener)
                                                 .setNegativeButton("Cancel", activity.new CancelClickListener())
                                                 .create();
      }
   }

   //simple wrapper to make instantiating 'cancel' buttons a bit easier
   private class CancelClickListener implements DialogInterface.OnClickListener {
      public void onClick(DialogInterface dialog, int which) {
         dialog.dismiss();
      }
   }

   public boolean messageUpdated(long id) {
      boolean result = false;
      mViewedRecordId = -1;
      Fragment fragment = getSupportFragmentManager().findFragmentByTag(LIST_FRAGMENT);
      if(fragment != null && fragment instanceof EditRecordsMenuFragment)
         result = ((EditRecordsMenuFragment)fragment).messageUpdated();
      return result;
   }

   public void onBackStackChanged() {
      FragmentManager manager = getSupportFragmentManager();
      int count = manager.getBackStackEntryCount();
      if(count == 0)
         hideRecordView();
   }

   public void onProfileChange(String newProfile) {
      mProfileAdapter.applyPreferenceChange(newProfile);
      Fragment fragment = getSupportFragmentManager().findFragmentByTag(LIST_FRAGMENT);
      //update fragment's URI, and reload List data
      if(fragment != null && fragment instanceof EditRecordsMenuFragment) {
         fragment.getArguments().putString("uri", getURI().toString());
         ((EditRecordsMenuFragment)fragment).messageUpdated();
      }
   }
}
