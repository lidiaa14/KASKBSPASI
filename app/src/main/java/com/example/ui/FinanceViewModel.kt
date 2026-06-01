package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    private val sharedPrefs = application.getSharedPreferences("kb_spasi_prefs", Context.MODE_PRIVATE)

    // Dark mode state - IMPORTANT: defaults to false (Light Mode)!
    val isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("dark_mode", false))

    // Language state - default is Indonesian ("id")
    val appLanguage = MutableStateFlow(sharedPrefs.getString("app_language", "id") ?: "id")

    // Admin mode authorization state
    val isAdminMode = MutableStateFlow(false)

    // Dynamic Admin password via Firestore state (default: "1234")
    val adminPasswordState = MutableStateFlow("1234")

    private val firestore: FirebaseFirestore by lazy {
        val context = getApplication<Application>().applicationContext
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            try {
                val options = FirebaseOptions.Builder()
                    .setProjectId("kb-spasi-finance")
                    .setApplicationId("com.example")
                    .setApiKey("AIzaSyA-mockApiKey1234567890abcdef")
                    .build()
                FirebaseApp.initializeApp(context, options)
                FirebaseFirestore.getInstance()
            } catch (ex: Exception) {
                try {
                    FirebaseApp.initializeApp(context)
                } catch (any: Exception) {}
                FirebaseFirestore.getInstance()
            }
        }
    }

    // Dynamic Lists from Database
    val periods: StateFlow<List<Period>>
    val members: StateFlow<List<Member>>
    val allTransactionsList: StateFlow<List<Transaction>>
    
    // Currently selected Period ID (null means "All Time")
    val selectedPeriodId = MutableStateFlow<Long?>(null)
    
    // Transactions inside the selected Period
    val currentTransactions: StateFlow<List<Transaction>>

    // Calculated metrics of the active/selected Period
    val metrics: StateFlow<FinanceMetrics>

    // Search query
    val searchQuery = MutableStateFlow("")

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FinanceRepository(database.financeDao())

        periods = repository.allPeriods
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        members = repository.allMembers
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        allTransactionsList = repository.allTransactions
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        // Reactively pull transactions when the selected period changes
        currentTransactions = selectedPeriodId
            .flatMapLatest { periodId ->
                if (periodId == null) {
                    repository.allTransactions
                } else {
                    repository.getTransactionsByPeriod(periodId)
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        // Reactively calculate metrics when selected period OR transactions list changes
        metrics = combine(selectedPeriodId, periods, currentTransactions) { periodId, periodList, transactions ->
            val matchingPeriod = periodList.find { it.id == periodId }
            val startBalance = matchingPeriod?.startingBalance ?: 0.0
            
            var inc = 0.0
            var exp = 0.0
            transactions.forEach {
                if (it.type == "INCOME") {
                     inc += it.amount
                } else {
                     exp += it.amount
                }
            }
            
            FinanceMetrics(
                startingBalance = startBalance,
                totalIncome = inc,
                totalExpenses = exp,
                currentBalance = startBalance + inc - exp
            )
        }.stateIn(viewModelScope, SharingStarted.Lazily, FinanceMetrics())

        // Autoload the active period on launch
        viewModelScope.launch {
            val active = repository.getActivePeriod()
            if (active != null) {
                selectedPeriodId.value = active.id
            } else {
                // Wait and select the first available if no active flag found
                periods.firstOrNull()?.firstOrNull()?.let {
                    selectedPeriodId.value = it.id
                }
            }
        }

        // Live snapshot listener to sync the password across all devices instantly
        viewModelScope.launch {
            try {
                firestore.collection("settings").document("admin")
                    .addSnapshotListener { snapshot, error ->
                        if (snapshot != null && snapshot.exists()) {
                            val dbPass = snapshot.getString("password")
                            if (!dbPass.isNullOrEmpty()) {
                                adminPasswordState.value = dbPass
                            }
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Theme toggles
    fun toggleDarkMode() {
        val nextVal = !isDarkMode.value
        isDarkMode.value = nextVal
        sharedPrefs.edit().putBoolean("dark_mode", nextVal).apply()
    }

    // Language Toggles
    fun setLanguage(lang: String) {
        appLanguage.value = lang
        sharedPrefs.edit().putString("app_language", lang).apply()
    }

    // Admin authorisation
    fun loginAdmin(pass: String): Boolean {
        return if (pass == adminPasswordState.value) {
            isAdminMode.value = true
            true
        } else {
            false
        }
    }

    fun changeAdminPassword(currentPass: String, newPass: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (currentPass != adminPasswordState.value) {
            onFailure("Sandi saat ini salah!")
            return
        }
        if (newPass.length < 4) {
            onFailure("Sandi baru minimal 4 karakter!")
            return
        }
        viewModelScope.launch {
            try {
                firestore.collection("settings").document("admin")
                    .set(mapOf("password" to newPass))
                    .addOnSuccessListener {
                        adminPasswordState.value = newPass
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        onFailure(e.localizedMessage ?: "Gagal memperbarui sandi ke Firestore")
                    }
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "Terjadi kesalahan koneksi")
            }
        }
    }

    fun logoutAdmin() {
        isAdminMode.value = false
    }

    // Period actions
    fun addPeriod(name: String, startingBalance: Double) {
        viewModelScope.launch {
            val newPeriod = Period(name = name, startingBalance = startingBalance, isActive = true)
            val insertedId = repository.insertPeriod(newPeriod)
            // Make it the active and selected one
            repository.selectActivePeriod(insertedId)
            selectedPeriodId.value = insertedId
        }
    }

    fun selectPeriod(periodId: Long?) {
        viewModelScope.launch {
            selectedPeriodId.value = periodId
            if (periodId != null) {
                repository.selectActivePeriod(periodId)
            }
        }
    }

    fun deletePeriod(period: Period) {
        viewModelScope.launch {
            repository.deletePeriod(period)
            if (selectedPeriodId.value == period.id) {
                selectedPeriodId.value = null
            }
        }
    }

    // Member actions
    fun addMember(name: String, notes: String) {
        viewModelScope.launch {
            repository.insertMember(Member(name = name, notes = notes))
        }
    }

    fun deleteMember(member: Member) {
        viewModelScope.launch {
            repository.deleteMember(member)
        }
    }

    // Transaction actions
    fun addTransaction(type: String, category: String, amount: Double, description: String, memberId: Long?, memberName: String?) {
        viewModelScope.launch {
            val currentPeriodId = selectedPeriodId.value
            if (currentPeriodId != null) {
                repository.insertTransaction(
                    Transaction(
                        periodId = currentPeriodId,
                        type = type,
                        category = category,
                        amount = amount,
                        description = description,
                        memberId = memberId,
                        memberName = memberName
                    )
                )
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun togglePeriodPayment(member: Member, period: Period) {
        viewModelScope.launch {
            val txs = allTransactionsList.value
            val matching = txs.filter { it.memberId == member.id && it.periodId == period.id && it.type == "INCOME" }
            if (matching.isNotEmpty()) {
                matching.forEach { repository.deleteTransaction(it) }
            } else {
                repository.insertTransaction(
                    Transaction(
                        periodId = period.id,
                        type = "INCOME",
                        category = "Iuran Bulanan",
                        amount = 50000.0,
                        description = "Iuran ${member.name} - ${period.name}",
                        memberId = member.id,
                        memberName = member.name
                    )
                )
            }
        }
    }

    // Database Reset
    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            selectedPeriodId.value = null
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
                return FinanceViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

data class FinanceMetrics(
    val startingBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val currentBalance: Double = 0.0
)
