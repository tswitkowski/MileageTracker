package com.switkows.mileage

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.LoaderManager
import android.support.v4.content.CursorLoader
import android.support.v4.content.Loader
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast

import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.switkows.mileage.ProfileSelector.ProfileSelectorCallbacks
import com.switkows.mileage.widgets.StatisticsView

import java.util.HashMap
import java.util.Locale

//FIXME - merge MileageTracker and EditRecordsMenu into single activity to take advantage of Action Bar enhancements. This will require:
//1. moving most code from MileageTracker Activity to a Fragment
//2. removing EditRecordsMenu Activity
//3. correctly replacing new Activity calls with Fragment transactions
class MileageTracker : AppCompatActivity(), LoaderManager.LoaderCallbacks<Cursor>, ProfileSelectorCallbacks {
    private lateinit var chartListeners: Array<ShowLargeChart?>
    private var chartManager: MileageChartManager? = null
    private var mProfileAdapter: ProfileSelector? = null

    // Firebase stuff
    private var mAuth: FirebaseAuth? = null
    private var mAuthListener: FirebaseAuth.AuthStateListener? = null

    // Google account stuff
    private var mGoogleApiClient: GoogleApiClient? = null


    private lateinit var mContext: Context

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("TJS", "Started onCreate...")
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        mContext = this
        setContentView(R.layout.main)
        val toolbar = findViewById<Toolbar>(R.id.main_toolbar)
        setSupportActionBar(toolbar)
        mStatsAdapter = StatisticsAdapter()
        // grab pointers to all my graphical elements
        initializePointers()
        supportLoaderManager.initLoader(45, null, this)
        mProfileAdapter = ProfileSelector.setupActionBar(this, null)!!
        // Log.d("TJS", "Finished opening/creating database");

        // Firebase stuff
        mAuth = FirebaseAuth.getInstance()
        mAuthListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // User is signed in
                Log.d("TJS", "onAuthStateChanged:signed_in:" + user.uid)
            } else {
                // User is signed out
                Log.d("TJS", "onAuthStateChanged:signed_out")
            }
            updateUI(user)
        }

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

        mGoogleApiClient = GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, GoogleAuthListener() /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build()

        val sib = findViewById<View>(R.id.sign_in_button)
        sib?.setOnClickListener {
            val signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient)
            startActivityForResult(signInIntent, RESULT_SIGN_IN)
        }

        val sob = findViewById<View>(R.id.sign_out_button)
        sob?.setOnClickListener {
            // Firebase sign out
            mAuth!!.signOut()

            // Google sign out
            Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback { updateUI(null) }
        }
        //      mAuth.
    }

    public override fun onResume() {
        super.onResume()
        // FIXME - maybe be a bit smarter about when we generate charts!
        supportLoaderManager.restartLoader(45, null, this)
        mProfileAdapter?.loadActionBarNavItems(this)
    }

    public override fun onStart() {
        super.onStart()
        mAuth!!.addAuthStateListener(mAuthListener!!)
    }

    public override fun onStop() {
        super.onStop()
        if (mAuthListener != null) {
            mAuth!!.removeAuthStateListener(mAuthListener!!)
        }
    }

    fun generateCharts(cursor: Cursor) {
        chartManager = MileageChartManager(mContext, cursor)
        for (chart in charts)
            chart?.removeAllViews()
        @Suppress("UNCHECKED_CAST")
        chartManager?.addCharts(charts as Array<LinearLayout>, chartListeners as Array<ShowLargeChart>)
    }

    fun printStatistics() {
        if(chartManager != null) {
            val ecoUnits = chartManager!!.economyUnits
            val distUnits = chartManager!!.distanceUnits
            mStatsAdapter.setValue(0, chartManager!!.averageMPG, ecoUnits)
            mStatsAdapter.setValue(1, chartManager!!.averageTrip, distUnits)
            mStatsAdapter.setValue(2, chartManager!!.bestMPG, ecoUnits)
            mStatsAdapter.setValue(3, chartManager!!.bestTrip, distUnits)
            mStatsAdapter.setValue(4, chartManager!!.averageDiff * 100, "%")
            mStatsAdapter.notifyDataSetInvalidated()
        }
    }

    fun initializePointers() {
        // root = (LinearLayout)findViewById(R.id.root);
        charts = arrayOfNulls<LinearLayout>(3)
        charts[0] = findViewById<LinearLayout>(R.id.chart1)
        charts[1] = findViewById<LinearLayout>(R.id.chart2)
        charts[2] = findViewById<LinearLayout>(R.id.chart3)
        chartListeners = arrayOfNulls<ShowLargeChart>(3)
        chartListeners[0] = ShowLargeChart(mContext, 0)
        chartListeners[1] = ShowLargeChart(mContext, 0)
        chartListeners[2] = ShowLargeChart(mContext, 0)
        val mStatsView = findViewById<ListView>(R.id.statistics_list)
        mStatsView.adapter = mStatsAdapter
    }

    val currentProfileURI: Uri
        get() {
            val car = currentProfile
            return Uri.withAppendedPath(MileageProvider.CAR_CONTENT_URI, car)
        }

    val currentProfile: String
        get() {
            val option = this.getString(R.string.carSelection)
            return PreferenceManager.getDefaultSharedPreferences(mContext).getString(option, "Car45")
        }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.add_item -> {
                startActivity(Intent(ACTION_INSERT, currentProfileURI))
                return true
            }
            R.id.show_data -> {
                startActivity(Intent(mContext, EditRecordsMenu::class.java))
                return true
            }
            R.id.preferences -> {
                startActivity(Intent(mContext, EditPreferences::class.java))
                return true
            }
        }
        return false
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RESULT_SIGN_IN) {
            val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            if (result.isSuccess) {
                // Google Sign In was successful, authenticate with Firebase
                val account = result.signInAccount
                firebaseAuthWithGoogle(account!!)
            } else {
                // Google Sign In failed, update UI appropriately
                updateUI(null)
            }
        }
    }

    private inner class StatisticsAdapter internal constructor() : BaseAdapter() {

        private val mLabels: Array<String> = resources.getStringArray(R.array.StatisticsLabels)
        private val mInflater: LayoutInflater
        private val mList: HashMap<String, FloatWithUnits> = HashMap()

        init {
            for (label in mLabels) {
                mList.put(label, FloatWithUnits(-1f, "??"))
            }
            mInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        }

        override fun areAllItemsEnabled(): Boolean {
            return false
        }

        override fun isEnabled(position: Int): Boolean {
            return false
        }

        override fun getCount(): Int {
            return mList.size
        }

        override fun getItem(position: Int): FloatWithUnits {
            return mList[mLabels[position]]!!
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        @SuppressLint("InflateParams")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val stats: StatisticsView
            if (convertView == null) {
                stats = mInflater.inflate(R.layout.statistics_item, null) as StatisticsView
            } else {
                stats = convertView as StatisticsView
            }
            stats.setValue(getItem(position).formattedString)
            stats.setLabel(mLabels[position])
            return stats
        }

        internal fun setValue(pos: Int, value: Float, units: String) {
            if (!(pos >= 0 && pos < mLabels.size))
                return
            val label = mLabels[pos]
            if (mList.containsKey(label)) {
                val curVal = mList[label]
                curVal?.setUnits(units)
                curVal?.setValue(value)
            } else {
                Log.e("TJS", "Should not have gotten here! HashMap not initialized or accessed correctly!!")
            }
        }
    }

    private inner class FloatWithUnits internal constructor(newVal: Float, un: String) {
        private var mValue: Float = 0.toFloat()
        private var mUnits: String? = null

        init {
            setValue(newVal)
            setUnits(un)
        }

        fun setValue(value: Float) {
            mValue = value
        }

        internal fun setUnits(un: String) {
            mUnits = un
        }

        internal val formattedString: String
            get() = String.format(Locale.getDefault(), "%.1f %s", mValue, mUnits)
    }

    internal inner class ShowLargeChart(private val mContext: Context, private var mID: Int) : OnClickListener {
        private val launcher: Intent = Intent(mContext, ChartViewer::class.java)

        fun setID(id: Int) {
            mID = id
        }

        override fun onClick(v: View) {
            val isUS = MileageData.isMilesGallons(PreferenceManager.getDefaultSharedPreferences(mContext), mContext)
            launcher.putExtra(ChartViewer.UNITS_KEY, isUS)
            launcher.putExtra(ChartViewer.CHART_KEY, mID)
            startActivity(launcher)
        }
    }

    /**
     * Data loader for any cursor's to be used by this activity
     * (only handles querying mileage data for a specific profile)
     */
    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        return CursorLoader(this, currentProfileURI, null, null, null, null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor) {
        generateCharts(cursor)
        printStatistics()
    }

    override fun onLoaderReset(cursor: Loader<Cursor>) {
        chartManager = null
    }

    override fun onProfileChange(newProfile: String) {
        mProfileAdapter?.applyPreferenceChange(newProfile)
        supportLoaderManager.restartLoader(45, null, this)
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        Log.d("TJS", "firebaseAuthWithGoogle:" + acct.id!!)
        // [START_EXCLUDE silent]
        //      showProgressDialog();
        // [END_EXCLUDE]

        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        mAuth!!.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                Log.d("TJS", "signInWithCredential:onComplete:" + task.isSuccessful)

                // If sign in fails, display a message to the user. If sign in succeeds
                // the auth state listener will be notified and logic to handle the
                // signed in user can be handled in the listener.
                if (!task.isSuccessful) {
                    Log.w("TJS", "signInWithCredential", task.exception)
                    Toast.makeText(this@MileageTracker, "Authentication failed.",
                            Toast.LENGTH_SHORT).show()
                }
                // [START_EXCLUDE]
                //                  hideProgressDialog();
                // [END_EXCLUDE]
            }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            findViewById<View>(R.id.sign_in_button).visibility = View.GONE
            findViewById<View>(R.id.sign_out_button).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.sign_in_button).visibility = View.VISIBLE
            findViewById<View>(R.id.sign_out_button).visibility = View.GONE
        }
    }

    private inner class GoogleAuthListener : GoogleApiClient.OnConnectionFailedListener {

        override fun onConnectionFailed(connectionResult: ConnectionResult) {
            // An unresolvable error has occurred and Google APIs (including Sign-In) will not
            // be available.
            Log.d("TJS", "onConnectionFailed:" + connectionResult)
            Toast.makeText(applicationContext, "Google Play Services error.", Toast.LENGTH_SHORT).show()

        }
    }

    companion object {
        val ACTION_INSERT = "com.switkows.mileage.INSERT"

        private val RESULT_SIGN_IN = 4544
        /** Called when the activity is first created.  */
        private lateinit var charts: Array<LinearLayout?>
        private lateinit var mStatsAdapter: StatisticsAdapter
    }

}
