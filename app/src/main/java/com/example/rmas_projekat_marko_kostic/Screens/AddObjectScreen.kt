package com.example.rmas_projekat_marko_kostic.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.storage.FirebaseStorage

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AddObjectScreen(navController: NavController) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf("") }
    var review by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val auth = FirebaseAuth.getInstance()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { inputStream ->
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                imageBitmap = bitmap.asImageBitmap()
                imageUri = uri.toString()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionState.hasPermission) {
            locationPermissionState.launchPermissionRequest()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Adding object... Please wait")
        } else {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = rating,
                onValueChange = {
                    if (it.toIntOrNull() in 1..5) rating = it
                },
                label = { Text("Rating (1-5)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = review,
                onValueChange = { review = it },
                label = { Text("Your Review") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                Text("Pick Image")
            }
            imageBitmap?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Image(bitmap = it, contentDescription = "Picked Image", modifier = Modifier.size(100.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val currentUser = auth.currentUser
                    if (currentUser != null && name.isNotEmpty() && description.isNotEmpty() && rating.isNotEmpty()) {
                        isLoading = true
                        val firestore = FirebaseFirestore.getInstance()
                        firestore.collection("users").document(currentUser.uid).get()
                            .addOnSuccessListener { document ->
                                val username = document.getString("username") ?: "Unknown"
                                addObjectToFirestore(
                                    context = context,
                                    name = name,
                                    description = description,
                                    rating = rating.toInt(),
                                    review = review,
                                    imageUri = imageUri,
                                    username = username,
                                    onComplete = {
                                        isLoading = false
                                        Toast.makeText(context, "Object added successfully", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    },
                                    onError = {
                                        isLoading = false
                                        Toast.makeText(context, "Failed to add object: $it", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            .addOnFailureListener {
                                isLoading = false
                                Toast.makeText(context, "Failed to retrieve user information", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(context, "All fields are required", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Object")
            }
        }
    }
}

fun addObjectToFirestore(
    context: Context,
    name: String,
    description: String,
    rating: Int,
    review: String,
    imageUri: String?,
    username: String,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val locationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        onError("Location permissions are not granted")
        return
    }

    locationProviderClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            val timestamp = System.currentTimeMillis()
            val objectId = name.replace(" ", "_")

            val objectData = hashMapOf(
                "name" to name,
                "description" to description,
                "rating" to rating,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "image_url" to "",
                "added_by" to username,
                "comments" to listOf(
                    mapOf(
                        "user_id" to username,
                        "comment_text" to review,
                        "timestamp" to timestamp
                    )
                )
            )

            if (imageUri != null) {
                val imageRef = storage.reference.child("object_images/$objectId.jpg")
                val uploadTask = imageRef.putFile(android.net.Uri.parse(imageUri))
                uploadTask.continueWithTask { task ->
                    if (!task.isSuccessful) {
                        task.exception?.let { throw it }
                    }
                    imageRef.downloadUrl
                }.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val downloadUri = task.result
                        objectData["image_url"] = downloadUri.toString()
                        saveObjectData(firestore, objectId, objectData, onComplete, onError)
                    } else {
                        onError("Failed to upload image")
                    }
                }
            } else {
                saveObjectData(firestore, objectId, objectData, onComplete, onError)
            }
        } else {
            onError("Failed to get current location")
        }
    }.addOnFailureListener { e ->
        onError("Failed to get current location: ${e.message}")
    }
}

fun saveObjectData(
    firestore: FirebaseFirestore,
    objectId: String,
    objectData: Map<String, Any>,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    firestore.collection("objects").document(objectId).set(objectData)
        .addOnSuccessListener {
            onComplete()
        }
        .addOnFailureListener { e ->
            onError("Failed to save object data: ${e.message}")
        }
}
