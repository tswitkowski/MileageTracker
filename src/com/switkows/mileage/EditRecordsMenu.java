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
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
	
    private static final int
		MENU_ADD=0,
	    MENU_CLEAR=1,
	    MENU_EXPORT=3,
	    MENU_IMPORT=4,
	    MENU_DELETE=5,
	    MENU_MODIFY=6;

	private SharedPreferences prefs;

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if(prefs==null)
			prefs = PreferenceManager.getDefaultSharedPreferences(this);
		Intent i = getIntent();
		if(i.getData() == null) {
			i.setData(MileageProvider.CONTENT_URI);
		}
		Cursor c = managedQuery(getIntent().getData(), null, null, null, null);

		getListView().setOnCreateContextMenuListener(this);
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        getListView().setClickable(true);
        setListAdapter(new ExtendedCheckBoxListAdapter(this,c,new String[] {MileageData.ToDBNames[MileageData.DATE]},new int[] {android.R.id.text1}));
	}
    public boolean performItemClick(View view, int position, long id) {
    	return false;
    }

    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e("TJS", "bad menuInfo", e);
            return;
        }

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }

        // Setup the menu header
        menu.setHeaderTitle(MileageData.getFormattedDate(cursor.getLong(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.DATE]))));

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
        switch (item.getItemId()) {
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
    	menu.add(0,MENU_EXPORT,0,"Export").setIcon(android.R.drawable.ic_menu_save);
    	menu.add(0, MENU_IMPORT,0,"Import").setIcon(android.R.drawable.ic_menu_upload);

    	return true;
    }
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
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
    	case MENU_EXPORT :	//don't show dialog if the SDcard is not installed.
    	case MENU_IMPORT :	//It'll issue a Toast-based message, though
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
    	case MENU_DELETE :
    		return new DeleteConfirm(this);
    	case MENU_IMPORT :
			return new ImportDialog(this);
    	case MENU_EXPORT :
    		return new ExportDialog(this);
    	}
    	Log.i("TJS","Got here. should not have!");
    	return null;
    }
    
    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
    	super.onPrepareDialog(id, dialog);
    	switch(id) {
    	case MENU_DELETE :
    		((DeleteConfirm)dialog).computeMessage();
    		break;
    	case MENU_IMPORT :
    		dialog = new ImportDialog(this);
    		break;
    	case MENU_EXPORT :
    		dialog = new ExportDialog(this);
    		break;
    	}
    }

    public void clearDB(boolean repaint) {
    	getContentResolver().delete(MileageProvider.CONTENT_URI, null, null);
    }
	private MileageData[] readAllEntries() {
		MileageData[] allData = new MileageData[getListAdapter().getCount()];
		Cursor cursor = ((ExtendedCheckBoxListAdapter)getListAdapter()).getCursor(); 
		for(int i=0 ; i<allData.length ; i++) {
			cursor.moveToPosition(i);
			allData[i] = new MileageData(getApplicationContext(),cursor);
		}
		return allData;
	}
	
	
	protected void deleteSelected() {
		ListView view = getListView();
		for(int i=0 ; i<view.getCount() ; i++) {
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
		for(int i=0 ; i<view.getCount() ; i++) {
			if(view.isItemChecked(i)) {
				Cursor cursor = (Cursor)view.getItemAtPosition(i);
				float mpg = cursor.getFloat(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE]));
				String date = MileageData.getDateFormatter().format(cursor.getLong(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.DATE])));
				String str =  String.format("%s (%2.1f MPG)\n", date,mpg);
				ret += str;
			}
		}
		return ret;
	}

	protected void importFile(String filename) {
		File in_file = new File(Environment.getExternalStorageDirectory(),filename);
		String importMessage = "Error! could not access/read "+filename;
		try {
			BufferedReader reader = new BufferedReader(new FileReader(in_file));
			String line;
			clearDB(false);
			while( reader.ready()) {
				line = reader.readLine();
				String[] fields = line.split(",");
				if(fields[0].equals(MileageData.ToDBNames[0]))
					continue;
				//are we an old format, without the 'cars' column?! if so, add it!
				if(fields.length == 10)
				{
					String[] newFields = new String[11];
					for(int i=0 ; i<fields.length ; i++)
						newFields[i] = fields[i];
					newFields[10] = prefs.getString(this.getString(R.string.carSelection), "Car45");
					fields = newFields;
				}
				MileageData record = new MileageData(this,fields);
		    	getContentResolver().insert(MileageProvider.CONTENT_URI,record.getContent());
			}
			reader.close();
			importMessage = "Data Successfully imported from " +filename;
		} 	catch (FileNotFoundException e) {
			Log.e("TJS",e.toString());
		} catch (IOException e) {
			Log.e("TJS",e.toString());
		}
		Toast.makeText(this, importMessage, Toast.LENGTH_LONG);
	}
		
	protected void exportFile(String filename) {
		File loc = Environment.getExternalStorageDirectory();
//		Log.d("TJS",Environment.getExternalStorageState());
		File csv_file = new File(loc,filename);
//		Log.d("TJS","File exists: " + csv_file.exists());
//		Log.d("TJS","is file: " + csv_file.isFile());
//		Log.d("TJS","is writeable: " + csv_file.canWrite());
		try {
//			FileOutputStream stream = new FileOutputStream(csv_file);
//			PrintWriter writer = new PrintWriter(stream);
			PrintWriter writer = new PrintWriter(csv_file);
        	MileageData [] data = readAllEntries();
        	writer.println(MileageData.exportCSVTitle());
        	for(MileageData word : data)
        		writer.println(word.exportCSV());
        	writer.close();
        	Toast.makeText(this, "Data Successfully Saved to "+filename, Toast.LENGTH_LONG).show();

		} catch(FileNotFoundException e) {
			Log.e("TJS",e.toString());
			Toast.makeText(this, "Error! could not access/write "+filename, Toast.LENGTH_LONG).show();
		};
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
		} else {	//we should NEVER enter here, as it is checked before import/export dialogs are shown!
			Toast.makeText(this, "Error! SDCARD not accessible!", Toast.LENGTH_LONG).show();
			return null;
		}
	}

	protected class DeleteConfirm extends AlertDialog {
		public DeleteConfirm(Context context) {
			super(context);
    		setTitle(R.string.confirm_delete);
    		computeMessage();
    		setButton(AlertDialog.BUTTON_POSITIVE, "Yes", acceptListener);
    		setButton(AlertDialog.BUTTON_NEGATIVE, "No", cancelListener);
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
		private final Spinner filename;
		private final Button confirm;
		private final View.OnClickListener importListener = new View.OnClickListener() {
			public void onClick(View v) {
				String name = (String)filename.getSelectedItem();
				dismiss();
				importFile(name);
			}
		};
		public ImportDialog(Context context) {
			super(context);
			String[] files = getCSVFiles();
			if(files != null) {
				setTitle("Select Import File:");
				setContentView(R.layout.import_dialog);
				filename = (Spinner)findViewById(R.id.file_list);
				confirm = (Button)findViewById(R.id.import_button);
				confirm.setOnClickListener(importListener);
				ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item);
				adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
				filename.setAdapter(adapter);
				for(String file : files)
					adapter.add(file);
			}
			else {
				filename = null;
				confirm = null;
				dismiss();
			}
		}
	};
	
	protected class ExportDialog extends Dialog {
		private final AutoCompleteTextView filename;
		private final Button confirm;
		private final View.OnClickListener exportListener = new View.OnClickListener() {
			public void onClick(View v) {
				String name = (String)filename.getText().toString();
				exportFile(name);
				dismiss();
			}
		};
		public ExportDialog(Context context) {
			super(context);
			setTitle("Select Export File:");
			setContentView(R.layout.export_dialog);
			filename = (AutoCompleteTextView)findViewById(R.id.file_list);
			confirm  = (Button)findViewById(R.id.export_button);
			confirm.setOnClickListener(exportListener);
			String[] files = getCSVFiles();
			ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
	                 android.R.layout.simple_dropdown_item_1line, files);
			filename.setAdapter(adapter);
		}
	}
	
	protected class MyListItem implements Comparable<MyListItem> {

		private String mLabel;
		private boolean mChecked;
		public MyListItem(String label, boolean checked) {
			mLabel = label;
			mChecked = checked;
		}
		
		public void setChecked(boolean checked) {
			mChecked = checked;
//			Log.d("TJS","Setting checked state to '"+checked+"'...");
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
		private TextView mLabel;
		private CheckBox mCheckBox;
		private MyListItem data;
		public MyListViewItem(Context context, MyListItem item) {
			super(context);
			mLabel = new TextView(context);
			mCheckBox = new CheckBox(context);
			addView(mLabel);
			addView(mCheckBox);
			data = item;
			mLabel.setText(data.getText());
			LayoutParams params = new LayoutParams(LayoutParams.FILL_PARENT,LayoutParams.WRAP_CONTENT,1);
			mLabel.setLayoutParams(params);
			mCheckBox.setChecked(data.getChecked());
			mCheckBox.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					data.setChecked(isChecked());
				}
			});
			params = new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,0);
			mCheckBox.setLayoutParams(params);
			
			setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					showContextMenu();
//					Log.d("TJS","Clicked...");
				}
			});
		}
		public void setText(String text) {
			mLabel.setText(text);
			data.setText(text);
		}
		public void toggle() {
//			Log.d("TJS","ListviewItem.toggle called...");
			setChecked(!isChecked());
		}
		public void setChecked(boolean checked) {
//			Log.d("TJS","ListViewItem.setChecked called with '"+checked+"'");
			data.setChecked(checked);
			mCheckBox.setChecked(checked);
		}
		public boolean isChecked() {
			return mCheckBox.isChecked();
		}
		
		public void refresh() {
//			Log.v("TJS","refreshing row. checked="+data.getChecked());
			mCheckBox.setChecked(data.getChecked());
			mCheckBox.postInvalidate();
//			Log.v("TJS","data.checked="+data.getChecked()+", checkbox.checked="+mCheckBox.isChecked());
		}
	}

	public class ExtendedCheckBoxListAdapter extends SimpleCursorAdapter {

	     /** Remember our context so we can use it when constructing views. */
	     private Context mContext;

	     /**
	      *
	      * @param context - Render context
	      */
	     public ExtendedCheckBoxListAdapter(Context context, Cursor c,String[] from, int[] to) {
			super(context, R.layout.record_list_item, c, from, to);
	          mContext = context;
	     }

	    /**
	     * Do not recycle a view if one is already there, if not the data could get corrupted and
	     * the checkbox state could be lost.
	     * @param convertView The old view to overwrite
	     * @returns a CheckBoxifiedTextView that holds wraps around an CheckBoxifiedText */
	     private MyListViewItem[] mDisplays;
	    public View getView(int position, View convertView, ViewGroup parent ){
	    	if(mDisplays==null) {
//	    		Log.v("TJS","Creating array...");
	    		mDisplays = new MyListViewItem[getCursor().getCount()];
	    	}
	    	if(mDisplays[position]==null) {
				Cursor cursor = getCursor();
				cursor.moveToPosition(position);
				String date = MileageData.getDateFormatter().format(cursor.getLong(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.DATE])));
				float mpg = cursor.getFloat(cursor.getColumnIndex(MileageData.ToDBNames[MileageData.ACTUAL_MILEAGE]));
//				Log.v("TJS","creating row "+position);
			    mDisplays[position] = new MyListViewItem(mContext, new MyListItem(
			    		 String.format("%s (%2.1f MPG)", date,mpg),
			    		 false));
	    	}
	    	mDisplays[position].refresh();
	    	return mDisplays[position];
	    }
	} 
}
