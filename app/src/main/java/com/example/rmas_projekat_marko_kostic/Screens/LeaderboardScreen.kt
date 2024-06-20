package com.example.rmas_projekat_marko_kostic.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.android.gms.tasks.Tasks

data class User(
    val firstName: String,
    val lastName: String,
    val username: String,
    val points: Long,
    val photoUrl: String
)

@Composable
fun LeaderboardScreen() {
    var users by remember { mutableStateOf<List<User>>(emptyList()) }

    LaunchedEffect(Unit) {
        fetchLeaderboard { fetchedUsers ->
            users = fetchedUsers
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Leaderboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Table with all users sorted by points
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(users) { user ->
                    Card(
                        modifier = Modifier
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                            .fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Image(
                                painter = rememberImagePainter(user.photoUrl),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("${user.firstName} ${user.lastName}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Username: ${user.username}")
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Points: ${user.points}")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fetchLeaderboard(onUsersFetched: (List<User>) -> Unit) {
    val firestore = FirebaseFirestore.getInstance()

    firestore.collection("users")
        .orderBy("points", Query.Direction.DESCENDING)
        .get()
        .addOnSuccessListener { documents ->
            val users = documents.mapNotNull { document ->
                val firstName = document.getString("firstName")
                val lastName = document.getString("lastName")
                val username = document.getString("username")
                val points = document.getLong("points")
                val photoUrl = document.getString("photoUrl")

                if (firstName != null && lastName != null && username != null && points != null && photoUrl != null) {
                    User(firstName, lastName, username, points, photoUrl)
                } else {
                    Log.d("LeaderboardScreen", "Invalid user data: $document")
                    null
                }
            }
            onUsersFetched(users)
        }
        .addOnFailureListener { e ->
            Log.e("LeaderboardScreen", "Error fetching leaderboard", e)
        }
}
