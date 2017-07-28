package com.switkows.mileage

import android.app.backup.BackupManager
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.util.Log

class MileageProvider : ContentProvider() {

    private var mBackup: BackupManager? = null // Allow to be null for unit tests

    override fun onCreate(): Boolean {
        mBackup = BackupManager(context)
        mDatabase = MileageDataSet(context)
        mSuppressBackupUpdate = false
        return true
    }

    private lateinit var mDatabase: MileageDataSet

    override fun delete(uri: Uri, where: String?, whereArgs: Array<String>?): Int {
        val db = mDatabase.writableDatabase
        val count: Int
        val id: String
        val extra = if (where != null && where.isNotEmpty()) " AND ($where)" else ""
        when (sriMatcher.match(uri)) {
            ALL_CAR -> count = db.delete(DB_TABLE, where, whereArgs)
            SPECIFIC_CAR -> count = db.delete(DB_TABLE, "carName = '" + uri.pathSegments[1] + "'" + extra, whereArgs)
            ONE -> {
                id = uri.pathSegments[1]
                count = db.delete(DB_TABLE, "_id=" + id + extra, whereArgs)
            }
            PROFILE_SELECT -> count = db.delete(PROFILE_TABLE, where, whereArgs)
            SINGLE_PROFILE_SELECT -> {
                id = uri.pathSegments[1]
                count = db.delete(PROFILE_TABLE, "_id=" + id + extra, whereArgs)
            }
            else -> throw IllegalArgumentException("Unknown URI : " + uri)
        }
        val resolver = if (context != null) context!!.contentResolver else null

        if (resolver != null) {
            resolver.notifyChange(uri, null)
            //FIXME - try to get rid of this. it's probably not needed..
            resolver.notifyChange(ALL_CONTENT_URI, null)
        }
        if (count > 0 && !mSuppressBackupUpdate)
            mBackup?.dataChanged()
        return count
    }

    override fun getType(uri: Uri): String? {
        when (sriMatcher.match(uri)) {
            ALL_CAR, SPECIFIC_CAR, PROFILE_SELECT -> return CONTENT_TYPE
            ONE -> return CONTENT_ITEM
            else -> throw IllegalArgumentException("Unknown URI : " + uri)
        }
    }

    override fun bulkInsert(uri: Uri, values: Array<ContentValues>): Int {
        mSuppressBackupUpdate = true
        val result = super.bulkInsert(uri, values)
        mSuppressBackupUpdate = false
        return result
    }

    override fun insert(uri: Uri, initialValues: ContentValues?): Uri? {
        val isProfile = sriMatcher.match(uri) == PROFILE_SELECT
        if (sriMatcher.match(uri) != ALL_CAR && sriMatcher.match(uri) != SPECIFIC_CAR && !isProfile) {
            throw IllegalArgumentException("Unknown URI : '$uri'")
        }
        val table = if (isProfile) PROFILE_TABLE else DB_TABLE
        val dataSetUri = if (isProfile) CAR_PROFILE_URI else ALL_CONTENT_URI

        val values: ContentValues
        if (initialValues != null)
            values = ContentValues(initialValues)
        else
            values = ContentValues()

        val db = mDatabase.writableDatabase
        val rowId = db.insert(table, null, values)
        if (rowId >= 0) {
            val noteUri = ContentUris.withAppendedId(dataSetUri, rowId)
            val resolver = if (context != null) context!!.contentResolver else null
            if (resolver != null) {
                resolver.notifyChange(noteUri, null)
                //FIXME - try to get rid of this. it's probably not needed..
                resolver.notifyChange(dataSetUri, null)
            }
            if (!mSuppressBackupUpdate)
                mBackup?.dataChanged()
            return noteUri
        }
        throw SQLException("Failed to insert row into " + uri)
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val qb = SQLiteQueryBuilder()
        var groupBy: String? = null

        when (sriMatcher.match(uri)) {
            SPECIFIC_CAR -> {
                qb.tables = DB_TABLE
                // limit to only this car's data
                qb.appendWhere("carName = '" + uri.pathSegments[1] + "'")
                // This will add all of the Columns in 'projection', except for
                // the _id column. This allows to 'uniquify' the return data
                // (currently, only used for giving suggestions in the gas-station
                // input dialog)
                // FIXME - do we query with projections when
                // we do NOT want to be unique?
                projection?.asSequence()?.filter { it != "_id" }?.forEach {
                    if (groupBy == null)
                        groupBy = "" + it
                    else
                        groupBy += it
                }
            }
            ALL_CAR -> {
                qb.tables = DB_TABLE
                projection?.asSequence()?.filter { it != "_id" }?.forEach {
                    if (groupBy == null)
                        groupBy = "" + it
                    else
                        groupBy += it
                }
            }
            ONE -> {
                // return a specific record (used for editing or deleting a single
                // item)
                qb.tables = DB_TABLE
                qb.appendWhere("_id = " + uri.pathSegments[1])
            }
            PROFILE_SELECT -> {
                qb.tables = PROFILE_TABLE
                projection?.asSequence()?.filter { it != "_id" }?.forEach {
                    groupBy = (groupBy ?: "") + it
//                    if (groupBy == null)
//                        groupBy = "" + it
//                    else
//                        groupBy += it
                }
            }
            else -> throw IllegalArgumentException("Unknown URI : " + uri)
        }

        val db = mDatabase.readableDatabase
        val c = qb.query(db, projection, selection, selectionArgs, groupBy, null, sortOrder)
        c.setNotificationUri(if (context != null) context!!.contentResolver else null, uri)
        return c
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        val db = mDatabase.writableDatabase
        val isProfile = sriMatcher.match(uri) == PROFILE_SELECT
        val count: Int
        val id: String
        val where: String
        when (sriMatcher.match(uri)) {
            ALL_CAR, SPECIFIC_CAR -> count = db.update(DB_TABLE, values, selection, selectionArgs)
            ONE -> {
                id = uri.pathSegments[1]
                where = if (selection != null && selection.isNotEmpty()) " AND ($selection)" else ""
                count = db.update(DB_TABLE, values, "_id=" + id + where, selectionArgs)
            }
            PROFILE_SELECT -> {
                //FIXME - add check to make sure URI has ID present!
                id = uri.pathSegments[1]
                where = if (selection != null && selection.isNotEmpty()) " AND ($selection)" else ""
                count = db.update(PROFILE_TABLE, values, "_id=" + id + where, selectionArgs)
            }
            SINGLE_PROFILE_SELECT -> {
                id = uri.pathSegments[1]
                where = if (selection != null && selection.isNotEmpty()) " AND ($selection)" else ""
                count = db.update(PROFILE_TABLE, values, "_id=" + id + where, selectionArgs)
            }
            else -> throw IllegalArgumentException("Unknown URI : " + uri)
        }
        val resolver = if (context != null) context!!.contentResolver else null
        if (resolver != null) {
            resolver.notifyChange(uri, null)
            //FIXME - try to get rid of this. it's probably not needed..
            resolver.notifyChange(if (isProfile) CAR_PROFILE_URI else ALL_CONTENT_URI, null)
        }
        if (count > 0 && !mSuppressBackupUpdate)
            mBackup!!.dataChanged()
        return count
    }

    companion object {
        class MileageDataSet internal constructor(private val mContext: Context, filename: String = DB_FILENAME, version: Int = DB_VERSION) : SQLiteOpenHelper(mContext, filename, null, version) {

            override fun onUpgrade(db: SQLiteDatabase, oldVer: Int, newVer: Int) {
                if (oldVer == 3 && (newVer == 4 || newVer == 5)) {
                    Log.d("TJS", "Trying to execute'" + mContext.getString(R.string.upgradeDBfrom3To4) + "'")
                    // create the new column (supports infinite number of users/cars)
                    db.execSQL(mContext.getString(R.string.upgradeDBfrom3To4))
                    val defaults = ContentValues()
                    val carName = "Car45"
                    defaults.put(MileageData.ToDBNames[MileageData.CAR], carName)
                    // add car name to all rows in old database
                    val rows = db.update(DB_TABLE, defaults, null, null)
                    Log.d("TJS", "Database successfully upgraded. $rows records were added to $carName's stats")
                } else if (oldVer == 4 && newVer == 5) {
                    // TODO - scan DB for car names, uniq, then pass in to create
                    createDefaultProfileTable(db)
                } else {
                    val sql = mContext.getString(R.string.clearDb).split("\n".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                    db.beginTransaction()
                    try {
                        executeMultipleCommands(db, sql)
                        db.setTransactionSuccessful()
                    } catch (e: SQLException) {
                        Log.e("Error creating table!", e.toString())
                    } finally {
                        db.endTransaction()
                    }
                    onCreate(db)
                }
            }

            override fun onCreate(db: SQLiteDatabase) {
                val sql = mContext.getString(R.string.initDb).split("\n".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                db.beginTransaction()
                try {
                    executeMultipleCommands(db, sql)
                    db.setTransactionSuccessful()
                } catch (e: SQLException) {
                    Log.e("Error creating table!", e.toString())
                } finally {
                    db.endTransaction()
                }
                createDefaultProfileTable(db)
            }

            private fun createDefaultProfileTable(db: SQLiteDatabase, cars: ArrayList<String>? = null) {
                db.execSQL(mContext.getString(R.string.initProfileTable))
                val values = ContentValues()
                (0..2).forEach { i ->
                    values.put(PROFILE_NAME, "Car" + (i + 1))
                    db.insert(PROFILE_TABLE, "", values)
                }
                cars?.forEach { name ->
                    values.put(PROFILE_NAME, name)
                    db.insert(PROFILE_TABLE, "", values)
                }
            }

            private fun executeMultipleCommands(db: SQLiteDatabase, sql: Array<String>) {
                sql.filter { s -> s.trim { it <= ' ' }.isNotEmpty() }
                   .forEach { db.execSQL(it) }
            }
        }

        var mSuppressBackupUpdate: Boolean = false

        val DB_FILENAME = "mileage_db"
        private val DB_TABLE = "mileageInfo"
        val PROFILE_TABLE = "mileageProfiles"
        private val DB_VERSION = 5
        val PROFILE_NAME = "carName"

        protected val AUTHORITY = "com.switkows.mileage.MileageProvider"
        val CAR_CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/car")
        val ALL_CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/all")
        val CAR_PROFILE_URI: Uri = Uri.parse("content://$AUTHORITY/profile")

        private val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.mileage"
        private val CONTENT_ITEM = "vnd.android.cursor.item/vnd.google.mileage"

        private val ALL_CAR = 0
        private val ONE = 1
        private val SPECIFIC_CAR = 2
        private val PROFILE_SELECT = 3
        private val SINGLE_PROFILE_SELECT = 4

        private val sriMatcher: UriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            sriMatcher.addURI(AUTHORITY, "all", ALL_CAR)
            sriMatcher.addURI(AUTHORITY, "car/*", SPECIFIC_CAR)
            sriMatcher.addURI(AUTHORITY, "all/#", ONE)
            sriMatcher.addURI(AUTHORITY, "profile", PROFILE_SELECT)
            sriMatcher.addURI(AUTHORITY, "profile/#", SINGLE_PROFILE_SELECT)
        }

        fun getProfiles(context: Context): Array<String> {
            val cursor = context.contentResolver.query(CAR_PROFILE_URI, null, null, null, null) ?: return arrayOf()
            val cars = Array(cursor.count, {
                i -> cursor.moveToPosition(i)
                cursor.getString(1) //FIXME - change to define!
            })
            cursor.close()
            return cars
        }

        //Convenience method to simplify adding new profiles
        fun addProfile(context: Context, profile: String) {
            context.contentResolver.insert(CAR_PROFILE_URI, createProfileContent(profile))
        }

        fun createProfileContent(name: String): ContentValues {
            val values = ContentValues()
            values.put(PROFILE_NAME, name)
            return values
        }

        //returns the default 'sortBy' argument, to reverse-sort by date (i.e. newest record on top)
        fun defaultSort(): String {
            return MileageData.ToDBNames[MileageData.DATE] + " desc"
        }
    }
}
