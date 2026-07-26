package com.delightreza.fund.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.delightreza.fund.data.AppConfig
import com.delightreza.fund.data.BillTypeConfig
import com.delightreza.fund.data.MemberConfig
import com.delightreza.fund.data.Repository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageConfigScreen(
    navController: NavController,
    repository: Repository,
    token: String?
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var originalConfig by remember { mutableStateOf<AppConfig?>(null) }
    
    var members by remember { mutableStateOf<List<MemberConfig>>(emptyList()) }
    var billTypes by remember { mutableStateOf<List<BillTypeConfig>>(emptyList()) }
    var currency by remember { mutableStateOf("₹") }
    var siteTitle by remember { mutableStateOf("Fund") }
    var isSaving by remember { mutableStateOf(false) }

    var newPersonName by remember { mutableStateOf("") }
    var newBillTypeName by remember { mutableStateOf("") }
    var newBillTypeIcon by remember { mutableStateOf("🧾") }

    // State for removal confirm dialogs
    var personToRemove by remember { mutableStateOf<MemberConfig?>(null) }
    var billTypeToRemove by remember { mutableStateOf<BillTypeConfig?>(null) }

    fun performConfigUpdate(newConfig: AppConfig, commitMessage: String) {
        members = newConfig.members
        billTypes = newConfig.billTypes
        siteTitle = newConfig.siteTitle
        currency = newConfig.currency
        originalConfig = newConfig

        scope.launch {
            isSaving = true
            if (!token.isNullOrBlank()) {
                val success = repository.updateRemoteConfig(token, newConfig, commitMessage)
                if (success) {
                    snackbarHostState.showSnackbar("Updated: $commitMessage")
                } else {
                    snackbarHostState.showSnackbar("Failed to update config on GitHub")
                }
            } else {
                repository.saveLocalConfig(newConfig)
                snackbarHostState.showSnackbar("Saved locally")
            }
            isSaving = false
        }
    }

    LaunchedEffect(Unit) {
        val remote = repository.fetchRemoteConfig(token)
        val cfg = remote ?: repository.getAppConfig()
        originalConfig = cfg
        if (cfg != null) {
            members = cfg.members
            billTypes = cfg.billTypes
            currency = cfg.currency
            siteTitle = cfg.siteTitle
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Configuration", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val current = originalConfig ?: AppConfig()
                            val updatedConfig = current.copy(
                                siteTitle = siteTitle,
                                currency = currency,
                                members = members,
                                billTypes = billTypes
                            )
                            performConfigUpdate(updatedConfig, "Updated configuration")
                        },
                        enabled = !isSaving,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Manage People Section
            item {
                Text("People (${members.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newPersonName,
                            onValueChange = { newPersonName = it },
                            placeholder = { Text("Enter name", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).height(50.dp)
                        )
                        Button(
                            onClick = {
                                val name = newPersonName.trim()
                                if (name.isNotEmpty()) {
                                    val id = name.lowercase().replace(Regex("\\s+"), "_").replace(Regex("[^a-z0-9_]"), "")
                                    if (members.any { it.id == id || it.name.equals(name, ignoreCase = true) }) {
                                        scope.launch { snackbarHostState.showSnackbar("Person already exists!") }
                                        return@Button
                                    }
                                    val newPeople = members + MemberConfig(id = id, name = name, active = true)
                                    newPersonName = ""
                                    val current = originalConfig ?: AppConfig()
                                    val updated = current.copy(
                                        siteTitle = siteTitle,
                                        currency = currency,
                                        members = newPeople,
                                        billTypes = billTypes
                                    )
                                    performConfigUpdate(updated, "Added person: $name")
                                }
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Add")
                        }
                    }
                }
            }

            items(members) { member ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(member.name, fontWeight = FontWeight.SemiBold)
                                    Surface(
                                        color = if (member.active) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = if (member.active) "Active" else "Inactive",
                                            color = if (member.active) Color(0xFF15803D) else Color(0xFFB91C1C),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text("ID: ${member.id}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    val person = members.find { it.id == member.id }
                                    if (person != null) {
                                        val newPeople = members.map { if (it.id == member.id) it.copy(active = !it.active) else it }
                                        val actionName = if (person.active) "Deactivated" else "Activated"
                                        val msg = "$actionName person: ${person.name}"
                                        val current = originalConfig ?: AppConfig()
                                        val updated = current.copy(
                                            siteTitle = siteTitle,
                                            currency = currency,
                                            members = newPeople,
                                            billTypes = billTypes
                                        )
                                        performConfigUpdate(updated, msg)
                                    }
                                },
                                enabled = !isSaving
                            ) {
                                Text(if (member.active) "Deactivate" else "Activate", fontSize = 12.sp)
                            }
                            IconButton(onClick = { personToRemove = member }, enabled = !isSaving) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Manage Bill Types Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Bill Types (${billTypes.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newBillTypeIcon,
                            onValueChange = { newBillTypeIcon = it },
                            placeholder = { Text("Icon", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.width(60.dp).height(50.dp)
                        )
                        OutlinedTextField(
                            value = newBillTypeName,
                            onValueChange = { newBillTypeName = it },
                            placeholder = { Text("Bill Type Name", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).height(50.dp)
                        )
                        Button(
                            onClick = {
                                val name = newBillTypeName.trim()
                                val icon = newBillTypeIcon.trim().ifEmpty { "🧾" }
                                if (name.isNotEmpty()) {
                                    val id = name.lowercase().replace(Regex("\\s+"), "_").replace(Regex("[^a-z0-9_]"), "")
                                    if (billTypes.any { it.id == id || it.name.equals(name, ignoreCase = true) }) {
                                        scope.launch { snackbarHostState.showSnackbar("Bill type already exists!") }
                                        return@Button
                                    }
                                    val newBillTypes = billTypes + BillTypeConfig(id = id, name = name, icon = icon)
                                    newBillTypeName = ""
                                    newBillTypeIcon = "🧾"
                                    val current = originalConfig ?: AppConfig()
                                    val updated = current.copy(
                                        siteTitle = siteTitle,
                                        currency = currency,
                                        members = members,
                                        billTypes = newBillTypes
                                    )
                                    performConfigUpdate(updated, "Added bill type: $name")
                                }
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Add")
                        }
                    }
                }
            }

            items(billTypes) { billType ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("${billType.icon} ${billType.name}", fontWeight = FontWeight.SemiBold)
                                Text("ID: ${billType.id}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                        IconButton(onClick = { billTypeToRemove = billType }, enabled = !isSaving) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Confirm Remove Person Dialog
    personToRemove?.let { person ->
        AlertDialog(
            onDismissRequest = { personToRemove = null },
            title = { Text("Remove Person") },
            text = { Text("Remove ${person.name}? Historical data will still exist but won't be linked directly in dropdowns.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val p = personToRemove
                        personToRemove = null
                        if (p != null) {
                            val newPeople = members.filter { it.id != p.id }
                            val current = originalConfig ?: AppConfig()
                            val updated = current.copy(
                                siteTitle = siteTitle,
                                currency = currency,
                                members = newPeople,
                                billTypes = billTypes
                            )
                            performConfigUpdate(updated, "Removed person: ${p.name}")
                        }
                    },
                    enabled = !isSaving
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { personToRemove = null }) { Text("Cancel") }
            }
        )
    }

    // Confirm Remove Bill Type Dialog
    billTypeToRemove?.let { bt ->
        AlertDialog(
            onDismissRequest = { billTypeToRemove = null },
            title = { Text("Remove Bill Type") },
            text = { Text("Remove ${bt.name} bill category?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val b = billTypeToRemove
                        billTypeToRemove = null
                        if (b != null) {
                            val newBillTypes = billTypes.filter { it.id != b.id }
                            val current = originalConfig ?: AppConfig()
                            val updated = current.copy(
                                siteTitle = siteTitle,
                                currency = currency,
                                members = members,
                                billTypes = newBillTypes
                            )
                            performConfigUpdate(updated, "Removed bill type: ${b.name}")
                        }
                    },
                    enabled = !isSaving
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { billTypeToRemove = null }) { Text("Cancel") }
            }
        )
    }
}

