package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.data.Member
import com.example.data.Period
import com.example.data.Transaction
import com.example.util.ReportExporter
import com.example.util.Translations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val currentLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val isAdminMode by viewModel.isAdminMode.collectAsStateWithLifecycle()

    val periods by viewModel.periods.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val transactions by viewModel.currentTransactions.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactionsList.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val selectedPeriodId by viewModel.selectedPeriodId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val selectedPeriod = periods.find { it.id == selectedPeriodId }

    // Navigation and dialogs states
    var currentTab by remember { mutableStateOf("Dashboard") }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showAddPeriodDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Helper functions for localization
    fun t(key: String): String = Translations.get(key, currentLanguage)
    fun formatIDR(amount: Double): String = ReportExporter.formatIDR(amount)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left action: Header Title & Active Period
                Column(
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = t("app_title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    selectedPeriod?.let {
                        Text(
                            text = "${if (currentLanguage == "en") "Period" else "Periode"}: ${it.name}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Right actions: Mode toggles & Admin indicator lock next to it
                IconButton(onClick = { viewModel.toggleDarkMode() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Toggle Dark Mode",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isAdminMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                if (isAdminMode) {
                                    viewModel.logoutAdmin()
                                    Toast
                                        .makeText(context, t("toast_logged_out"), Toast.LENGTH_SHORT)
                                        .show()
                                } else {
                                    showLoginDialog = true
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock indicator",
                            modifier = Modifier.size(14.dp),
                            tint = if (isAdminMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAdminMode) t("admin") else t("visitor"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAdminMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                // Dasbor Tab
                NavigationBarItem(
                    selected = currentTab == "Dashboard",
                    onClick = { currentTab = "Dashboard" },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text(t("summary"), fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("tab_dashboard")
                )
                // Lembar Kerja Tab (Spreadsheet)
                NavigationBarItem(
                    selected = currentTab == "Spreadsheet",
                    onClick = { currentTab = "Spreadsheet" },
                    icon = { Icon(Icons.Default.List, contentDescription = "Spreadsheet") },
                    label = { Text(if (currentLanguage == "en") "Sheet" else "Lembar K.", fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("tab_spreadsheet")
                )
                // Anggota Tab (Members)
                NavigationBarItem(
                    selected = currentTab == "Members",
                    onClick = { currentTab = "Members" },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Members") },
                    label = { Text(if (currentLanguage == "en") "Members" else "Anggota", fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("tab_members")
                )
                // Pengeluaran Tab (Expenses)
                NavigationBarItem(
                    selected = currentTab == "Expenses",
                    onClick = { currentTab = "Expenses" },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Expenses") },
                    label = { Text(if (currentLanguage == "en") "Expenses" else "Pengeluaran", fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("tab_expenses")
                )
                // Pengaturan Tab (Settings & Language)
                NavigationBarItem(
                    selected = currentTab == "Settings",
                    onClick = { currentTab = "Settings" },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text(if (currentLanguage == "en") "Settings" else "Pengaturan", fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentTab) {
                "Dashboard" -> DashboardTab(
                    viewModel = viewModel,
                    metrics = metrics,
                    transactions = transactions,
                    onAddTransaction = {
                        if (isAdminMode) {
                            if (selectedPeriodId != null) {
                                showAddTransactionDialog = true
                            } else {
                                Toast.makeText(context, t("no_active_period"), Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, t("admin_mode_locked"), Toast.LENGTH_LONG).show()
                        }
                    },
                    onShareText = {
                        showExportDialog = true
                    }
                )
                "Spreadsheet" -> SpreadsheetTab(
                    viewModel = viewModel,
                    periods = periods,
                    selectedPeriodId = selectedPeriodId,
                    transactions = transactions,
                    isAdminMode = isAdminMode,
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.searchQuery.value = it },
                    onPeriodSelect = { viewModel.selectPeriod(it) },
                    onAddPeriodTrigger = {
                        if (isAdminMode) showAddPeriodDialog = true
                        else Toast.makeText(context, t("admin_mode_locked"), Toast.LENGTH_LONG).show()
                    },
                    onDeletePeriodTrigger = { viewModel.deletePeriod(it) },
                    onAddTransactionTrigger = {
                        if (isAdminMode) {
                            if (selectedPeriodId != null) showAddTransactionDialog = true
                            else Toast.makeText(context, t("no_active_period"), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, t("admin_mode_locked"), Toast.LENGTH_LONG).show()
                        }
                    },
                    onDeleteTransactionTrigger = { viewModel.deleteTransaction(it) }
                )
                "Members" -> MembersTab(
                    viewModel = viewModel,
                    members = members,
                    transactions = transactions,
                    isAdminMode = isAdminMode,
                    onAddMember = {
                        if (isAdminMode) showAddMemberDialog = true
                        else Toast.makeText(context, t("admin_mode_locked"), Toast.LENGTH_LONG).show()
                    },
                    onDeleteMember = { viewModel.deleteMember(it) }
                )
                "Expenses" -> ExpensesTab(
                    viewModel = viewModel,
                    transactions = transactions,
                    isAdminMode = isAdminMode,
                    onDeleteTransaction = { viewModel.deleteTransaction(it) }
                )
                "Settings" -> SettingsTab(
                    viewModel = viewModel,
                    periods = periods,
                    membersCount = members.size,
                    currentLanguage = currentLanguage,
                    isAdminMode = isAdminMode,
                    onResetData = { viewModel.clearAllData() }
                )
            }
        }
    }

    // Dialogs initialization setups

    // 1. Log-in Admin dialog
    if (showLoginDialog) {
        var inputPasscode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text(t("enter_admin_password")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputPasscode,
                        onValueChange = { inputPasscode = it },
                        label = { Text(t("password")) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().testTag("admin_password_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = t("default_password_hint"), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (viewModel.loginAdmin(inputPasscode)) {
                            showLoginDialog = false
                            Toast.makeText(context, t("toast_logged_in"), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, t("toast_wrong_password"), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("admin_login_confirm")
                ) {
                    Text(t("save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) {
                    Text(t("cancel"))
                }
            }
        )
    }

    // 2. Add custom Period dialog
    if (showAddPeriodDialog) {
        var inputName by remember { mutableStateOf("") }
        var inputStartBal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPeriodDialog = false },
            title = { Text(t("add_period")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text(t("period_name")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inputStartBal,
                        onValueChange = { inputStartBal = it },
                        label = { Text(t("starting_balance_label")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (inputName.isNotBlank()) {
                         val dBal = inputStartBal.toDoubleOrNull() ?: 0.0
                         viewModel.addPeriod(inputName, dBal)
                         showAddPeriodDialog = false
                    }
                }) {
                    Text(t("add"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPeriodDialog = false }) {
                    Text(t("cancel"))
                }
            }
        )
    }

    // 3. Add organization Member dialog
    if (showAddMemberDialog) {
        var mName by remember { mutableStateOf("") }
        var mNote by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text(t("add_member")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = mName,
                        onValueChange = { mName = it },
                        label = { Text(t("member_name")) },
                        modifier = Modifier.fillMaxWidth().testTag("member_name_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = mNote,
                        onValueChange = { mNote = it },
                        label = { Text(t("notes")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (mName.isNotBlank()) {
                         viewModel.addMember(mName, mNote)
                         showAddMemberDialog = false
                    }
                }, modifier = Modifier.testTag("member_add_confirm")) {
                    Text(t("add"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) {
                    Text(t("cancel"))
                }
            }
        )
    }

    // 4. Add Transaction Entry dialog
    if (showAddTransactionDialog) {
        var trType by remember { mutableStateOf("INCOME") } // "INCOME" or "EXPENSE"
        var trCat by remember { mutableStateOf("Iuran Bulanan") }
        var trAmt by remember { mutableStateOf("") }
        var trDesc by remember { mutableStateOf("") }
        var selectedMember by remember { mutableStateOf<Member?>(null) }
        var expandedMemberDropdown by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddTransactionDialog = false },
            title = { Text(t("add_item")) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        // Type toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { 
                                    trType = "INCOME"
                                    trCat = "Iuran Bulanan"
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (trType == "INCOME") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (trType == "INCOME") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(t("contributions"))
                            }
                            Button(
                                onClick = { 
                                    trType = "EXPENSE"
                                    trCat = "Operasional"
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (trType == "EXPENSE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (trType == "EXPENSE") MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(t("expenses"))
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // Amount
                        OutlinedTextField(
                            value = trAmt,
                            onValueChange = { trAmt = it },
                            label = { Text(t("amount")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("transaction_amount_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Category
                        OutlinedTextField(
                            value = trCat,
                            onValueChange = { trCat = it },
                            label = { Text(t("category")) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Description
                        OutlinedTextField(
                            value = trDesc,
                            onValueChange = { trDesc = it },
                            label = { Text(t("description")) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Member link selection (mostly for Income/Contributions)
                        if (trType == "INCOME" && members.isNotEmpty()) {
                            Text(t("select_member"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { expandedMemberDropdown = true }
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = selectedMember?.name ?: t("not_linked"),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }

                            if (expandedMemberDropdown) {
                                Dialog(onDismissRequest = { expandedMemberDropdown = false }) {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 300.dp)
                                    ) {
                                        LazyColumn(modifier = Modifier.padding(12.dp)) {
                                            item {
                                                Text(
                                                    text = t("select_member"),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                                Divider()
                                            }
                                            item {
                                                Text(
                                                    text = t("not_linked"),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            selectedMember = null
                                                            expandedMemberDropdown = false
                                                        }
                                                        .padding(12.dp)
                                                )
                                            }
                                            items(members) { m ->
                                                Text(
                                                    text = m.name,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            selectedMember = m
                                                            expandedMemberDropdown = false
                                                        }
                                                        .padding(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dAmt = trAmt.toDoubleOrNull() ?: 0.0
                        if (dAmt > 0.0 && trDesc.isNotBlank()) {
                            viewModel.addTransaction(
                                type = trType,
                                category = trCat,
                                amount = dAmt,
                                description = trDesc,
                                memberId = selectedMember?.id,
                                memberName = selectedMember?.name
                            )
                            showAddTransactionDialog = false
                        }
                    },
                    modifier = Modifier.testTag("transaction_add_confirm")
                ) {
                    Text(t("add"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTransactionDialog = false }) {
                    Text(t("cancel"))
                }
            }
        )
    }

    // 5. Sharing financial snap info card
    if (showExportDialog) {
        val shareTextReport = ReportExporter.makeTextReport(selectedPeriod, transactions, metrics.totalIncome, metrics.totalExpenses)
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(t("export_report")) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    item {
                        Text(text = shareTextReport, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        // Share via system share intent
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, shareTextReport)
                            type = "text/plain"
                        }
                        val shareIntent = android.content.Intent.createChooser(sendIntent, t("share_via"))
                        context.startActivity(shareIntent)
                        showExportDialog = false
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(t("share"))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(t("cancel"))
                }
            }
        )
    }
}

// Sub-Tab Layout: 1. Dashboard Tab
@Composable
fun DashboardTab(
    viewModel: FinanceViewModel,
    metrics: FinanceMetrics,
    transactions: List<Transaction>,
    onAddTransaction: () -> Unit,
    onShareText: () -> Unit
) {
    val context = LocalContext.current
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    fun t(key: String): String = Translations.get(key, language)
    fun formatIDR(amount: Double): String = ReportExporter.formatIDR(amount)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Action additions
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = t("current_cash_balance"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatIDR(metrics.currentBalance),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("remaining_balance"),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = t("starting_pool"),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = formatIDR(metrics.startingBalance),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = t("total_income"),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = formatIDR(metrics.totalIncome),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF047857),
                                textAlign = TextAlign.Center
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = t("total_expenses"),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = formatIDR(metrics.totalExpenses),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSystemInDarkTheme()) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Custom drawn interactive charts trend
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == "en") "Cash Flow Statistics" else "Tren Pemasukan & Pengeluaran",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Trend graph icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Draw a beautiful interactive Canvas comparative trend chart
                    val incoming = metrics.totalIncome.toFloat()
                    val outgoing = metrics.totalExpenses.toFloat()
                    val totalFlow = incoming + outgoing

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (totalFlow == 0f) {
                            Text(t("no_data"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        } else {
                            val inThemeColor = if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF047857)
                            val outThemeColor = if (isSystemInDarkTheme()) Color(0xFFFCA5A5) else Color(0xFFB91C1C)

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height

                                val ratioIn = incoming / totalFlow
                                val ratioOut = outgoing / totalFlow

                                val barHeightMax = canvasHeight - 30f
                                val barWidth = 60.dp.toPx()

                                val inBarHeight = barHeightMax * ratioIn
                                val outBarHeight = barHeightMax * ratioOut

                                // Draw Income Bar
                                drawRect(
                                    color = inThemeColor,
                                    topLeft = Offset(x = (canvasWidth / 2) - barWidth - 20f, y = canvasHeight - inBarHeight - 20F),
                                    size = Size(width = barWidth, height = inBarHeight)
                                )

                                // Draw Expense Bar
                                drawRect(
                                    color = outThemeColor,
                                    topLeft = Offset(x = (canvasWidth / 2) + 20f, y = canvasHeight - outBarHeight - 20F),
                                    size = Size(width = barWidth, height = outBarHeight)
                                )
                            }
                        }
                    }

                    // Legends
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF047857))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (language == "en") "Income" else "Pemasukan", fontSize = 11.sp)

                        Spacer(modifier = Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSystemInDarkTheme()) Color(0xFFFCA5A5) else Color(0xFFB91C1C))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (language == "en") "Expenses" else "Pengeluaran", fontSize = 11.sp)
                    }
                }
            }
        }

        // WhatsApp Share report trigger
        item {
            Button(
                onClick = onShareText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(t("share_via"), fontWeight = FontWeight.Bold)
            }
        }

        // Float fast add transaction shortcut
        item {
            Button(
                onClick = onAddTransaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("floating_add_transaction"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
                Spacer(modifier = Modifier.width(8.dp))
                Text(t("add_item"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Sub-Tab Layout: 2. Spreadsheet Tab (Periods and Records Spreadsheet)
@Composable
fun SpreadsheetTab(
    viewModel: FinanceViewModel,
    periods: List<Period>,
    selectedPeriodId: Long?,
    transactions: List<Transaction>,
    isAdminMode: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onPeriodSelect: (Long?) -> Unit,
    onAddPeriodTrigger: () -> Unit,
    onDeletePeriodTrigger: (Period) -> Unit,
    onAddTransactionTrigger: () -> Unit,
    onDeleteTransactionTrigger: (Transaction) -> Unit
) {
    val currentLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    fun t(key: String): String = Translations.get(key, currentLanguage)
    fun formatIDR(amount: Double): String = ReportExporter.formatIDR(amount)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Dropdown selection panel of Periods
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var expandedPeriods by remember { mutableStateOf(false) }
            val activePeriod = periods.find { it.id == selectedPeriodId }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { expandedPeriods = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = activePeriod?.let { "${t("active_period")}: ${it.name}" } ?: t("all_time"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }

            if (expandedPeriods) {
                Dialog(onDismissRequest = { expandedPeriods = false }) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .heightIn(max = 350.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(14.dp)) {
                            item {
                                Text(
                                    text = t("choose_period"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                                Divider()
                            }
                            item {
                                Text(
                                    text = t("all_time"),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onPeriodSelect(null)
                                            expandedPeriods = false
                                        }
                                        .padding(12.dp),
                                    fontWeight = if (selectedPeriodId == null) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            items(periods) { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onPeriodSelect(p.id)
                                            expandedPeriods = false
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = p.name,
                                        fontWeight = if (selectedPeriodId == p.id) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isAdminMode) {
                                        IconButton(
                                            onClick = { onDeletePeriodTrigger(p) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Create period trigger icon
            IconButton(
                onClick = onAddPeriodTrigger,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = "Add Period",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Live Spreadsheet ledger list
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(t("search")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "SearchIcon") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentLanguage == "en") "Financial Records" else "Daftar Transaksi Kas",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            IconButton(
                onClick = onAddTransactionTrigger,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .size(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        val filteredList = transactions.filter {
            it.description.contains(searchQuery, ignoreCase = true) || 
            it.category.contains(searchQuery, ignoreCase = true) ||
            (it.memberName ?: "").contains(searchQuery, ignoreCase = true)
        }

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(t("no_data"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList) { tr ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val inTag = tr.type == "INCOME"
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (inTag) {
                                            if (isSystemInDarkTheme()) Color(0xFF064E3B) else Color(0xFFD1FAE5)
                                        } else {
                                            if (isSystemInDarkTheme()) Color(0xFF7F1D1D) else Color(0xFFFFD1D1)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (inTag) Icons.Default.Add else Icons.Default.Clear,
                                    contentDescription = null,
                                    tint = if (inTag) {
                                        if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF047857)
                                    } else {
                                        if (isSystemInDarkTheme()) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr.description,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${tr.category}${if (!tr.memberName.isNullOrEmpty()) " • ${tr.memberName}" else ""}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${if (inTag) "+" else "-"} ${formatIDR(tr.amount)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (inTag) {
                                        if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF047857)
                                    } else {
                                        if (isSystemInDarkTheme()) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                                    }
                                )
                                if (isAdminMode) {
                                    IconButton(
                                        onClick = { onDeleteTransactionTrigger(tr) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Item",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Sub-Tab Layout: 3. Members Tab (Tracking contributions and members)
@Composable
fun MembersTab(
    viewModel: FinanceViewModel,
    members: List<Member>,
    transactions: List<Transaction>,
    isAdminMode: Boolean,
    onAddMember: () -> Unit,
    onDeleteMember: (Member) -> Unit
) {
    val context = LocalContext.current
    val currentLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    fun t(key: String): String = Translations.get(key, currentLanguage)
    fun formatIDR(amount: Double): String = ReportExporter.formatIDR(amount)

    val periods by viewModel.periods.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactionsList.collectAsStateWithLifecycle()

    var memberToShowDetails by remember { mutableStateOf<Member?>(null) }
    var showDetailsAnimated by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = t("member_list"),
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
            IconButton(
                onClick = onAddMember,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .size(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        if (members.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (currentLanguage == "en") "No verified members." else "Belum memiliki daftar anggota.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(members) { mb ->
                    // Calculate totals specifically linked to this member
                    val memberInflows = transactions
                        .filter { it.memberId == mb.id && it.type == "INCOME" }
                        .sumOf { it.amount }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                memberToShowDetails = mb
                                showDetailsAnimated = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mb.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mb.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (mb.notes.isNotBlank()) {
                                    Text(
                                        text = mb.notes,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatIDR(memberInflows),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF047857)
                                )
                                if (isAdminMode) {
                                    IconButton(
                                        onClick = { onDeleteMember(mb) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Clicked Member details custom popup with smooth exit animation
    if (memberToShowDetails != null) {
        val mb = memberToShowDetails!!
        
        // Calculations
        val memberAllPaid = allTransactions.filter { it.memberId == mb.id && it.type == "INCOME" }
        val paidPeriodsSet = memberAllPaid.map { it.periodId }.toSet()
        
        val paidPeriods = periods.filter { paidPeriodsSet.contains(it.id) }
        val unpaidPeriods = periods.filter { !paidPeriodsSet.contains(it.id) }
        val paidTransactions = memberAllPaid
        val totalPaid = paidTransactions.sumOf { it.amount }
        
        val monthlyRate = 50000.0 // Custom base iuran
        val remainingUnpaid = unpaidPeriods.size * monthlyRate

        Dialog(onDismissRequest = {
            scope.launch {
                showDetailsAnimated = false
                delay(200)
                memberToShowDetails = null
            }
        }) {
            var activePopupTab by remember { mutableStateOf("status") } // "status" or "history"

            AnimatedVisibility(
                visible = showDetailsAnimated,
                enter = scaleIn(initialScale = 0.9f) + fadeIn(),
                exit = scaleOut(targetScale = 0.9f) + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title / Header with Avatar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mb.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mb.name,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (currentLanguage == "en") "Member Payment Details" else "Detail Pembayaran Kas Anggota",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    showDetailsAnimated = false
                                    delay(200)
                                    memberToShowDetails = null
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        // Divider using robust Box layout
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        // Stats Summary Row (Cards side-by-side)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Card 1: Total Paid
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (currentLanguage == "en") "Total Paid" else "Total Bayar",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = formatIDR(totalPaid),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Card 2: Remaining Unpaid / Tunggakan
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (remainingUnpaid > 0) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f)
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (currentLanguage == "en") "Dues / Unpaid" else "Tunggakan",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (remainingUnpaid > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatIDR(remainingUnpaid),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (remainingUnpaid > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Tab Toggle inside the popup: Status Periods vs Transaction Ledger
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (activePopupTab == "status") MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { activePopupTab = "status" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (currentLanguage == "en") "Dues Status" else "Status Iuran",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activePopupTab == "status") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (activePopupTab == "history") MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { activePopupTab = "history" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (currentLanguage == "en") "History Ledger" else "Riwayat Bayar",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activePopupTab == "history") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Detailed list of periods (Scrollable inside popup) or transaction list
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            // LazyColumn / Scrollable list inside Dialog
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(8.dp)
                            ) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (activePopupTab == "status") {
                                        if (periods.isEmpty()) {
                                            item {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (currentLanguage == "en") "No periods defined." else "Belum memiliki daftar periode.",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        } else {
                                            items(periods) { pr ->
                                                val transactionsInPeriod = paidTransactions.filter { it.periodId == pr.id }
                                                val isPaidInPeriod = transactionsInPeriod.isNotEmpty()
                                                val periodSum = transactionsInPeriod.sumOf { it.amount }

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            color = if (isPaidInPeriod) {
                                                                if (isSystemInDarkTheme()) Color(0xFF064E3B).copy(alpha = 0.2f) else Color(0xFFD1FAE5).copy(alpha = 0.4f)
                                                            } else {
                                                                if (isSystemInDarkTheme()) Color(0xFF7F1D1D).copy(alpha = 0.2f) else Color(0xFFFFD1D1).copy(alpha = 0.4f)
                                                            },
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable {
                                                            if (isAdminMode) {
                                                                viewModel.togglePeriodPayment(mb, pr)
                                                            } else {
                                                                Toast.makeText(
                                                                    context,
                                                                    if (currentLanguage == "en") "Admin mode required to change payment status" else "Gunakan mode Admin untuk mengubah status pembayaran",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isAdminMode) {
                                                        Checkbox(
                                                            checked = isPaidInPeriod,
                                                            onCheckedChange = { viewModel.togglePeriodPayment(mb, pr) },
                                                            colors = CheckboxDefaults.colors(
                                                                checkedColor = if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF047857)
                                                            ),
                                                            modifier = Modifier.size(36.dp).testTag("checkbox_period_${pr.id}")
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    } else {
                                                        Icon(
                                                            imageVector = if (isPaidInPeriod) Icons.Default.CheckCircle else Icons.Default.Clear,
                                                            contentDescription = null,
                                                            tint = if (isPaidInPeriod) {
                                                                if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF047857)
                                                            } else {
                                                                if (isSystemInDarkTheme()) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                                                            },
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                    }
                                                    Text(
                                                        text = pr.name,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = if (isPaidInPeriod) formatIDR(periodSum) else "(${formatIDR(monthlyRate)})",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isPaidInPeriod) {
                                                            if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF047857)
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        if (paidTransactions.isEmpty()) {
                                            item {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (currentLanguage == "en") "No historical payments found." else "Belum memiliki riwayat pembayaran.",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        } else {
                                            items(paidTransactions) { tr ->
                                                val dateFormatted = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(tr.date))
                                                val correspondingPeriod = periods.find { it.id == tr.periodId }?.name ?: ""
                                                
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            color = MaterialTheme.colorScheme.surface,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = tr.description.ifBlank { tr.category },
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "${if (correspondingPeriod.isNotEmpty()) "$correspondingPeriod • " else ""}$dateFormatted",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                        )
                                                    }
                                                    Text(
                                                        text = formatIDR(tr.amount),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF047857)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Notes Section
                        if (mb.notes.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (currentLanguage == "en") "MEMBER NOTES" else "CATATAN ANGGOTA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = mb.notes,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(10.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Sub-Tab Layout: 4. Expenses Tab (Keeping track of operations outgoing investments)
@Composable
fun ExpensesTab(
    viewModel: FinanceViewModel,
    transactions: List<Transaction>,
    isAdminMode: Boolean,
    onDeleteTransaction: (Transaction) -> Unit
) {
    val currentLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    fun t(key: String): String = Translations.get(key, currentLanguage)
    fun formatIDR(amount: Double): String = ReportExporter.formatIDR(amount)

    val expenseItems = transactions.filter { it.type == "EXPENSE" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (currentLanguage == "en") "EXPENSES LEDGER" else "DAFTAR PENGELUARAN KAS KELUAR",
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp
        )

        if (expenseItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (currentLanguage == "en") "No expenses registered." else "Belum memiliki catatan pengeluaran.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenseItems) { exp ->
                    val dateFormatted = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(exp.date))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSystemInDarkTheme()) Color(0xFF7F1D1D) else Color(0xFFFFD1D1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = null,
                                    tint = if (isSystemInDarkTheme()) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exp.description,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${exp.category} • $dateFormatted",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "- ${formatIDR(exp.amount)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSystemInDarkTheme()) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                                )
                                if (isAdminMode) {
                                    IconButton(
                                        onClick = { onDeleteTransaction(exp) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Sub-Tab Layout: 5. Settings Tab (Control Panel & Configurations)
@Composable
fun SettingsTab(
    viewModel: FinanceViewModel,
    periods: List<Period>,
    membersCount: Int,
    currentLanguage: String,
    isAdminMode: Boolean,
    onResetData: () -> Unit
) {
    val context = LocalContext.current
    fun t(key: String): String = Translations.get(key, currentLanguage)

    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (currentLanguage == "en") "SETTINGS PANEL" else "PENGATURAN & KONFIGURASI",
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp
        )

        // General Stats card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (currentLanguage == "en") "Quick Overview" else "Ringkasan Parameter",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = t("members_count"), fontSize = 12.sp)
                    Text(text = membersCount.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = if (currentLanguage == "en") "Periods count" else "Jumlah Periode", fontSize = 12.sp)
                    Text(text = periods.size.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = t("role"), fontSize = 12.sp)
                    Text(
                        text = if (isAdminMode) "ADMINISTRATOR" else "GUEST (VIEW-ONLY)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isAdminMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Language Configurations
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = t("language"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.setLanguage("id") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentLanguage == "id") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentLanguage == "id") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bahasa Indonesia")
                    }
                    Button(
                        onClick = { viewModel.setLanguage("en") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentLanguage == "en") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentLanguage == "en") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("English")
                    }
                }
            }
        }

        // Password Change panel
        if (isAdminMode) {
            var currentPassInput by remember { mutableStateOf("") }
            var newPassInput by remember { mutableStateOf("") }
            var isSavingPass by remember { mutableStateOf(false) }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().testTag("change_password_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (currentLanguage == "en") "Change Admin Password" else "Ubah Sandi Admin",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (currentLanguage == "en") "Update password in local app and cloud database." else "Ubah kata sandi admin untuk login di seluruh perangkat secara real-time.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    OutlinedTextField(
                        value = currentPassInput,
                        onValueChange = { currentPassInput = it },
                        label = { Text(if (currentLanguage == "en") "Current Password" else "Sandi Saat Ini") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().testTag("current_password_field")
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = newPassInput,
                        onValueChange = { newPassInput = it },
                        label = { Text(if (currentLanguage == "en") "New Password" else "Sandi Baru") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().testTag("new_password_field")
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            if (currentPassInput.isEmpty() || newPassInput.isEmpty()) {
                                Toast.makeText(context, if (currentLanguage == "en") "All fields must be filled!" else "Semua kolom harus diisi!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSavingPass = true
                            viewModel.changeAdminPassword(
                                currentPass = currentPassInput,
                                newPass = newPassInput,
                                onSuccess = {
                                    isSavingPass = false
                                    currentPassInput = ""
                                    newPassInput = ""
                                    Toast.makeText(context, if (currentLanguage == "en") "Password updated successfully!" else "Sandi admin berhasil diubah!", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { errorMsg ->
                                    isSavingPass = false
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().testTag("save_password_button"),
                        enabled = !isSavingPass
                    ) {
                        Text(if (currentLanguage == "en") "Update Password" else "Ubah Kata Sandi")
                    }
                }
            }
        }

        // Danger resetting configurations panel
        if (isAdminMode) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (currentLanguage == "en") "Database Maintenance" else "Pemeliharaan Database",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (currentLanguage == "en") "Irreversibly delete database items." else "Menghapus seluruh transaksi, anggota, dan periode secara permanen.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showClearConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("clear_db_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (currentLanguage == "en") "Wipe All Data" else "Wipe / Kosongkan Database")
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        text = t("admin_mode_locked"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(if (currentLanguage == "en") "Confirm Wipe" else "Konfirmasi Hapus Semua") },
            text = { Text(t("confirm_delete")) },
            confirmButton = {
                Button(
                    onClick = {
                        onResetData()
                        showClearConfirm = false
                        Toast.makeText(context, if (currentLanguage == "en") "All data wiped!" else "Database dikosongkan!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_wipe_data")
                ) {
                    Text(t("delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(t("cancel"))
                }
            }
        )
    }
}
