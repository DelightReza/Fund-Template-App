package com.delightreza.fund.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.delightreza.fund.data.AppDataStore
import com.delightreza.fund.data.Repository
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    rootNavController: NavController,
    repository: Repository,
    dataStore: AppDataStore,
    currentUser: String,
    hasToken: Boolean,
    onSwitchRepo: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                if (hasToken) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Admin") },
                        label = { Text("Admin") },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                }
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> {
                HomeScreen(
                    modifier = Modifier.padding(padding),
                    repository = repository,
                    navController = rootNavController
                )
            }
            1 -> {
                AdminScreen(
                    modifier = Modifier.padding(padding),
                    navController = rootNavController,
                    repository = repository,
                    dataStore = dataStore
                )
            }
            2 -> {
                ProfileScreen(
                    modifier = Modifier.padding(padding),
                    repository = repository,
                    dataStore = dataStore,
                    currentUser = currentUser,
                    navController = rootNavController,
                    onLogout = {
                        scope.launch {
                            dataStore.clearUser()
                            rootNavController.navigate("onboarding") {
                                popUpTo("main") { inclusive = true }
                            }
                        }
                    },
                    onSwitchRepo = onSwitchRepo
                )
            }
        }
    }
}
