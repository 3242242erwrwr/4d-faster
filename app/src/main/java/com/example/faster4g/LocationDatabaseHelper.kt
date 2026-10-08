package com.example.faster4g

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocationDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "faster4g_locations.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_LOCATIONS = "locations"

        private const val COLUMN_ID = "id"
        private const val COLUMN_LATITUDE = "latitude"
        private const val COLUMN_LONGITUDE = "longitude"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_IS_SYNCED = "is_synced"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = ("CREATE TABLE $TABLE_LOCATIONS ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_LATITUDE REAL, "
                + "$COLUMN_LONGITUDE REAL, "
                + "$COLUMN_TIMESTAMP INTEGER, "
                + "$COLUMN_IS_SYNCED INTEGER)")
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOCATIONS")
        onCreate(db)
    }

    fun insertLocation(latitude: Double, longitude: Double, timestamp: Long, isSynced: Boolean = false): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LATITUDE, latitude)
            put(COLUMN_LONGITUDE, longitude)
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_IS_SYNCED, if (isSynced) 1 else 0)
        }
        return db.insert(TABLE_LOCATIONS, null, values)
    }

    fun getUnsyncedLocations(): List<LocationRecord> {
        val list = mutableListOf<LocationRecord>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_LOCATIONS,
            null,
            "$COLUMN_IS_SYNCED = ?",
            arrayOf("0"),
            null,
            null,
            "$COLUMN_TIMESTAMP ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID))
                val lat = it.getDouble(it.getColumnIndexOrThrow(COLUMN_LATITUDE))
                val lon = it.getDouble(it.getColumnIndexOrThrow(COLUMN_LONGITUDE))
                val time = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val synced = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_SYNCED)) == 1
                list.add(LocationRecord(id, lat, lon, time, synced))
            }
        }
        return list
    }

    fun markAsSynced(ids: List<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        val idString = ids.joinToString(",")
        db.execSQL("UPDATE $TABLE_LOCATIONS SET $COLUMN_IS_SYNCED = 1 WHERE $COLUMN_ID IN ($idString)")
    }

    fun getUnsyncedCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_LOCATIONS WHERE $COLUMN_IS_SYNCED = 0", null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }
}
