package com.switkows.mileage;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

public class ImportExportProgressHandler extends Handler {
   public static final String MAX_KEY       = "max",
                              CURRENT_KEY   = "current",
                              FINISHED_KEY  = "done";
   private final Activity   mContext;
   private final int        mDialogId;
   private ProgressDialog   mDialog;
   
   public ImportExportProgressHandler(Activity context, int dialogId) {
      super();
      mContext  = context;
      mDialogId = dialogId;
   }
   @Override
   public void handleMessage(Message m) {
      Bundle data = m.getData();
      if(data.containsKey(MAX_KEY)) {
         mDialog.setProgress(0);
         mDialog.setMax(data.getInt(MAX_KEY));
      }
      if(data.containsKey(CURRENT_KEY))
         mDialog.setProgress(data.getInt(CURRENT_KEY));
      else if(data.containsKey(FINISHED_KEY)) {
         mDialog.setProgress(mDialog.getMax());
         // might not be worth updating this, but put it here, just in case
         mContext.dismissDialog(mDialogId);//PERFORM_IMPORT);
//         if(mAdapter != null)
//            mAdapter.getCursor().requery();
//         iThread = null;
         Toast.makeText(mContext, data.getString(FINISHED_KEY),
                     Toast.LENGTH_LONG).show();
      }
   }
   
   public void setDialog(ProgressDialog dialog) {
      mDialog = dialog;
   }
}
