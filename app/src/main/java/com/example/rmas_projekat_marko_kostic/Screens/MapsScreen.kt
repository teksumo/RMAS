package com.example.rmas_projekat_marko_kostic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import coil.compose.rememberImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState

data class MapObject(
    val name: String,
    val description: String,
    val rating: Int,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String,
    val addedBy: String
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapsScreen() {
    val context = LocalContext.current
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    val cameraPositionState = rememberCameraPositionState {
        position = com.google.android.gms.maps.model.CameraPosition(LatLng(0.0, 0.0), 15f, 0f, 0f)
    }
    var selectedObject by remember { mutableStateOf<MapObject?>(null) }
    val firestore = FirebaseFirestore.getInstance()
    var mapObjects by remember { mutableStateOf<List<MapObject>>(emptyList()) }

    LaunchedEffect(locationPermissionState.hasPermission) {
        if (locationPermissionState.hasPermission) {
            Log.d("MapsScreen", "Location permission granted")
            requestLocationUpdates(context, fusedLocationClient) { location ->
                if (location != null) {
                    currentLocation = location
                    Log.d("MapsScreen", "Location received: $location")
                } else {
                    Log.e("MapsScreen", "Location is null")
                }
            }
            fetchMapObjects(firestore) { objects ->
                mapObjects = objects
                Log.d("MapsScreen", "Fetched objects: $objects")
            }
        } else {
            Log.d("MapsScreen", "Requesting location permission")
            locationPermissionState.launchPermissionRequest()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = locationPermissionState.hasPermission)
            ) {
                val customPin = resizeBitmap(context, R.drawable.custom_pin, 100, 100) // Resize bitmap to desired size
                mapObjects.forEach { obj ->
                    Marker(
                        position = LatLng(obj.latitude, obj.longitude),
                        title = obj.name,
                        snippet = obj.description,
                        icon = BitmapDescriptorFactory.fromBitmap(customPin),
                        onClick = {
                            selectedObject = obj
                            true
                        }
                    )
                }
            }
            currentLocation?.let {
                LaunchedEffect(it) {
                    cameraPositionState.position = com.google.android.gms.maps.model.CameraPosition(it, 15f, 0f, 0f)
                    Log.d("MapsScreen", "Camera position updated: $it")
                }
            }
            selectedObject?.let { obj ->
                ObjectDetailsDialog(
                    mapObject = obj,
                    onDismissRequest = { selectedObject = null },
                    onVisit = {
                        addPoints("visit")
                        Toast.makeText(context, "Visited!", Toast.LENGTH_SHORT).show()
                        selectedObject = null
                    },
                    onAddReview = { review ->
                        addPoints("review")
                        addReviewToObject(obj, review)
                        Toast.makeText(context, "Review added!", Toast.LENGTH_SHORT).show()
                        selectedObject = null
                    }
                )
            }
        }
    }
}

@Composable
fun ObjectDetailsDialog(
    mapObject: MapObject,
    onDismissRequest: () -> Unit,
    onVisit: () -> Unit,
    onAddReview: (String) -> Unit
) {
    var reviewText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(mapObject.name) },
        text = {
            Column {
                Image(
                    painter = rememberImagePainter(mapObject.imageUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                Text("Description: ${mapObject.description}")
                Text("Rating: ${mapObject.rating}")
                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { reviewText = it },
                    label = { Text("Your Review") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                Button(onClick = onVisit) {
                    Text("Visited")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onAddReview(reviewText) }) {
                    Text("Add Review")
                }
            }
        }
    )
}

private fun requestLocationUpdates(context: Context, fusedLocationClient: FusedLocationProviderClient, onLocationReceived: (LatLng?) -> Unit) {
    val locationRequest = LocationRequest.create().apply {
        interval = 10000 // 10 seconds
        fastestInterval = 5000 // 5 seconds
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                val latLng = LatLng(location.latitude, location.longitude)
                onLocationReceived(latLng)
                Log.d("MapsScreen", "Location received in callback: $latLng")
                fusedLocationClient.removeLocationUpdates(this) // Remove updates after getting the first location
                break
            }
        }
    }

    try {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            Log.d("MapsScreen", "Location updates requested")
        } else {
            Log.e("MapsScreen", "Location permission not granted")
            onLocationReceived(null)
        }
    } catch (e: SecurityException) {
        Log.e("MapsScreen", "Location permission denied", e)
        onLocationReceived(null)
    }
}

private fun fetchMapObjects(firestore: FirebaseFirestore, onObjectsFetched: (List<MapObject>) -> Unit) {
    Log.d("MapsScreen", "Fetching map objects from Firestore")
    firestore.collection("objects")
        .get()
        .addOnSuccessListener { documents ->
            val objects = documents.mapNotNull { document ->
                val name = document.getString("name")
                val description = document.getString("description")
                val rating = document.getLong("rating")?.toInt()
                val latitude = document.getDouble("latitude")
                val longitude = document.getDouble("longitude")
                val imageUrl = document.getString("image_url")
                val addedBy = document.getString("added_by")

                Log.d("MapsScreen", "Document data: ${document.data}")

                if (name != null && description != null && rating != null && latitude != null && longitude != null && imageUrl != null && addedBy != null) {
                    MapObject(name, description, rating, latitude, longitude, imageUrl, addedBy)
                } else {
                    Log.d("MapsScreen", "Invalid object data: $document")
                    null
                }
            }
            Log.d("MapsScreen", "Objects fetched: $objects")
            onObjectsFetched(objects)
        }
        .addOnFailureListener { exception ->
            Log.e("MapsScreen", "Error fetching objects", exception)
        }
}

private fun addPoints(action: String) {
    val user = FirebaseAuth.getInstance().currentUser
    val userId = user?.uid ?: return
    val firestore = FirebaseFirestore.getInstance()

    firestore.collection("users").document(userId).get()
        .addOnSuccessListener { document ->
            val currentPoints = document.getLong("points") ?: 0
            val newPoints = when (action) {
                "visit" -> currentPoints + 3
                "review" -> currentPoints + 5
                else -> currentPoints
            }
            firestore.collection("users").document(userId).update("points", newPoints)
                .addOnSuccessListener {
                    Log.d("MapsScreen", "Points updated successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("MapsScreen", "Error updating points", e)
                }
        }
        .addOnFailureListener { e ->
            Log.e("MapsScreen", "Error getting user points", e)
        }
}

private fun addReviewToObject(mapObject: MapObject, reviewText: String) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val reviewData = mapOf(
        "user_id" to (currentUser?.displayName ?: "Anonymous"),
        "comment_text" to reviewText,
        "timestamp" to System.currentTimeMillis()
    )

    firestore.collection("objects").document(mapObject.name).update("comments", reviewData)
        .addOnSuccessListener {
            Log.d("MapsScreen", "Review added successfully")
        }
        .addOnFailureListener { e ->
            Log.e("MapsScreen", "Error adding review", e)
        }
}

private fun resizeBitmap(context: Context, drawableRes: Int, width: Int, height: Int): Bitmap {
    val bitmap = BitmapFactory.decodeResource(context.resources, drawableRes)
    return Bitmap.createScaledBitmap(bitmap, width, height, false)
}

@Preview(showBackground = true)
@Composable
fun MapsScreenPreview() {
    MapsScreen()
}
