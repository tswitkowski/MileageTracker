package com.switkows.mileage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class EditRecordsMenu extends ListActivity {

   private static final int    MENU_ADD            = 0, MENU_CLEAR = 1, MENU_EXPORT = 3, MENU_IMPORT = 4,
         PERFORM_IMPORT = 7, MENU_DELETE = 5, MENU_MODIFY = 6;

   private static final String IMPORT_PROGRESS_STR = "linesRead", IMPORT_MAX_STR = "numLines",
         IMPORT_FINISHED_STR = "toast";

   private SharedPreferences   prefs;

   // 'global' fields for handling Import of data within a separate thread
   protected ImportThread      iThread;
   protected ProgressDialog    iProgress;
   protected final Handler     iHandler = new ImportProgressHandler();

   private class ImportProgressHandler extends Handler {
      @Override
      public void handleMessage(Message m) {
         Bundle data = m.getData();
         if(data.containsKey(IMPORT_MAX_STR)) {
            // iProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            iProgress.setProgress(0);
            iProgress.setMax(data.getInt(IMPORT_MAX_STR));
         } else if(data.containsKey(IMPORT_PROGRESS_STR))
            iProgress.setProgress(data.getInt(IMPORT_PROGRESS_STR));
         else if(data.containsKey(IMPORT_FINISHED_STR)) {
            iProgress.setProgress(iProgress.getMax()); // might not be
            // worth updating
            // this, but put
            // it here, just
            // in case
            dismissDialog(PERFORM_IMPORT);
            Toast.makeText(getApplicationContext(),
                  data.getString(IMPORT_FINISHED_STR),
                  Toast.LENGTH_LONG).show();
         }
      }
   }
   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      if(prefs == null)
         prefs = PreferenceManager.getDefaultSharedPreferences(this);
      Intent i = getIntent();
      if(i.getData() == null) {
         i.setData(MileageProvider.CONTENT_URI);
      }
      Cursor c = managedQuery(getIntent().getData(), null, null, null, null);

      getListView().setOnCreateContextMenuListener(this);
      getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
      getListView().setClickable(true);
      setListAdapter(new ExtendedCheckBoxListAdapter(this, c, new String[] { MileageData.ToDBNames[MileageData.DATE] },
            new int[] { android.R.id.text1 }));
   }

   public boolean performItemClick(View view, int position, long id) {
      return false;
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
            clearDB(true);
            return true;
         case MENU_EXPORT: // don't show dialog if the SDcard is not installed.
         case MENU_IMPORT: // It'll issue a Toast-based message, though
            checkSDState(item.getItemId());
            return true;
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
         case PERFORM_IMPORT:
            Log.d("TJS", "Starting ImportThread...");
            iThread.start();
            break;
         case MENU_EXPORT:
            dialog = new ExportDialog(this);
            break;
      }
   }

   public void clearDB(boolean repaint) {
      getContentResolver().delete(MileageProvider.CONTENT_URI, null, null);
   }

   private MileageData[] readAllEntries() {
      MileageData[] allData = new MileageData[getListAdapter().getCount()];
      Cursor cursor = ((ExtendedCheckBoxListAdapter) getListAdapter()).getCursor();
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
         if(view.isItemChecked(i)) {
            Cursor cursor = (Cursor) view.getItemAtPosition(i);
            float mpg = cursor.getFloat(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE]));
            String date = MileageData.getDateFormatter().format(
                  cursor.getLong(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.DATE])));
            String str = String.format("%s (%2.1f MPG)\n", date, mpg);
            ret += str;
         }
      }
      return ret;
   }

   protected class ImportThread extends Thread {
      Handler mHandler;
      Context mContext;
      String  mFile;

      ImportThread(Handler h, Context c, String f) {
         mHandler = h;
         mContext = c;
         mFile = f;
         // Log.d("TJS","Created ImportThread...");
      }

      @Override
      public void run() {
         // Log.d("TJS","Started ImportThread");
         File in_file = new File(Environment.getExternalStorageDirectory(), mFile);
         String importMessage = "Error! could not access/read " + mFile;
         try {
            BufferedReader reader = new BufferedReader(new FileReader(in_file));
            String line;
            clearDB(false);
            int lineCount = 0;
            while(reader.ready()) {
               reader.readLine();
               lineCount++;
            }
            Message msg = mHandler.obtainMessage();
            Bundle b = new Bundle();
            b.putInt(IMPORT_MAX_STR, lineCount);
            msg.setData(b);
            mHandler.sendMessage(msg);

            reader = new BufferedReader(new FileReader(in_file));
            lineCount = 0;
            while(reader.ready()) {
               line = reader.readLine();
               String[] fields = line.split(",");
               if(fields[0].equals(MileageData.ToDBNames[0])) {
                  // Log.d("TJS","Found header in CSV file");
                  continue;
               }
               // are we an old format, without the 'cars' column?! if so, add it!
               if(fields.length == 10) {
                  String[] newFields = new String[11];
                  for(int i = 0; i < fields.length; i++)
                     newFields[i] = fields[i];
                  newFields[10] = prefs.getString(mContext.getString(R.string.carSelection), "Car45");
                  fields = newFields;
                  Log.v("TJS", "Importing entry from CSV file into current car: " + newFields[10]);
               } else if(fields.length == 11) {
                  // Log.v("TJS","Importing entry from CSV file into car: "+fields[10]);
               } else {
                  Log.d("TJS", "Skipping import line: only " + fields.length + "elements in CSV line!!");
               }
               // Log.d("TJS","Read line '"+line+"', date = '"+fields[0]+"'...");
               MileageData record = new MileageData(mContext, fields);
               getContentResolver().insert(MileageProvider.CONTENT_URI, record.getContent());
               lineCount++;
               msg = mHandler.obtainMessage();
               b = new Bundle();
               b.putInt(IMPORT_PROGRESS_STR, lineCount);
               msg.setData(b);
               mHandler.sendMessage(msg);
            }
            reader.close();
            importMessage = "Data Successfully imported from " + mFile;
            Log.d("TJS", importMessage);
         } catch (FileNotFoundException e) {
            Log.e("TJS", e.toString());
         } catch (IOException e) {
            Log.e("TJS", e.toString());
         }
         // Toast.makeText(this, importMessage, Toast.LENGTH_LONG);
         Message msg = mHandler.obtainMessage();
         Bundle b = new Bundle();
         b.putString(IMPORT_FINISHED_STR, importMessage);
         msg.setData(b);
         mHandler.sendMessage(msg);
      }
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

      //FIXME - it might be good to make this appear very similar to the import listener (that is, start a new
      //thread, and present the user with a progress dialog)
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

   protected class MyListItem implements Comparable<MyListItem> {

      private String  mLabel;
      private boolean mChecked;

      public MyListItem(String label, boolean checked) {
         mLabel = label;
         mChecked = checked;
      }

      public void setChecked(boolean checked) {
         mChecked = checked;
         // Log.d("TJS","Setting checked state to '"+checked+"'...");
      }

      public boolean getChecked() {
         return mChecked;
      }

      public void setText(String text) {
         mLabel = text;
      }

      public String getText() {
         return mLabel;
      }

      public int compareTo(MyListItem another) {
         if(mLabel != null)
            return mLabel.compareTo(another.getText());
         else
            return 0;
      }
   }

   protected class MyListViewItem extends LinearLayout implements Checkable {
      private TextView   mLabel;
      private CheckBox   mCheckBox;
      private MyListItem data;

      public MyListViewItem(Context context, MyListItem item) {
         super(context);
         mLabel = new TextView(context);
         mCheckBox = new CheckBox(context);
         addView(mLabel);
         addView(mCheckBox);
         data = item;
         mLabel.setText(data.getText());
         LayoutParams params = new LayoutParams(android.view.ViewGroup.LayoutParams.FILL_PARENT,
               android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
         mLabel.setLayoutParams(params);
         mCheckBox.setChecked(data.getChecked());
         mCheckBox.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
               data.setChecked(isChecked());
            }
         });
         params = new LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
               android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 0);
         mCheckBox.setLayoutParams(params);

         setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
               showContextMenu();
               // Log.d("TJS","Clicked...");
            }
         });
      }

      public void setText(String text) {
         mLabel.setText(text);
         data.setText(text);
      }

      public void toggle() {
         // Log.d("TJS","ListviewItem.toggle called...");
         setChecked(!isChecked());
      }

      public void setChecked(boolean checked) {
         // Log.d("TJS","ListViewItem.setChecked called with '"+checked+"'");
         data.setChecked(checked);
         mCheckBox.setChecked(checked);
      }

      public boolean isChecked() {
         return mCheckBox.isChecked();
      }

      public void refresh() {
         // Log.v("TJS","refreshing row. checked="+data.getChecked());
         mCheckBox.setChecked(data.getChecked());
         mCheckBox.postInvalidate();
         // Log.v("TJS","data.checked="+data.getChecked()+", checkbox.checked="+mCheckBox.isChecked());
      }
   }

   public class ExtendedCheckBoxListAdapter extends SimpleCursorAdapter {

      /** Remember our context so we can use it when constructing views. */
      private Context mContext;

      /**
       * 
       * @param context
       *           - Render context
       */
      public ExtendedCheckBoxListAdapter(Context context, Cursor c, String[] from, int[] to) {
         super(context, R.layout.record_list_item, c, from, to);
         mContext = context;
      }

      /**
       * Do not recycle a view if one is already there, if not the data could get corrupted and the checkbox state could
       * be lost.
       * 
       * @param convertView
       *           The old view to overwrite
       * @returns a CheckBoxifiedTextView that holds wraps around an CheckBoxifiedText
       */
      private MyListViewItem[] mDisplays;

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
         if(mDisplays != null && (mDisplays.length != getCursor().getCount())) // if the array size doesn't match the
            // database, reset everything!
            mDisplays = null;
         if(mDisplays == null) {
            // Log.v("TJS","Creating array...");
            mDisplays = new MyListViewItem[getCursor().getCount()];
            // Log.d("TJS","Creating array with "+mDisplays.length+" entries...");
         }
         // Log.d("TJS","Trying to retrieve list item "+position+" (cursor contains "+getCursor().getCount()+" elements)");
         if(mDisplays[position] == null) {
            Cursor cursor = getCursor();
            cursor.moveToPosition(position);
            String date = MileageData.getDateFormatter().format(
                  cursor.getLong(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.DATE])));
            float mpg = cursor.getFloat(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE]));
            // Log.v("TJS","creating row "+position);
            mDisplays[position] = new MyListViewItem(mContext, new MyListItem(String
                  .format("%s (%2.1f MPG)", date, mpg), false));
         }
         mDisplays[position].refresh();
         return mDisplays[position];
      }
   }
}
