package com.delightreza.fund.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delightreza.fund.data.Repository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoSelectionScreen(
    repository: Repository,
    onConfigLoaded: () -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var isTokenVisible by remember { mutableStateOf(false) }
    var showTokenInput by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    val savedRepos by repository.getSavedRepos().collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    fun attemptConnection() {
        if (urlInput.isBlank()) { errorMsg = "Please enter a URL or owner/repo"; return }
        isLoading = true
        errorMsg = null
        scope.launch {
            val token = tokenInput.trim().takeIf { it.isNotEmpty() }
            val config = try {
                repository.setActiveConfig(urlInput.trim(), token)
            } catch (e: Exception) {
                null
            }
            isLoading = false
            if (config != null) {
                tokenInput = "" // Clear token on success
                onConfigLoaded()
            } else {
                errorMsg = "Failed to load config. Check URL or Token."
                showTokenInput = true
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Your Funds", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("Select a workspace to manage expenses", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(32.dp))

            if (savedRepos.isNotEmpty()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    items(savedRepos.toList()) { entry ->
                        val (title, url) = parseRepoEntry(entry)
                        SavedRepoItem(title = title, url = url, onClick = { urlInput = url; attemptConnection() },
                            onDelete = { scope.launch { repository.removeSavedRepo(url) } })
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(24.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add New Fund", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 12.dp))
                    
                    OutlinedTextField(
                        value = urlInput, onValueChange = { urlInput = it },
                        label = { Text("Repo URL or owner/repo") }, 
                        placeholder = { Text("owner/repo") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, isError = errorMsg != null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    if (showTokenInput) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = tokenInput, onValueChange = { tokenInput = it },
                            label = { Text("GitHub Token (Optional)") },
                            placeholder = { Text("ghp_...") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { attemptConnection() }),
                            trailingIcon = {
                                IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                    Icon(if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle visibility")
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = { attemptConnection() }, enabled = !isLoading, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Connect Fund")
                    }
                }
            }
        }
    }
}

fun parseRepoEntry(entry: String): Pair<String, String> {
    return if (entry.trim().startsWith("{")) {
        try {
            val json = org.json.JSONObject(entry)
            json.optString("t", "Repository") to json.optString("u", "")
        } catch (e: Exception) { "Repository" to entry }
    } else {
        val name = try {
            val uri = java.net.URI(entry)
            val parts = uri.path.split("/")
            if (parts.size >= 3) parts[2].replaceFirstChar { it.uppercase() } else "Repository"
        } catch (e: Exception) { "Repository" }
        name to entry
    }
}

@Composable
fun SavedRepoItem(title: String, url: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
        }
    }
}
