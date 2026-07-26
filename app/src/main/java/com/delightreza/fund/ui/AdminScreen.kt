package com.delightreza.fund.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.delightreza.fund.data.AppDataStore
import com.delightreza.fund.data.Repository
import com.delightreza.fund.data.Settlement
import com.delightreza.fund.utils.FormatUtils

@Composable
fun AdminScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    repository: Repository,
    dataStore: AppDataStore
) {
    var settlements by remember { mutableStateOf<List<Settlement>>(emptyList()) }
    var memberMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var currency by remember { mutableStateOf("₹") }

    LaunchedEffect(Unit) {
        val config = repository.fetchRemoteConfig() ?: repository.getAppConfig()
        if (config != null) {
            currency = config.currency
            memberMap = config.members.associate { it.id to it.name }
        }
        val cached = repository.getCachedData()
        if (cached != null) {
            val balances = repository.calculateBalances(cached)
            settlements = repository.calculateDebtSettlements(balances)
        }
        val fresh = repository.fetchData()
        val updatedConfig = repository.getAppConfig()
        if (updatedConfig != null) {
            currency = updatedConfig.currency
            memberMap = updatedConfig.members.associate { it.id to it.name }
        }
        if (fresh != null) {
            val balances = repository.calculateBalances(fresh)
            settlements = repository.calculateDebtSettlements(balances)
        }
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                "Transaction Entry",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AdminActionCard(
                    title = "Quick Expense", icon = Icons.Default.Bolt, color = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f), onClick = { navController.navigate("add_transaction?type=expense") }
                )
                AdminActionCard(
                    title = "Add Credit", icon = Icons.Default.ArrowDownward, color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f), onClick = { navController.navigate("add_transaction?type=credit") }
                )
                AdminActionCard(
                    title = "Add Debit", icon = Icons.Default.ArrowUpward, color = Color(0xFFEF4444),
                    modifier = Modifier.weight(1f), onClick = { navController.navigate("add_transaction?type=debit") }
                )
            }
        }

        item {
            Text(
                "Advanced Tools",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AdminActionCard(
                    title = "Distribute", icon = Icons.Default.Group, color = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f), onClick = { navController.navigate("add_transaction?type=distribute") }
                )
                AdminActionCard(
                    title = "Settle Debt", icon = Icons.Default.Handshake, color = Color(0xFF059669),
                    modifier = Modifier.weight(1f), onClick = { navController.navigate("add_transaction?type=settlement") }
                )
                AdminActionCard(
                    title = "Transfer", icon = Icons.Default.SwapHoriz, color = Color(0xFF2563EB),
                    modifier = Modifier.weight(1f), onClick = { navController.navigate("add_transaction?type=transfer") }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AdminActionCard(
                    title = "Reset Commit", icon = Icons.Default.Restore, color = Color(0xFFEF4444),
                    modifier = Modifier.weight(1f), onClick = { navController.navigate("reset_commit") }
                )
                Spacer(modifier = Modifier.weight(2f))
            }
        }

        if (settlements.isNotEmpty()) {
            item {
                Text(
                    "Debt Simplifier",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        settlements.forEachIndexed { index, s ->
                            val fromName = memberMap[s.from] ?: s.from
                            val toName = memberMap[s.to] ?: s.to
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(fromName, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(toName, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text("${FormatUtils.formatAmount(s.amount)} $currency", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            if (index < settlements.lastIndex) {
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("settings") }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Manage Config", fontWeight = FontWeight.Bold)
                        Text("Add or disable People & Bill Types", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminActionCard(title: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 14.sp)
        }
    }
}
