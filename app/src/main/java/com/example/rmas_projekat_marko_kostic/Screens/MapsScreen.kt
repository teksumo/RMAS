package com.example.rmas_projekat_marko_kostic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState

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
        } else {
            Log.d("MapsScreen", "Requesting location permission")
            locationPermissionState.launchPermissionRequest()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (currentLocation != null) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = locationPermissionState.hasPermission)
            ) {
                Marker(
                    position = currentLocation!!,
                    title = "Current Location"
                )
            }
            LaunchedEffect(currentLocation) {
                cameraPositionState.position = com.google.android.gms.maps.model.CameraPosition(currentLocation!!, 15f, 0f, 0f)
                Log.d("MapsScreen", "Camera position updated: $currentLocation")
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Locating...", style = MaterialTheme.typography.bodyLarge)
                Log.d("MapsScreen", "Current location is null")
            }
        }
    }
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

@Preview(showBackground = true)
@Composable
fun MapsScreenPreview() {
    MapsScreen()
}
