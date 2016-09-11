package com.switkows.mileage;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.switkows.mileage.ProfileSelector.ProfileSelectorCallbacks;
import com.switkows.mileage.widgets.StatisticsView;

import java.util.HashMap;
import java.util.Locale;

//FIXME - merge MileageTracker and EditRecordsMenu into single activity to take advantage of Action Bar enhancements. This will require:
//1. moving most code from MileageTracker Activity to a Fragment
//2. removing EditRecordsMenu Activity
//3. correctly replacing new Activity calls with Fragment transactions
public class MileageTracker extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>, ProfileSelectorCallbacks {
   public static final String       ACTION_INSERT = "com.switkows.mileage.INSERT";

   private static final int         RESULT_SIGN_IN = 4544;
   /** Called when the activity is first created. */
   private static LinearLayout[]    charts;
   private ShowLargeChart[]         chartListeners;
   private MileageChartManager      chartManager;
   private static StatisticsAdapter mStatsAdapter;
   protected ProfileSelector        mProfileAdapter;

   // Firebase stuff
   private FirebaseAuth mAuth;
   private FirebaseAuth.AuthStateListener mAuthListener;

   // Google account stuff
   private GoogleApiClient mGoogleApiClient;


   private Context                  mContext;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      Log.d("TJS", "Started onCreate...");
      PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
      mContext = this;
      setContentView(R.layout.main);
      Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
      setSupportActionBar(toolbar);
      mStatsAdapter = new StatisticsAdapter();
      // grab pointers to all my graphical elements
      initializePointers();
      getSupportLoaderManager().initLoader(45, null, this);
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
         mProfileAdapter = ProfileSelector.setupActionBar(this, null);
      // Log.d("TJS", "Finished opening/creating database");

      // Firebase stuff
      mAuth = FirebaseAuth.getInstance();
      mAuthListener = new FirebaseAuth.AuthStateListener() {
         public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
               // User is signed in
               Log.d("TJS", "onAuthStateChanged:signed_in:" + user.getUid());
            } else {
               // User is signed out
               Log.d("TJS", "onAuthStateChanged:signed_out");
            }
            updateUI(user);
         }
      };

      // Configure Google Sign In
      GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build();

      mGoogleApiClient = new GoogleApiClient.Builder(this)
            .enableAutoManage(this /* FragmentActivity */, new GoogleAuthListener() /* OnConnectionFailedListener */)
            .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
            .build();

      View sib = findViewById(R.id.sign_in_button);
      if(sib != null) {
         sib.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
               Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
               startActivityForResult(signInIntent, RESULT_SIGN_IN);
            }
         });
      }

      View sob = findViewById(R.id.sign_out_button);
      if(sob != null) {
         sob.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
               // Firebase sign out
               mAuth.signOut();

               // Google sign out
               Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                     new ResultCallback<Status>() {
                        public void onResult(@NonNull Status status) {
                           updateUI(null);
                        }
                     });
            }
         });
      }
//      mAuth.
   }

   @Override
   public void onResume() {
      super.onResume();
      // FIXME - maybe be a bit smarter about when we generate charts!
      getSupportLoaderManager().restartLoader(45, null, this);
      if(mProfileAdapter != null)
         mProfileAdapter.loadActionBarNavItems(this);
   }

   @Override
   public void onStart() {
      super.onStart();
      mAuth.addAuthStateListener(mAuthListener);
   }

   @Override
   public void onStop() {
      super.onStop();
      if (mAuthListener != null) {
         mAuth.removeAuthStateListener(mAuthListener);
      }
   }

   public void generateCharts(Cursor cursor) {
      chartManager = new MileageChartManager(mContext, cursor);
      for(LinearLayout chart : charts)
         chart.removeAllViews();
      chartManager.addCharts(charts, chartListeners);
   }

   public void printStatistics() {
      String ecoUnits = chartManager.getEconomyUnits();
      String distUnits = chartManager.getDistanceUnits();
      mStatsAdapter.setValue(0, chartManager.getAverageMPG(), ecoUnits);
      mStatsAdapter.setValue(1, chartManager.getAverageTrip(), distUnits);
      mStatsAdapter.setValue(2, chartManager.getBestMPG(), ecoUnits);
      mStatsAdapter.setValue(3, chartManager.getBestTrip(), distUnits);
      mStatsAdapter.setValue(4, chartManager.getAverageDiff() * 100, "%");
      mStatsAdapter.notifyDataSetInvalidated();
   }

   public void initializePointers() {
      // root = (LinearLayout)findViewById(R.id.root);
      charts = new LinearLayout[3];
      charts[0] = (LinearLayout)findViewById(R.id.chart1);
      charts[1] = (LinearLayout)findViewById(R.id.chart2);
      charts[2] = (LinearLayout)findViewById(R.id.chart3);
      chartListeners = new ShowLargeChart[3];
      chartListeners[0] = new ShowLargeChart(mContext, 0);
      chartListeners[1] = new ShowLargeChart(mContext, 0);
      chartListeners[2] = new ShowLargeChart(mContext, 0);
      ListView mStatsView = (ListView) findViewById(R.id.statistics_list);
      mStatsView.setAdapter(mStatsAdapter);
   }

   public Uri getCurrentProfileURI() {
      String car = getCurrentProfile();
      return Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI, car);
   }

   public String getCurrentProfile() {
      String option = this.getString(R.string.carSelection);
      return PreferenceManager.getDefaultSharedPreferences(mContext).getString(option, "Car45");
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.main_menu, menu);
      return true;
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      switch(item.getItemId()) {
         case R.id.add_item:
            startActivity(new Intent(ACTION_INSERT, getCurrentProfileURI()));
            return true;
         case R.id.show_data:
            startActivity(new Intent(mContext, EditRecordsMenu.class));
            return true;
         case R.id.preferences:
            startActivity(new Intent(mContext, EditPreferences.class));
            return true;
      }
      return false;
   }

   @Override
   public void onActivityResult(int requestCode, int resultCode, Intent data) {
      super.onActivityResult(requestCode, resultCode, data);

      // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
      if (requestCode == RESULT_SIGN_IN) {
         GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
         if (result.isSuccess()) {
            // Google Sign In was successful, authenticate with Firebase
            GoogleSignInAccount account = result.getSignInAccount();
            firebaseAuthWithGoogle(account);
         } else {
            // Google Sign In failed, update UI appropriately
            updateUI(null);
         }
      }
   }

   private class StatisticsAdapter extends BaseAdapter {

      private final String[]                  mLabels;
      private final LayoutInflater            mInflater;
      private HashMap<String, FloatWithUnits> mList;

      StatisticsAdapter() {
         super();
         mLabels = getResources().getStringArray(R.array.StatisticsLabels);
         mList = new HashMap<String, FloatWithUnits>();
         for(String label : mLabels) {
            mList.put(label, new FloatWithUnits(-1, "??"));
         }
         mInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      }

      @Override
      public boolean areAllItemsEnabled() {
         return false;
      }

      @Override
      public boolean isEnabled(int position) {
         return false;
      }

      public int getCount() {
         return mList.size();
      }

      public FloatWithUnits getItem(int position) {
         return mList.get(mLabels[position]);
      }

      public long getItemId(int position) {
         return position;
      }

      @SuppressLint("InflateParams")
      public View getView(int position, View convertView, ViewGroup parent) {
         StatisticsView stats;
         if(convertView == null) {
            stats = (StatisticsView)mInflater.inflate(R.layout.statistics_item, null);
         } else {
            stats = (StatisticsView)convertView;
         }
         stats.setValue(getItem(position).getFormattedString());
         stats.setLabel(mLabels[position]);
         return stats;
      }

      void setValue(int pos, float value, String units) {
         if(!(pos >= 0 && pos < mLabels.length))
            return;
         String label = mLabels[pos];
         if(mList.containsKey(label)) {
            FloatWithUnits val = mList.get(label);
            val.setUnits(units);
            val.setValue(value);
         } else {
            Log.e("TJS", "Should not have gotten here! HashMap not initialized or accessed correctly!!");
         }
      }
   }

   protected class FloatWithUnits {
      private float  mValue;
      private String mUnits;

      FloatWithUnits(float val, String un) {
         setValue(val);
         setUnits(un);
      }

      public void setValue(float val) {
         mValue = val;
      }

      void setUnits(String un) {
         mUnits = un;
      }

      String getFormattedString() {
         return String.format(Locale.getDefault(), "%.1f %s", mValue, mUnits);
      }
   }

   protected class ShowLargeChart implements OnClickListener {
      private Context mContext;
      private Intent  launcher;
      private int     mID;

      ShowLargeChart(Context c, int id) {
         mContext = c;
         mID = id;
         launcher = new Intent(mContext, ChartViewer.class);
      }

      void setID(int id) {
         mID = id;
      }

      public void onClick(View v) {
         boolean isUS = MileageData.isMilesGallons(PreferenceManager.getDefaultSharedPreferences(mContext), mContext);
         launcher.putExtra(ChartViewer.UNITS_KEY, isUS);
         launcher.putExtra(ChartViewer.CHART_KEY, mID);
         startActivity(launcher);
      }
   }

   /**
    * Data loader for any cursor's to be used by this activity
    * (only handles querying mileage data for a specific profile)
    */
   public Loader<Cursor> onCreateLoader(int id, Bundle args) {
      return new CursorLoader(this, getCurrentProfileURI(), null, null, null, null);
   }

   public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
      generateCharts(cursor);
      printStatistics();
   }

   public void onLoaderReset(Loader<Cursor> cursor) {
      chartManager = null;
   }

   public void onProfileChange(String newProfile) {
      mProfileAdapter.applyPreferenceChange(newProfile);
      getSupportLoaderManager().restartLoader(45, null, this);
   }

   private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
      Log.d("TJS", "firebaseAuthWithGoogle:" + acct.getId());
      // [START_EXCLUDE silent]
//      showProgressDialog();
      // [END_EXCLUDE]

      AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
      mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
               public void onComplete(@NonNull Task<AuthResult> task) {
                  Log.d("TJS", "signInWithCredential:onComplete:" + task.isSuccessful());

                  // If sign in fails, display a message to the user. If sign in succeeds
                  // the auth state listener will be notified and logic to handle the
                  // signed in user can be handled in the listener.
                  if (!task.isSuccessful()) {
                     Log.w("TJS", "signInWithCredential", task.getException());
                     Toast.makeText(MileageTracker.this, "Authentication failed.",
                           Toast.LENGTH_SHORT).show();
                  }
                  // [START_EXCLUDE]
//                  hideProgressDialog();
                  // [END_EXCLUDE]
               }
            });
   }

   private void updateUI(FirebaseUser user) {
      if (user != null) {
         findViewById(R.id.sign_in_button).setVisibility(View.GONE);
         findViewById(R.id.sign_out_button).setVisibility(View.VISIBLE);
      } else {
         findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
         findViewById(R.id.sign_out_button).setVisibility(View.GONE);
      }
   }

   private class GoogleAuthListener implements GoogleApiClient.OnConnectionFailedListener {

      public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
         // An unresolvable error has occurred and Google APIs (including Sign-In) will not
         // be available.
         Log.d("TJS", "onConnectionFailed:" + connectionResult);
         Toast.makeText(getApplicationContext(), "Google Play Services error.", Toast.LENGTH_SHORT).show();

      }
   }

}
