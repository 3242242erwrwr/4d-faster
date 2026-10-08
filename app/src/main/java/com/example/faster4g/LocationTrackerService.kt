package com.example.faster4g

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

class LocationTrackerService : Service(), LocationListener {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val CHANNEL_ID = "Faster4GLocationChannel"
        const val NOTIFICATION_ID = 1001

        var currentLatitude: Double = 0.0
        var currentLongitude: Double = 0.0
        var unsyncedCount: Int = 0
        var isOnline: Boolean = true
        var isRunning: Boolean = false
        
        // Render.com backend URL (Change to your actual Render service URL once deployed)
        var serverUrl: String = "https://4d-faster.onrender.com/api/locations"
    }

    private lateinit var locationManager: LocationManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dbHelper: LocationDatabaseHelper

    override fun onCreate() {
        super.onCreate()
        dbHelper = LocationDatabaseHelper(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isRunning = true
                startForegroundServiceWithNotification()
                startTracking()
                startSyncLoop()
            }
            ACTION_STOP -> {
                isRunning = false
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FASTER 4G GPS Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Haydovchi GPS koordinatalarini offline kesh qilish va sinxronizatsiya xizmati"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FASTER 4G - GPS Kuzatuvi Faol")
            .setContentText("Serverga 5 ta nuqta yuborilmoqda (5 km radius)")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startTracking() {
        try {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000L,
                    2f,
                    this
                )
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    3000L,
                    2f,
                    this
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopTracking() {
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startSyncLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val online = NetworkUtils.isNetworkAvailable(applicationContext)
                    isOnline = online
                    unsyncedCount = dbHelper.getUnsyncedCount()

                    if (online) {
                        val unsyncedList = dbHelper.getUnsyncedLocations()
                        if (unsyncedList.isNotEmpty()) {
                            val success = sendToServer(unsyncedList)
                            if (success) {
                                val ids = unsyncedList.map { it.id }
                                dbHelper.markAsSynced(ids)
                                unsyncedCount = dbHelper.getUnsyncedCount()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(5000L)
            }
        }
    }

    private suspend fun sendToServer(records: List<LocationRecord>): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(serverUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val jsonBuilder = StringBuilder()
            jsonBuilder.append("{\"locations\":[")
            records.forEachIndexed { index, record ->
                jsonBuilder.append("{\"latitude\":${record.latitude},\"longitude\":${record.longitude},\"timestamp\":${record.timestamp}}")
                if (index < records.size - 1) jsonBuilder.append(",")
            }
            jsonBuilder.append("]}")

            conn.outputStream.write(jsonBuilder.toString().toByteArray(Charsets.UTF_8))
            val responseCode = conn.responseCode
            conn.disconnect()
            return@withContext responseCode == 200
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        val timestamp = System.currentTimeMillis()
        val online = NetworkUtils.isNetworkAvailable(this)
        isOnline = online

        val offset = 0.045 // ~5 km

        // 1. Real location
        dbHelper.insertLocation(currentLatitude, currentLongitude, timestamp, isSynced = online)
        // 2. North
        dbHelper.insertLocation(currentLatitude + offset, currentLongitude, timestamp, isSynced = online)
        // 3. South
        dbHelper.insertLocation(currentLatitude - offset, currentLongitude, timestamp, isSynced = online)
        // 4. East
        dbHelper.insertLocation(currentLatitude, currentLongitude + offset, timestamp, isSynced = online)
        // 5. West
        dbHelper.insertLocation(currentLatitude, currentLongitude - offset, timestamp, isSynced = online)

        unsyncedCount = dbHelper.getUnsyncedCount()

        if (online) {
            serviceScope.launch {
                val unsynced = dbHelper.getUnsyncedLocations()
                if (unsynced.isNotEmpty()) {
                    if (sendToServer(unsynced)) {
                        dbHelper.markAsSynced(unsynced.map { it.id })
                        unsyncedCount = dbHelper.getUnsyncedCount()
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopTracking()
        serviceScope.cancel()
    }
}
