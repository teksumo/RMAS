package com.example.rmas_projekat_marko_kostic.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.rmas_projekat_marko_kostic.MainActivity
import com.example.rmas_projekat_marko_kostic.R
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import java.util.concurrent.ConcurrentHashMap

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val firestore = FirebaseFirestore.getInstance()
    private val lastNotificationTimes = ConcurrentHashMap<String, Long>()
    private val notificationInterval = 20 * 60 * 1000 // ovime smo osigurali da ako korisnik bude blizu ob1
    //i onda izadje iz tog range-a, i posle 2 minuta se npr vrati, da nece opet da mu se prikaze ista poruka,
    //jer necemo da ga zatrpamo sa notifikacijama
    // nego tek za 20 minuta ce da se prikaze poruka o ISTOM objektu (ako stoji u njehov range).
    // a ako nastavi da hoda, poruke o drugim objektima ce da se prikazuju normalno..

    //ovo nam samo sluzi da se poruke mogu prikazivati vise puta o 1 objektu, ali ako je u zanjih
    //30 min prosao pored tog objekta nece mu se prikazati.

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service created")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        startForeground(1, getNotification("Service is running..."))

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                Log.d("LocationService", "Location result received")
                for (location in locationResult.locations) {
                    Log.d("LocationService", "Location: $location")
                    updateLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "Service started")
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocationService", "Service destroyed")
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000 // 10 seconds
            fastestInterval = 5000 // 5 seconds
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("LocationService", "Starting location updates")
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            Log.e("LocationService", "Location permission not granted")
        }
    }

    private fun updateLocation(location: Location) {
        val geoPoint = com.google.firebase.firestore.GeoPoint(location.latitude, location.longitude)
        val userLocation = mapOf(
            "location" to geoPoint,
            "timestamp" to System.currentTimeMillis()
        )
        firestore.collection("user_locations").document("user_id").set(userLocation)
            .addOnSuccessListener {
                Log.d("LocationService", "Location updated in Firestore")
                // sendNotification("Location Update", "Location updated in Firestore: ${location.latitude}, ${location.longitude}")
            }
            .addOnFailureListener { e ->
                Log.e("LocationService", "Failed to update location in Firestore", e)
            }

        // Check nearby objects or users
        checkNearbyObjects(location)
    }

    private fun checkNearbyObjects(location: Location) {
        Log.d("LocationService", "Checking nearby objects for location: (${location.latitude}, ${location.longitude})")

        firestore.collection("objects")
            .get()
            .addOnSuccessListener { documents ->
                Log.d("LocationService", "Firestore success: Retrieved ${documents.size()} objects")
                for (document in documents) {
                    Log.d("LocationService", "Document data: ${document.data}")
                    val data = document.data
                    val lat = data?.get("latitude") as? Double
                    val lon = data?.get("longitude") as? Double
                    val name = data?.get("name") as? String
                    val description = data?.get("description") as? String
                    val rating = data?.get("rating").toString().toDoubleOrNull()
                    Log.d("LocationService", "Latitude: $lat, Longitude: $lon for document: ${document.id}")
                    if (lat != null && lon != null) {
                        val objectLocation = Location("").apply {
                            latitude = lat
                            longitude = lon
                        }
                        val distance = location.distanceTo(objectLocation)
                        Log.d("LocationService", "Object location: (${objectLocation.latitude}, ${objectLocation.longitude}) is $distance meters away")
                        if (distance < 100) { // 100 meters threshold
                            Log.d("LocationService", "Object within 100 meters")
                            val currentTime = System.currentTimeMillis()
                            val lastNotificationTime = lastNotificationTimes[document.id] ?: 0
                            if (currentTime - lastNotificationTime > notificationInterval) {
                                sendNotification("Object Nearby: $name", "Description: $description\nRating: $rating")
                                lastNotificationTimes[document.id] = currentTime
                            }
                        }
                    } else {
                        Log.d("LocationService", "Latitude or Longitude is null for document: ${document.id}")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("LocationService", "Failed to retrieve objects from Firestore", e)
            }
    }

    private fun sendNotification(title: String, content: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "location_channel")
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "location_channel",
                "Location Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun getNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, "location_channel")
            .setContentTitle("Location Service")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }
}
