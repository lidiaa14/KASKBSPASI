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
            val options = FirebaseOptions.Builder()
                .setProjectId("uangkas-ef7cf")
                .setApplicationId("1:1037396381254:android:3a6fe42cc78be098760447")
                .setApiKey("AIzaSyA-mockApiKey1234567890abcdef")
                .build()
            try {
                FirebaseApp.initializeApp(context, options)
            } catch (ex: Exception) {
                // Already initialized or fallback
            }
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            e.printStackTrace()
            FirebaseFirestore.getInstance()
        }
    }

    // Dynamic Lists from Database (Powered directly by realtime Firestore)
    private val _periods = MutableStateFlow<List<Period>>(emptyList())
    val periods: StateFlow<List<Period>> = _periods.asStateFlow()

    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    private val _allTransactionsList = MutableStateFlow<List<Transaction>>(emptyList())
    val allTransactionsList: StateFlow<List<Transaction>> = _allTransactionsList.asStateFlow()
    
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

        // Setup realtime firestore snapshot listeners to be the true cloud source of truth
        try {
            firestore.collection("periods")
                .addSnapshotListener { snapshot, error ->
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val docIdLong = doc.id.toLongOrNull()
                                val id = (doc.get("id") as? Number)?.toLong() ?: docIdLong ?: (1..100000).random().toLong()
                                val name = doc.getString("name") ?: ""
                                val startingBalance = (doc.get("startingBalance") as? Number)?.toDouble() ?: (doc.get("starting_balance") as? Number)?.toDouble() ?: 0.0
                                val isActive = doc.getBoolean("isActive") ?: doc.getBoolean("is_active") ?: false
                                Period(id = id, name = name, startingBalance = startingBalance, isActive = isActive)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                null
                            }
                        }.sortedByDescending { it.id }
                        _periods.value = list
                    }
                }

            firestore.collection("members")
                .addSnapshotListener { snapshot, error ->
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val docIdLong = doc.id.toLongOrNull()
                                val id = (doc.get("id") as? Number)?.toLong() ?: docIdLong ?: (1..100000).random().toLong()
                                val name = doc.getString("name") ?: ""
                                val notes = doc.getString("notes") ?: ""
                                val isActive = doc.getBoolean("isActive") ?: doc.getBoolean("is_active") ?: true
                                Member(id = id, name = name, notes = notes, isActive = isActive)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                null
                            }
                        }.sortedBy { it.name.lowercase() }
                        _members.value = list
                    }
                }

            firestore.collection("transactions")
                .addSnapshotListener { snapshot, error ->
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val docIdLong = doc.id.toLongOrNull()
                                val id = (doc.get("id") as? Number)?.toLong() ?: docIdLong ?: (1..1000000).random().toLong()
                                val pId = (doc.get("periodId") as? Number)?.toLong() ?: (doc.get("period_id") as? Number)?.toLong() ?: 0L
                                val type = doc.getString("type") ?: ""
                                val category = doc.getString("category") ?: ""
                                val amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0
                                val description = doc.getString("description") ?: ""
                                val date = (doc.get("date") as? Number)?.toLong() ?: System.currentTimeMillis()
                                val mId = (doc.get("memberId") as? Number)?.toLong() ?: (doc.get("member_id") as? Number)?.toLong()
                                val mName = doc.getString("memberName") ?: doc.getString("member_name")
                                Transaction(
                                    id = id,
                                    periodId = pId,
                                    type = type,
                                    category = category,
                                    amount = amount,
                                    description = description,
                                    date = date,
                                    memberId = mId,
                                    memberName = mName
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                                null
                            }
                        }.sortedByDescending { it.date }
                        _allTransactionsList.value = list
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Reactively pull transactions when the selected period changes
        currentTransactions = combine(selectedPeriodId, allTransactionsList) { periodId, list ->
            if (periodId == null) {
                list
            } else {
                list.filter { it.periodId == periodId }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
            periods.filter { it.isNotEmpty() }.firstOrNull()?.let { list ->
                if (selectedPeriodId.value == null) {
                    val active = list.find { it.isActive }
                    if (active != null) {
                        selectedPeriodId.value = active.id
                    } else {
                        selectedPeriodId.value = list.first().id
                    }
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

        // Trigger spreadsheet seeder if it hasn't been run yet
        if (!sharedPrefs.getBoolean("spreadsheet_synced_2025_v2", false)) {
            seedSpreadsheetData()
        }
    }

    // Pull-to-refresh state
    val isRefreshing = MutableStateFlow(false)

    fun refreshData() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch {
            try {
                // Fetch and sync periods
                firestore.collection("periods").get().addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val list = task.result.documents.mapNotNull { doc ->
                            try {
                                val docIdLong = doc.id.toLongOrNull()
                                val id = (doc.get("id") as? Number)?.toLong() ?: docIdLong ?: (1..100000).random().toLong()
                                val name = doc.getString("name") ?: ""
                                val startingBalance = (doc.get("startingBalance") as? Number)?.toDouble() ?: (doc.get("starting_balance") as? Number)?.toDouble() ?: 0.0
                                val isActive = doc.getBoolean("isActive") ?: doc.getBoolean("is_active") ?: false
                                Period(id = id, name = name, startingBalance = startingBalance, isActive = isActive)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                null
                            }
                        }.sortedByDescending { it.id }
                        _periods.value = list
                        
                        viewModelScope.launch {
                            for (p in list) {
                                try { repository.insertPeriod(p) } catch (e: Exception) {}
                            }
                        }
                    }
                }

                // Fetch and sync members
                firestore.collection("members").get().addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val list = task.result.documents.mapNotNull { doc ->
                            try {
                                val docIdLong = doc.id.toLongOrNull()
                                val id = (doc.get("id") as? Number)?.toLong() ?: docIdLong ?: (1..100000).random().toLong()
                                val name = doc.getString("name") ?: ""
                                val notes = doc.getString("notes") ?: ""
                                val isActive = doc.getBoolean("isActive") ?: doc.getBoolean("is_active") ?: true
                                Member(id = id, name = name, notes = notes, isActive = isActive)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                null
                            }
                        }.sortedBy { it.name.lowercase() }
                        _members.value = list
                        
                        viewModelScope.launch {
                            for (m in list) {
                                try { repository.insertMember(m) } catch (e: Exception) {}
                            }
                        }
                    }
                }

                // Fetch and sync transactions
                firestore.collection("transactions").get().addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val list = task.result.documents.mapNotNull { doc ->
                            try {
                                val docIdLong = doc.id.toLongOrNull()
                                val id = (doc.get("id") as? Number)?.toLong() ?: docIdLong ?: (1..1000000).random().toLong()
                                val pId = (doc.get("periodId") as? Number)?.toLong() ?: (doc.get("period_id") as? Number)?.toLong() ?: 0L
                                val type = doc.getString("type") ?: ""
                                val category = doc.getString("category") ?: ""
                                val amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0
                                val description = doc.getString("description") ?: ""
                                val date = (doc.get("date") as? Number)?.toLong() ?: System.currentTimeMillis()
                                val mId = (doc.get("memberId") as? Number)?.toLong() ?: (doc.get("member_id") as? Number)?.toLong()
                                val mName = doc.getString("memberName") ?: doc.getString("member_name")
                                Transaction(
                                    id = id,
                                    periodId = pId,
                                    type = type,
                                    category = category,
                                    amount = amount,
                                    description = description,
                                    date = date,
                                    memberId = mId,
                                    memberName = mName
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                                null
                            }
                        }.sortedByDescending { it.date }
                        _allTransactionsList.value = list
                        
                        viewModelScope.launch {
                            for (t in list) {
                                try { repository.insertTransaction(t) } catch (e: Exception) {}
                            }
                        }
                    }
                }

                kotlinx.coroutines.delay(1200)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRefreshing.value = false
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

    // Period actions (Firestore writing source)
    fun addPeriod(name: String, startingBalance: Double) {
        viewModelScope.launch {
            val id = System.currentTimeMillis()
            val newPeriodMap = mapOf(
                "id" to id,
                "name" to name,
                "startingBalance" to startingBalance,
                "starting_balance" to startingBalance,
                "isActive" to true,
                "is_active" to true
            )
            // deactivate other periods first in Firestore
            try {
                _periods.value.forEach {
                    if (it.isActive) {
                        firestore.collection("periods").document(it.id.toString())
                            .update(mapOf("isActive" to false, "is_active" to false))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Re-sync Room repository as fallback
            try {
                repository.insertPeriod(Period(id = id, name = name, startingBalance = startingBalance, isActive = true))
                repository.selectActivePeriod(id)
            } catch (any: Exception) {}

            firestore.collection("periods").document(id.toString()).set(newPeriodMap)
            selectedPeriodId.value = id
        }
    }

    fun selectPeriod(periodId: Long?) {
        viewModelScope.launch {
            selectedPeriodId.value = periodId
            if (periodId != null) {
                try {
                    _periods.value.forEach {
                        val active = (it.id == periodId)
                        firestore.collection("periods").document(it.id.toString())
                            .update(mapOf("isActive" to active, "is_active" to active))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                try {
                    repository.selectActivePeriod(periodId)
                } catch (any: Exception) {}
            }
        }
    }

    fun deletePeriod(period: Period) {
        viewModelScope.launch {
            try {
                firestore.collection("periods").document(period.id.toString()).delete()
                // delete associated transactions in this period
                _allTransactionsList.value.filter { it.periodId == period.id }.forEach {
                    firestore.collection("transactions").document(it.id.toString()).delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            try {
                repository.deletePeriod(period)
            } catch (any: Exception) {}

            if (selectedPeriodId.value == period.id) {
                selectedPeriodId.value = null
            }
        }
    }

    // Member actions
    fun addMember(name: String, notes: String) {
        viewModelScope.launch {
            val id = System.currentTimeMillis()
            val newMemberMap = mapOf(
                "id" to id,
                "name" to name,
                "notes" to notes,
                "isActive" to true,
                "is_active" to true
            )
            
            try {
                repository.insertMember(Member(id = id, name = name, notes = notes))
            } catch (any: Exception) {}

            firestore.collection("members").document(id.toString()).set(newMemberMap)
        }
    }

    fun deleteMember(member: Member) {
        viewModelScope.launch {
            try {
                firestore.collection("members").document(member.id.toString()).delete()
                // delete associated transactions for this member
                _allTransactionsList.value.filter { it.memberId == member.id }.forEach {
                    firestore.collection("transactions").document(it.id.toString()).delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            try {
                repository.deleteMember(member)
            } catch (any: Exception) {}
        }
    }

    // Transaction actions
    fun addTransaction(type: String, category: String, amount: Double, description: String, memberId: Long?, memberName: String?) {
        viewModelScope.launch {
            val currentPeriodId = selectedPeriodId.value
            if (currentPeriodId != null) {
                val id = System.currentTimeMillis() + (1..1000).random()
                val newTxMap = mutableMapOf<String, Any?>(
                    "id" to id,
                    "periodId" to currentPeriodId,
                    "period_id" to currentPeriodId,
                    "type" to type,
                    "category" to category,
                    "amount" to amount,
                    "description" to description,
                    "date" to System.currentTimeMillis(),
                    "memberId" to memberId,
                    "member_id" to memberId,
                    "memberName" to memberName,
                    "member_name" to memberName
                )
                
                try {
                    repository.insertTransaction(
                        Transaction(
                            id = id,
                            periodId = currentPeriodId,
                            type = type,
                            category = category,
                            amount = amount,
                            description = description,
                            memberId = memberId,
                            memberName = memberName
                        )
                    )
                } catch (any: Exception) {}

                firestore.collection("transactions").document(id.toString()).set(newTxMap)
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                firestore.collection("transactions").document(transaction.id.toString()).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            try {
                repository.deleteTransaction(transaction)
            } catch (any: Exception) {}
        }
    }

    fun togglePeriodPayment(member: Member, period: Period) {
        viewModelScope.launch {
            val txs = allTransactionsList.value
            val matching = txs.filter { it.memberId == member.id && it.periodId == period.id && it.type == "INCOME" }
            if (matching.isNotEmpty()) {
                matching.forEach { deleteTransaction(it) }
            } else {
                val id = System.currentTimeMillis() + (1..1000).random()
                val newTxMap = mapOf(
                    "id" to id,
                    "periodId" to period.id,
                    "period_id" to period.id,
                    "type" to "INCOME",
                    "category" to "Iuran Bulanan",
                    "amount" to 10000.0,
                    "description" to "Iuran ${member.name} - ${period.name}",
                    "date" to System.currentTimeMillis(),
                    "memberId" to member.id,
                    "member_id" to member.id,
                    "memberName" to member.name,
                    "member_name" to member.name
                )
                
                try {
                    repository.insertTransaction(
                        Transaction(
                            id = id,
                            periodId = period.id,
                            type = "INCOME",
                            category = "Iuran Bulanan",
                            amount = 10000.0,
                            description = "Iuran ${member.name} - ${period.name}",
                            memberId = member.id,
                            memberName = member.name
                        )
                    )
                } catch (any: Exception) {}

                firestore.collection("transactions").document(id.toString()).set(newTxMap)
            }
        }
    }

    fun seedSpreadsheetData() {
        viewModelScope.launch {
            try {
                // Delete existing collections to ensure we replace everything cleanly
                firestore.collection("periods").get().addOnSuccessListener { qs ->
                    qs.documents.forEach { doc -> doc.reference.delete() }
                    
                    // After deleting, insert new academic periods for 2025/2026
                    val periodsToSeed = listOf(
                        Period(id = 1, name = "JANUARY 2025/2026", startingBalance = 0.0, isActive = true),
                        Period(id = 2, name = "FEB 2025/2026", startingBalance = 0.0, isActive = true),
                        Period(id = 3, name = "MARET 2025/2026", startingBalance = 0.0, isActive = true),
                        Period(id = 4, name = "APRIL 2025/2026", startingBalance = 0.0, isActive = true),
                        Period(id = 5, name = "MEI 2025/2026", startingBalance = 0.0, isActive = true),
                        Period(id = 6, name = "JUNI 2025/2026", startingBalance = 0.0, isActive = true),
                        Period(id = 7, name = "JULI 2025/2026", startingBalance = 0.0, isActive = true),
                        Period(id = 8, name = "AGUST 2025/2026", startingBalance = 0.0, isActive = true)
                    )
                    for (p in periodsToSeed) {
                        val pMap = mapOf(
                            "id" to p.id,
                            "name" to p.name,
                            "startingBalance" to p.startingBalance,
                            "isActive" to p.isActive
                        )
                        firestore.collection("periods").document(p.id.toString()).set(pMap)
                        viewModelScope.launch {
                            try {
                                repository.insertPeriod(p)
                            } catch (e: Exception) {}
                        }
                    }
                }

                firestore.collection("members").get().addOnSuccessListener { qs ->
                    qs.documents.forEach { doc -> doc.reference.delete() }
                    
                    val membersToSeed = listOf(
                        Member(id = 1, name = "BG TANTO", notes = "", isActive = true),
                        Member(id = 2, name = "BG NANANG", notes = "", isActive = true),
                        Member(id = 3, name = "BG YUDI", notes = "", isActive = true),
                        Member(id = 4, name = "BG TEMIM", notes = "", isActive = true),
                        Member(id = 5, name = "BG EPI", notes = "", isActive = true),
                        Member(id = 6, name = "BG RELLY", notes = "", isActive = true),
                        Member(id = 7, name = "BG JAKA", notes = "", isActive = true),
                        Member(id = 8, name = "BG BUDI", notes = "", isActive = true),
                        Member(id = 9, name = "BG COKI", notes = "", isActive = true),
                        Member(id = 10, name = "BG SAMSURI", notes = "", isActive = true),
                        Member(id = 11, name = "BG ROHMAN", notes = "", isActive = true),
                        Member(id = 12, name = "BG HERU", notes = "", isActive = true),
                        Member(id = 13, name = "BG TONY", notes = "", isActive = true)
                    )
                    for (m in membersToSeed) {
                        val mMap = mapOf(
                            "id" to m.id,
                            "name" to m.name,
                            "notes" to m.notes,
                            "isActive" to m.isActive
                        )
                        firestore.collection("members").document(m.id.toString()).set(mMap)
                        viewModelScope.launch {
                            try {
                                repository.insertMember(m)
                            } catch (e: Exception) {}
                        }
                    }
                }

                firestore.collection("transactions").get().addOnSuccessListener { qs ->
                    qs.documents.forEach { doc -> doc.reference.delete() }
                    
                    val payments = listOf(
                        Triple(1L, 1L, "BG TANTO"), Triple(1L, 2L, "BG TANTO"), Triple(1L, 3L, "BG TANTO"),
                        Triple(1L, 4L, "BG TANTO"), Triple(1L, 5L, "BG TANTO"), Triple(1L, 6L, "BG TANTO"),
                        Triple(2L, 1L, "BG NANANG"), Triple(2L, 2L, "BG NANANG"),
                        Triple(3L, 1L, "BG YUDI"), Triple(3L, 2L, "BG YUDI"), Triple(3L, 3L, "BG YUDI"),
                        Triple(4L, 1L, "BG TEMIM"), Triple(4L, 2L, "BG TEMIM"),
                        Triple(5L, 1L, "BG EPI"), Triple(5L, 2L, "BG EPI"),
                        Triple(7L, 1L, "BG JAKA"), Triple(7L, 2L, "BG JAKA"), Triple(7L, 3L, "BG JAKA"),
                        Triple(8L, 1L, "BG BUDI"), Triple(8L, 2L, "BG BUDI"),
                        Triple(9L, 1L, "BG COKI"), Triple(9L, 2L, "BG COKI"), Triple(9L, 3L, "BG COKI"),
                        Triple(10L, 1L, "BG SAMSURI"), Triple(10L, 2L, "BG SAMSURI"), Triple(10L, 3L, "BG SAMSURI"), Triple(10L, 4L, "BG SAMSURI"),
                        Triple(12L, 4L, "BG HERU"), Triple(12L, 5L, "BG HERU"),
                        Triple(13L, 1L, "BG TONY"), Triple(13L, 2L, "BG TONY"), Triple(13L, 3L, "BG TONY"), Triple(13L, 4L, "BG TONY")
                    )

                    for (p in payments) {
                        val txId = 1000 + p.first * 100 + p.second
                        val pName = when(p.second) {
                            1L -> "JANUARY 2025/2026"
                            2L -> "FEB 2025/2026"
                            3L -> "MARET 2025/2026"
                            4L -> "APRIL 2025/2026"
                            5L -> "MEI 2025/2026"
                            6L -> "JUNI 2025/2026"
                            7L -> "JULI 2025/2026"
                            8L -> "AGUST 2025/2026"
                            else -> ""
                        }
                        val txMap = mapOf(
                            "id" to txId,
                            "periodId" to p.second,
                            "period_id" to p.second,
                            "type" to "INCOME",
                            "category" to "Iuran Bulanan",
                            "amount" to 10000.0,
                            "description" to "Iuran ${p.third} - $pName",
                            "date" to System.currentTimeMillis() - (10 - p.second) * 86400000L,
                            "memberId" to p.first,
                            "member_id" to p.first,
                            "memberName" to p.third,
                            "member_name" to p.third
                        )
                        firestore.collection("transactions").document(txId.toString()).set(txMap)
                        viewModelScope.launch {
                            try {
                                repository.insertTransaction(
                                    Transaction(
                                        id = txId,
                                        periodId = p.second,
                                        type = "INCOME",
                                        category = "Iuran Bulanan",
                                        amount = 10000.0,
                                        description = "Iuran ${p.third} - $pName",
                                        memberId = p.first,
                                        memberName = p.third
                                    )
                                )
                            } catch (e: Exception) {}
                        }
                    }

                    // Add starting balance income
                    val startTxMap = mapOf(
                        "id" to 9999L,
                        "periodId" to 1L,
                        "period_id" to 1L,
                        "type" to "INCOME",
                        "category" to "Saldo Awal",
                        "amount" to 755000.0,
                        "description" to "Saldo Awal Kas KB SPASI",
                        "date" to System.currentTimeMillis() - 12 * 86400000L,
                        "memberId" to null,
                        "member_id" to null,
                        "memberName" to null,
                        "member_name" to null
                    )
                    firestore.collection("transactions").document("9999").set(startTxMap)
                    viewModelScope.launch {
                        try {
                            repository.insertTransaction(
                                Transaction(
                                    id = 9999L,
                                    periodId = 1L,
                                    type = "INCOME",
                                    category = "Saldo Awal",
                                    amount = 755000.0,
                                    description = "Saldo Awal Kas KB SPASI"
                                )
                            )
                        } catch (e: Exception) {}
                    }

                    // Add expense transaction
                    val expTxMap = mapOf(
                        "id" to 8888L,
                        "periodId" to 1L,
                        "period_id" to 1L,
                        "type" to "EXPENSE",
                        "category" to "Pengeluaran",
                        "amount" to 573000.0,
                        "description" to "Pengeluaran Kas Belanja & Operasional",
                        "date" to System.currentTimeMillis() - 8 * 86400000L,
                        "memberId" to null,
                        "member_id" to null,
                        "memberName" to null,
                        "member_name" to null
                    )
                    firestore.collection("transactions").document("8888").set(expTxMap)
                    viewModelScope.launch {
                        try {
                            repository.insertTransaction(
                                Transaction(
                                    id = 8888L,
                                    periodId = 1L,
                                    type = "EXPENSE",
                                    category = "Pengeluaran",
                                    amount = 573000.0,
                                    description = "Pengeluaran Kas Belanja & Operasional"
                                )
                            )
                        } catch (e: Exception) {}
                    }
                }

                sharedPrefs.edit().putBoolean("spreadsheet_synced_2025_v2", true).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Database Reset
    fun clearAllData() {
        viewModelScope.launch {
            try {
                firestore.collection("periods").get().addOnSuccessListener { snapshot ->
                    snapshot.documents.forEach { it.reference.delete() }
                }
                firestore.collection("members").get().addOnSuccessListener { snapshot ->
                    snapshot.documents.forEach { it.reference.delete() }
                }
                firestore.collection("transactions").get().addOnSuccessListener { snapshot ->
                    snapshot.documents.forEach { it.reference.delete() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            try {
                repository.clearAllData()
            } catch (any: Exception) {}

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
