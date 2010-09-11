package com.switkows.mileage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.PrintWriter;

import com.switkows.mileage.EditRecordsListAdapter.MyListViewItem;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class EditRecordsMenu extends ListActivity {

   private static final int MENU_ADD = 0, MENU_CLEAR = 1, MENU_EXPORT = 3, MENU_IMPORT = 4, PERFORM_IMPORT = 7,
         MENU_DELETE = 5, MENU_MODIFY = 6, MENU_PREFS = 8;

   // 'global' fields for handling Import of data within a separate thread
   protected ImportThread   iThread;
   protected ProgressDialog iProgress;
   protected final Handler  iHandler = new ImportProgressHandler();

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      Intent i = getIntent();
      if(i.getData() == null) {
         i.setData(MileageProvider.CONTENT_URI);
      }
      Cursor c = managedQuery(getIntent().getData(), null, null, null, null);

      getListView().setOnCreateContextMenuListener(this);
      getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
      getListView().setClickable(true);
      setListAdapter(new EditRecordsListAdapter(this, c, new String[] { MileageData.ToDBNames[MileageData.DATE] },
            new int[] { android.R.id.text1 }));

      TextView empty = new TextView(this);
      empty.setText("No records present");
      getListView().setEmptyView(empty);
      LayoutParams params = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
      addContentView(empty, params);

      // restore ImportThread pointer, if we got here by way of an orientation change
      if(getLastNonConfigurationInstance() != null) {
         iThread = (ImportThread) getLastNonConfigurationInstance();
         iThread.setHandler(iHandler);
      }
   }

   @Override
   public void onResume() {
      super.onResume();
      // This might not be the best way to do this, but if we 'resume' this activity,
      // throw away the old cursor, and re-generate the data. This was needed to
      // support a user changing 'profiles' from the preferences screen
      Cursor c = managedQuery(getIntent().getData(), null, null, null, null);
      ((SimpleCursorAdapter) getListAdapter()).changeCursor(c);
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

      Uri uri = ContentUris.withAppendedId(getIntent().getData(), info.id);
      switch(item.getItemId()) {
         case MENU_DELETE: {
            // Delete the note that the context menu is for
            getContentResolver().delete(uri, null, null);
            return true;
         }
         case MENU_MODIFY: {
            // Launch activity to view/edit the currently selected item
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
            return true;
         }
      }
      return false;
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      super.onCreateOptionsMenu(menu);
      menu.add(0, MENU_ADD, 0, "Add Entry").setIcon(android.R.drawable.ic_menu_add);
      menu.add(0, MENU_CLEAR, 0, "Clear Data").setIcon(android.R.drawable.ic_menu_close_clear_cancel);
      menu.add(0, MENU_DELETE, 0, "Delete").setIcon(android.R.drawable.ic_menu_delete);
      menu.add(0, MENU_EXPORT, 0, "Export").setIcon(android.R.drawable.ic_menu_save);
      menu.add(0, MENU_IMPORT, 0, "Import").setIcon(android.R.drawable.ic_menu_upload);
      menu.add(0, MENU_PREFS, 0, "Preferences").setIcon(android.R.drawable.ic_menu_preferences);

      return true;
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      switch(item.getItemId()) {
         case MENU_ADD:
            Uri uri = getIntent().getData();
            startActivity(new Intent(Intent.ACTION_INSERT, uri));
            return true;
         case MENU_DELETE:
            showDialog(MENU_DELETE);
            return true;
         case MENU_CLEAR:
            clearDB();
            return true;
         case MENU_EXPORT: // don't show dialog if the SDcard is not installed.
         case MENU_IMPORT: // It'll issue a Toast-based message, though
            checkSDState(item.getItemId());
            return true;
         case MENU_PREFS: {
            // Launch preferences activity
            startActivityForResult(new Intent(this, EditPreferences.class), MENU_PREFS);
            return true;
         }
      }
      return false;
   }

   @Override
   public void onActivityResult(int requestCode, int resultCode, Intent data) {
      // if we just finished the preferences activity, then get rid of all
      // of the ListActivity data. we could be a little smarter about this,
      // but this shouldn't adversely affect performance, at least not noticeably.
      // This was needed to support a user changing the 'units' from the
      // preferences screen
      if(requestCode == MENU_PREFS) {
         ((EditRecordsListAdapter) getListAdapter()).invalidate();
      }
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
         case MENU_DELETE:
            return new DeleteConfirm(this);
         case MENU_IMPORT:
            return new ImportDialog(this);
         case PERFORM_IMPORT:
            iProgress = new ProgressDialog(this);
            iProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            iProgress.setMessage("Importing Data...");
            iProgress.setCancelable(false);
            // Thread started below, in onPrepareDialog
            return iProgress;
         case MENU_EXPORT:
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
         case MENU_IMPORT:
            dialog = new ImportDialog(this);
            break;
         case MENU_EXPORT:
            dialog = new ExportDialog(this);
            break;
      }
   }

   public void clearDB() {
      getContentResolver().delete(MileageProvider.CONTENT_URI, null, null);
   }

   private MileageData[] readAllEntries() {
      MileageData[] allData = new MileageData[getListAdapter().getCount()];
      Cursor cursor = ((EditRecordsListAdapter) getListAdapter()).getCursor();
      for(int i = 0; i < allData.length; i++) {
         cursor.moveToPosition(i);
         allData[i] = new MileageData(getApplicationContext(), cursor);
      }
      return allData;
   }

   protected void deleteSelected() {
      ListView view = getListView();
      for(int i = 0; i < view.getCount(); i++) {
         if(view.isItemChecked(i)) {
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), view.getItemIdAtPosition(i));
            getContentResolver().delete(uri, null, null);
         }
      }
   }

   protected void deselectAll() {
      getListView().clearChoices();
   }

   protected String getSelectedMessage() {
      String ret = "";
      ListView view = getListView();
      for(int i = 0; i < view.getCount(); i++) {
         Log.d("TJS", "Checking item " + i + " in the listView");
         MyListViewItem mine = (MyListViewItem) view.getAdapter().getView(i, null, null);
         if(mine.isChecked()) {
            // if(view.isItemChecked(i)) {
            Log.d("TJS", "   Item " + i + " is checked!!");
            // MyListViewItem mine = (MyListViewItem)view.getAdapter().getView(i, null, null);
            String str = mine.toString();
            // Cursor cursor = (Cursor) view.getItemAtPosition(i);
            // float mpg = cursor.getFloat(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE]));
            // String date = MileageData.getDateFormatter().format(
            // cursor.getLong(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.DATE])));
            // String str = String.format("%s (%2.1f MPG)\n", date, mpg);
            ret += str;
         }
      }
      return ret;
   }

   protected void exportFile(String filename) {
      File loc = Environment.getExternalStorageDirectory();
      // Log.d("TJS",Environment.getExternalStorageState());
      File csv_file = new File(loc, filename);
      // Log.d("TJS","File exists: " + csv_file.exists());
      // Log.d("TJS","is file: " + csv_file.isFile());
      // Log.d("TJS","is writeable: " + csv_file.canWrite());
      try {
         // FileOutputStream stream = new FileOutputStream(csv_file);
         // PrintWriter writer = new PrintWriter(stream);
         PrintWriter writer = new PrintWriter(csv_file);
         MileageData[] data = readAllEntries();
         writer.println(MileageData.exportCSVTitle());
         for(MileageData word : data)
            writer.println(word.exportCSV());
         writer.close();
         Toast.makeText(this, "Data Successfully Saved to " + filename, Toast.LENGTH_LONG).show();

      } catch (FileNotFoundException e) {
         Log.e("TJS", e.toString());
         Toast.makeText(this, "Error! could not access/write " + filename, Toast.LENGTH_LONG).show();
      }
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
            iThread = new ImportThread(iHandler, getContext(), name);
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
         } else {
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

      // FIXME - it might be good to make this appear very similar to the import listener (that is, start a new
      // thread, and present the user with a progress dialog)
      private class ExportListener implements View.OnClickListener {
         public void onClick(View v) {
            String name = filename.getText().toString();
            exportFile(name);
            dismiss();
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

   private class ImportProgressHandler extends Handler {
      @Override
      public void handleMessage(Message m) {
         Bundle data = m.getData();
         if(data.containsKey(ImportThread.IMPORT_MAX_STR)) {
            // iProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            iProgress.setProgress(0);
            iProgress.setMax(data.getInt(ImportThread.IMPORT_MAX_STR));
         }
         if(data.containsKey(ImportThread.IMPORT_PROGRESS_STR))
            iProgress.setProgress(data.getInt(ImportThread.IMPORT_PROGRESS_STR));
         else if(data.containsKey(ImportThread.IMPORT_FINISHED_STR)) {
            iProgress.setProgress(iProgress.getMax()); // might not be
            // worth updating
            // this, but put
            // it here, just
            // in case
            dismissDialog(PERFORM_IMPORT);
            iThread = null;
            Toast
                  .makeText(getApplicationContext(), data.getString(ImportThread.IMPORT_FINISHED_STR),
                        Toast.LENGTH_LONG).show();
         }
      }
   }
}
