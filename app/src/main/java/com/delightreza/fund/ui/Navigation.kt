package com.delightreza.fund.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.delightreza.fund.data.AppDataStore
import com.delightreza.fund.data.Repository
import kotlinx.coroutines.launch

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val dataStore = remember { AppDataStore(context) }
    val repository = remember { Repository(dataStore) }
    val scope = rememberCoroutineScope()
    
    val userState = dataStore.userFlow.collectAsState(initial = "__LOADING__")
    val configUrlState = dataStore.configUrlFlow.collectAsState(initial = "__LOADING__")
    val tokenState = dataStore.tokenFlow.collectAsState(initial = null)

    if (userState.value == "__LOADING__" || configUrlState.value == "__LOADING__") {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startDest = if (configUrlState.value.isNullOrEmpty()) {
        "repo_selection"
    } else if (userState.value.isNullOrEmpty()) {
        "onboarding"
    } else {
        "main"
    }

    NavHost(navController = navController, startDestination = startDest) {
        
        composable("repo_selection") {
            RepoSelectionScreen(
                repository = repository,
                onConfigLoaded = {
                    scope.launch {
                        navController.navigate("onboarding") {
                            popUpTo("repo_selection") { inclusive = true }
                        }
                    }
                }
            )
        }

        composable("onboarding") {
            UserSelectionScreen(
                repository = repository,
                onUserSelected = { user ->
                    scope.launch {
                        dataStore.saveUser(user)
                        navController.navigate("main") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                },
                onChangeRepo = {
                    scope.launch {
                        dataStore.clearConfig()
                        navController.navigate("repo_selection") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                }
            )
        }

        composable("main") {
            MainScreen(
                rootNavController = navController, 
                repository = repository, 
                dataStore = dataStore, 
                currentUser = userState.value ?: "",
                hasToken = !tokenState.value.isNullOrBlank(),
                onSwitchRepo = {
                    scope.launch {
                        dataStore.clearConfig()
                        navController.navigate("repo_selection") {
                            popUpTo("main") { inclusive = true }
                        }
                    }
                }
            )
        }

        composable("settings") {
            ManageConfigScreen(
                navController = navController,
                repository = repository,
                token = tokenState.value
            )
        }

        composable(
            "add_transaction?type={type}&txId={txId}",
            arguments = listOf(
                navArgument("type") { nullable = true; type = NavType.StringType },
                navArgument("txId") { nullable = true; type = NavType.StringType }
            )
        ) { backStackEntry ->
            val token = tokenState.value
            val txId = backStackEntry.arguments?.getString("txId")
            val defaultType = backStackEntry.arguments?.getString("type")

            if (token.isNullOrBlank()) {
                AlertDialog(
                    onDismissRequest = { navController.popBackStack() },
                    title = { Text("Admin Access Required") },
                    text = { Text("You need a GitHub Token to add/edit transactions. Save your token in the Admin tab.") },
                    confirmButton = {
                        TextButton(onClick = { navController.popBackStack() }) { Text("OK") }
                    }
                )
            } else {
                AddTransactionScreen(
                    navController = navController,
                    repository = repository,
                    token = token,
                    transactionIdToEdit = txId,
                    defaultType = defaultType
                )
            }
        }

        composable(
            "detail/{txId}?forUser={forUser}",
            arguments = listOf(
                navArgument("txId") { type = NavType.StringType },
                navArgument("forUser") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val txId = backStackEntry.arguments?.getString("txId")
            val forUser = backStackEntry.arguments?.getString("forUser")
            TransactionDetailScreen(
                navController = navController,
                repository = repository,
                transactionId = txId,
                hasToken = !tokenState.value.isNullOrBlank(), 
                token = tokenState.value,
                currentUser = forUser
            )
        }
        composable("reset_commit") {
            ResetCommitScreen(navController, repository, tokenState.value)
        }
    }
}
