package com.delightreza.fund.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.delightreza.fund.data.BillTypeConfig
import com.delightreza.fund.data.MemberConfig
import com.delightreza.fund.data.Repository
import com.delightreza.fund.data.Transaction
import com.delightreza.fund.utils.DateUtils
import com.delightreza.fund.utils.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    navController: NavController, 
    repository: Repository, 
    token: String,
    transactionIdToEdit: String? = null,
    defaultType: String? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var isSubmitting by remember { mutableStateOf(false) }
    var isLoadingData by remember { mutableStateOf(true) }
    var showConfirmation by remember { mutableStateOf(false) }
    
    var type by rememberSaveable { mutableStateOf(defaultType ?: "debit") }
    var amount by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf("") } 
    var fromId by rememberSaveable { mutableStateOf("") }
    var toId by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var excludedIds by remember { mutableStateOf(setOf<String>()) }
    var selectedDateTime by remember { mutableStateOf<Calendar?>(null) }
    
    var activeMembers by remember { mutableStateOf(listOf<MemberConfig>()) }
    var allMembers by remember { mutableStateOf(listOf<MemberConfig>()) }
    var activeBillTypes by remember { mutableStateOf(listOf<BillTypeConfig>()) }
    var currency by remember { mutableStateOf("₹") }
    
    var originalId by remember { mutableStateOf("") }
    var originalDate by remember { mutableStateOf("") }
    var originalParentId by remember { mutableStateOf<String?>(null) }

    val typeRowState = rememberLazyListState()
    val creditMembersState = rememberLazyListState()
    val debitBillTypesState = rememberLazyListState()
    val expenseFromMembersState = rememberLazyListState()
    val expenseToBillTypesState = rememberLazyListState()
    val settleFromMembersState = rememberLazyListState()
    val settleToMembersState = rememberLazyListState()

    LaunchedEffect(type, isLoadingData) {
        if (!isLoadingData) {
            delay(150)
            val types = listOf("expense", "debit", "credit", "distribute", "settlement", "transfer")
            val idx = types.indexOf(type)
            if (idx >= 0) typeRowState.animateScrollToItem(idx)
        }
    }

    LaunchedEffect(selectedId, type, isLoadingData, allMembers) {
        if (!isLoadingData && type == "credit" && selectedId.isNotEmpty()) {
            delay(150)
            val idx = allMembers.indexOfFirst { it.id.equals(selectedId, ignoreCase = true) || it.name.equals(selectedId, ignoreCase = true) }
            if (idx >= 0) creditMembersState.animateScrollToItem(idx)
        }
    }

    LaunchedEffect(selectedId, type, isLoadingData, activeBillTypes) {
        if (!isLoadingData && type == "debit" && selectedId.isNotEmpty()) {
            delay(150)
            val idx = activeBillTypes.indexOfFirst { it.id.equals(selectedId, ignoreCase = true) || it.name.equals(selectedId, ignoreCase = true) }
            if (idx >= 0) debitBillTypesState.animateScrollToItem(idx)
        }
    }

    LaunchedEffect(fromId, toId, type, isLoadingData, allMembers, activeBillTypes) {
        if (!isLoadingData && type == "expense") {
            delay(150)
            if (fromId.isNotEmpty()) {
                val fromIdx = allMembers.indexOfFirst { it.id.equals(fromId, ignoreCase = true) || it.name.equals(fromId, ignoreCase = true) }
                if (fromIdx >= 0) expenseFromMembersState.animateScrollToItem(fromIdx)
            }
            if (toId.isNotEmpty()) {
                val toIdx = activeBillTypes.indexOfFirst { it.id.equals(toId, ignoreCase = true) || it.name.equals(toId, ignoreCase = true) }
                if (toIdx >= 0) expenseToBillTypesState.animateScrollToItem(toIdx)
            }
        }
    }

    LaunchedEffect(fromId, toId, type, isLoadingData, allMembers) {
        if (!isLoadingData && (type == "settlement" || type == "transfer")) {
            delay(150)
            if (fromId.isNotEmpty()) {
                val fromIdx = allMembers.indexOfFirst { it.id.equals(fromId, ignoreCase = true) || it.name.equals(fromId, ignoreCase = true) }
                if (fromIdx >= 0) settleFromMembersState.animateScrollToItem(fromIdx)
            }
            if (toId.isNotEmpty()) {
                val toIdx = allMembers.indexOfFirst { it.id.equals(toId, ignoreCase = true) || it.name.equals(toId, ignoreCase = true) }
                if (toIdx >= 0) settleToMembersState.animateScrollToItem(toIdx)
            }
        }
    }

    LaunchedEffect(Unit) {
        val config = repository.getAppConfig()
        if (config != null) {
            allMembers = config.members.sortedWith(compareBy({ !it.active }, { it.name }))
            activeMembers = config.members.filter { it.active }
            activeBillTypes = config.billTypes
            currency = config.currency
        }
        if (transactionIdToEdit != null) {
            val data = repository.getCachedData()
            val tx = data?.transactions?.find { it.id == transactionIdToEdit }
            if (tx != null) {
                originalId = tx.id; originalDate = tx.date; type = tx.type; originalParentId = tx.parentId
                amount = if(tx.amount % 1.0 == 0.0) tx.amount.toInt().toString() else tx.amount.toString()
                selectedId = if (type == "credit") tx.payerId ?: tx.whoOrBill else tx.billTypeId ?: tx.whoOrBill
                
                val isExpenseGroup = tx.parentId?.startsWith("tx_exp") == true
                if (isExpenseGroup) {
                    type = "expense"
                    val linkedTx = data.transactions.find { it.parentId == tx.parentId && it.id != tx.id }
                    val creditTx = if (tx.type == "credit") tx else linkedTx
                    val debitTx = if (tx.type == "debit") tx else linkedTx
                    
                    if (creditTx != null) fromId = creditTx.payerId ?: creditTx.whoOrBill
                    if (debitTx != null) {
                        toId = debitTx.billTypeId ?: debitTx.whoOrBill
                        if (debitTx.splitAmong != null && config != null) {
                            excludedIds = config.members.map { it.id }.filter { !debitTx.splitAmong.contains(it) }.toSet()
                        }
                    }
                } else {
                    if (type == "debit" && tx.splitAmong != null && config != null) {
                        excludedIds = config.members.map { it.id }.filter { !tx.splitAmong.contains(it) }.toSet()
                    }
                }

                val cleanNote = when {
                    tx.note.contains(" paid for ") -> ""
                    tx.note.contains(" is paid by ") -> ""
                    else -> tx.note
                }
                note = cleanNote
            }
        }
        isLoadingData = false
    }

    val timePickerDialog = TimePickerDialog(context, { _, h, m -> 
        selectedDateTime?.let { val n = it.clone() as Calendar; n.set(Calendar.HOUR_OF_DAY, h); n.set(Calendar.MINUTE, m); selectedDateTime = n } 
    }, Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE), true)
    
    val datePickerDialog = DatePickerDialog(context, { _, y, m, d -> 
        val n = Calendar.getInstance(); n.set(y, m, d); selectedDateTime = n; timePickerDialog.show() 
    }, Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH))

    val dateDisplay = remember(selectedDateTime, originalDate) {
        if (selectedDateTime != null) "Selected: ${selectedDateTime!!.time}"
        else if (transactionIdToEdit != null) "Original Date"
        else "Today (Now)"
    }

    val themeColor = when(type) {
        "credit" -> Color(0xFF059669)
        "expense" -> Color(0xFF4F46E5)
        "distribute" -> Color(0xFF8B5CF6)
        "settlement" -> Color(0xFF059669)
        "transfer" -> Color(0xFF2563EB)
        else -> Color(0xFFE11D48)
    }

    val handleSave = {
        isSubmitting = true
        scope.launch {
            val finalDate = if (selectedDateTime != null) DateUtils.getStringFromLocal(selectedDateTime!!)
            else if (transactionIdToEdit != null) originalDate else DateUtils.getCurrentTime()

            var success = false
            try {
                when (type) {
                    "expense" -> {
                        if (transactionIdToEdit != null && originalParentId != null) {
                            success = repository.editQuickExpense(token, originalParentId!!, fromId, toId, amount.toDouble(), note, finalDate, excludedIds.toList())
                        } else {
                            success = repository.addQuickExpense(token, fromId, toId, amount.toDouble(), note, finalDate, excludedIds.toList())
                        }
                    }
                    "distribute" -> {
                        val participants = activeMembers.map { it.id }.filter { !excludedIds.contains(it) }
                        success = repository.addDistribution(token, amount.toDouble(), note, finalDate, participants)
                    }
                    "settlement" -> success = repository.addSettlement(token, fromId, toId, amount.toDouble(), note, finalDate)
                    "transfer" -> success = repository.addTransfer(token, fromId, toId, amount.toDouble(), note, finalDate)
                    else -> {
                        var splitAmong: List<String>? = null
                        if (type == "debit") splitAmong = activeMembers.map { it.id }.filter { !excludedIds.contains(it) }
                        val tx = Transaction(
                            id = if (transactionIdToEdit != null) originalId else DateUtils.generateTransactionId(),
                            type = type, payerId = if (type == "credit") selectedId else null,
                            billTypeId = if (type == "debit") selectedId else null, splitAmong = splitAmong,
                            whoOrBill = selectedId, note = note, amount = amount.toDouble(), date = finalDate,
                            parentId = if (transactionIdToEdit != null) originalParentId else null
                        )
                        success = if (transactionIdToEdit != null) repository.editTransaction(token, tx) else repository.addTransaction(token, tx)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            if (success) navController.popBackStack() else isSubmitting = false
        }
    }

    val pageTitle = if (transactionIdToEdit != null) {
        when(type) {
            "expense" -> "Edit Quick Expense"
            "credit" -> "Edit Credit"
            "debit" -> "Edit Debit"
            else -> "Edit Transaction"
        }
    } else {
        when(type) {
            "expense" -> "New Quick Expense"
            "credit" -> "New Credit"
            "debit" -> "New Debit"
            else -> "New Transaction"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { p ->
        if (isLoadingData || isSubmitting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = themeColor)
            }
        } else {
            if (showConfirmation) {
                AlertDialog(
                    onDismissRequest = { showConfirmation = false },
                    title = { Text("Confirm Transaction") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val cleanAmount = amount.toDoubleOrNull()?.let { if (it % 1.0 == 0.0) it.toInt().toString() else amount } ?: amount
                            Text(text = "$cleanAmount $currency", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = themeColor)
                            Text(text = "Type: ${type.uppercase()}", style = MaterialTheme.typography.titleMedium)
                            if (note.isNotEmpty()) Text(text = "Note: $note", style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showConfirmation = false; handleSave() },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Confirm & Submit") }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showConfirmation = false },
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Cancel") }
                    }
                )
            }

            Column(
                modifier = Modifier
                    .padding(p)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Type Selector Chips
                LazyRow(
                    state = typeRowState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        val col = Color(0xFF4F46E5)
                        FilterChip(
                            selected = type == "expense",
                            onClick = { type = "expense"; fromId = ""; toId = ""; excludedIds = emptySet() },
                            label = { Text("Quick Expense", fontWeight = if (type == "expense") FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (type == "expense") { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = col.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    item {
                        val col = Color(0xFFE11D48)
                        FilterChip(
                            selected = type == "debit",
                            onClick = { type = "debit"; selectedId = ""; excludedIds = emptySet() },
                            label = { Text("Debit", fontWeight = if (type == "debit") FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (type == "debit") { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = col.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    item {
                        val col = Color(0xFF059669)
                        FilterChip(
                            selected = type == "credit",
                            onClick = { type = "credit"; selectedId = "" },
                            label = { Text("Credit", fontWeight = if (type == "credit") FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (type == "credit") { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = col.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    if (transactionIdToEdit == null) {
                        item {
                            val col = Color(0xFF8B5CF6)
                            FilterChip(
                                selected = type == "distribute",
                                onClick = { type = "distribute"; selectedId = "All"; excludedIds = emptySet() },
                                label = { Text("Distribute", fontWeight = if (type == "distribute") FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (type == "distribute") { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = col.copy(alpha = 0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        item {
                            val col = Color(0xFF059669)
                            FilterChip(
                                selected = type == "settlement",
                                onClick = { type = "settlement"; fromId = ""; toId = "" },
                                label = { Text("Settlement", fontWeight = if (type == "settlement") FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (type == "settlement") { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = col.copy(alpha = 0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        item {
                            val col = Color(0xFF2563EB)
                            FilterChip(
                                selected = type == "transfer",
                                onClick = { type = "transfer"; fromId = ""; toId = "" },
                                label = { Text("Transfer", fontWeight = if (type == "transfer") FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (type == "transfer") { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = col.copy(alpha = 0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }

                // Main Details Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Amount Hero Input
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text("Amount ($currency)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themeColor, focusedLabelColor = themeColor)
                        )

                        // Subject Selection
                        if (type == "debit" || type == "credit") {
                            Text(
                                if (type == "credit") "Payer / Member" else "Bill Category",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (type == "credit") {
                                LazyRow(state = creditMembersState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(allMembers.size) { i ->
                                        val m = allMembers[i]
                                        val isSelected = selectedId.equals(m.id, ignoreCase = true) || selectedId.equals(m.name, ignoreCase = true)
                                        InputChip(
                                            selected = isSelected,
                                            onClick = { selectedId = m.id },
                                            label = { Text(m.name + if (!m.active) " (Inactive)" else "", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                            leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                            colors = InputChipDefaults.inputChipColors(selectedContainerColor = themeColor, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White)
                                        )
                                    }
                                }
                            } else {
                                LazyRow(state = debitBillTypesState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(activeBillTypes.size) { i ->
                                        val b = activeBillTypes[i]
                                        val isSelected = selectedId.equals(b.id, ignoreCase = true) || selectedId.equals(b.name, ignoreCase = true)
                                        InputChip(
                                            selected = isSelected,
                                            onClick = { selectedId = b.id },
                                            label = { Text("${b.icon} ${b.name}", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                            leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                            colors = InputChipDefaults.inputChipColors(selectedContainerColor = themeColor, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White)
                                        )
                                    }
                                }
                            }
                        }

                        if (type == "expense") {
                            Text("Paid By", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyRow(state = expenseFromMembersState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(allMembers.size) { i ->
                                    val m = allMembers[i]
                                    val isSelected = fromId.equals(m.id, ignoreCase = true) || fromId.equals(m.name, ignoreCase = true)
                                    InputChip(
                                        selected = isSelected,
                                        onClick = { fromId = m.id },
                                        label = { Text(m.name + if (!m.active) " (Inactive)" else "", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                        colors = InputChipDefaults.inputChipColors(selectedContainerColor = themeColor, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White)
                                    )
                                }
                            }

                            Text("For Bill Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyRow(state = expenseToBillTypesState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(activeBillTypes.size) { i ->
                                    val b = activeBillTypes[i]
                                    val isSelected = toId.equals(b.id, ignoreCase = true) || toId.equals(b.name, ignoreCase = true)
                                    InputChip(
                                        selected = isSelected,
                                        onClick = { toId = b.id },
                                        label = { Text("${b.icon} ${b.name}", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                        colors = InputChipDefaults.inputChipColors(selectedContainerColor = themeColor, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White)
                                    )
                                }
                            }
                        }

                        if (type == "settlement" || type == "transfer") {
                            Text("From / Sender", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyRow(state = settleFromMembersState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(allMembers.size) { i ->
                                    val m = allMembers[i]
                                    val isSelected = fromId.equals(m.id, ignoreCase = true) || fromId.equals(m.name, ignoreCase = true)
                                    InputChip(
                                        selected = isSelected,
                                        onClick = { fromId = m.id },
                                        label = { Text(m.name + if (!m.active) " (Inactive)" else "", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                        colors = InputChipDefaults.inputChipColors(selectedContainerColor = themeColor, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White)
                                    )
                                }
                            }

                            Text("To / Receiver", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyRow(state = settleToMembersState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(allMembers.size) { i ->
                                    val m = allMembers[i]
                                    val isSelected = toId.equals(m.id, ignoreCase = true) || toId.equals(m.name, ignoreCase = true)
                                    InputChip(
                                        selected = isSelected,
                                        onClick = { toId = m.id },
                                        label = { Text(m.name + if (!m.active) " (Inactive)" else "", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                        colors = InputChipDefaults.inputChipColors(selectedContainerColor = themeColor, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Note / Description") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themeColor, focusedLabelColor = themeColor)
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = dateDisplay,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Transaction Date & Time") },
                                trailingIcon = { Icon(Icons.Default.CalendarToday, "") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline)
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { datePickerDialog.show() })
                        }
                    }
                }

                // Member Split Exclusion Card
                if (type == "debit" || type == "expense" || type == "distribute") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                if (type == "distribute") "Split Exclusion List" else "Member Split Exclusions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Check members who should be EXCLUDED from this transaction split:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            activeMembers.chunked(2).forEach { row ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    row.forEach { person ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f).clickable {
                                                excludedIds = if (excludedIds.contains(person.id)) excludedIds - person.id else excludedIds + person.id
                                            }
                                        ) {
                                            Checkbox(
                                                checked = excludedIds.contains(person.id),
                                                onCheckedChange = null,
                                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error)
                                            )
                                            Text(person.name, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }

                            val includedCount = activeMembers.size - excludedIds.size
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            if (includedCount > 0 && amt > 0) {
                                val share = amt / includedCount
                                Surface(
                                    color = themeColor.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Per-person share ($includedCount people):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text("${FormatUtils.formatAmount(share)} $currency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = themeColor)
                                    }
                                }
                            }
                        }
                    }
                }

                val isValid = when(type) {
                    "expense" -> amount.isNotEmpty() && fromId.isNotEmpty() && toId.isNotEmpty()
                    "debit", "credit" -> amount.isNotEmpty() && selectedId.isNotEmpty()
                    "distribute" -> amount.isNotEmpty() && excludedIds.size < activeMembers.size
                    "settlement", "transfer" -> amount.isNotEmpty() && fromId.isNotEmpty() && toId.isNotEmpty() && fromId != toId
                    else -> false
                }

                Button(
                    onClick = { showConfirmation = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = isValid,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) {
                    Text(if(transactionIdToEdit != null) "Update Transaction" else "Save Transaction", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

