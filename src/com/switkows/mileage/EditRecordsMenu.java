package com.switkows.mileage;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashSet;


import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ContentUris;
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
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class EditRecordsMenu extends ListActivity implements AnimationListener, android.view.View.OnClickListener {

   private static final int PERFORM_EXPORT = 8, PERFORM_IMPORT = 7, MENU_DELETE = 5, MENU_MODIFY = 6;

   // 'global' fields for handling Import of data within a separate thread
   protected DataImportThread   iThread;
   protected final ImportExportProgressHandler iHandler = new ImportExportProgressHandler(this, PERFORM_IMPORT);
   protected final ImportExportProgressHandler eHandler = new ImportExportProgressHandler(this, PERFORM_EXPORT);
   protected LinearLayout   mFooter;
   private EditRecordsListAdapter mAdapter;

   //for handling 'delete' option selection via context menu
   private boolean mSingleSelection;
   private long mSingleSelectionID;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      mSingleSelection = false;
      setContentView(R.layout.edit_record_activity);
      Intent i = getIntent();
      if(i.getData() == null) {
         i.setData(getURI());
      }
      mFooter = (LinearLayout)findViewById(R.id.edit_records_footer);
      findViewById(R.id.button_delete).setOnClickListener(this);
      findViewById(R.id.button_deselect).setOnClickListener(this);

      Cursor c = managedQuery(getIntent().getData(), null, null, null, null);

      getListView().setOnCreateContextMenuListener(this);
      getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
      getListView().setClickable(true);
      mAdapter = new EditRecordsListAdapter(this, this, c, new String[] { MileageData.ToDBNames[MileageData.DATE] },
            new int[] { android.R.id.text1 });
      setListAdapter(mAdapter);

      TextView empty = new TextView(this);
      empty.setText("No records present");
      getListView().setEmptyView(empty);
      LayoutParams params = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
      addContentView(empty, params);

      // restore ImportThread pointer, if we got here by way of an orientation change
      if(getLastNonConfigurationInstance() != null) {
         iThread = (DataImportThread) getLastNonConfigurationInstance();
         iThread.setHandler(iHandler);
      }
   }

   @Override
   public void onResume() {
      super.onResume();
      // This might not be the best way to do this, but if we 'resume' this activity,
      // throw away the old cursor, and re-generate the data. This was needed to
      // support a user changing 'profiles' from the preferences screen
      //FIXME - is this okay? should i always reset the data?!
      getIntent().setData(getURI());
      Cursor c = managedQuery(getIntent().getData(), null, null, null, null);
      mAdapter.changeCursor(c);
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
         getActionBar().setDisplayHomeAsUpEnabled(true);
      }
   }

   // Save the import worker thread, so re-launching the program
   // due to an orientation change will not result in a lockup
   @Override
   public Object onRetainNonConfigurationInstance() {
      removeDialog(PERFORM_IMPORT);
      if(iThread != null) {
         iThread.setHandler(null);
         return iThread;
      }
      return super.onRetainNonConfigurationInstance();
   }

   @Override
   public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
      AdapterView.AdapterContextMenuInfo info;
      try {
         info = (AdapterView.AdapterContextMenuInfo) menuInfo;
      } catch (ClassCastException e) {
         Log.e("TJS", "bad menuInfo", e);
         return;
      }

      Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
      if(cursor == null) {
         // For some reason the requested item isn't available, do nothing
         return;
      }

      // Setup the menu header
      menu.setHeaderTitle(MileageData.getFormattedDate(cursor.getLong(cursor
            .getColumnIndex(MileageData.ToDBNames[MileageData.DATE]))));

      // Add a menu item to delete the note
      menu.add(0, MENU_DELETE, 0, "Delete Entry");
      menu.add(0, MENU_MODIFY, 0, "Edit Entry");
   }

   @Override
   public boolean onContextItemSelected(MenuItem item) {
      AdapterView.AdapterContextMenuInfo info;
      try {
         info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
      } catch (ClassCastException e) {
         Log.e("TJS", "bad menuInfo", e);
         return false;
      }

      switch(item.getItemId()) {
         case MENU_DELETE: {
            // Delete the note that the context menu is for
            mSingleSelection = true;
            mSingleSelectionID = info.id;
            showDialog(MENU_DELETE);
            return true;
         }
         case MENU_MODIFY: {
            // Launch activity to view/edit the currently selected item
            Uri uri = ContentUris.withAppendedId(MileageProvider.ALL_CONTENT_URI, info.id);
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
            return true;
         }
      }
      return false;
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      super.onCreateOptionsMenu(menu);
	  MenuInflater inflater = getMenuInflater();
	  inflater.inflate(R.menu.database_menu, menu);
      return true;
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      switch(item.getItemId()) {
         case R.id.add_item:
            Uri uri = getIntent().getData();
            startActivity(new Intent(MileageTracker.ACTION_INSERT, uri));
            return true;
         case MENU_DELETE:
            showDialog(MENU_DELETE);
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
            startActivityForResult(new Intent(this, EditPreferences.class), R.id.preferences);
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
      ProgressDialog dialog;
      switch(id) {
         case MENU_DELETE:
            return new DeleteConfirm(this);
         case R.id.import_csv:
            return new ImportDialog(this);
         case PERFORM_IMPORT:
            dialog = new ProgressDialog(this);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setMessage("Importing Data...");
            dialog.setCancelable(false);
            iHandler.setDialog(dialog);
            // Thread started below, in onPrepareDialog
            return dialog;
         case PERFORM_EXPORT:
            dialog = new ProgressDialog(this);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setMessage("Exporting Data...");
            dialog.setCancelable(false);
            eHandler.setDialog(dialog);
            return dialog;
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
         case MENU_DELETE:
            ((DeleteConfirm) dialog).computeMessage();
            break;
         case R.id.import_csv:
            dialog = new ImportDialog(this);
            break;
         case R.id.export_csv:
            dialog = new ExportDialog(this);
            break;
      }
   }

   //This is not a true database clear...just clears THIS profile's data!!!
   //FIXME - there should be another which deletes ALL data (For use with import)
   public void clearDB() {
      getContentResolver().delete(getURI(), null, null);
   }

   private Uri getURI() {
      String option = getString(R.string.carSelection);
      String car = PreferenceManager.getDefaultSharedPreferences(this).getString(option, "Car45");
      Uri uri = Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI,car);
      return uri;
   }

   protected void deleteSelected() {
      HashSet<Long> checked = mAdapter.getSelected();
      Uri baseUri = MileageProvider.ALL_CONTENT_URI;
      if(mSingleSelection)
         getContentResolver().delete(ContentUris.withAppendedId(baseUri, mSingleSelectionID), null, null);
      for(Long id : checked)
         getContentResolver().delete(ContentUris.withAppendedId(baseUri, id.longValue()), null, null);
      //FIXME - this is needed, apparently, since i'm not using the same URI as the adapter uses?!
      mAdapter.getCursor().requery();
      mAdapter.notifyDataSetChanged();
      checked.clear();
   }

   protected void deselectAll() {
      mAdapter.clearSelected();
      handleSelection(true);
   }

   //This method queries the Adapter to determine which items are selected.
   //It then walks through the Cursor, adding to the return message a string
   //containing a message for each selected row in the Adapter.
   @SuppressWarnings("unchecked")
   protected String getSelectedMessage() {
      String ret = "";
      HashSet<Long> mySelected = (HashSet<Long>) mAdapter.getSelected().clone();
      if(mSingleSelection)
         mySelected.add(Long.valueOf(mSingleSelectionID));
      
      Cursor cursor = mAdapter.getCursor();
      int idColumn   = cursor.getColumnIndex("_id");
      int dateColumn = cursor.getColumnIndex(MileageData.ToDBNames[MileageData.DATE]);
      int mpgColumn  = cursor.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE]);
      SharedPreferences prefs = mAdapter.getPrefs();
      for(int i=0 ; i<cursor.getCount() ; i++) {
         cursor.moveToPosition(i);
         long id = cursor.getLong(idColumn);
         String str;
         if(mySelected.contains(Long.valueOf(id))) {
            str = MileageData.getSimpleDescription(cursor, dateColumn, mpgColumn, prefs, this);
            if(ret.length() > 0)
               ret = String.format("%s\n%s", ret, str);
            else
               ret = str.toString();
         }
      }
      return ret;
   }

   public void handleSelection(boolean hide) {
      if(hide && mFooter.getVisibility() != View.GONE) {
         mFooter.setVisibility(View.GONE);
         Animation animation = AnimationUtils.loadAnimation(this, R.anim.footer_hide);
         animation.setAnimationListener(this);
         mFooter.startAnimation(animation);
      } else if(!hide && mFooter.getVisibility() != View.VISIBLE) {
         mFooter.setVisibility(View.VISIBLE);
         Animation animation = AnimationUtils.loadAnimation(this, R.anim.footer_show);
         animation.setAnimationListener(this);
         mFooter.startAnimation(animation);
      }
   }

   public void onAnimationEnd(Animation animation) { }

   public void onAnimationRepeat(Animation animation) { }

   public void onAnimationStart(Animation animation) { }

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

   protected class DeleteConfirm extends AlertDialog {
      public DeleteConfirm(Context context) {
         super(context);
         setTitle(R.string.confirm_delete);
         computeMessage();
         setButton(DialogInterface.BUTTON_POSITIVE, "Yes", acceptListener);
         setButton(DialogInterface.BUTTON_NEGATIVE, "No", cancelListener);
      }

      private final OnClickListener acceptListener = new OnClickListener() {
                                                      public void onClick(DialogInterface dialog, int which) {
                                                         deleteSelected();
                                                         deselectAll();
                                                      }
                                                   };
      private final OnClickListener cancelListener = new OnClickListener() {
                                                      public void onClick(DialogInterface dialog, int which) {
                                                         dismiss();
                                                      }
                                                   };

      public void computeMessage() {
         setMessage(getSelectedMessage());
      }
   }

   protected class ImportDialog extends Dialog {
      private final Spinner              filename;
      private final Button               confirm;
      private final View.OnClickListener importListener = new ImportListener();

      // Upon the button being clicked, a new thread will be started, which imports the data into the database,
      // and presents a progress dialog box to the user
      private class ImportListener implements View.OnClickListener {
         public void onClick(View v) {
            String name = (String) filename.getSelectedItem();
            dismiss();
            iThread = new DataImportThread(iHandler, getContext(), Environment.getExternalStorageDirectory(), name);
            iThread.start();
            showDialog(PERFORM_IMPORT);
         }
      }

      public ImportDialog(Context context) {
         super(context);
         String[] files = getCSVFiles();
         if(files != null) {
            setTitle("Select Import File:");
            setContentView(R.layout.import_dialog);
            filename = (Spinner) findViewById(R.id.file_list);
            confirm = (Button) findViewById(R.id.import_button);
            confirm.setOnClickListener(importListener);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            filename.setAdapter(adapter);
            for(String file : files)
               adapter.add(file);
         } else {   //FIXME - this will never fire! check files.length, and display an error message!
            filename = null;
            confirm = null;
            dismiss();
         }
      }
   };

   protected class ExportDialog extends Dialog {
      private final AutoCompleteTextView filename;
      private final Button               confirm;
      private final View.OnClickListener exportListener = new ExportListener();

      private class ExportListener implements View.OnClickListener {
         public void onClick(View v) {
            String name = filename.getText().toString();
            dismiss();
            DataExportThread exporter = new DataExportThread(eHandler,getContext(),Environment.getExternalStorageDirectory(), name);
            exporter.start();
            showDialog(PERFORM_EXPORT);
         }
      }

      public ExportDialog(Context context) {
         super(context);
         setTitle("Select Export File:");
         setContentView(R.layout.export_dialog);
         filename = (AutoCompleteTextView) findViewById(R.id.file_list);
         confirm = (Button) findViewById(R.id.export_button);
         confirm.setOnClickListener(exportListener);
         String[] files = getCSVFiles();
         ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line,
               files);
         filename.setAdapter(adapter);
      }
   }

   public void onClick(View v) {
      switch(v.getId()) {
         case R.id.button_delete:
            mSingleSelection = false;
            showDialog(MENU_DELETE);
            break;
         case R.id.button_deselect:
            deselectAll();
            break;
         }
   }
}
