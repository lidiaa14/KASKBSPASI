package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ContributionEntity
import com.example.data.ExpenseEntity
import com.example.data.PeriodEntity
import com.example.util.ReportExporter
import com.example.util.Translations
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Observe flows from viewmodel
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val allPeriods by viewModel.allPeriods.collectAsStateWithLifecycle()
    val activePeriodDb by viewModel.activePeriodDb.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()

    val contributions by viewModel.filteredContributions.collectAsStateWithLifecycle()
    val expenses by viewModel.filteredExpenses.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val rawContributions by viewModel.rawContributions.collectAsStateWithLifecycle()

    val isAdminMode by viewModel.isAdminMode.collectAsStateWithLifecycle()
    val passwordInputError by viewModel.passwordInputError.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupRestoreStatus.collectAsStateWithLifecycle()

    // Query states
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val monthFilter by viewModel.monthFilter.collectAsStateWithLifecycle()

    // Local UI control states
    var currentTab by remember { mutableStateOf("Dashboard") } // "Dashboard", "Spreadsheet", "Contributions", "Expenses", "Settings"
    var showLoginDialog by remember { mutableStateOf(false) }
    var showAddPeriodDialog by remember { mutableStateOf(false) }
    var showAddContributionDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var selectedMemberForDetail by remember { mutableStateOf<String?>(null) }

    fun t(key: String): String = Translations.get(key, currentLanguage)
    fun t(key: String, vararg args: Any): String = Translations.get(key, currentLanguage, *args)

    // Backup restore status Toast observer
    LaunchedEffect(backupStatus) {
        backupStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearBackupRestoreStatus()
        }
    }

    // Modern color themes based on slate blue design system
    val themeColorScheme = if (isDarkMode) {
        darkColorScheme(
            primary = Color(0xFF1E6BFF), // Electric vibrant blue
            onPrimary = Color.White,
            primaryContainer = Color(0xFF1C273C),
            onPrimaryContainer = Color(0xFF00D8F6),
            secondary = Color(0xFF00D8F6), // Neon cyan
            onSecondary = Color(0xFF070B16),
            tertiary = Color(0xFF10B981), // Emerald green
            background = Color(0xFF070B16), // Extraterrestrial space-deep background
            surface = Color(0xFF0E1626), // Navy card container
            surfaceVariant = Color(0xFF1C273C), // Contrast surface
            outline = Color(0xFF1E293B) // Sharp borders
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF2563EB), // Clean trading blue
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDBEAFE),
            onPrimaryContainer = Color(0xFF1E40AF),
            secondary = Color(0xFF0891B2), // Deep cyan
            onSecondary = Color.White,
            tertiary = Color(0xFF10B981), // Emerald green
            background = Color(0xFFF4F6F9), // Cool slate-cream
            surface = Color.White,
            surfaceVariant = Color(0xFFE2E8F0),
            outline = Color(0xFFCBD5E1)
        )
    }

    MaterialTheme(
        colorScheme = themeColorScheme,
        typography = com.example.ui.theme.Typography
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Column {
                            Text(
                                text = t("app_title"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                letterSpacing = 0.5.sp
                            )
                            selectedPeriod?.let {
                                Text(
                                    text = "${if (currentLanguage == "en") "Period" else "Periode"}: ${it.name}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleDarkMode() }) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark Mode"
                            )
                        }

                        // Admin toggle Indicator
                        Button(
                            onClick = {
                                if (isAdminMode) {
                                    viewModel.logoutAdmin()
                                    Toast.makeText(context, t("toast_logged_out"), Toast.LENGTH_SHORT).show()
                                } else {
                                    showLoginDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAdminMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isAdminMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isAdminMode) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = "Admin lock",
                                modifier = Modifier.size(16.dp),
                                tint = if (isAdminMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAdminMode) t("admin") else t("visitor"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            },
            bottomBar = {
                // Bottom navigation tab bar
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    val tabs = listOf(
                        Triple("Dashboard", Icons.Default.Dashboard, t("tab_dashboard")),
                        Triple("Spreadsheet", Icons.Default.GridOn, t("tab_spreadsheet")),
                        Triple("Members", Icons.Default.People, t("tab_members")),
                        Triple("Expenses", Icons.Default.ReceiptLong, t("tab_expenses")),
                        Triple("Settings", Icons.Default.Settings, t("tab_settings"))
                    )

                    tabs.forEach { (tabId, icon, label) ->
                        NavigationBarItem(
                            selected = currentTab == tabId,
                            onClick = { currentTab = tabId },
                            alwaysShowLabel = true,
                            icon = { 
                                Icon(
                                    imageVector = icon, 
                                    contentDescription = label,
                                    modifier = Modifier.size(24.dp)
                                ) 
                            },
                            label = { 
                                Text(
                                    text = label, 
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                ) 
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            },
            floatingActionButton = {
                // Floating Action Button for admin shortcut insertion
                if (isAdminMode && (currentTab == "Members" || currentTab == "Expenses")) {
                    FloatingActionButton(
                        onClick = {
                            if (currentTab == "Members") {
                                showAddContributionDialog = true
                            } else {
                                showAddExpenseDialog = true
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .testTag("add_record_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Item")
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Tab Selection routing
                when (currentTab) {
                    "Dashboard" -> DashboardTab(
                        metrics = metrics,
                        contributions = rawContributions,
                        expenses = expenses,
                        isAdminMode = isAdminMode,
                        currentLanguage = currentLanguage,
                        onShareReport = {
                            selectedPeriod?.let { period ->
                                val shareText = ReportExporter.generateShareText(
                                    periodName = period.name,
                                    startingBalance = metrics.startingBalance,
                                    contributions = rawContributions,
                                    expenses = expenses
                                )
                                ReportExporter.shareToGroupChat(context, shareText)
                            }
                        },
                        onNavigateToMembers = { currentTab = "Members" },
                        onMemberClick = { selectedMemberForDetail = it }
                    )

                    "Spreadsheet" -> SpreadsheetTab(
                        metrics = metrics,
                        contributions = contributions,
                        expenses = expenses,
                        periodName = selectedPeriod?.name ?: "N/A",
                        currentLanguage = currentLanguage,
                        onExportPdf = {
                            selectedPeriod?.let { period ->
                                ReportExporter.printOrSavePdf(
                                    context = context,
                                    periodName = period.name,
                                    startingBalance = metrics.startingBalance,
                                    contributions = contributions,
                                    expenses = expenses
                                )
                            }
                        },
                        onMemberClick = { selectedMemberForDetail = it }
                    )

                    "Members" -> MembersTab(
                        contributions = contributions,
                        rawContributions = rawContributions,
                        searchQuery = searchQuery,
                        statusFilter = statusFilter,
                        monthFilter = monthFilter,
                        isAdminMode = isAdminMode,
                        currentLanguage = currentLanguage,
                        onSearchChange = { viewModel.searchQuery.value = it },
                        onStatusFilterChange = { viewModel.statusFilter.value = it },
                        onMonthFilterChange = { viewModel.monthFilter.value = it },
                        onAddClick = { showAddContributionDialog = true },
                        onTogglePaid = { viewModel.toggleContributionPaid(it) },
                        onDeleteClick = { viewModel.deleteContribution(it) },
                        onDeleteMember = { viewModel.deleteMemberAllContributions(it) },
                        onMemberClick = { selectedMemberForDetail = it }
                    )

                    "Expenses" -> ExpensesTab(
                        expenses = expenses,
                        isAdminMode = isAdminMode,
                        currentLanguage = currentLanguage,
                        onAddClick = { showAddExpenseDialog = true },
                        onDeleteClick = { viewModel.deleteExpense(it) }
                    )

                    "Settings" -> SettingsTab(
                        allPeriods = allPeriods,
                        activePeriodDb = activePeriodDb,
                        selectedPeriod = selectedPeriod,
                        isAdminMode = isAdminMode,
                        adminPasswordValue = viewModel.adminPassword.value,
                        currentLanguage = currentLanguage,
                        onLanguageChange = { viewModel.setAppLanguage(it) },
                        onPeriodSelect = { viewModel.selectPeriod(it) },
                        onPeriodActiveSet = { viewModel.switchActivePeriod(it.id) },
                        onPeriodDelete = { viewModel.deletePeriod(it) },
                        onAddPeriodClick = { showAddPeriodDialog = true },
                        onBackupClick = { showBackupRestoreDialog = true },
                        onChangePassword = { viewModel.changeAdminPassword(it) },
                        onLogout = { viewModel.logoutAdmin() },
                        onLoginClick = { showLoginDialog = true }
                    )
                }

                // --- DIALOGS CONTAINER ---

                selectedMemberForDetail?.let { memberName ->
                    val selectedPeriodYear = getYearFromPeriodName(selectedPeriod?.name)
                    MemberDetailDialog(
                        memberName = memberName,
                        allContributions = rawContributions,
                        isAdminMode = isAdminMode,
                        currentLanguage = currentLanguage,
                        periodYear = selectedPeriodYear,
                        onTogglePaid = { viewModel.toggleContributionPaid(it) },
                        onAddContribution = { name, amt, date, paid, notes ->
                            viewModel.addContribution(name, amt, date, paid, notes)
                        },
                        onDeleteContribution = { viewModel.deleteContribution(it) },
                        onUpdateContribution = { viewModel.updateContribution(it) },
                        onDismiss = { selectedMemberForDetail = null }
                    )
                }

                if (showLoginDialog) {
                    LoginDialog(
                        passwordInputError = passwordInputError,
                        currentLanguage = currentLanguage,
                        onDismiss = { showLoginDialog = false },
                        onSubmit = { pin ->
                            if (viewModel.loginAdmin(pin)) {
                                showLoginDialog = false
                                Toast.makeText(context, t("toast_logged_in"), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                if (showAddPeriodDialog) {
                    AddPeriodDialog(
                        currentLanguage = currentLanguage,
                        onDismiss = { showAddPeriodDialog = false },
                        onSubmit = { name, startBalance, makeActive ->
                            viewModel.addPeriod(name, startBalance, makeActive)
                            showAddPeriodDialog = false
                            Toast.makeText(context, t("toast_period_created"), Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                if (showAddContributionDialog) {
                    AddContributionDialog(
                        currentLanguage = currentLanguage,
                        periodYear = getYearFromPeriodName(selectedPeriod?.name),
                        onDismiss = { showAddContributionDialog = false },
                        onSubmit = { name, amount, date, isPaid, notes ->
                            viewModel.addContribution(name, amount, date, isPaid, notes)
                            showAddContributionDialog = false
                            Toast.makeText(context, t("toast_contribution_added"), Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                if (showAddExpenseDialog) {
                    AddExpenseDialog(
                        currentLanguage = currentLanguage,
                        onDismiss = { showAddExpenseDialog = false },
                        onSubmit = { purpose, amount, date, notes ->
                            viewModel.addExpense(purpose, amount, date, notes)
                            showAddExpenseDialog = false
                            Toast.makeText(context, t("toast_expense_added"), Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                if (showBackupRestoreDialog) {
                    BackupRestoreDialog(
                        backupString = viewModel.exportBackupToJson(),
                        currentLanguage = currentLanguage,
                        onDismiss = { showBackupRestoreDialog = false },
                        onImport = { json ->
                            if (viewModel.importBackupFromJson(json)) {
                                showBackupRestoreDialog = false
                            }
                        }
                    )
                }
            }
        }
    }
}

// --- CURRENCY UTILS ---
fun formatIDR(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    return format.format(amount).replace("Rp", "Rp ").replace(",00", "")
}

fun formatMiniDate(time: Long): String {
    val sdf = SimpleDateFormat("MMM dd", Locale.US)
    return sdf.format(Date(time))
}

// --- STYLISH COMPOSABLE COMPONENTS ---

@Composable
fun DashboardTab(
    metrics: DashboardMetrics,
    contributions: List<ContributionEntity>,
    expenses: List<ExpenseEntity>,
    isAdminMode: Boolean,
    currentLanguage: String,
    onShareReport: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onMemberClick: (String) -> Unit
) {
    fun t(key: String): String = Translations.get(key, currentLanguage)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core Numeric Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Large styled main remaining balance focus card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = t("current_cash_balance"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatIDR(metrics.remainingBalance),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.testTag("remaining_balance")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = t("starting_pool"),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = formatIDR(metrics.startingBalance),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = t("total_income"),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = formatIDR(metrics.totalIncome),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF047857)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = t("total_expenses"),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = formatIDR(metrics.totalExpenses),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSystemInDarkTheme()) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Custom drawn interactive charts/graphs card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t("income_expense_trend"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Icon(
                            imageVector = Icons.Default.InsertChart,
                            contentDescription = "Trend graph icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Draw our beautifully fluid custom canvas timeline graph
                    FinancialTrendChart(
                        contributions = contributions,
                        expenses = expenses,
                        currentLanguage = currentLanguage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }

        // Actions & Sharing strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onShareReport() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share, 
                            contentDescription = "Share Report icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = t("share_via_whatsapp"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Member Payment Summary Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = t("member_payment_summary"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Compute Member Metrics from Raw contributions list
                    val memberGroup = remember(contributions) {
                        contributions.groupBy { it.memberName.trim().lowercase() }
                    }
                    val totalMembersCount = memberGroup.size
                    val unpaidMembersCount = memberGroup.values.count { list -> list.any { !it.isPaid } }
                    val paidMembersCount = totalMembersCount - unpaidMembersCount

                    // Display as 3 side-by-side or stacked modern indicator badges/widgets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total Members Card
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = totalMembersCount.toString(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = t("total_members"),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Paid Members Card
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = Color(0xFFDCFCE7).copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = paidMembersCount.toString(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = t("paid_members"),
                                fontSize = 10.sp,
                                color = Color(0xFF15803D).copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Unpaid Members Card
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = Color(0xFFFEE2E2).copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFB91C1C),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = unpaidMembersCount.toString(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB91C1C)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = t("unpaid_members"),
                                fontSize = 10.sp,
                                color = Color(0xFFB91C1C).copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // A clean button navigating to the details tab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToMembers() }
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = t("view_all_members_btn"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// Custom graphic Bar plot drawn cleanly with Core Canvas API on top of responsive containers
@Composable
fun FinancialTrendChart(
    contributions: List<ContributionEntity>,
    expenses: List<ExpenseEntity>,
    currentLanguage: String = "id",
    modifier: Modifier = Modifier
) {
    val defaultPrimaryColor = MaterialTheme.colorScheme.primary
    val defaultSecondaryColor = MaterialTheme.colorScheme.secondary
    val defaultDividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val textPaintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 15f || height <= 30f) return@Canvas

        // Calculate totals dynamically for simple interval bar plots (divided by months)
        val monthIncome = DoubleArray(12) { 0.0 }
        val monthExpenses = DoubleArray(12) { 0.0 }

        // Compile item values
        contributions.filter { it.isPaid }.forEach { c ->
            val cal = Calendar.getInstance().apply { timeInMillis = c.date }
            val m = cal.get(Calendar.MONTH)
            if (m in 0..11) {
                monthIncome[m] += c.amount
            }
        }

        expenses.forEach { e ->
            val cal = Calendar.getInstance().apply { timeInMillis = e.date }
            val m = cal.get(Calendar.MONTH)
            if (m in 0..11) {
                monthExpenses[m] += e.amount
            }
        }

        // Get bounds
        val maxAmount = maxOf(
            monthIncome.maxOrNull() ?: 1000.0,
            monthExpenses.maxOrNull() ?: 1000.0,
            1.0
        )

        val paddingBottom = 25f
        val paddingLeft = 10f
        val chartHeight = height - paddingBottom
        val colWidth = (width - paddingLeft) / 12f

        // Draw horizontal grid lines
        val lineCount = 3
        for (i in 0..lineCount) {
            val yPos = chartHeight * (i.toFloat() / lineCount)
            drawLine(
                color = defaultDividerColor,
                start = Offset(0f, yPos),
                end = Offset(width, yPos),
                strokeWidth = 1f
            )
        }

        // Draw side-by-side Monthly Bars
        val mLabels = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
        for (i in 0..11) {
            val incVal = monthIncome[i]
            val expVal = monthExpenses[i]

            val incBarHeight = (incVal / maxAmount * chartHeight).toFloat()
            val expBarHeight = (expVal / maxAmount * chartHeight).toFloat()

            val originX = paddingLeft + (i * colWidth)
            val barW = (colWidth * 0.35f).coerceAtLeast(4f)

            // Income Column (Green)
            if (incVal > 0) {
                drawRect(
                    color = Color(0xFF10B981), // Solid Emerald Green
                    topLeft = Offset(originX, chartHeight - incBarHeight),
                    size = androidx.compose.ui.geometry.Size(barW, incBarHeight)
                )
            } else {
                // Invisible zero bar placeholders
                drawCircle(
                    color = Color(0xFF10B981).copy(alpha = 0.3f),
                    radius = 2f,
                    center = Offset(originX + barW/2, chartHeight - 1f)
                )
            }

            // Expense Column (Red)
            if (expVal > 0) {
                drawRect(
                    color = Color(0xFFEF4444), // Solid Rose Red
                    topLeft = Offset(originX + barW + 2, chartHeight - expBarHeight),
                    size = androidx.compose.ui.geometry.Size(barW, expBarHeight)
                )
            }

            // Simple line tags down
            drawLine(
                color = defaultDividerColor,
                start = Offset(originX + barW, chartHeight),
                end = Offset(originX + barW, chartHeight + 6f),
                strokeWidth = 1.5f
            )
        }
    }

    // Legend panel on base
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).background(Color(0xFF10B981), RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = if (currentLanguage == "en") "Income" else "Pemasukan", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Box(modifier = Modifier.size(10.dp).background(Color(0xFFEF4444), RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = if (currentLanguage == "en") "Expenses" else "Pengeluaran", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- SPREADSHEET LEDGER VIEW WITH SIZED COLUMNS ---

@Composable
fun SpreadsheetTab(
    metrics: DashboardMetrics,
    contributions: List<ContributionEntity>,
    expenses: List<ExpenseEntity>,
    periodName: String,
    currentLanguage: String,
    onExportPdf: () -> Unit,
    onMemberClick: (String) -> Unit
) {
    fun t(key: String): String = Translations.get(key, currentLanguage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Toolbar with Excel styling
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = t("spreadsheet_ledger"),
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Button(
                onClick = onExportPdf,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF Icon", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(t("export_pdf_print"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Ledger Grid frame (scrollable both directions if values exceed, mapped safely)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // Table Row Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    TableCell(text = t("table_header_no"), weight = 0.1f, isHeader = true)
                    TableCell(text = t("table_header_date"), weight = 0.22f, isHeader = true)
                    TableCell(text = t("table_header_member"), weight = 0.33f, isHeader = true)
                    TableCell(text = t("table_header_status"), weight = 0.15f, isHeader = true)
                    TableCell(text = t("table_header_amount"), weight = 0.2f, isHeader = true, alignRight = true)
                }

                // Table Rows container (scrollable list of spreadsheet rows)
                Box(modifier = Modifier.weight(1f)) {
                    val allLedgerRows = remember(contributions, expenses) {
                        val cList = contributions.map { 
                            LedgerItem(
                                date = it.date,
                                isIncome = true,
                                label = it.memberName,
                                status = if (it.isPaid) "Paid" else "Unpaid",
                                amount = it.amount
                            )
                        }
                        val eList = expenses.map {
                            LedgerItem(
                                date = it.date,
                                isIncome = false,
                                label = "[EXP] ${it.purpose}",
                                status = "Paid",
                                amount = it.amount
                            )
                        }
                        (cList + eList).sortedBy { it.date }
                    }

                    if (allLedgerRows.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                t("no_transactions_recorded"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(allLedgerRows) { idx, r ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (idx % 2 == 0) MaterialTheme.colorScheme.surface 
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                        )
                                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                ) {
                                    TableCell(text = "${idx + 1}", weight = 0.1f)
                                    TableCell(text = formatMiniDate(r.date), weight = 0.22f)
                                    TableCell(
                                        text = r.label,
                                        weight = 0.33f,
                                        isBold = r.isIncome,
                                        modifier = if (r.isIncome) Modifier.clickable { onMemberClick(r.label) } else Modifier
                                    )
                                    
                                    // Status cell with colored background pill
                                    Box(
                                        modifier = Modifier
                                            .weight(0.15f)
                                            .align(Alignment.CenterVertically)
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val bgClr = if (r.status == "Paid") Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                        val textClr = if (r.status == "Paid") Color(0xFF16A34A) else Color(0xFFDC2626)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(bgClr)
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (r.status == "Paid") t("status_paid") else t("status_unpaid"),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textClr
                                            )
                                        }
                                    }

                                    val amtSign = if (r.isIncome && r.status == "Paid") "+" else if (!r.isIncome) "-" else ""
                                    val amtClr = if (r.isIncome && r.status == "Paid") Color(0xFF10B981) else if (!r.isIncome) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                                    TableCell(
                                        text = "$amtSign${formatIDR(r.amount)}",
                                        weight = 0.2f,
                                        textColor = amtClr,
                                        alignRight = true
                                    )
                                }
                            }
                        }
                    }
                }

                // Table Summary Bottom (Automatically calculated spreadsheet sum rows)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    SpreadsheetFooterRow(label = t("footer_starting"), value = formatIDR(metrics.startingBalance))
                    SpreadsheetFooterRow(label = t("footer_total_income"), value = "+ ${formatIDR(metrics.totalIncome)}", valueColor = Color(0xFF10B981))
                    SpreadsheetFooterRow(label = t("footer_total_expenses"), value = "- ${formatIDR(metrics.totalExpenses)}", valueColor = Color(0xFFEF4444))
                    SpreadsheetFooterRow(
                        label = t("footer_remaining"), 
                        value = formatIDR(metrics.remainingBalance), 
                        isHighlight = true
                    )
                }
            }
        }
    }
}

data class LedgerItem(
    val date: Long,
    val isIncome: Boolean,
    val label: String,
    val status: String,
    val amount: Double
)

@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    isHeader: Boolean = false,
    isBold: Boolean = false,
    alignRight: Boolean = false,
    textColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        Modifier
            .weight(weight)
            .then(modifier)
            .padding(8.dp),
        fontWeight = if (isHeader || isBold) FontWeight.Bold else FontWeight.Normal,
        fontSize = if (isHeader) 11.sp else 11.sp,
        textAlign = if (alignRight) TextAlign.Right else TextAlign.Left,
        color = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun SpreadsheetFooterRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = if (isHighlight) 12.sp else 11.sp,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = if (isHighlight) 13.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else valueColor
        )
    }
}

// --- MEMBERS MANAGEMENT SCREEN (MEMBERS TAB) ---

@Composable
fun MembersTab(
    contributions: List<ContributionEntity>,
    rawContributions: List<ContributionEntity>,
    searchQuery: String,
    statusFilter: String,
    monthFilter: Int,
    isAdminMode: Boolean,
    currentLanguage: String,
    onSearchChange: (String) -> Unit,
    onStatusFilterChange: (String) -> Unit,
    onMonthFilterChange: (Int) -> Unit,
    onAddClick: () -> Unit,
    onTogglePaid: (ContributionEntity) -> Unit,
    onDeleteClick: (ContributionEntity) -> Unit,
    onDeleteMember: (String) -> Unit,
    onMemberClick: (String) -> Unit
) {
    fun t(key: String): String = Translations.get(key, currentLanguage)
    fun t(key: String, vararg args: Any): String = Translations.get(key, currentLanguage, *args)

    var expandedMonthFilter by remember { mutableStateOf(false) }
    var memberToDelete by remember { mutableStateOf<String?>(null) }
    val monthsLabels = listOf(
        t("month_jan"), t("month_feb"), t("month_mar"), t("month_apr"), t("month_may"), t("month_jun"),
        t("month_jul"), t("month_aug"), t("month_sep"), t("month_oct"), t("month_nov"), t("month_dec")
    )

    if (memberToDelete != null) {
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            title = { Text(text = if (currentLanguage == "en") "Delete Member" else "Hapus Anggota") },
            text = { 
                Text(
                    text = if (currentLanguage == "en") 
                        "Are you sure you want to delete member \"$memberToDelete\" and all of their contribution records for the active period?" 
                    else 
                        "Apakah Anda yakin ingin menghapus anggota \"$memberToDelete\" beserta seluruh catatan iurannya untuk periode aktif ini?"
                ) 
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val name = memberToDelete
                        if (name != null) {
                            onDeleteMember(name)
                        }
                        memberToDelete = null
                    }
                ) {
                    Text(t("button_delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) {
                    Text(t("button_cancel"))
                }
            }
        )
    }

    // Compute unique members list with aggregate stats based on active period contributions
    val allUniqueMembers = remember(rawContributions) {
        rawContributions.map { it.memberName.trim() }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    // Filter unique members based on search queries and status/month filters.
    val filteredMembers = remember(allUniqueMembers, rawContributions, searchQuery, statusFilter, monthFilter) {
        allUniqueMembers.filter { member ->
            val matchesSearch = member.contains(searchQuery, ignoreCase = true)
            if (!matchesSearch) return@filter false

            val memberContributions = rawContributions.filter { 
                it.memberName.trim().equals(member.trim(), ignoreCase = true) 
            }

            val matchesStatus = when (statusFilter) {
                "All" -> true
                "Paid" -> {
                    if (monthFilter == -1) {
                        memberContributions.isNotEmpty() && memberContributions.all { it.isPaid }
                    } else {
                        val monthEntry = memberContributions.find { c ->
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = c.date }
                            cal.get(java.util.Calendar.MONTH) == monthFilter
                        }
                        monthEntry?.isPaid == true
                    }
                }
                "Unpaid" -> {
                    if (monthFilter == -1) {
                        memberContributions.any { !it.isPaid }
                    } else {
                        val monthEntry = memberContributions.find { c ->
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = c.date }
                            cal.get(java.util.Calendar.MONTH) == monthFilter
                        }
                        monthEntry == null || !monthEntry.isPaid
                    }
                }
                else -> true
            }

            matchesStatus
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Title block
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = t("tab_members"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = t("income_contributions_sub"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isAdminMode) {
                Button(
                    onClick = onAddClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(t("dialog_contribution_add"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Search Bar and Filters Box
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(t("search_member_placeholder"), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "SearchIcon") },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    singleLine = true
                )

                // Month picker chip
                Box {
                    FilterChip(
                        selected = monthFilter != -1,
                        onClick = { expandedMonthFilter = true },
                        label = { 
                            Text(
                                text = if (monthFilter == -1) t("all_months") else monthsLabels[monthFilter].take(3),
                                fontSize = 11.sp
                            ) 
                        },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )

                    DropdownMenu(
                        expanded = expandedMonthFilter,
                        onDismissRequest = { expandedMonthFilter = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(t("all_months")) },
                            onClick = {
                                onMonthFilterChange(-1)
                                expandedMonthFilter = false
                            }
                        )
                        monthsLabels.forEachIndexed { idx, mName ->
                            DropdownMenuItem(
                                text = { Text(mName) },
                                onClick = {
                                    onMonthFilterChange(idx)
                                    expandedMonthFilter = false
                                }
                            )
                        }
                    }
                }
            }

            // Modern Segmented Tab UI for Statuses
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val statusOptions = listOf("All", "Paid", "Unpaid")
                statusOptions.forEach { opt ->
                    val active = statusFilter == opt
                    val labelStr = when (opt) {
                        "All" -> t("status_all")
                        "Paid" -> t("status_paid")
                        "Unpaid" -> t("status_unpaid")
                        else -> opt
                    }
                    val bgSelectedColor = MaterialTheme.colorScheme.primaryContainer
                    val contentSelectedColor = MaterialTheme.colorScheme.onPrimaryContainer
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) bgSelectedColor else Color.Transparent)
                            .clickable { onStatusFilterChange(opt) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = labelStr,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) contentSelectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Unpaid Warning Banner if applicable
        val generalUnpaidCount = remember(rawContributions) {
            rawContributions.groupBy { it.memberName.trim().lowercase() }.values.count { list -> list.any { !it.isPaid } }
        }
        if (generalUnpaidCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = t("warning_unpaid_title"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = t("warning_unpaid_desc"),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Members List Scroll area
        if (filteredMembers.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = t("no_contributions_match"),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredMembers, key = { it }) { member ->
                    // Calculate specific stats for this member
                    val memberContributions = remember(rawContributions, member) {
                        rawContributions.filter { 
                            it.memberName.trim().equals(member.trim(), ignoreCase = true) 
                        }
                    }
                    val paidSum = remember(memberContributions) {
                        memberContributions.filter { it.isPaid }.sumOf { it.amount }
                    }
                    val isAllPaid = remember(memberContributions, monthFilter) {
                        if (monthFilter == -1) {
                            memberContributions.isNotEmpty() && memberContributions.all { it.isPaid }
                        } else {
                            memberContributions.any { c ->
                                val cal = java.util.Calendar.getInstance().apply { timeInMillis = c.date }
                                cal.get(java.util.Calendar.MONTH) == monthFilter && c.isPaid
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMemberClick(member) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Member Initial Avatar
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                            )
                                        ),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                              ) {
                                val initials = member.split(" ").filter { it.isNotBlank() }
                                    .take(2)
                                    .map { it.first().uppercase() }
                                    .joinToString("")
                                Text(
                                    text = initials,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Member Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = member,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = t("total_paid_pending", memberContributions.count { it.isPaid }, memberContributions.count { !it.isPaid }),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            if (isAdminMode) {
                                IconButton(
                                    onClick = { memberToDelete = member },
                                    modifier = Modifier.padding(horizontal = 4.dp).size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Member",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Payment status summary
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatIDR(paidSum),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = if (isAllPaid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isAllPaid) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                    contentColor = if (isAllPaid) Color(0xFF15803D) else Color(0xFFB91C1C)
                                ) {
                                    Text(
                                        text = if (isAllPaid) t("status_paid") else t("status_unpaid"),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

// --- EXPENSES MANAGEMENT SCREEN (EXPENSE TAB) ---

@Composable
fun ExpensesTab(
    expenses: List<ExpenseEntity>,
    isAdminMode: Boolean,
    currentLanguage: String,
    onAddClick: () -> Unit,
    onDeleteClick: (ExpenseEntity) -> Unit
) {
    fun t(key: String): String = Translations.get(key, currentLanguage)
    fun t(key: String, vararg args: Any): String = Translations.get(key, currentLanguage, *args)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(t("expense_tracking"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(t("transparent_expenditures"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isAdminMode) {
                Button(
                    onClick = onAddClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(t("add_expense"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (expenses.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Receipt, 
                        contentDescription = null, 
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        t("no_expenses_recorded"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(expenses, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFEE2E2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ReceiptLong, 
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.purpose, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (item.notes.isNotBlank()) {
                                    Text(item.notes, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = t("date_label", formatMiniDate(item.date)),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "- ${formatIDR(item.amount)}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Color(0xFFEF4444)
                                )
                                if (isAdminMode) {
                                    IconButton(
                                        onClick = { onDeleteClick(item) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete, 
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

// --- SETUP CONFIG & PERIOD TAB ---

@Composable
fun SettingsTab(
    allPeriods: List<PeriodEntity>,
    activePeriodDb: PeriodEntity?,
    selectedPeriod: PeriodEntity?,
    isAdminMode: Boolean,
    adminPasswordValue: String,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onPeriodSelect: (PeriodEntity) -> Unit,
    onPeriodActiveSet: (PeriodEntity) -> Unit,
    onPeriodDelete: (PeriodEntity) -> Unit,
    onAddPeriodClick: () -> Unit,
    onBackupClick: () -> Unit,
    onChangePassword: (String) -> Unit,
    onLogout: () -> Unit,
    onLoginClick: () -> Unit
) {
    fun t(key: String): String = Translations.get(key, currentLanguage)
    fun t(key: String, vararg args: Any): String = Translations.get(key, currentLanguage, *args)

    var isPasswordEditExpand by remember { mutableStateOf(false) }
    var tempNewPassword by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // App Language Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Language switcher",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(t("app_language"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    t("app_language_desc"),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("id" to "Bahasa Indonesia", "en" to "English").forEach { (code, label) ->
                        val isSelected = currentLanguage == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    1.dp, 
                                    if (isSelected) MaterialTheme.colorScheme.primary 
                                    else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onLanguageChange(code) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Admin Access Control Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(t("role_access_management"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    t("role_access_desc"),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (isAdminMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(t("role_admin_active"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(t("role_active_ops_enabled"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(t("logout_admin_button"), fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Password update option
                    if (!isPasswordEditExpand) {
                        Button(
                            onClick = { isPasswordEditExpand = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(t("change_pin_button"), fontSize = 11.sp)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(t("enter_new_pin"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = tempNewPassword,
                                onValueChange = { tempNewPassword = it },
                                singleLine = true,
                                label = { Text(t("new_passcode_placeholder")) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (tempNewPassword.isNotBlank()) {
                                            onChangePassword(tempNewPassword)
                                            isPasswordEditExpand = false
                                            tempNewPassword = ""
                                            val changedMsg = if (currentLanguage == "en") "PIN Passcode updated successfully!" else "PIN Kata Sandi berhasil diperbarui!"
                                            Toast.makeText(context, changedMsg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(t("button_apply"), fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { isPasswordEditExpand = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(t("button_cancel"), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(t("role_visitor_active"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(t("role_visitor_desc"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = onLoginClick,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(t("login_admin_button"), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Financial/Academic Periods Administration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(t("financial_periods"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(t("financial_periods_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isAdminMode) {
                        IconButton(onClick = onAddPeriodClick, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add Period", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                allPeriods.forEach { p ->
                    val isViewing = selectedPeriod?.id == p.id
                    val isDbActive = activePeriodDb?.id == p.id

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onPeriodSelect(p) },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp, 
                            if (isViewing) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        ),
                        color = if (isViewing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (isDbActive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                t("active"), 
                                                fontSize = 8.sp, 
                                                fontWeight = FontWeight.Bold, 
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(t("prev_remaining", formatIDR(p.startingBalance)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isAdminMode && !isDbActive) {
                                    Button(
                                        onClick = { onPeriodActiveSet(p) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text(t("set_active"), fontSize = 9.sp)
                                    }
                                }

                                if (isAdminMode && allPeriods.size > 1) {
                                    IconButton(
                                        onClick = { onPeriodDelete(p) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Automatic local Backup restore section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(t("database_backup"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    t("database_backup_desc"),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onBackupClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(t("audit_backup"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- DIALOG COMPOSE FUNCTIONS ---

@Composable
fun LoginDialog(
    passwordInputError: Boolean,
    currentLanguage: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    fun t(key: String): String = Translations.get(key, currentLanguage)
    var pinValue by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(t("dialog_login_title"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                Text(
                    t("dialog_login_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pinValue,
                    onValueChange = { pinValue = it },
                    singleLine = true,
                    label = { Text(t("dialog_login_placeholder")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    isError = passwordInputError,
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )

                if (passwordInputError) {
                    Text(
                        t("dialog_login_incorrect"),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(t("button_cancel"))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(pinValue) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(t("dialog_login_button"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AddPeriodDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onSubmit: (String, Double, Boolean) -> Unit
) {
    fun t(key: String): String = Translations.get(key, currentLanguage)
    var nameValue by remember { mutableStateOf("") }
    var startBalanceValue by remember { mutableStateOf("") }
    var makeActiveChecked by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(t("dialog_period_title"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                
                OutlinedTextField(
                    value = nameValue,
                    onValueChange = { nameValue = it },
                    singleLine = true,
                    label = { Text(t("dialog_period_name")) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = startBalanceValue,
                    onValueChange = { startBalanceValue = it },
                    singleLine = true,
                    label = { Text(t("dialog_period_starting")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = makeActiveChecked, onCheckedChange = { makeActiveChecked = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(t("dialog_period_set_active"), fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(t("button_cancel"))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val bal = startBalanceValue.toDoubleOrNull() ?: 0.0
                            if (nameValue.isNotBlank()) {
                                onSubmit(nameValue, bal, makeActiveChecked)
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(t("button_save"))
                    }
                }
            }
        }
    }
}

@Composable
fun AddContributionDialog(
    currentLanguage: String,
    periodYear: Int,
    onDismiss: () -> Unit,
    onSubmit: (String, Double, Long, Boolean, String) -> Unit
) {
    fun t(key: String): String = Translations.get(key, currentLanguage)
    var memberName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var isPaidCheck by remember { mutableStateOf(true) }
    var selectedMonthIndex by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)) }
    var expandedMonth by remember { mutableStateOf(false) }

    val monthsLabels = listOf(
        t("month_jan"), t("month_feb"), t("month_mar"), t("month_apr"), t("month_may"), t("month_jun"),
        t("month_jul"), t("month_aug"), t("month_sep"), t("month_oct"), t("month_nov"), t("month_dec")
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(t("dialog_contribution_add"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                
                OutlinedTextField(
                    value = memberName,
                    onValueChange = { memberName = it },
                    singleLine = true,
                    label = { Text(t("dialog_contribution_member")) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Month Selector Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedMonth = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        val mLabel = if (currentLanguage == "en") "Month" else "Bulan"
                        Text(text = "$mLabel: ${monthsLabels[selectedMonthIndex]}")
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = expandedMonth,
                        onDismissRequest = { expandedMonth = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        monthsLabels.forEachIndexed { idx, mName ->
                            DropdownMenuItem(
                                text = { Text(mName) },
                                onClick = {
                                    selectedMonthIndex = idx
                                    expandedMonth = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    singleLine = true,
                    label = { Text(t("dialog_contribution_amount")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    singleLine = true,
                    label = { Text(t("dialog_contribution_notes")) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isPaidCheck, onCheckedChange = { isPaidCheck = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(t("dialog_contribution_status"), fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(t("button_cancel"))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (memberName.isNotBlank() && amt > 0.0) {
                                val cal = java.util.Calendar.getInstance()
                                cal.set(java.util.Calendar.YEAR, periodYear)
                                cal.set(java.util.Calendar.MONTH, selectedMonthIndex)
                                cal.set(java.util.Calendar.DAY_OF_MONTH, 15)
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
                                cal.set(java.util.Calendar.MINUTE, 0)
                                cal.set(java.util.Calendar.SECOND, 0)
                                cal.set(java.util.Calendar.MILLISECOND, 0)
                                onSubmit(memberName, amt, cal.timeInMillis, isPaidCheck, notesText)
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(t("button_save"))
                    }
                }
            }
        }
    }
}

@Composable
fun AddExpenseDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onSubmit: (String, Double, Long, String) -> Unit
) {
    fun t(key: String): String = Translations.get(key, currentLanguage)
    var purposeText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(t("dialog_expense_add"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFEF4444))
                
                OutlinedTextField(
                    value = purposeText,
                    onValueChange = { purposeText = it },
                    singleLine = true,
                    label = { Text(t("dialog_expense_purpose")) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    singleLine = true,
                    label = { Text(t("dialog_expense_amount")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    singleLine = true,
                    label = { Text(t("dialog_expense_notes")) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(t("button_cancel"))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (purposeText.isNotBlank() && amt > 0.0) {
                                onSubmit(purposeText, amt, System.currentTimeMillis(), notesText)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text(t("button_save"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BackupRestoreDialog(
    backupString: String,
    currentLanguage: String,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    fun t(key: String): String = Translations.get(key, currentLanguage)
    val clipboardManager = LocalClipboardManager.current
    var inputBackupJson by remember { mutableStateOf("") }

    val copyBackupText = if (currentLanguage == "en") "Copy Backup to Clipboard" else "Salin Cadangan ke Clipboard"
    val restoreLedgerLabel = if (currentLanguage == "en") "Restore Ledger from paste code:" else "Pulihkan Buku Besar dari kode tempel:"
    val pastePlaceholder = if (currentLanguage == "en") "Paste JSON backup code here..." else "Tempel kode cadangan JSON di sini..."
    val importOverlayLabel = if (currentLanguage == "en") "Import & Overlay" else "Impor & Tindih"
    val copyInstructionsText = if (currentLanguage == "en") "Copy the backup block below to keep a digital snapshot safe in your notes:" else "Salin blok cadangan di bawah ini untuk menyimpan salinan digital dengan aman di catatan Anda:"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(t("database_copy_backup"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                
                Text(copyInstructionsText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
                        Text(backupString, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(backupString))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(copyBackupText, fontSize = 11.sp)
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Text(restoreLedgerLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = inputBackupJson,
                    onValueChange = { inputBackupJson = it },
                    placeholder = { Text(pastePlaceholder, fontSize = 11.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    textStyle = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(t("button_cancel"))
                    }
                    Button(
                        onClick = {
                            if (inputBackupJson.isNotBlank()) {
                                onImport(inputBackupJson)
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(importOverlayLabel)
                    }
                }
            }
        }
    }
}

private fun getYearFromPeriodName(periodName: String?): Int {
    if (periodName == null) return 2025
    val regex = Regex("\\b(\\d{4})\\b")
    val match = regex.find(periodName)
    return match?.value?.toIntOrNull() ?: 2025
}

private fun getMonthTimeForYear(month: Int, year: Int): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.YEAR, year)
    cal.set(java.util.Calendar.MONTH, month)
    cal.set(java.util.Calendar.DAY_OF_MONTH, 15)
    cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Composable
fun MemberDetailDialog(
    memberName: String,
    allContributions: List<ContributionEntity>,
    isAdminMode: Boolean,
    currentLanguage: String,
    periodYear: Int,
    onTogglePaid: (ContributionEntity) -> Unit,
    onAddContribution: (String, Double, Long, Boolean, String) -> Unit,
    onDeleteContribution: (ContributionEntity) -> Unit,
    onUpdateContribution: (ContributionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    fun t(key: String): String = Translations.get(key, currentLanguage)

    var activeMonthAction by remember { mutableStateOf<MemberMonthStatus?>(null) }
    var activeMonthEdit by remember { mutableStateOf<MemberMonthStatus?>(null) }

    // Calculate dynamic stats
    val memberContributions = remember(allContributions, memberName) {
        allContributions.filter { it.memberName.trim().equals(memberName.trim(), ignoreCase = true) }
    }

    val expectedAmount = remember(memberContributions) {
        memberContributions.firstOrNull { it.amount > 0 }?.amount ?: 10000.0
    }

    val monthStatusList = remember(memberContributions, currentLanguage) {
        (0..11).map { m ->
            val name = when (m) {
                0 -> t("month_jan")
                1 -> t("month_feb")
                2 -> t("month_mar")
                3 -> t("month_apr")
                4 -> t("month_may")
                5 -> t("month_jun")
                6 -> t("month_jul")
                7 -> t("month_aug")
                8 -> t("month_sep")
                9 -> t("month_oct")
                10 -> t("month_nov")
                else -> t("month_dec")
            }
            
            val existing = memberContributions.find { c ->
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = c.date }
                cal.get(java.util.Calendar.MONTH) == m
            }
            
            MemberMonthStatus(
                monthIndex = m,
                monthName = name,
                isPaid = existing?.isPaid == true,
                amount = existing?.amount ?: expectedAmount,
                existingContribution = existing,
                date = existing?.date,
                notes = existing?.notes ?: ""
            )
        }
    }

    val unpaidMonths = monthStatusList.filter { !it.isPaid }
    val totalArrears = unpaidMonths.sumOf { it.amount }
    
    val paidMonthsList = monthStatusList.filter { it.isPaid }.map { it.monthName }
    val unpaidMonthsList = monthStatusList.filter { !it.isPaid }.map { it.monthName }
    
    val lastPaymentTime = memberContributions.filter { it.isPaid }.maxOfOrNull { it.date }
    val lastPaymentDateString = if (lastPaymentTime != null) formatMiniDate(lastPaymentTime) else "-"

    val initials = remember(memberName) {
        val parts = memberName.trim().split("\\s+".toRegex())
        if (parts.size >= 2) {
            (parts[0].take(1) + parts[1].take(1)).uppercase()
        } else if (parts.isNotEmpty() && parts[0].isNotBlank()) {
            parts[0].take(2).uppercase()
        } else {
            "MB"
        }
    }

    // Modern Dialog Actions Pop-up
    if (activeMonthAction != null) {
        val mStatus = activeMonthAction!!
        Dialog(onDismissRequest = { activeMonthAction = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${mStatus.monthName} - $memberName",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = if (currentLanguage == "en") "Select an action for this month:" else "Pilih tindakan untuk bulan ini:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // 1. Toggle status
                    TextButton(
                        onClick = {
                            if (mStatus.existingContribution != null) {
                                onTogglePaid(mStatus.existingContribution)
                            } else {
                                onAddContribution(
                                    memberName,
                                    expectedAmount,
                                    getMonthTimeForYear(mStatus.monthIndex, periodYear),
                                    true,
                                    "Iuran " + mStatus.monthName
                                )
                            }
                            activeMonthAction = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val lbl = if (mStatus.isPaid) {
                            if (currentLanguage == "en") "Mark as Unpaid" else "Tandai Belum Lunas"
                        } else {
                            if (currentLanguage == "en") "Mark as Paid" else "Tandai Lunas"
                        }
                        Text(lbl, fontWeight = FontWeight.Bold)
                    }

                    // 2. Edit Notes & Amount
                    if (mStatus.existingContribution != null) {
                        TextButton(
                            onClick = {
                                activeMonthEdit = mStatus
                                activeMonthAction = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (currentLanguage == "en") "Edit Amount & Notes" else "Ubah Jumlah & Catatan",
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // 3. Delete Record completely
                        TextButton(
                            onClick = {
                                onDeleteContribution(mStatus.existingContribution)
                                activeMonthAction = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                text = if (currentLanguage == "en") "Delete Contribution Record" else "Hapus Catatan Iuran",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    TextButton(
                        onClick = { activeMonthAction = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = t("button_cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Edit Contribution Dialog
    if (activeMonthEdit != null) {
        val mStatus = activeMonthEdit!!
        val contrib = mStatus.existingContribution!!
        var editAmountText by remember(contrib.id) { mutableStateOf(contrib.amount.toInt().toString()) }
        var editNotesText by remember(contrib.id) { mutableStateOf(contrib.notes) }

        Dialog(onDismissRequest = { activeMonthEdit = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (currentLanguage == "en") "Edit Contribution Note/Amount" else "Ubah Catatan / Jumlah Iuran",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "${mStatus.monthName} - $memberName",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = editAmountText,
                        onValueChange = { editAmountText = it },
                        singleLine = true,
                        label = { Text(t("dialog_contribution_amount")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editNotesText,
                        onValueChange = { editNotesText = it },
                        singleLine = true,
                        label = { Text(t("dialog_contribution_notes")) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { activeMonthEdit = null }) {
                            Text(t("button_cancel"))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val amt = editAmountText.toDoubleOrNull() ?: contrib.amount
                                val updated = contrib.copy(
                                    amount = amt,
                                    notes = editNotesText
                                )
                                onUpdateContribution(updated)
                                activeMonthEdit = null
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(t("button_save"))
                        }
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header row with avatar and member name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Styled avatar circular badge with gradients
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                ),
                                shape = RoundedCornerShape(27.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = memberName,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (currentLanguage == "en") "Member Payment Detail" else "Rincian Pembayaran Anggota",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Stats row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Total Arrears display card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (currentLanguage == "en") "TOTAL ARREARS" else "TOTAL TUNGGAKAN",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatIDR(totalArrears),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Last Payment Date Display box
                    Card(
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (currentLanguage == "en") "LAST PAYMENT" else "TGL BAYAR TERAKHIR",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = lastPaymentDateString,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Explicit Unpaid/Paid list block (satisfying prompt logic)
                val unpaidHeader = if (currentLanguage == "en") "Unpaid Months: " else "Bulan Tunggakan: "
                val paidHeader = if (currentLanguage == "en") "Paid Months: " else "Bulan Lunas: "
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (unpaidMonthsList.isNotEmpty()) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f) else Color(0xFFDCFCE7).copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, if (unpaidMonthsList.isNotEmpty()) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else Color(0xFF15803D).copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (unpaidMonthsList.isNotEmpty()) {
                            Text(
                                text = "$unpaidHeader${unpaidMonthsList.joinToString(", ")}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = if (currentLanguage == "en") "Fully Paid for All Months! 🎉" else "Lunas Semua Bulan! 🎉",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }
                        if (paidMonthsList.isNotEmpty() && unpaidMonthsList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$paidHeader${paidMonthsList.joinToString(", ")}",
                                fontSize = 11.sp,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Checklist Section
                Text(
                    text = if (currentLanguage == "en") "Monthly Payment Checklist" else "Checklist Pembayaran Bulanan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isAdminMode) {
                    Text(
                        text = if (currentLanguage == "en") "💡 Tap a month to manage." else "💡 Ketuk bulan untuk mengelola.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Grid layout for months list (Scrollable to prevent overflow)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 240.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        monthStatusList.chunked(2).forEach { rowMonths ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowMonths.forEach { mStatus ->
                                    val isPaid = mStatus.isPaid
                                    val bgColor = if (isPaid) {
                                        Color(0xFFDCFCE7).copy(alpha = 0.8f) // soft green
                                    } else {
                                        Color(0xFFFEE2E2).copy(alpha = 0.8f) // soft red
                                    }
                                    val contentColor = if (isPaid) Color(0xFF15803D) else Color(0xFFB91C1C)

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = bgColor,
                                        contentColor = contentColor,
                                        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.15f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable(enabled = isAdminMode) {
                                                activeMonthAction = mStatus
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = mStatus.monthName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(
                                                imageVector = if (isPaid) Icons.Default.CheckCircle else Icons.Default.RemoveCircleOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = contentColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Payment comments/notes section if any exist
                val notesList = monthStatusList.filter { it.isPaid && it.notes.isNotBlank() }
                if (notesList.isNotEmpty()) {
                    Text(
                        text = if (currentLanguage == "en") "Payment Notes" else "Catatan Pembayaran",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        notesList.forEach { mStatus ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${mStatus.monthName}: ${mStatus.notes}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (mStatus.date != null) {
                                    Text(
                                        text = formatMiniDate(mStatus.date),
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Footer button to close
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (currentLanguage == "en") "Close" else "Tutup",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

data class MemberMonthStatus(
    val monthIndex: Int,
    val monthName: String,
    val isPaid: Boolean,
    val amount: Double,
    val existingContribution: ContributionEntity?,
    val date: Long?,
    val notes: String
)
