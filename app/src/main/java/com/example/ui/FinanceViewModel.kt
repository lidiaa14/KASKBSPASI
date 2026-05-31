package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = FinanceRepository(database.financeDao())

    // All available financial periods from the database
    val allPeriods = repository.allPeriods.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // The current active period set in database
    val activePeriodDb = repository.activePeriodFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Currently viewed/selected period in UI (can be toggled in filters)
    val selectedPeriod = MutableStateFlow<PeriodEntity?>(null)

    // Real-time search queries and filters
    val searchQuery = MutableStateFlow("")
    val statusFilter = MutableStateFlow("All") // "All", "Paid", "Unpaid"
    val monthFilter = MutableStateFlow(-1) // -1 for All, 0-11 for Jan-Dec

    // Admin authorization session parameters
    val isAdminMode = MutableStateFlow(false)
    val adminPassword = MutableStateFlow("") // Default admin123
    val passwordInputError = MutableStateFlow(false)

    // UI Dark mode preference (persisted easily in SharedPreferences)
    private val sharedPrefs = application.getSharedPreferences("kb_spasi_prefs", Context.MODE_PRIVATE)
    val isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("dark_mode", true))

    // UI Language preference (persisted easily in SharedPreferences), default: "id" (Indonesian)
    val appLanguage = MutableStateFlow(sharedPrefs.getString("app_language", "id") ?: "id")

    // JSON Backup import/export state
    val backupRestoreStatus = MutableStateFlow<String?>(null)

    init {
        // Retrieve custom admin password if set previously, default is "admin123"
        adminPassword.value = sharedPrefs.getString("admin_password", "admin123") ?: "admin123"

        viewModelScope.launch {
            // Seed base initial data so the app has working examples right off the bat
            repository.seedInitialDataIfNecessary()

            // Observe the active database period. If a selectedPeriod hasn't been set, sync with active.
            repository.activePeriodFlow.collect { active ->
                if (selectedPeriod.value == null && active != null) {
                    selectedPeriod.value = active
                }
            }
        }
    }

    // Reactively load contributions based on selected period
    @OptIn(ExperimentalCoroutinesApi::class)
    val rawContributions = selectedPeriod.flatMapLatest { period ->
        if (period != null) {
            repository.getContributionsForPeriod(period.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reactively load expenses based on selected period
    @OptIn(ExperimentalCoroutinesApi::class)
    val rawExpenses = selectedPeriod.flatMapLatest { period ->
        if (period != null) {
            repository.getExpensesForPeriod(period.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Compose filtered, searchable lists of contributions
    val filteredContributions = combine(
        rawContributions,
        searchQuery,
        statusFilter,
        monthFilter
    ) { list, query, status, month ->
        list.filter { item ->
            val matchesSearch = item.memberName.contains(query, ignoreCase = true) || 
                                item.notes.contains(query, ignoreCase = true)
            
            val matchesStatus = when (status) {
                "Paid" -> item.isPaid
                "Unpaid" -> !item.isPaid
                else -> true
            }

            val matchesMonth = if (month == -1) {
                true
            } else {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = item.date }
                cal.get(java.util.Calendar.MONTH) == month
            }

            matchesSearch && matchesStatus && matchesMonth
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Compose filtered list of expenses
    val filteredExpenses = combine(
        rawExpenses,
        searchQuery,
        monthFilter
    ) { list, query, month ->
        list.filter { item ->
            val matchesSearch = item.purpose.contains(query, ignoreCase = true) ||
                                item.notes.contains(query, ignoreCase = true)

            val matchesMonth = if (month == -1) {
                true
            } else {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = item.date }
                cal.get(java.util.Calendar.MONTH) == month
            }

            matchesSearch && matchesMonth
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Global dashboard metric numbers calculated automatically based on selected period state
    val dashboardMetrics = combine(
        selectedPeriod,
        rawContributions,
        rawExpenses
    ) { period, contributionsList, expensesList ->
        val startBalance = period?.startingBalance ?: 0.0
        val paidTotal = contributionsList.filter { it.isPaid }.sumOf { it.amount }
        val expensesTotal = expensesList.sumOf { it.amount }
        val remaining = startBalance + paidTotal - expensesTotal

        DashboardMetrics(
            startingBalance = startBalance,
            totalIncome = paidTotal,
            totalExpenses = expensesTotal,
            remainingBalance = remaining
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())


    // --- OPERATIONS ---

    fun toggleDarkMode() {
        val nextVal = !isDarkMode.value
        isDarkMode.value = nextVal
        sharedPrefs.edit().putBoolean("dark_mode", nextVal).apply()
    }

    fun setAppLanguage(lang: String) {
        appLanguage.value = lang
        sharedPrefs.edit().putString("app_language", lang).apply()
    }

    fun loginAdmin(password: String): Boolean {
        if (password == adminPassword.value) {
            isAdminMode.value = true
            passwordInputError.value = false
            return true
        } else {
            passwordInputError.value = true
            return false
        }
    }

    fun logoutAdmin() {
        isAdminMode.value = false
    }

    fun changeAdminPassword(newPassword: String) {
        if (newPassword.isNotBlank()) {
            adminPassword.value = newPassword
            sharedPrefs.edit().putString("admin_password", newPassword).apply()
        }
    }

    fun selectPeriod(period: PeriodEntity) {
        selectedPeriod.value = period
    }

    // -- DATABASE WRITES (ONLY allowed when isAdminMode == true) ---

    fun addPeriod(name: String, startingBalance: Double, setAsActive: Boolean) {
        viewModelScope.launch {
            val period = PeriodEntity(name = name, startingBalance = startingBalance, isActive = setAsActive)
            val insertedId = repository.insertPeriod(period).toInt()
            if (setAsActive) {
                repository.setActivePeriod(insertedId)
                // Also update local selected viewing period to this new active one
                selectedPeriod.value = period.copy(id = insertedId, isActive = true)
            }
        }
    }

    fun switchActivePeriod(periodId: Int) {
        viewModelScope.launch {
            repository.setActivePeriod(periodId)
            allPeriods.value.find { it.id == periodId }?.let {
                selectedPeriod.value = it.copy(isActive = true)
            }
        }
    }

    fun deletePeriod(period: PeriodEntity) {
        viewModelScope.launch {
            repository.deletePeriod(period)
            // If we deleted our currently selected view, fallback to active db period or first database period
            if (selectedPeriod.value?.id == period.id) {
                val dbActive = repository.activePeriodFlow.first()
                if (dbActive != null) {
                    selectedPeriod.value = dbActive
                } else {
                    selectedPeriod.value = allPeriods.value.firstOrNull { it.id != period.id }
                }
            }
        }
    }

    fun addContribution(name: String, amount: Double, date: Long, isPaid: Boolean, notes: String) {
        val pId = selectedPeriod.value?.id ?: return
        viewModelScope.launch {
            val contribution = ContributionEntity(
                periodId = pId,
                memberName = name,
                amount = amount,
                date = date,
                isPaid = isPaid,
                notes = notes
            )
            repository.insertContribution(contribution)
        }
    }

    fun toggleContributionPaid(contribution: ContributionEntity) {
        viewModelScope.launch {
            val updated = contribution.copy(isPaid = !contribution.isPaid)
            repository.updateContribution(updated)
        }
    }

    fun updateContribution(contribution: ContributionEntity) {
        viewModelScope.launch {
            repository.updateContribution(contribution)
        }
    }

    fun deleteContribution(contribution: ContributionEntity) {
        viewModelScope.launch {
            repository.deleteContribution(contribution)
        }
    }

    fun deleteMemberAllContributions(memberName: String) {
        val pId = selectedPeriod.value?.id ?: return
        viewModelScope.launch {
            val list = rawContributions.value.filter {
                it.periodId == pId && it.memberName.trim().equals(memberName.trim(), ignoreCase = true)
            }
            list.forEach {
                repository.deleteContribution(it)
            }
        }
    }

    fun addExpense(purpose: String, amount: Double, date: Long, notes: String) {
        val pId = selectedPeriod.value?.id ?: return
        viewModelScope.launch {
            val expense = ExpenseEntity(
                periodId = pId,
                purpose = purpose,
                amount = amount,
                date = date,
                notes = notes
            )
            repository.insertExpense(expense)
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.updateExpense(expense)
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    // --- ENHANCED OFFLINE JSON BACKUP & RESTORE SYSTEM ---
    
    fun exportBackupToJson(): String {
        return try {
            val root = JSONObject()
            
            // Note: Since Flow values are reactive, we take the current snapshot values
            val periodsList = allPeriods.value
            val contributionsList = rawContributions.value
            val expensesList = rawExpenses.value

            val periodsArray = JSONArray()
            periodsList.forEach { p ->
                val pObj = JSONObject()
                pObj.put("id", p.id)
                pObj.put("name", p.name)
                pObj.put("startingBalance", p.startingBalance)
                pObj.put("isActive", p.isActive)
                periodsArray.put(pObj)
            }
            root.put("periods", periodsArray)

            val contributionsArray = JSONArray()
            contributionsList.forEach { c ->
                val cObj = JSONObject()
                cObj.put("id", c.id)
                cObj.put("periodId", c.periodId)
                cObj.put("memberName", c.memberName)
                cObj.put("amount", c.amount)
                cObj.put("date", c.date)
                cObj.put("isPaid", c.isPaid)
                cObj.put("notes", c.notes)
                contributionsArray.put(cObj)
            }
            root.put("contributions", contributionsArray)

            val expensesArray = JSONArray()
            expensesList.forEach { e ->
                val eObj = JSONObject()
                eObj.put("id", e.id)
                eObj.put("periodId", e.periodId)
                eObj.put("date", e.date)
                eObj.put("amount", e.amount)
                eObj.put("purpose", e.purpose)
                eObj.put("notes", e.notes)
                expensesArray.put(eObj)
            }
            root.put("expenses", expensesArray)

            root.toString(4) // Prettified backing
        } catch (e: Exception) {
            "Error exporting: ${e.localizedMessage}"
        }
    }

    fun importBackupFromJson(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            
            val periodsArray = root.optJSONArray("periods")
            val contributionsArray = root.optJSONArray("contributions")
            val expensesArray = root.optJSONArray("expenses")

            if (periodsArray == null) {
                backupRestoreStatus.value = "Failed: Invalid backup schema (missing periods)"
                return false
            }

            viewModelScope.launch {
                // To safely overwrite, we do local database insertion.
                // 1. Process periods
                for (i in 0 until periodsArray.length()) {
                    val pObj = periodsArray.getJSONObject(i)
                    val p = PeriodEntity(
                        id = pObj.optInt("id", 0),
                        name = pObj.getString("name"),
                        startingBalance = pObj.optDouble("startingBalance", 0.0),
                        isActive = pObj.optBoolean("isActive", false)
                    )
                    repository.insertPeriod(p)
                }

                // 2. Process contributions
                if (contributionsArray != null) {
                    for (i in 0 until contributionsArray.length()) {
                        val cObj = contributionsArray.getJSONObject(i)
                        val c = ContributionEntity(
                            id = cObj.optInt("id", 0),
                            periodId = cObj.getInt("periodId"),
                            memberName = cObj.getString("memberName"),
                            amount = cObj.getDouble("amount"),
                            date = cObj.getLong("date"),
                            isPaid = cObj.getBoolean("isPaid"),
                            notes = cObj.optString("notes", "")
                        )
                        repository.insertContribution(c)
                    }
                }

                // 3. Process expenses
                if (expensesArray != null) {
                    for (i in 0 until expensesArray.length()) {
                        val eObj = expensesArray.getJSONObject(i)
                        val e = ExpenseEntity(
                            id = eObj.optInt("id", 0),
                            periodId = eObj.getInt("periodId"),
                            date = eObj.getLong("date"),
                            amount = eObj.getDouble("amount"),
                            purpose = eObj.getString("purpose"),
                            notes = eObj.optString("notes", "")
                        )
                        repository.insertExpense(e)
                    }
                }

                backupRestoreStatus.value = "Success: Restored backup database records!"
            }
            true
        } catch (e: Exception) {
            backupRestoreStatus.value = "Restore Error: ${e.localizedMessage}"
            false
        }
    }

    fun clearBackupRestoreStatus() {
        backupRestoreStatus.value = null
    }
}

// Data holder representing current dynamic metrics of a cash ledger
data class DashboardMetrics(
    val startingBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val remainingBalance: Double = 0.0
)
