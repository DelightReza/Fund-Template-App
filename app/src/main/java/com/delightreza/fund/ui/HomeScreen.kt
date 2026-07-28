package com.delightreza.fund.ui

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.delightreza.fund.data.AppConfig
import com.delightreza.fund.data.AppDataStore
import com.delightreza.fund.data.FundData
import com.delightreza.fund.data.Repository
import com.delightreza.fund.data.Transaction
import com.delightreza.fund.utils.DateUtils
import com.delightreza.fund.utils.FormatUtils
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

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
    val dataStore = remember { AppDataStore(context) }
    val darkModeMode by dataStore.darkModeFlow.collectAsState(initial = "system")

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(ptrState.nestedScrollConnection)
    ) {
            if (data == null && isInitialLoad) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (data != null && config != null) {
                val currency = config!!.currency
                val resolveName = { id: String -> config!!.members.find { it.id == id }?.name ?: id }
                val resolveBillName = { id: String -> config!!.billTypes.find { it.id == id }?.name ?: id }

                // Computed Expenses Breakdown
                val expensesByCategory = config!!.billTypes.mapNotNull { bt ->
                    val amount = data!!.billTypes[bt.id] ?: 0.0
                    if (amount > 0) Pair(bt, amount) else null
                }.sortedByDescending { it.second }

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

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Section
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = config?.siteTitle ?: "Fund",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = config?.siteSubtitle ?: "Expense & Budget Tracker",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (hasPendingSync) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(20.dp),
                                        tonalElevation = 2.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CloudOff,
                                                contentDescription = "Sync Pending",
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                "Offline",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                FilledIconButton(
                                    onClick = {
                                        val nextMode = if (darkModeMode == "dark") "light" else "dark"
                                        scope.launch { dataStore.setDarkMode(nextMode) }
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (darkModeMode == "dark") Icons.Default.DarkMode else Icons.Default.LightMode,
                                        contentDescription = "Toggle Dark Mode",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        if (hasPendingSync) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Sync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Offline Changes Saved",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            "Auto-sync will push changes when online.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                    TextButton(onClick = { loadData(forceNetwork = true) }) {
                                        Text("Sync Now", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    }
                                }
                            }
                        }
                    }

                    // Modern Gradient Hero Card
                    item {
                        val totalCredits = data!!.transactions.filter { it.type == "credit" }.sumOf { it.amount }
                        val totalDebits = data!!.transactions.filter { it.type == "debit" }.sumOf { it.amount }
                        val currentBalance = totalCredits - totalDebits
                        
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF0F172A),
                                                Color(0xFF1E1B4B),
                                                Color(0xFF312E81)
                                            )
                                        )
                                    )
                                    .padding(24.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Outlined.AccountBalanceWallet,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Text(
                                                "Current Balance",
                                                color = Color.White.copy(alpha = 0.8f),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Surface(
                                            color = Color.White.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = currency,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "${FormatUtils.formatAmount(currentBalance)} $currency",
                                        color = Color.White,
                                        style = MaterialTheme.typography.displayLarge
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Income Stat Pill
                                        Surface(
                                            color = Color.White.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp).fillMaxSize(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF10B981).copy(alpha = 0.25f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.ArrowDownward,
                                                        contentDescription = null,
                                                        tint = Color(0xFF34D399),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        "Collected",
                                                        color = Color.White.copy(alpha = 0.7f),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                    Text(
                                                        "+${FormatUtils.formatAmount(totalCredits)}",
                                                        color = Color(0xFF34D399),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // Expense Stat Pill
                                        Surface(
                                            color = Color.White.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp).fillMaxSize(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFEF4444).copy(alpha = 0.25f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.ArrowUpward,
                                                        contentDescription = null,
                                                        tint = Color(0xFFF87171),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        "Spent",
                                                        color = Color.White.copy(alpha = 0.7f),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                    Text(
                                                        "-${FormatUtils.formatAmount(totalDebits)}",
                                                        color = Color(0xFFF87171),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Category Expense Carousel
                    if (expensesByCategory.isNotEmpty()) {
                        item {
                            Text(
                                "Top Bill Breakdown",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(end = 8.dp)
                            ) {
                                items(expensesByCategory) { (billType, amount) ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Receipt,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    billType.name,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    "${FormatUtils.formatAmount(amount)} $currency",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Member Status Header
                    item {
                        Text(
                            "Member Balance Overview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    val activeBalances = balances.entries.filter { (id, balance) ->
                        val isActive = config!!.members.find { it.id == id }?.active == true
                        isActive || balance != 0.0
                    }.sortedByDescending { it.value }

                    items(activeBalances.chunked(2)) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { (id, net) ->
                                val name = resolveName(id)
                                val given = data!!.transactions.filter { it.type == "credit" && (it.payerId == id || it.whoOrBill == id) }.sumOf { it.amount }
                                RedesignedMemberCard(name, net, given, Modifier.weight(1f).fillMaxHeight(), currency)
                            }
                            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    // Transactions & Filters Header
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Recent Transactions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    "${filteredTransactions.size} entry(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { showFilters = !showFilters },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (showFilters) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(
                                    if (showFilters) Icons.Default.FilterListOff else Icons.Default.FilterList,
                                    "Toggle Filters",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = showFilters,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Search notes, names, amounts...") },
                                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    // Category Filter Dropdown
                                    var expanded by remember { mutableStateOf(false) }
                                    val filterOptions = mutableListOf(
                                        Pair("all", "All Categories"),
                                        Pair("credit", "Income Only"),
                                        Pair("debit", "Expenses Only")
                                    )
                                    config!!.members.forEach { filterOptions.add(Pair("person_${it.id}", "Person: ${it.name}")) }
                                    config!!.billTypes.forEach { filterOptions.add(Pair("bill_${it.id}", "Bill: ${it.name}")) }

                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = !expanded }
                                    ) {
                                        OutlinedTextField(
                                            value = filterOptions.find { it.first == filterCategory }?.second ?: "All Categories",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Category Filter") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth().menuAnchor()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            filterOptions.forEach { (id, label) ->
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = { filterCategory = id; expanded = false }
                                                )
                                            }
                                        }
                                    }

                                    // Date Filters
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = dateFrom,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("From") },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f),
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    DatePickerDialog(context, { _, y, m, d -> dateFrom = String.format("%04d-%02d-%02d", y, m+1, d) }, 
                                                    Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).show()
                                                }) { Icon(Icons.Default.CalendarToday, null) }
                                            }
                                        )
                                        OutlinedTextField(
                                            value = dateTo,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("To") },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f),
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    DatePickerDialog(context, { _, y, m, d -> dateTo = String.format("%04d-%02d-%02d", y, m+1, d) }, 
                                                    Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).show()
                                                }) { Icon(Icons.Default.CalendarToday, null) }
                                            }
                                        )
                                    }

                                    if (searchQuery.isNotEmpty() || filterCategory != "all" || dateFrom.isNotEmpty() || dateTo.isNotEmpty()) {
                                        TextButton(
                                            onClick = { searchQuery = ""; filterCategory = "all"; dateFrom = ""; dateTo = "" },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reset Filters")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val visibleTransactions = filteredTransactions.take(displayedCount)

                    if (filteredTransactions.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "No matching transactions",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(visibleTransactions) { tx ->
                            val displayName = if (tx.type == "credit") {
                                resolveName(tx.payerId ?: tx.whoOrBill)
                            } else {
                                val bid = tx.billTypeId ?: tx.whoOrBill
                                val billName = resolveBillName(bid)
                                if (billName.equals("Other", ignoreCase = true) && tx.note.isNotEmpty()) tx.note else billName
                            }
                            RedesignedTransactionRow(tx, displayName, currency) {
                                navController.navigate("detail/${tx.id}")
                            }
                        }
                    }

                    item {
                        if (displayedCount < filteredTransactions.size) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(
                                    onClick = { displayedCount += 20 },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Load More Transactions")
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
            PullToRefreshContainer(state = ptrState, modifier = Modifier.align(Alignment.TopCenter))
        }
}

@Composable
fun RedesignedTransactionRow(tx: Transaction, displayTitle: String, currency: String, onClick: () -> Unit) {
    val localDate = DateUtils.formatToLocalDateOnly(tx.date)
    val isIncome = tx.type == "credit"
    val badgeBg = if (isIncome) Color(0xFFD1FAE5) else Color(0xFFFFE4E6)
    val badgeIconColor = if (isIncome) Color(0xFF059669) else Color(0xFFE11D48)
    val icon = if (isIncome) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeIconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (tx.note.isNotEmpty() && tx.note != displayTitle) "$localDate • ${tx.note}" else localDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isIncome) "+" else "-"}${FormatUtils.formatAmount(tx.amount)} $currency",
                    color = badgeIconColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun RedesignedMemberCard(name: String, net: Double, given: Double, modifier: Modifier, currency: String) {
    val isPositive = net >= 0
    val netBgColor = if (isPositive) Color(0xFFD1FAE5) else Color(0xFFFFE4E6)
    val netTextColor = if (isPositive) Color(0xFF059669) else Color(0xFFE11D48)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.take(1),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = netBgColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${if (isPositive) "+" else ""}${FormatUtils.formatAmount(net)}",
                        color = netTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Given:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${FormatUtils.formatAmount(given)} $currency",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

