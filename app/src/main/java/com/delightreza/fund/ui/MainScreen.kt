package com.delightreza.fund.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    var profileViewUser by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                NavigationBarItem(
                    icon = { 
                        Icon(
                            if (selectedTab == 0) Icons.Default.Home else Icons.Outlined.Home, 
                            contentDescription = "Home"
                        ) 
                    },
                    label = { Text("Overview", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                if (hasToken) {
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                if (selectedTab == 1) Icons.Default.Settings else Icons.Outlined.Settings, 
                                contentDescription = "Admin"
                            ) 
                        },
                        label = { Text("Admin", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
                NavigationBarItem(
                    icon = { 
                        Icon(
                            if (selectedTab == 2) Icons.Default.Person else Icons.Outlined.Person, 
                            contentDescription = "Profile"
                        ) 
                    },
                    label = { Text("Profile", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    selected = selectedTab == 2,
                    onClick = { 
                        profileViewUser = null
                        selectedTab = 2 
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> {
                HomeScreen(
                    modifier = Modifier.padding(padding),
                    repository = repository,
                    navController = rootNavController,
                    onOpenProfile = { memberId ->
                        profileViewUser = memberId
                        selectedTab = 2
                    }
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
                    currentUser = profileViewUser ?: currentUser,
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

