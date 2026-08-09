package com.catcontroller.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.catcontroller.ui.main.MainScreen
import com.catcontroller.ui.main.MainViewModel
import com.catcontroller.ui.profiles.ProfilesScreen
import com.catcontroller.ui.profiles.ProfilesViewModel
import com.catcontroller.ui.settings.SettingsScreen
import com.catcontroller.ui.settings.SettingsViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val mainVm: MainViewModel = hiltViewModel()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = Color.Black,
    ) {
        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                MainScreen(
                    vm                 = mainVm,
                    onNavigateSettings = { navController.navigate("settings") },
                    onNavigateProfiles = { navController.navigate("profiles") },
                )
            }
            composable("settings") {
                val vm: SettingsViewModel = hiltViewModel()
                SettingsScreen(vm = vm, onBack = navController::popBackStack)
            }
            composable("profiles") {
                val vm: ProfilesViewModel = hiltViewModel()
                ProfilesScreen(
                    vm       = vm,
                    onBack   = navController::popBackStack,
                    onSelect = { profile ->
                        mainVm.loadProfile(profile)
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}
