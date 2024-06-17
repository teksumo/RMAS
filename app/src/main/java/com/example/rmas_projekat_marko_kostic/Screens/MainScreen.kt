package com.example.rmas_projekat_marko_kostic.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.example.rmas_projekat_marko_kostic.service.LocationService

@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            Text(text = "Welcome to the Best Coffee Finder App!")

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
        }
    }
}
