package com.example.rmas_projekat_marko_kostic.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import coil.compose.rememberImagePainter
import com.example.rmas_projekat_marko_kostic.MapObject
import com.example.rmas_projekat_marko_kostic.R
import com.example.rmas_projekat_marko_kostic.service.LocationService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current
    var mapObjects by remember { mutableStateOf<List<MapObject>>(emptyList()) }
    var selectedObject by remember { mutableStateOf<MapObject?>(null) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var showReviewsDialog by remember { mutableStateOf(false) }
    var showAlreadyReviewedDialog by remember { mutableStateOf(false) }
    var showAlreadyVisitedDialog by remember { mutableStateOf(false) }
    var currentUsername by remember { mutableStateOf("") }

    // Fetch objects from Firestore
    LaunchedEffect(Unit) {
        fetchMapObjects { objects ->
            mapObjects = objects
        }
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let {
            FirebaseFirestore.getInstance().collection("users").document(it.uid).get()
                .addOnSuccessListener { document ->
                    currentUsername = document.getString("username") ?: ""
                }
                .addOnFailureListener { exception ->
                    Log.e("MainScreen", "Error fetching username", exception)
                }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Welcome to the Best Coffee Finder App!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(onClick = {
                val intent = Intent(context, LocationService::class.java)
                context.startService(intent)
            }) {
                Text(text = "Start Service")
            }

            Button(onClick = {
                val intent = Intent(context, LocationService::class.java)
                context.stopService(intent)
            }) {
                Text(text = "Stop Service")
            }

            Button(onClick = {
                navController.navigate("map")
            }) {
                Text("Open Map")
            }

            Button(onClick = { navController.navigate("add_object") }) {
                Text("Add Object")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { navController.navigate("leaderboard") }) {
                Text("Leaderboard")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title for the objects table
            Text(
                text = "Registrovani Objekti:",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Table with all objects
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(mapObjects) { obj ->
                    Card(
                        modifier = Modifier
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                            .fillMaxWidth()
                            .clickable { selectedObject = obj },
                        elevation = CardDefaults.cardElevation(4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Name: ${obj.name}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Description: ${obj.description}")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Rating: %.2f".format(obj.rating))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Added by: ${obj.addedBy}")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Number of Reviews: ${obj.numberReviews}")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Date Added: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(obj.timestamp))}")
                        }
                    }
                }
            }

            selectedObject?.let { obj ->
                ObjectDetailsDialog(
                    mapObject = obj,
                    onDismissRequest = { selectedObject = null },
                    onVisit = {
                        checkIfUserVisited(context, obj) { visited ->
                            if (visited) {
                                showAlreadyVisitedDialog = true
                            } else {
                                markAsVisited(context, obj)
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

private fun fetchMapObjects(onObjectsFetched: (List<MapObject>) -> Unit) {
    val firestore = FirebaseFirestore.getInstance()
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
                val timestamp = document.getLong("created_at") ?: 0L

                if (name != null && description != null && latitude != null && longitude != null && imageUrl != null && addedBy != null) {
                    MapObject(name, description, rating, latitude, longitude, imageUrl, addedBy, numberReviews, timestamp)
                } else {
                    Log.d("MainScreen", "Invalid object data: $document")
                    null
                }
            }
            onObjectsFetched(objects)
        }
        .addOnFailureListener { exception ->
            Log.e("MainScreen", "Error fetching objects", exception)
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
        Log.d("MainScreen", "Review added successfully")
    }.addOnFailureListener { e ->
        Log.e("MainScreen", "Error adding review", e)
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
            Log.e("MainScreen", "Error checking reviews", exception)
            onResult(false)
        }
}

private fun checkIfUserVisited(context: Context, mapObject: MapObject, onResult: (Boolean) -> Unit) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser

    firestore.collection("users").document(currentUser?.uid ?: "").get()
        .addOnSuccessListener { document ->
            val visited = document.get("visited") as? Map<String, Boolean>
            val objectId = mapObject.name.replace(" ", "_")
            val hasVisited = visited?.containsKey(objectId) ?: false
            onResult(hasVisited)
        }
        .addOnFailureListener { exception ->
            Log.e("MainScreen", "Error checking visited objects", exception)
            onResult(false)
        }
}

private fun markAsVisited(context: Context, mapObject: MapObject) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser

    currentUser?.let { user ->
        val objectId = mapObject.name.replace(" ", "_")
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                val visited = document.get("visited") as? Map<String, Boolean>
                if (visited != null && visited.containsKey(objectId)) {
                    Toast.makeText(context, "You have already marked this object as visited.", Toast.LENGTH_SHORT).show()
                } else {
                    firestore.collection("users").document(user.uid)
                        .update("visited.$objectId", true)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Marked as visited successfully.", Toast.LENGTH_SHORT).show()
                            addPoints("visit")
                        }
                        .addOnFailureListener { e ->
                            Log.e("MainScreen", "Error marking as visited", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainScreen", "Error fetching user data", e)
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
                Log.e("MainScreen", "Error fetching reviews", exception)
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
                    Log.d("MainScreen", "Points updated successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("MainScreen", "Error updating points", e)
                }
        }
        .addOnFailureListener { e ->
            Log.e("MainScreen", "Error getting user points", e)
        }
}
