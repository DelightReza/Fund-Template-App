package com.delightreza.fund.ui

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*


import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.delightreza.fund.data.AppConfig
import com.delightreza.fund.data.FundData
import com.delightreza.fund.data.Repository
import com.delightreza.fund.data.Transaction
import com.delightreza.fund.utils.DateUtils
import com.delightreza.fund.utils.FormatUtils
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier, 
    repository: Repository,
    navController: NavController
) {
    var data by remember { mutableStateOf<FundData?>(null) }
    var config by remember { mutableStateOf<AppConfig?>(null) }
    var balances by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isInitialLoad by remember { mutableStateOf(true) }
    var displayedCount by remember { mutableIntStateOf(20) }
    
    // Filters State
    var searchQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf("all") }
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val hasPendingSync by repository.pendingSyncFlow.collectAsState(initial = false)
    
    val context = LocalContext.current

    suspend fun updateData(newData: FundData) {
        data = newData
        balances = repository.calculateBalances(newData)
    }

    fun loadData(forceNetwork: Boolean) {
        scope.launch {
            if (forceNetwork) isRefreshing = true
            if (forceNetwork) displayedCount = 20
            config = repository.getAppConfig()
            if (isInitialLoad && !forceNetwork) {
                val cached = repository.getCachedData()
                if (cached != null) updateData(cached)
            }
            val freshData = repository.fetchData()
            config = repository.getAppConfig()
            if (freshData != null) updateData(freshData)
            isRefreshing = false
            isInitialLoad = false
        }
    }

    LaunchedEffect(Unit) { loadData(forceNetwork = false) }

    val ptrState = rememberPullToRefreshState()
    if (ptrState.isRefreshing) {
        LaunchedEffect(true) {
            loadData(forceNetwork = true)
            ptrState.endRefresh()
        }
    }

    Scaffold(modifier = modifier) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().nestedScroll(ptrState.nestedScrollConnection)) {
            if (data == null && isInitialLoad) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (data != null && config != null) {
                val currency = config!!.currency
                val resolveName = { id: String -> config!!.members.find { it.id == id }?.name ?: id }
                val resolveBillName = { id: String -> config!!.billTypes.find { it.id == id }?.name ?: id }

                // Computed Expenses Breakdown
                val expensesByCategory = config!!.billTypes.mapNotNull { bt ->
                    val amount = data!!.billTypes[bt.id] ?: 0.0
                    if (amount > 0) Pair(bt, amount) else null
                }

                // Filtered Transactions
                val filteredTransactions = data!!.transactions.filter { tx ->
                    val targetName = if (tx.type == "credit") resolveName(tx.payerId ?: tx.whoOrBill) else resolveBillName(tx.billTypeId ?: tx.whoOrBill)
                    
                    if (searchQuery.isNotEmpty()) {
                        val q = searchQuery.lowercase()
                        val matchesNote = tx.note.lowercase().contains(q)
                        val matchesName = targetName.lowercase().contains(q)
                        val matchesAmount = tx.amount.toString().contains(q)
                        if (!matchesNote && !matchesName && !matchesAmount) return@filter false
                    }

                    if (filterCategory != "all") {
                        if (filterCategory == "credit" && tx.type != "credit") return@filter false
                        if (filterCategory == "debit" && tx.type != "debit") return@filter false
                        if (filterCategory.startsWith("person_") && (tx.type != "credit" || (tx.payerId ?: tx.whoOrBill) != filterCategory.removePrefix("person_"))) return@filter false
                        if (filterCategory.startsWith("bill_") && (tx.type != "debit" || (tx.billTypeId ?: tx.whoOrBill) != filterCategory.removePrefix("bill_"))) return@filter false
                    }

                    if (dateFrom.isNotEmpty() && tx.date < dateFrom) return@filter false
                    if (dateTo.isNotEmpty() && tx.date > "$dateTo 23:59:59") return@filter false

                    true
                }

                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(text = config?.siteTitle ?: "Fund", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(text = config?.siteSubtitle ?: "Expense Tracker", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            }
                            if (hasPendingSync) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = CircleShape
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.CloudOff, contentDescription = "Offline Sync Pending", tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(14.dp))
                                        Text("Offline Sync", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (hasPendingSync) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Offline Mode — Auto-Sync Enabled", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        Text("Changes are displayed instantly & saved locally. Will sync automatically when online.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f))
                                    }
                                    TextButton(onClick = { loadData(forceNetwork = true) }) {
                                        Text("Sync Now", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Dashboard Cards
                    item {
                        val totalCredits = data!!.transactions.filter { it.type == "credit" }.sumOf { it.amount }
                        val totalDebits = data!!.transactions.filter { it.type == "debit" }.sumOf { it.amount }
                        val currentBalance = totalCredits - totalDebits
                        
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3B82F6)), modifier = Modifier.fillMaxWidth().height(100.dp)) {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Current Balance", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("${FormatUtils.formatAmount(currentBalance)} $currency", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatCard("Collected", totalCredits, Color(0xFF10B981), Modifier.weight(1f).fillMaxHeight(), currency)
                            StatCard("Spent", totalDebits, Color(0xFFEF4444), Modifier.weight(1f).fillMaxHeight(), currency)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Member Status
                    item {
                        Text("Member Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    val activeBalances = balances.entries.filter { (id, balance) ->
                        val isActive = config!!.members.find { it.id == id }?.active == true
                        isActive || balance != 0.0
                    }.sortedByDescending { it.value }

                    items(activeBalances.chunked(2)) { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { (id, net) ->
                                val name = resolveName(id)
                                val given = data!!.transactions.filter { it.type == "credit" && (it.payerId == id || it.whoOrBill == id) }.sumOf { it.amount }
                                CompactMemberCard(name, net, given, Modifier.weight(1f).fillMaxHeight(), currency)
                            }
                            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }



                    // Transactions & Filters
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showFilters = !showFilters }) {
                                Icon(if (showFilters) Icons.Default.FilterListOff else Icons.Default.FilterList, "Toggle Filters", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        
                        AnimatedVisibility(visible = showFilters) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = searchQuery, onValueChange = { searchQuery = it },
                                        placeholder = { Text("Search notes, names, amounts...") },
                                        leadingIcon = { Icon(Icons.Default.Search, null) },
                                        modifier = Modifier.fillMaxWidth(), singleLine = true
                                    )
                                    
                                    // Category Filter
                                    var expanded by remember { mutableStateOf(false) }
                                    val filterOptions = mutableListOf(Pair("all", "All Categories"), Pair("credit", "Income Only"), Pair("debit", "Expenses Only"))
                                    config!!.members.forEach { filterOptions.add(Pair("person_${it.id}", "Person: ${it.name}")) }
                                    config!!.billTypes.forEach { filterOptions.add(Pair("bill_${it.id}", "Bill: ${it.name}")) }
                                    
                                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                                        OutlinedTextField(
                                            value = filterOptions.find { it.first == filterCategory }?.second ?: "All Categories",
                                            onValueChange = {}, readOnly = true,
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                            modifier = Modifier.fillMaxWidth().menuAnchor()
                                        )
                                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                            filterOptions.forEach { (id, label) ->
                                                DropdownMenuItem(text = { Text(label) }, onClick = { filterCategory = id; expanded = false })
                                            }
                                        }
                                    }

                                    // Date Filters
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = dateFrom, onValueChange = {}, readOnly = true,
                                            label = { Text("From Date") }, modifier = Modifier.weight(1f),
                                            trailingIcon = { IconButton(onClick = {
                                                DatePickerDialog(context, { _, y, m, d -> dateFrom = String.format("%04d-%02d-%02d", y, m+1, d) }, 
                                                Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).show()
                                            }) { Icon(Icons.Default.CalendarToday, null) } }
                                        )
                                        OutlinedTextField(
                                            value = dateTo, onValueChange = {}, readOnly = true,
                                            label = { Text("To Date") }, modifier = Modifier.weight(1f),
                                            trailingIcon = { IconButton(onClick = {
                                                DatePickerDialog(context, { _, y, m, d -> dateTo = String.format("%04d-%02d-%02d", y, m+1, d) }, 
                                                Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).show()
                                            }) { Icon(Icons.Default.CalendarToday, null) } }
                                        )
                                    }
                                    
                                    if (searchQuery.isNotEmpty() || filterCategory != "all" || dateFrom.isNotEmpty() || dateTo.isNotEmpty()) {
                                        TextButton(onClick = { searchQuery = ""; filterCategory = "all"; dateFrom = ""; dateTo = "" }, modifier = Modifier.align(Alignment.End)) {
                                            Text("Clear Filters", color = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    val visibleTransactions = filteredTransactions.take(displayedCount)
                    
                    if (filteredTransactions.isEmpty()) {
                        item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No transactions found", color = Color.Gray) } }
                    } else {
                        items(visibleTransactions) { tx ->
                            val displayName = if (tx.type == "credit") {
                                resolveName(tx.payerId ?: tx.whoOrBill)
                            } else {
                                val bid = tx.billTypeId ?: tx.whoOrBill
                                val billName = resolveBillName(bid)
                                if (billName.equals("Other", ignoreCase = true) && tx.note.isNotEmpty()) tx.note else billName
                            }
                            TransactionRow(tx, displayName, currency) { navController.navigate("detail/${tx.id}") }
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                        }
                    }
                    
                    item {
                        if (displayedCount < filteredTransactions.size) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                OutlinedButton(onClick = { displayedCount += 20 }) { Text("Load Older Transactions") }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
            PullToRefreshContainer(state = ptrState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
fun TransactionRow(tx: Transaction, displayTitle: String, currency: String, onClick: () -> Unit) {
    val localDate = DateUtils.formatToLocalDateOnly(tx.date)
    val displaySubtitle = if (tx.note.isNotEmpty() && tx.note != displayTitle) "$localDate • ${tx.note}" else localDate

    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(displayTitle, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(displaySubtitle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${if(tx.type=="credit") "+" else "-"}${FormatUtils.formatAmount(tx.amount)} $currency",
                    color = if(tx.type=="credit") Color(0xFF059669) else Color(0xFFDC2626),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, "", tint = Color.LightGray, modifier = Modifier.size(16.dp))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun StatCard(title: String, amount: Double, color: Color, modifier: Modifier, currency: String) {
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("${FormatUtils.formatAmount(amount)} $currency", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CompactMemberCard(name: String, net: Double, given: Double, modifier: Modifier, currency: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Given: ${FormatUtils.formatAmount(given)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if(net>0) "+" else ""}${FormatUtils.formatAmount(net)}", 
                        color = if (net >= 0) Color(0xFF059669) else Color(0xFFE11D48), 
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.End
                    )
                    Text(currency, fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.End)
                }
            }
        }
    }
}
