package com.example.rmas_projekat_marko_kostic.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

/* VAZNO remember nam cuva stanje tokom recomposition faze*/
/* mutableStateOf funkcija koja nam kreira stanje koje se moze menjati. Kad se
* vrenost stanja promeni, Compose automatski osvezava deo UI gde se koristi ta vrednost*/

/* Composable koristimo kod funkcija koje definisu UI. Kad se stanje unutar ovih
funkcija promeni, Compose automatski osvezava UI koji zavisi od tog stanja.*/


@Composable
fun RegisterScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }

    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    /* sa ovim rememberLauncher... kreiramo aktivnosti*/
    val imagePickerLauncher = rememberLauncherForActivityResult(
        /* sa ovim getContent otvara galerijku za izbor slike*/
        contract = ActivityResultContracts.GetContent(),
        /* ovo ispod je lambda fja koja se poziva kada korisnik izabere sliku i aktivnost se zavrsi
        onResult iskecuje rezultat Uri, i postavlja photoUri na uri
         */
        onResult = { uri: Uri? ->
            photoUri = uri
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Registering... Please wait")
        } else {
            if (showSuccessMessage) {
                Text(text = "Registration successful!", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { navController.navigate("login") }) {
                    Text(text = "Go to Login")
                }
            } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("First Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Last Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pick Profile Photo")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (password == confirmPassword) {
                            loading = true
                            /* auth ovo je instanca firebase Auth, s njom radimo reg i sve */
                            auth.createUserWithEmailAndPassword(email, password) /*pozivamo registraciju u firebase*/
                                .addOnCompleteListener { task -> /* ovim Listenerom proveravamo da li je asinhrona operacija uspesno izvrsena ili ne*/
                                    if (task.isSuccessful) {
                                        val user = auth.currentUser
                                        val userId = user?.uid

                                        val profileData = hashMapOf(
                                            "username" to username,
                                            "firstName" to firstName,
                                            "lastName" to lastName,
                                            "phoneNumber" to phoneNumber,
                                            "email" to email,
                                            "points" to 0 // Dodavanje polja "points" sa vrednošću 0
                                        )

                                        if (userId != null) {
                                            /* ovo.set(profileData) koristimo da upisemo podatke u odabrani dokument, upisujemo ovaj profileData*/
                                            db.collection("users").document(userId).set(profileData)
                                                .addOnCompleteListener { profileTask ->
                                                    if (profileTask.isSuccessful) {/*sad se vrsi upload slike AKO JE IMA*/
                                                        photoUri?.let { uri -> /* AKO JE photoURI null onda se samo preskoci ovo*/
                                                            /*ovime storage.reference.child dobijamo referencu za cuvanje slike*/
                                                            val storageRef = storage.reference.child("profile_photos/${UUID.randomUUID()}.jpg")
                                                            storageRef.putFile(uri) /* ovime uploadujemo sliku*/
                                                                .addOnSuccessListener {
                                                                    storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                                                                        /*ovime stavljamo photoUrl u firebase*/
                                                                        db.collection("users").document(userId).update("photoUrl", downloadUrl.toString())
                                                                            .addOnSuccessListener {
                                                                                loading = false
                                                                                showSuccessMessage = true
                                                                            }
                                                                            .addOnFailureListener { exception ->
                                                                                loading = false
                                                                                Toast.makeText(context, "Photo URL save failed: ${exception.message}", Toast.LENGTH_LONG).show()
                                                                                Log.e("RegisterScreen", "Photo URL save failed", exception)
                                                                            }
                                                                    }
                                                                }
                                                                .addOnFailureListener { exception ->
                                                                    loading = false
                                                                    Toast.makeText(context, "Photo upload failed: ${exception.message}", Toast.LENGTH_LONG).show()
                                                                    Log.e("RegisterScreen", "Photo upload failed", exception)
                                                                }
                                                        } ?: run {/* ?: sam iskoristio ako se desi null vrednost za photoUri */
                                                            loading = false
                                                            showSuccessMessage = true
                                                        }
                                                    } else {
                                                        loading = false
                                                        val errorMessage = profileTask.exception?.message ?: "Unknown error"
                                                        Toast.makeText(context, "Profile save failed: $errorMessage", Toast.LENGTH_LONG).show()
                                                        Log.e("RegisterScreen", "Profile save failed", profileTask.exception)
                                                    }
                                                }
                                        } else {
                                            loading = false
                                            Toast.makeText(context, "Registration failed: User ID is null", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        loading = false
                                        val errorMessage = task.exception?.message ?: "Unknown error"
                                        Toast.makeText(context, "Registration failed: $errorMessage", Toast.LENGTH_LONG).show()
                                        Log.e("RegisterScreen", "Registration failed", task.exception)
                                    }
                                }
                        } else {
                            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Register")
                }
            }
        }
    }
}
