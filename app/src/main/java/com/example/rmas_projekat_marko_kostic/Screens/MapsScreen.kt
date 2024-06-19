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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalContext


data class MapObject(
    val name: String,
    val description: String,
    val rating: Double,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String,
    val addedBy: String,
    val numberReviews: Int
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
    var showReviewDialog by remember { mutableStateOf(false) }
    var showReviewsDialog by remember { mutableStateOf(false) }
    var showAlreadyReviewedDialog by remember { mutableStateOf(false) }
    var showAlreadyVisitedDialog by remember { mutableStateOf(false) }
    var currentUsername by remember { mutableStateOf("") }
    val firestore = FirebaseFirestore.getInstance()
    var mapObjects by remember { mutableStateOf<List<MapObject>>(emptyList()) }

    val currentUser = FirebaseAuth.getInstance().currentUser

    LaunchedEffect(currentUser) {
        currentUser?.let {
            firestore.collection("users").document(it.uid).get()
                .addOnSuccessListener { document ->
                    currentUsername = document.getString("username") ?: ""
                }
                .addOnFailureListener { exception ->
                    Log.e("MapsScreen", "Error fetching username", exception)
                }
        }
    }

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
                        checkIfUserVisited(obj) { visited ->
                            if (visited) {
                                showAlreadyVisitedDialog = true
                            } else {
                                markAsVisited(obj)
                                addPoints("visit")
                                Toast.makeText(context, "Marked as visited!", Toast.LENGTH_SHORT).show()
                                selectedObject = null
                            }
                        }
                    },
                    onAddReview = {
                        checkIfUserReviewed(obj, currentUsername) { reviewed ->
                            if (reviewed) {
                                showAlreadyReviewedDialog = true
                            } else {
                                showReviewDialog = true
                            }
                        }
                    },
                    onViewReviews = { showReviewsDialog = true }
                )
            }
            if (showReviewDialog && selectedObject != null) {
                AddReviewDialog(
                    mapObject = selectedObject!!,
                    onDismissRequest = { showReviewDialog = false },
                    onSubmitReview = { review, rating ->
                        addReviewToObject(selectedObject!!, review, rating, currentUsername)
                        addPoints("review")
                        Toast.makeText(context, "Review added!", Toast.LENGTH_SHORT).show()
                        showReviewDialog = false
                        selectedObject = null
                    }
                )
            }
            if (showReviewsDialog && selectedObject != null) {
                ShowReviewsDialog(
                    mapObject = selectedObject!!,
                    onDismissRequest = { showReviewsDialog = false }
                )
            }
            if (showAlreadyReviewedDialog) {
                AlreadyReviewedDialog(
                    onDismissRequest = { showAlreadyReviewedDialog = false }
                )
            }
            if (showAlreadyVisitedDialog) {
                AlreadyVisitedDialog(
                    onDismissRequest = { showAlreadyVisitedDialog = false }
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
    onAddReview: () -> Unit,
    onViewReviews: () -> Unit
) {
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
                Text("Rating: %.2f (%d reviews)".format(mapObject.rating, mapObject.numberReviews))
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onVisit,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    Text("Mark As Visited")
                }
                Button(
                    onClick = onAddReview,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    Text("Add Review")
                }
                Button(
                    onClick = onViewReviews,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reviews")
                }
            }
        }
    )
}

@Composable
fun AddReviewDialog(
    mapObject: MapObject,
    onDismissRequest: () -> Unit,
    onSubmitReview: (String, Int) -> Unit
) {
    var reviewText by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(1) } // Default rating

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Add Review for ${mapObject.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { reviewText = it },
                    label = { Text("Your Review") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Rating:")
                Slider(
                    value = rating.toFloat(),
                    onValueChange = { rating = it.toInt() },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Rating: $rating")
            }
        },
        confirmButton = {
            Button(onClick = { onSubmitReview(reviewText, rating) }) {
                Text("Submit Review")
            }
        }
    )
}

@Composable
fun ShowReviewsDialog(
    mapObject: MapObject,
    onDismissRequest: () -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()
    var reviews by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(mapObject) {
        firestore.collection("objects").document(mapObject.name.replace(" ", "_")).get()
            .addOnSuccessListener { document ->
                val comments = document.get("comments") as? List<Map<String, Any>> ?: emptyList()
                reviews = comments
                loading = false
            }
            .addOnFailureListener { exception ->
                Log.e("MapsScreen", "Error fetching reviews", exception)
                loading = false
            }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Reviews") },
        text = {
            if (loading) {
                Text("Loading Reviews...")
            } else {
                Column {
                    reviews.forEach { review ->
                        val user = review["user_id"] as? String ?: "Anonymous"
                        val comment = review["comment_text"] as? String ?: ""
                        val rating = (review["rating"] as? Long)?.toInt() ?: 0
                        val timestamp = review["timestamp"] as? Long ?: 0L
                        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("User: $user", fontWeight = FontWeight.Bold)
                            Text("Rating: $rating")
                            Text("Comment: $comment")
                            Text("Date: $date")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AlreadyReviewedDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Review Already Exists") },
        text = { Text("You already left a review, you cannot do it again.") },
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text("OK")
            }
        }
    )
}

@Composable
fun AlreadyVisitedDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Already Marked as Visited") },
        text = { Text("You have already marked this object as visited.") },
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text("OK")
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
                val rating = document.getDouble("rating") ?: 0.0
                val latitude = document.getDouble("latitude")
                val longitude = document.getDouble("longitude")
                val imageUrl = document.getString("image_url")
                val addedBy = document.getString("added_by")
                val numberReviews = document.getLong("number_reviews")?.toInt() ?: 0

                Log.d("MapsScreen", "Document data: ${document.data}")

                if (name != null && description != null && latitude != null && longitude != null && imageUrl != null && addedBy != null) {
                    MapObject(name, description, rating, latitude, longitude, imageUrl, addedBy, numberReviews)
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

private fun addReviewToObject(mapObject: MapObject, reviewText: String, rating: Int, username: String) {
    val firestore = FirebaseFirestore.getInstance()
    val reviewData = mapOf(
        "user_id" to username,
        "comment_text" to reviewText,
        "timestamp" to System.currentTimeMillis(),
        "rating" to rating
    )

    firestore.runTransaction { transaction ->
        val snapshot = transaction.get(firestore.collection("objects").document(mapObject.name.replace(" ", "_")))
        val numberReviews = snapshot.getLong("number_reviews")?.toInt() ?: 0
        val currentRating = snapshot.getDouble("rating") ?: 0.0

        val newNumberReviews = numberReviews + 1
        val newRating = ((currentRating * numberReviews) + rating) / newNumberReviews

        transaction.update(firestore.collection("objects").document(mapObject.name.replace(" ", "_")), "number_reviews", newNumberReviews)
        transaction.update(firestore.collection("objects").document(mapObject.name.replace(" ", "_")), "rating", newRating)
        transaction.update(firestore.collection("objects").document(mapObject.name.replace(" ", "_")), "comments", FieldValue.arrayUnion(reviewData))
    }.addOnSuccessListener {
        Log.d("MapsScreen", "Review added successfully")
    }.addOnFailureListener { e ->
        Log.e("MapsScreen", "Error adding review", e)
    }
}

private fun checkIfUserReviewed(mapObject: MapObject, username: String, onResult: (Boolean) -> Unit) {
    val firestore = FirebaseFirestore.getInstance()

    firestore.collection("objects").document(mapObject.name.replace(" ", "_")).get()
        .addOnSuccessListener { document ->
            val comments = document.get("comments") as? List<Map<String, Any>> ?: emptyList()
            val reviewed = comments.any { it["user_id"] == username }
            onResult(reviewed)
        }
        .addOnFailureListener { exception ->
            Log.e("MapsScreen", "Error checking reviews", exception)
            onResult(false)
        }
}

private fun checkIfUserVisited(mapObject: MapObject, onResult: (Boolean) -> Unit) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser

    firestore.collection("users").document(currentUser?.uid ?: "").get()
        .addOnSuccessListener { document ->
            val visited = document.get("visited") as? Map<*, *>
            val hasVisited = visited?.containsKey(mapObject.name.replace(" ", "_")) ?: false
            onResult(hasVisited)
        }
        .addOnFailureListener { exception ->
            Log.e("MapsScreen", "Error checking visited objects", exception)
            onResult(false)
        }
}

private fun markAsVisited(mapObject: MapObject) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser

    currentUser?.let { user ->
        val visitedData = mapOf(mapObject.name.replace(" ", "_") to true)
        firestore.collection("users").document(user.uid)
            .update("visited", FieldValue.arrayUnion(visitedData))
            .addOnSuccessListener {
                Log.d("MapsScreen", "Marked as visited successfully")
            }
            .addOnFailureListener { e ->
                Log.e("MapsScreen", "Error marking as visited", e)
            }
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
