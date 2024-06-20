package com.example.rmas_projekat_marko_kostic.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.rmas_projekat_marko_kostic.MapsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "login") {
        composable("login") { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }
        composable("main") { MainScreen(navController) }
        composable("map") { MapsScreen() }
        composable("add_object") { AddObjectScreen(navController) }
        composable("leaderboard") { LeaderboardScreen() }
    }
}
