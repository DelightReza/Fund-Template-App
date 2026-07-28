package com.delightreza.fund.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.delightreza.fund.data.Repository
import com.delightreza.fund.data.GitHubCommitResponse
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetCommitScreen(navController: NavController, repository: Repository, token: String?) {
    var commits by remember { mutableStateOf<List<GitHubCommitResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isResetting by remember { mutableStateOf(false) }
    var commitToReset by remember { mutableStateOf<GitHubCommitResponse?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    if (commitToReset != null) {
        AlertDialog(
            onDismissRequest = { commitToReset = null },
            title = { Text("Confirm Reset Commit") },
            text = {
                Text("Are you sure you want to reset the repository to commit '${commitToReset!!.commit.message}' (${commitToReset!!.sha.take(7)})?\n\nThis will overwrite all newer commits in the branch.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = commitToReset
                        commitToReset = null
                        if (target != null && token != null) {
                            scope.launch {
                                isResetting = true
                                try {
                                    repository.resetCommit(token, target.sha)
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Failed to reset commit: ${e.message}")
                                    isResetting = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset Commit")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { commitToReset = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    LaunchedEffect(Unit) {
        if (token != null) {
            try {
                commits = repository.getRecentCommits(token)
            } catch (e: Exception) {
                // error
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reset Commit") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { p ->
        if (isLoading || isResetting) {
            Box(modifier = Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.padding(p).padding(16.dp)) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Warning: Resetting to an older commit will permanently overwrite recent changes in the repository.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (commits.isEmpty()) {
                    item { Text("No commits found.", modifier = Modifier.padding(16.dp)) }
                } else {
                    items(commits) { commit ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clickable { commitToReset = commit },
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(commit.commit.message, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(commit.sha.take(7), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(commit.commit.author.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
