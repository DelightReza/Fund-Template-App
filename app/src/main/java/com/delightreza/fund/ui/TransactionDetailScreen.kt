package com.delightreza.fund.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.delightreza.fund.data.AppConfig
import com.delightreza.fund.data.Repository
import com.delightreza.fund.data.Transaction
import com.delightreza.fund.utils.DateUtils
import com.delightreza.fund.utils.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    navController: NavController,
    repository: Repository,
    transactionId: String?,
    hasToken: Boolean = false,
    token: String? = null,
    currentUser: String? = null
) {
    var transaction by remember { mutableStateOf<Transaction?>(null) }
    var groupTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var config by remember { mutableStateOf<AppConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteReason by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }
    
    var balanceBefore by remember { mutableStateOf<Double?>(null) }
    var balanceAfter by remember { mutableStateOf<Double?>(null) }
    var userChange by remember { mutableStateOf(0.0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(transactionId, currentUser) {
        val cached = repository.getCachedData()
        config = repository.getAppConfig()
        val data = cached ?: repository.fetchData()
        if (data != null) {
            val single = data.transactions.find { it.id == transactionId }
            if (single != null) {
                transaction = single
            } else {
                val group = data.transactions.filter { it.parentId == transactionId }
                if (group.isNotEmpty()) groupTransactions = group
            }

            if (!currentUser.isNullOrEmpty()) {
                val sortedTxs = data.transactions.sortedBy { it.date }
                var running = 0.0
                var before: Double? = null
                var after: Double? = null
                var change = 0.0

                val targetIds = if (single != null) setOf(single.id) else groupTransactions.map { it.id }.toSet()

                for (tx in sortedTxs) {
                    var impact = 0.0
                    if (tx.type == "credit") {
                        val pid = tx.payerId ?: tx.whoOrBill
                        if (pid == currentUser) impact = tx.amount
                    } else {
                        val activeMembers = config?.members?.filter { it.active }?.map { it.id } ?: emptyList()
                        val splitMembers = if (!tx.splitAmong.isNullOrEmpty()) tx.splitAmong else activeMembers.filter { !(tx.exemptions ?: emptyList()).contains(it) }
                        if (splitMembers.contains(currentUser)) {
                            impact = - (tx.amount / splitMembers.size.coerceAtLeast(1))
                        }
                    }

                    val isTarget = targetIds.contains(tx.id)
                    if (isTarget) {
                        if (before == null) before = running
                        change += impact
                        running += impact
                        after = running
                    } else {
                        running += impact
                    }
                }
                balanceBefore = before
                balanceAfter = after
                userChange = change
            }
        }
        isLoading = false
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Transaction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (groupTransactions.isNotEmpty()) "This is a Group Transaction. Deleting it will remove ALL entries." else "Delete this transaction?")
                    OutlinedTextField(
                        value = deleteReason,
                        onValueChange = { deleteReason = it },
                        label = { Text("Reason for deletion (optional)") },
                        placeholder = { Text("e.g. Mistake, duplicated entry...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        showDeleteDialog = false
                        scope.launch {
                            if (token != null) {
                                val targetId = transaction?.id ?: groupTransactions.firstOrNull()?.id
                                if (targetId != null && repository.deleteTransaction(token, targetId, deleteReason.ifBlank { null })) {
                                    navController.popBackStack()
                                }
                            }
                            isDeleting = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Delete") }
            },
            dismissButton = { OutlinedButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    val targetEditId = transaction?.id ?: groupTransactions.firstOrNull()?.id

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (groupTransactions.isNotEmpty()) "Group Details" else "Transaction Details") },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
            actions = {
                if (!isDeleting && targetEditId != null) {
                    IconButton(onClick = { navController.navigate("add_transaction?txId=$targetEditId") }) {
                        Icon(Icons.Default.Edit, "Edit Transaction")
                    }
                }
                if (hasToken && !isDeleting && targetEditId != null) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
                    }
                }
            }
        )
    }) { p ->
        if (isDeleting) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.Red) }
        else if (isLoading) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (groupTransactions.isNotEmpty()) GroupDetailView(groupTransactions, p, config, currentUser, balanceBefore, balanceAfter, userChange)
        else if (transaction != null) SingleTransactionView(transaction!!, p, navController, config, currentUser, balanceBefore, balanceAfter, userChange)
        else Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Transaction not found") }
    }
}

@Composable
fun RunningBalanceCard(userName: String, before: Double?, change: Double, after: Double?, currency: String) {
    if (before == null || after == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Running Balance for $userName", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Balance Before:", color = Color.Gray)
                Text("${FormatUtils.formatAmount(before)} $currency", fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Transaction Impact:", color = Color.Gray)
                val sign = if (change >= 0) "+" else ""
                Text("$sign${FormatUtils.formatAmount(change)} $currency", fontWeight = FontWeight.Bold, color = if (change >= 0) Color(0xFF059669) else Color(0xFFDC2626))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.Gray.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Balance After:", fontWeight = FontWeight.Bold)
                Text("${FormatUtils.formatAmount(after)} $currency", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun GroupDetailView(
    transactions: List<Transaction>,
    paddingValues: PaddingValues,
    config: AppConfig?,
    currentUser: String?,
    balanceBefore: Double?,
    balanceAfter: Double?,
    userChange: Double
) {
    val totalCredit = transactions.filter { it.type == "credit" }.sumOf { it.amount }
    val totalDebit = transactions.filter { it.type == "debit" }.sumOf { it.amount }
    val firstTx = transactions.first()
    val groupTitle = if(firstTx.note.contains("Settlement")) "Settlement Group" else if (firstTx.note.contains("Transfer")) "Transfer Group" else "Transaction Group"
    val currency = config?.currency ?: "₹"
    val currentUserName = config?.members?.find { it.id == currentUser }?.name ?: currentUser

    Column(modifier = Modifier.padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState())) {
        if (!currentUserName.isNullOrEmpty() && balanceBefore != null) {
            RunningBalanceCard(currentUserName, balanceBefore, userChange, balanceAfter, currency)
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Layers, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(groupTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(DateUtils.formatToLocal(firstTx.date), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Total Credit", style = MaterialTheme.typography.labelMedium); Text("${FormatUtils.formatAmount(totalCredit)} $currency", style = MaterialTheme.typography.titleLarge, color = Color(0xFF059669), fontWeight = FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Total Debit", style = MaterialTheme.typography.labelMedium); Text("${FormatUtils.formatAmount(totalDebit)} $currency", style = MaterialTheme.typography.titleLarge, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold) }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Included Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        transactions.forEach { tx ->
            val displayName = if (tx.type == "credit") {
                config?.members?.find { it.id == (tx.payerId ?: tx.whoOrBill) }?.name ?: tx.whoOrBill
            } else {
                config?.billTypes?.find { it.id == (tx.billTypeId ?: tx.whoOrBill) }?.name ?: tx.whoOrBill
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text(displayName, fontWeight = FontWeight.Bold); if(tx.note.isNotEmpty()) Text(tx.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                        Text("${if(tx.type=="credit") "+" else "-"}${FormatUtils.formatAmount(tx.amount)} $currency", color = if(tx.type=="credit") Color(0xFF059669) else Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                    }

                    // Split Breakdown for Debit in Group
                    if (tx.type == "debit") {
                        val activeMembers = config?.members?.filter { it.active } ?: emptyList()
                        val splitMemberIds = if (!tx.splitAmong.isNullOrEmpty()) tx.splitAmong else activeMembers.filter { !(tx.exemptions ?: emptyList()).contains(it.id) }.map { it.id }
                        if (splitMemberIds.isNotEmpty()) {
                            val perPerson = tx.amount / splitMemberIds.size.coerceAtLeast(1)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Split Details:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            splitMemberIds.forEach { id ->
                                val mName = config?.members?.find { m -> m.id == id }?.name ?: id
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(mName, fontSize = 12.sp)
                                    Text("${FormatUtils.formatAmount(perPerson)} $currency", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SingleTransactionView(
    tx: Transaction,
    paddingValues: PaddingValues,
    navController: NavController,
    config: AppConfig?,
    currentUser: String?,
    balanceBefore: Double?,
    balanceAfter: Double?,
    userChange: Double
) {
    val currency = config?.currency ?: "₹"
    val currentUserName = config?.members?.find { it.id == currentUser }?.name ?: currentUser

    Column(modifier = Modifier.padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!currentUserName.isNullOrEmpty() && balanceBefore != null) {
            RunningBalanceCard(currentUserName, balanceBefore, userChange, balanceAfter, currency)
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(if(tx.type=="credit") Color(0xFFD1FAE5) else Color(0xFFFFE4E6)), contentAlignment = Alignment.Center) {
                    Icon(if(tx.type=="credit") Icons.Default.Check else Icons.Default.Receipt, null, tint = if(tx.type=="credit") Color(0xFF059669) else Color(0xFFE11D48), modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(if(tx.type=="credit") "Money Received" else "Bill Payment", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("${FormatUtils.formatAmount(tx.amount)} $currency", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.LightGray.copy(alpha = 0.5f))
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                val displayName = if(tx.type == "credit") config?.members?.find { it.id == (tx.payerId ?: tx.whoOrBill) }?.name ?: tx.whoOrBill
                else config?.billTypes?.find { it.id == (tx.billTypeId ?: tx.whoOrBill) }?.name ?: tx.whoOrBill
                
                DetailRow("Subject", displayName)
                DetailRow("Date", DateUtils.formatToLocal(tx.date))
                if (tx.note.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Note", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text(tx.note, fontSize = 16.sp)
                }

                // Split Details Breakdown
                if (tx.type == "debit") {
                    val activeMembers = config?.members?.filter { it.active } ?: emptyList()
                    val splitMemberIds = if (!tx.splitAmong.isNullOrEmpty()) tx.splitAmong else activeMembers.filter { !(tx.exemptions ?: emptyList()).contains(it.id) }.map { it.id }
                    if (splitMemberIds.isNotEmpty()) {
                        val perPerson = tx.amount / splitMemberIds.size.coerceAtLeast(1)
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))) {
                            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                Text("Split Breakdown (${splitMemberIds.size} people)", color = Color(0xFF1E3A8A), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                splitMemberIds.forEach { id ->
                                    val mName = config?.members?.find { m -> m.id == id }?.name ?: id
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(mName, color = Color(0xFF1E40AF))
                                        Text("${FormatUtils.formatAmount(perPerson)} $currency", fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                                    }
                                }
                            }
                        }
                    }
                }

                if (tx.parentId != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            val route = "detail/${tx.parentId}" + if (!currentUser.isNullOrEmpty()) "?forUser=$currentUser" else ""
                            navController.navigate(route)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Group Transaction")
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray); Text(value, fontWeight = FontWeight.Bold)
    }
}
