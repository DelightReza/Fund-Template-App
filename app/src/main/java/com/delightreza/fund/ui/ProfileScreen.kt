package com.delightreza.fund.ui
import kotlinx.coroutines.launch

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.delightreza.fund.data.AppDataStore
import com.delightreza.fund.data.AppConfig
import com.delightreza.fund.data.FundData
import com.delightreza.fund.data.Repository
import com.delightreza.fund.data.Transaction
import com.delightreza.fund.utils.DateUtils
import com.delightreza.fund.utils.FormatUtils
import kotlin.math.abs
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    repository: Repository,
    dataStore: AppDataStore,
    currentUser: String,
    navController: NavController,
    onLogout: () -> Unit,
    onSwitchRepo: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    val savedToken by dataStore.tokenFlow.collectAsState(initial = "")
    val darkModeMode by dataStore.darkModeFlow.collectAsState(initial = "system")
    
    LaunchedEffect(savedToken) {
        tokenInput = savedToken ?: ""
    }
    var data by remember { mutableStateOf<FundData?>(null) }
    var config by remember { mutableStateOf<AppConfig?>(null) }
    var balances by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    
    val listState = rememberLazyListState()
    var savedScrollIndex by rememberSaveable { mutableStateOf(0) }
    var savedScrollOffset by rememberSaveable { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            savedScrollIndex = listState.firstVisibleItemIndex
            savedScrollOffset = listState.firstVisibleItemScrollOffset
        }
    }

    LaunchedEffect(Unit) {
        // Load cached data first
        config = repository.getAppConfig()
        val cached = repository.getCachedData()
        if (cached != null) { data = cached; balances = repository.calculateBalances(cached) }
        
        // Then fetch remote data
        val remoteConfig = repository.fetchRemoteConfig()
        if (remoteConfig != null) {
            config = remoteConfig
        }
        val result = repository.fetchData()
        if (result != null) { 
            data = result
            balances = repository.calculateBalances(result) 
        }
    }

    var hasRestoredScroll by remember { mutableStateOf(false) }
    LaunchedEffect(data) {
        if (data != null && !hasRestoredScroll && (savedScrollIndex > 0 || savedScrollOffset > 0)) {
            listState.scrollToItem(savedScrollIndex, savedScrollOffset)
            hasRestoredScroll = true
        }
    }

    val ptrState = rememberPullToRefreshState()
    if (ptrState.isRefreshing) {
        LaunchedEffect(true) {
            val remoteConfig = repository.fetchRemoteConfig()
            if (remoteConfig != null) config = remoteConfig
            val result = repository.fetchData()
            if (result != null) { 
                data = result
                balances = repository.calculateBalances(result) 
            }
            ptrState.endRefresh()
        }
    }

    val currentUserName = config?.members?.find { it.id == currentUser }?.name ?: currentUser

    Box(modifier = modifier.fillMaxSize().nestedScroll(ptrState.nestedScrollConnection)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            val netBalance = balances[currentUser] ?: 0.0
            val given = data?.transactions?.filter { it.type == "credit" && (it.payerId == currentUser || it.whoOrBill == currentUser) }?.sumOf { it.amount } ?: 0.0
            val spent = given - netBalance
            
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF312E81))
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .pointerInput(Unit) {
                                    detectTapGestures(onLongPress = { showTokenDialog = true })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentUserName.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = currentUserName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Given", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                                Text(text = FormatUtils.formatAmount(given), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                            VerticalDivider(color = Color.White.copy(alpha = 0.2f))
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Spent", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                                Text(text = FormatUtils.formatAmount(spent), style = MaterialTheme.typography.titleLarge, color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                            VerticalDivider(color = Color.White.copy(alpha = 0.2f))
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Net", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                                Text(text = "${if(netBalance > 0) "+" else ""}${FormatUtils.formatAmount(netBalance)}", style = MaterialTheme.typography.titleLarge, color = if (netBalance >= 0) Color(0xFF86EFAC) else Color(0xFFFDA4AF), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onLogout() }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp)
                ) { Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Switch User", textAlign = TextAlign.Center, fontSize = 12.sp) }
                
                OutlinedButton(onClick = { onSwitchRepo() }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp)
                ) { Icon(Icons.Default.Sync, null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Switch Repo", textAlign = TextAlign.Center, fontSize = 12.sp) }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)); Text("Your Recent Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

        if (data != null) {
            val myTransactions = data!!.transactions.filter { tx ->
                if (tx.type == "credit") tx.payerId == currentUser || tx.whoOrBill == currentUser
                else if (tx.splitAmong != null) tx.splitAmong.contains(currentUser)
                else !(tx.exemptions ?: emptyList()).contains(currentUser)
            }
            if (myTransactions.isEmpty()) {
                item { Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("No recent activity.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) } }
            } else {
                items(myTransactions.take(30)) { tx ->
                    val myShare = if (tx.type == "debit") {
                        if (!tx.splitAmong.isNullOrEmpty()) tx.amount / tx.splitAmong.size
                        else { val ex = tx.exemptions?.size ?: 0; val total = config?.members?.size ?: 1; tx.amount / (total - ex).coerceAtLeast(1) }
                    } else tx.amount
                    ProfileTransactionRow(tx, myShare, config) { navController.navigate("detail/${tx.parentId ?: tx.id}?forUser=$currentUser") }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
    PullToRefreshContainer(state = ptrState, modifier = Modifier.align(Alignment.TopCenter))
    }

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("GitHub Token") },
            text = {
                Column {
                    Text("Enter your GitHub token for admin access.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        dataStore.saveToken(tokenInput.trim())
                        showTokenDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ProfileTransactionRow(tx: Transaction, myShare: Double, config: AppConfig?, onClick: () -> Unit) {
    val localDate = DateUtils.formatToLocalDateOnly(tx.date)
    val isPositiveEffect = if (tx.type == "debit") false else myShare > 0
    val color = if (isPositiveEffect) Color(0xFF059669) else Color(0xFFDC2626)
    val icon = if (isPositiveEffect) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
    val title = when {
        tx.type == "credit" -> if (myShare < 0) "Transfer / Settle" else "Deposit / Received"
        tx.type == "debit" -> {
            val bid = tx.billTypeId ?: tx.whoOrBill
            val billName = config?.billTypes?.find { it.id == bid }?.name ?: bid
            if (billName.equals("Other", ignoreCase = true) && tx.note.isNotEmpty()) tx.note else billName
        }
        else -> tx.whoOrBill
    }
    val subtitle = if (tx.note.isNotEmpty() && tx.note != title) "${tx.note} • $localDate" else localDate

    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                val sign = if (isPositiveEffect) "+" else "-"
                Text(text = "$sign${FormatUtils.formatAmount(abs(myShare))}", color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                if (tx.type == "debit") Text(text = "My Share", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
        }
    }
}
