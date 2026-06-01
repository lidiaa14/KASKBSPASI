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
import com.google.firebase.firestore.ListenerRegistration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

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

    private var listenerErrorRetryCount = 0

    private val firestore: FirebaseFirestore? by lazy {
        val context = getApplication<Application>().applicationContext
        try {
            val app = if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId("uangkas-ef7cf")
                    .setApplicationId("1:1037396381254:android:3a6fe42cc78be098760447")
                    .setApiKey("AIzaSyBLzzewLA-WuzkcWDv7R0Yqz0AMIUjqqJg")
                    .build()
                FirebaseApp.initializeApp(context, options)
            } else {
                FirebaseApp.getInstance()
            }
            FirebaseFirestore.getInstance(app)
        } catch (e: Exception) {
            android.util.Log.e("FinanceViewModel", "Firebase initialization failed, utilizing default fallback", e)
            try {
                FirebaseFirestore.getInstance()
            } catch (ex: Exception) {
                android.util.Log.e("FinanceViewModel", "Firestore fallback also failed and is unavailable", ex)
                null
            }
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

    // Sync / Loading States
    val isPeriodsLoading = MutableStateFlow(true)
    val isMembersLoading = MutableStateFlow(true)
    val isTransactionsLoading = MutableStateFlow(true)
    var isSeeding = false

    enum class ConnectionState {
        CONNECTED,
        SYNCING,
        RECONNECTING,
        OFFLINE
    }

    val connectionState = MutableStateFlow(ConnectionState.CONNECTED)
    private val _networkConnected = MutableStateFlow(true)
    val networkConnected: StateFlow<Boolean> = _networkConnected.asStateFlow()

    private var periodsListener: ListenerRegistration? = null
    private var membersListener: ListenerRegistration? = null
    private var transactionsListener: ListenerRegistration? = null
    private var passwordListener: ListenerRegistration? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var reconnectJob: Job? = null

    val isDataLoading = combine(
        combine(isPeriodsLoading, isMembersLoading, isTransactionsLoading) { p, m, t -> p || m || t },
        networkConnected,
        _periods,
        _allTransactionsList
    ) { anyLoading, isOnline, pList, tList ->
        if (isOnline) {
            anyLoading
        } else {
            if (pList.isNotEmpty() || tList.isNotEmpty()) {
                false
            } else {
                anyLoading
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, true)

    private fun safeDouble(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun safeLong(value: Any?): Long? {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun stableStringHashToLong(docId: String): Long {
        docId.toLongOrNull()?.let { return it }
        var hash = 1125899906842597L
        for (char in docId) {
            hash = 31L * hash + char.code
        }
        val absHash = java.lang.Math.abs(hash)
        return if (absHash <= 0L) 1L else absHash
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FinanceRepository(database.financeDao())

        // 1. Set up active and reactive collections from Room local database
        viewModelScope.launch {
            try {
                repository.allPeriods.collect { list ->
                    if (list.isNotEmpty()) {
                        _periods.value = list
                        
                        // Select active period immediately if list has active period
                        if (selectedPeriodId.value == null) {
                            val active = list.find { it.isActive }
                            if (active != null) {
                                selectedPeriodId.value = active.id
                            } else {
                                selectedPeriodId.value = list.firstOrNull()?.id
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        viewModelScope.launch {
            try {
                repository.allMembers.collect { list ->
                    if (list.isNotEmpty()) {
                        _members.value = list
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        viewModelScope.launch {
            try {
                repository.allTransactions.collect { list ->
                    if (list.isNotEmpty()) {
                        _allTransactionsList.value = list
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

        // Autoload the active period on launch if selectedPeriodId is still null
        viewModelScope.launch {
            periods.filter { it.isNotEmpty() }.firstOrNull()?.let { list ->
                if (selectedPeriodId.value == null) {
                    val active = list.find { it.isActive }
                    if (active != null) {
                        selectedPeriodId.value = active.id
                    } else {
                        selectedPeriodId.value = list.firstOrNull()?.id
                    }
                }
            }
        }

        // Trigger spreadsheet seeder if it hasn't been run yet
        if (!sharedPrefs.getBoolean("spreadsheet_synced_2025_v2", false)) {
            seedSpreadsheetData()
        }

        // Setup connectivity monitor & register realtime cloud snapshot listeners as the true source of truth
        monitorNetwork()
        setupRealtimeListeners()
    }

    private fun monitorNetwork() {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager != null) {
            val builder = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            
            // Set initial state
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val isInitialConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            
            _networkConnected.value = isInitialConnected
            connectionState.value = if (isInitialConnected) ConnectionState.SYNCING else ConnectionState.OFFLINE
            
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    viewModelScope.launch {
                        val wasOffline = !_networkConnected.value
                        _networkConnected.value = true
                        if (wasOffline) {
                            connectionState.value = ConnectionState.RECONNECTING
                            delay(1000)
                            setupRealtimeListeners()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    viewModelScope.launch {
                        _networkConnected.value = false
                        connectionState.value = ConnectionState.OFFLINE
                    }
                }
            }
            networkCallback = callback
            try {
                connectivityManager.registerNetworkCallback(builder.build(), callback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleListenerError(error: Exception?) {
        error?.printStackTrace()
        listenerErrorRetryCount++
        android.util.Log.e("FinanceViewModel", "handleListenerError called. Retry count: $listenerErrorRetryCount", error)
        
        if (listenerErrorRetryCount > 3) {
            android.util.Log.e("FinanceViewModel", "Firestore listeners failing/unauthorized repeatedly. Backing off to prevent crash loop.")
            connectionState.value = ConnectionState.OFFLINE
            isPeriodsLoading.value = false
            isMembersLoading.value = false
            isTransactionsLoading.value = false
            return
        }

        if (_networkConnected.value) {
            connectionState.value = ConnectionState.RECONNECTING
            // Debounced reconnection attempt
            reconnectJob?.cancel()
            reconnectJob = viewModelScope.launch {
                delay(10000)
                if (_networkConnected.value) {
                    setupRealtimeListeners()
                }
            }
        } else {
            connectionState.value = ConnectionState.OFFLINE
        }
    }

    private fun clearRealtimeListeners() {
        try {
            periodsListener?.remove()
            membersListener?.remove()
            transactionsListener?.remove()
            passwordListener?.remove()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        periodsListener = null
        membersListener = null
        transactionsListener = null
        passwordListener = null
    }

    fun setupRealtimeListeners() {
        clearRealtimeListeners()
        
        if (!_networkConnected.value) {
            connectionState.value = ConnectionState.OFFLINE
        } else {
            connectionState.value = ConnectionState.SYNCING
        }

        val fs = firestore
        if (fs == null) {
            android.util.Log.w("FinanceViewModel", "Firestore is currently unavailable. Operating in persistent Room offline cache mode.")
            isPeriodsLoading.value = false
            isMembersLoading.value = false
            isTransactionsLoading.value = false
            connectionState.value = ConnectionState.OFFLINE
            return
        }

        try {
            android.util.Log.d("FinanceViewModel", "Setting up Firestore realtime listeners...")
            periodsListener = fs.collection("payments")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("FinanceViewModel", "Firestore payments listener error (connection failure/rules)", error)
                        isPeriodsLoading.value = false
                        checkSyncStatus()
                        handleListenerError(error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        listenerErrorRetryCount = 0
                        if (snapshot.isEmpty) {
                            android.util.Log.d("FinanceViewModel", "Firestore payments: Snapshot received, but it is empty!")
                        } else {
                            android.util.Log.d("FinanceViewModel", "Firestore payments: Fetched successfully with ${snapshot.size()} documents")
                        }
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val docIdLong = doc.id.toLongOrNull()
                                val id = safeLong(doc.get("id")) ?: docIdLong ?: stableStringHashToLong(doc.id)
                                val name = doc.getString("name") ?: ""
                                val startingBalance = safeDouble(doc.get("startingBalance") ?: doc.get("starting_balance"))
                                val isActive = when (val activeVal = doc.get("isActive") ?: doc.get("is_active")) {
                                    is Boolean -> activeVal
                                    is String -> activeVal.toBoolean()
                                    is Number -> activeVal.toInt() != 0
                                    else -> false
                                }
                                Period(id = id, name = name, startingBalance = startingBalance, isActive = isActive)
                            } catch (e: Exception) {
                                android.util.Log.e("FinanceViewModel", "Error parsing payment/period doc: ${doc.id}", e)
                                null
                            }
                        }.sortedByDescending { it.id }

                        if (list.isEmpty() && (isSeeding || connectionState.value == ConnectionState.OFFLINE || connectionState.value == ConnectionState.RECONNECTING)) {
                            isPeriodsLoading.value = false
                            checkSyncStatus()
                            return@addSnapshotListener
                        }
                        
                        if (list.isNotEmpty()) {
                            _periods.value = list
                        }
                        checkSyncStatus()

                        viewModelScope.launch {
                            try {
                                for (p in list) {
                                    repository.insertPeriod(p)
                                }
                                val localPeriods = repository.allPeriods.firstOrNull() ?: emptyList()
                                if (list.isNotEmpty()) {
                                    for (localP in localPeriods) {
                                        if (list.none { it.id == localP.id }) {
                                            repository.deletePeriod(localP)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FinanceViewModel", "Error syncing periods to Room cache", e)
                            }
                        }
                    } else {
                        android.util.Log.d("FinanceViewModel", "Firestore payments: Snapshot received was null")
                    }
                    isPeriodsLoading.value = false
                }

            membersListener = fs.collection("members")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("FinanceViewModel", "Firestore members listener error (connection failure/rules)", error)
                        isMembersLoading.value = false
                        checkSyncStatus()
                        handleListenerError(error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        listenerErrorRetryCount = 0
                        if (snapshot.isEmpty) {
                            android.util.Log.d("FinanceViewModel", "Firestore members: Snapshot received, but it is empty!")
                        } else {
                            android.util.Log.d("FinanceViewModel", "Firestore members: Fetched successfully with ${snapshot.size()} documents")
                        }
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val docIdLong = doc.id.toLongOrNull()
                                val id = safeLong(doc.get("id")) ?: docIdLong ?: stableStringHashToLong(doc.id)
                                val name = doc.getString("name") ?: ""
                                val notes = doc.getString("notes") ?: ""
                                val isActive = when (val activeVal = doc.get("isActive") ?: doc.get("is_active")) {
                                    is Boolean -> activeVal
                                    is String -> activeVal.toBoolean()
                                    is Number -> activeVal.toInt() != 0
                                    else -> true
                                }
                                Member(id = id, name = name, notes = notes, isActive = isActive)
                            } catch (e: Exception) {
                                android.util.Log.e("FinanceViewModel", "Error parsing member doc: ${doc.id}", e)
                                null
                            }
                        }.sortedBy { it.name.lowercase() }

                        if (list.isEmpty() && (isSeeding || connectionState.value == ConnectionState.OFFLINE || connectionState.value == ConnectionState.RECONNECTING)) {
                            isMembersLoading.value = false
                            checkSyncStatus()
                            return@addSnapshotListener
                        }

                        if (list.isNotEmpty()) {
                            _members.value = list
                        }
                        checkSyncStatus()

                        viewModelScope.launch {
                            try {
                                for (m in list) {
                                    repository.insertMember(m)
                                }
                                val localMembers = repository.allMembers.firstOrNull() ?: emptyList()
                                if (list.isNotEmpty()) {
                                    for (localM in localMembers) {
                                        if (list.none { it.id == localM.id }) {
                                            repository.deleteMember(localM)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FinanceViewModel", "Error syncing members to Room cache", e)
                            }
                        }
                    } else {
                        android.util.Log.d("FinanceViewModel", "Firestore members: Snapshot received was null")
                    }
                    isMembersLoading.value = false
                }

            transactionsListener = fs.collection("transactions")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("FinanceViewModel", "Firestore transactions listener error (connection failure/rules)", error)
                        isTransactionsLoading.value = false
                        checkSyncStatus()
                        handleListenerError(error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        listenerErrorRetryCount = 0
                        if (snapshot.isEmpty) {
                            android.util.Log.d("FinanceViewModel", "Firestore transactions: Snapshot received, but it is empty!")
                        } else {
                            android.util.Log.d("FinanceViewModel", "Firestore transactions: Fetched successfully with ${snapshot.size()} documents")
                        }
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val docIdLong = doc.id.toLongOrNull()
                                val id = safeLong(doc.get("id")) ?: docIdLong ?: stableStringHashToLong(doc.id)
                                val pId = safeLong(doc.get("periodId") ?: doc.get("period_id")) ?: 0L
                                val type = doc.getString("type") ?: ""
                                val category = doc.getString("category") ?: ""
                                val amount = safeDouble(doc.get("amount"))
                                val description = doc.getString("description") ?: ""
                                val date = safeLong(doc.get("date")) ?: System.currentTimeMillis()
                                val mId = safeLong(doc.get("memberId") ?: doc.get("member_id"))
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
                                android.util.Log.e("FinanceViewModel", "Error parsing transaction doc: ${doc.id}", e)
                                null
                            }
                        }.sortedByDescending { it.date }

                        if (list.isEmpty() && (isSeeding || connectionState.value == ConnectionState.OFFLINE || connectionState.value == ConnectionState.RECONNECTING)) {
                            isTransactionsLoading.value = false
                            checkSyncStatus()
                            return@addSnapshotListener
                        }

                        if (list.isNotEmpty()) {
                            _allTransactionsList.value = list
                        }
                        checkSyncStatus()

                        viewModelScope.launch {
                            try {
                                for (t in list) {
                                    repository.insertTransaction(t)
                                }
                                val localTxs = repository.allTransactions.firstOrNull() ?: emptyList()
                                if (list.isNotEmpty()) {
                                    for (localT in localTxs) {
                                        if (list.none { it.id == localT.id }) {
                                            repository.deleteTransaction(localT)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FinanceViewModel", "Error syncing transactions to Room cache", e)
                            }
                        }
                    } else {
                        android.util.Log.d("FinanceViewModel", "Firestore transactions: Snapshot received was null")
                    }
                    isTransactionsLoading.value = false
                }

            passwordListener = fs.collection("settings").document("admin")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("FinanceViewModel", "Firestore settings/admin error", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        android.util.Log.d("FinanceViewModel", "Firestore settings/admin fetched successfully")
                        val dbPass = snapshot.getString("password")
                        if (!dbPass.isNullOrEmpty()) {
                            adminPasswordState.value = dbPass
                        }
                    } else {
                        android.util.Log.d("FinanceViewModel", "Firestore settings/admin: Document doesn't exist or is null")
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("FinanceViewModel", "Error while setting up realtime listeners", e)
            handleListenerError(e)
            isPeriodsLoading.value = false
            isMembersLoading.value = false
            isTransactionsLoading.value = false
        }
    }

    private fun checkSyncStatus() {
        if (!isPeriodsLoading.value && !isMembersLoading.value && !isTransactionsLoading.value) {
            if (_networkConnected.value) {
                connectionState.value = ConnectionState.CONNECTED
            } else {
                connectionState.value = ConnectionState.OFFLINE
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        clearRealtimeListeners()
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Pull-to-refresh state
    val isRefreshing = MutableStateFlow(false)

    fun refreshData() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        listenerErrorRetryCount = 0 // Reset error backoff count to force-revive connection
        android.util.Log.d("FinanceViewModel", "Manually refreshing data from Firestore...")
        viewModelScope.launch {
            try {
                setupRealtimeListeners() // Re-establish streaming listeners
                val fs = firestore
                if (fs == null) {
                    delay(500)
                    isRefreshing.value = false
                    return@launch
                }
                // Fetch and sync periods
                fs.collection("payments").get().addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val snapshot = task.result
                        if (snapshot.isEmpty) {
                            android.util.Log.d("FinanceViewModel", "Refresh payments successfully fetched but snapshot is empty")
                        } else {
                            android.util.Log.d("FinanceViewModel", "Refresh payments successfully fetched with ${snapshot.size()} documents")
                        }
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val docIdLong = doc.id.toLongOrNull()
                                val id = (doc.get("id") as? Number)?.toLong() ?: docIdLong ?: stableStringHashToLong(doc.id)
                                val name = doc.getString("name") ?: ""
                                val startingBalance = (doc.get("startingBalance") as? Number)?.toDouble() ?: (doc.get("starting_balance") as? Number)?.toDouble() ?: 0.0
                                val isActive = doc.getBoolean("isActive") ?: doc.getBoolean("is_active") ?: false
                                Period(id = id, name = name, startingBalance = startingBalance, isActive = isActive)
                            } catch (e: Exception) {
                                android.util.Log.e("FinanceViewModel", "Refresh: error parsing payments doc ${doc.id}", e)
                                null
                            }
                        }.sortedByDescending { it.id }
                        _periods.value = list
                        
                        viewModelScope.launch {
                            for (p in list) {
                                try { repository.insertPeriod(p) } catch (e: Exception) {}
                            }
                        }
                    } else {
                        android.util.Log.e("FinanceViewModel", "Refresh payments failed to fetch", task.exception)
                    }
                }

                // Fetch and sync members
                fs.collection("members").get().addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val snapshot = task.result
                        if (snapshot.isEmpty) {
                            android.util.Log.d("FinanceViewModel", "Refresh members successfully fetched but snapshot is empty")
                        } else {
                            android.util.Log.d("FinanceViewModel", "Refresh members successfully fetched with ${snapshot.size()} documents")
                        }
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val docIdLong = doc.id.toLongOrNull()
                                val id = (doc.get("id") as? Number)?.toLong() ?: docIdLong ?: stableStringHashToLong(doc.id)
                                val name = doc.getString("name") ?: ""
                                val notes = doc.getString("notes") ?: ""
                                val isActive = doc.getBoolean("isActive") ?: doc.getBoolean("is_active") ?: true
                                Member(id = id, name = name, notes = notes, isActive = isActive)
                            } catch (e: Exception) {
                                android.util.Log.e("FinanceViewModel", "Refresh: error parsing members doc ${doc.id}", e)
                                null
                            }
                        }.sortedBy { it.name.lowercase() }
                        _members.value = list
                        
                        viewModelScope.launch {
                            for (m in list) {
                                try { repository.insertMember(m) } catch (e: Exception) {}
                            }
                        }
                    } else {
                        android.util.Log.e("FinanceViewModel", "Refresh members failed to fetch", task.exception)
                    }
                }

                // Fetch and sync transactions
                fs.collection("transactions").get().addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val snapshot = task.result
                        if (snapshot.isEmpty) {
                            android.util.Log.d("FinanceViewModel", "Refresh transactions successfully fetched but snapshot is empty")
                        } else {
                            android.util.Log.d("FinanceViewModel", "Refresh transactions successfully fetched with ${snapshot.size()} documents")
                        }
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val docIdLong = doc.id.toLongOrNull()
                                val id = (doc.get("id") as? Number)?.toLong() ?: docIdLong ?: stableStringHashToLong(doc.id)
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
                                android.util.Log.e("FinanceViewModel", "Refresh: error parsing transactions doc ${doc.id}", e)
                                null
                            }
                        }.sortedByDescending { it.date }
                        _allTransactionsList.value = list
                        
                        viewModelScope.launch {
                            for (t in list) {
                                try { repository.insertTransaction(t) } catch (e: Exception) {}
                            }
                        }
                    } else {
                        android.util.Log.e("FinanceViewModel", "Refresh transactions failed to fetch", task.exception)
                    }
                }

                kotlinx.coroutines.delay(1200)
            } catch (e: Exception) {
                android.util.Log.e("FinanceViewModel", "Error inside refreshData task scope", e)
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
                val fs = firestore
                if (fs == null) {
                    onFailure("Layanan Firestore tidak tersedia!")
                    return@launch
                }
                fs.collection("settings").document("admin")
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
            
            // deactivate other periods first in Room
            try {
                _periods.value.forEach {
                    if (it.isActive) {
                        repository.insertPeriod(it.copy(isActive = false))
                    }
                }
            } catch (any: Exception) {}

            val fs = firestore
            if (fs != null) {
                // deactivate other periods first in Firestore
                try {
                    _periods.value.forEach {
                        if (it.isActive) {
                            fs.collection("payments").document(it.id.toString())
                                .update(mapOf("isActive" to false, "is_active" to false))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FinanceViewModel", "Error deactivating other periods during addPeriod", e)
                }
            }
            
            // Re-sync Room repository as fallback
            try {
                repository.insertPeriod(Period(id = id, name = name, startingBalance = startingBalance, isActive = true))
                repository.selectActivePeriod(id)
            } catch (any: Exception) {}

            if (fs != null) {
                fs.collection("payments").document(id.toString()).set(newPeriodMap)
                    .addOnSuccessListener {
                        android.util.Log.d("FinanceViewModel", "Successfully added period $name (id: $id) to Firestore payments")
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("FinanceViewModel", "Failed to add period $name (id: $id) to Firestore payments", e)
                    }
            }
            selectedPeriodId.value = id
        }
    }

    fun selectPeriod(periodId: Long?) {
        viewModelScope.launch {
            selectedPeriodId.value = periodId
            if (periodId != null) {
                val fs = firestore
                if (fs != null) {
                    try {
                        _periods.value.forEach {
                            val active = (it.id == periodId)
                            fs.collection("payments").document(it.id.toString())
                                .update(mapOf("isActive" to active, "is_active" to active))
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FinanceViewModel", "Error updating period active state in selectPeriod", e)
                    }
                }
                
                try {
                    repository.selectActivePeriod(periodId)
                } catch (any: Exception) {}
            }
        }
    }

    fun deletePeriod(period: Period) {
        viewModelScope.launch {
            val fs = firestore
            if (fs != null) {
                try {
                    fs.collection("payments").document(period.id.toString()).delete()
                        .addOnSuccessListener {
                            android.util.Log.d("FinanceViewModel", "Successfully deleted period ${period.name} from payments")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("FinanceViewModel", "Error deleting period ${period.name} from payments", e)
                        }
                    // delete associated transactions in this period
                    _allTransactionsList.value.filter { it.periodId == period.id }.forEach {
                        fs.collection("transactions").document(it.id.toString()).delete()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FinanceViewModel", "Error deleting period or associated transactions", e)
                }
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

            val fs = firestore
            if (fs != null) {
                fs.collection("members").document(id.toString()).set(newMemberMap)
            }
        }
    }

    fun deleteMember(member: Member) {
        viewModelScope.launch {
            val fs = firestore
            if (fs != null) {
                try {
                    fs.collection("members").document(member.id.toString()).delete()
                    // delete associated transactions for this member
                    _allTransactionsList.value.filter { it.memberId == member.id }.forEach {
                        fs.collection("transactions").document(it.id.toString()).delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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

                val fs = firestore
                if (fs != null) {
                    fs.collection("transactions").document(id.toString()).set(newTxMap)
                }
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            val fs = firestore
            if (fs != null) {
                try {
                    fs.collection("transactions").document(transaction.id.toString()).delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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

                val fs = firestore
                if (fs != null) {
                    fs.collection("transactions").document(id.toString()).set(newTxMap)
                }
            }
        }
    }

    fun seedSpreadsheetData() {
        isSeeding = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(8000)
            isSeeding = false
        }
        viewModelScope.launch {
            try {
                android.util.Log.d("FinanceViewModel", "Seeding default default data to local Room cache first...")
                
                // 1. Seed periods to Room (ONLY August 2025/2026 is active)
                val periodsToSeed = listOf(
                    Period(id = 1, name = "JANUARY 2025/2026", startingBalance = 0.0, isActive = false),
                    Period(id = 2, name = "FEB 2025/2026", startingBalance = 0.0, isActive = false),
                    Period(id = 3, name = "MARET 2025/2026", startingBalance = 0.0, isActive = false),
                    Period(id = 4, name = "APRIL 2025/2026", startingBalance = 0.0, isActive = false),
                    Period(id = 5, name = "MEI 2025/2026", startingBalance = 0.0, isActive = false),
                    Period(id = 6, name = "JUNI 2025/2026", startingBalance = 0.0, isActive = false),
                    Period(id = 7, name = "JULI 2025/2026", startingBalance = 0.0, isActive = false),
                    Period(id = 8, name = "AGUST 2025/2026", startingBalance = 0.0, isActive = true)
                )
                for (p in periodsToSeed) {
                    try { repository.insertPeriod(p) } catch (e: Exception) {}
                }

                // 2. Seed members to Room
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
                    try { repository.insertMember(m) } catch (e: Exception) {}
                }

                // 3. Seed transactions to Room
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

                // Add starting balance income to Room
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

                // Add expense transaction to Room
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

                android.util.Log.d("FinanceViewModel", "Local Room seeding completed successfully!")

                // Now asynchronously upload / seed Firestore if it's empty
                val fs = firestore
                if (fs == null) {
                    sharedPrefs.edit().putBoolean("spreadsheet_synced_2025_v2", true).apply()
                    isSeeding = false
                    return@launch
                }
                fs.collection("payments").get().addOnSuccessListener { qs ->
                    if (qs != null && !qs.isEmpty) {
                        android.util.Log.d("FinanceViewModel", "Firestore payments already contains data, skipping cloud upload.")
                        sharedPrefs.edit().putBoolean("spreadsheet_synced_2025_v2", true).apply()
                        return@addOnSuccessListener
                    }
                    
                    for (p in periodsToSeed) {
                        val pMap = mapOf(
                            "id" to p.id,
                            "name" to p.name,
                            "startingBalance" to p.startingBalance,
                            "isActive" to p.isActive
                        )
                        fs.collection("payments").document(p.id.toString()).set(pMap)
                    }

                    for (m in membersToSeed) {
                        val mMap = mapOf(
                            "id" to m.id,
                            "name" to m.name,
                            "notes" to m.notes,
                            "isActive" to m.isActive
                        )
                        fs.collection("members").document(m.id.toString()).set(mMap)
                    }

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
                        fs.collection("transactions").document(txId.toString()).set(txMap)
                    }

                    val startTxMap = mapOf(
                        "id" to 9999L,
                        "periodId" to 1L,
                        "period_id" to 1L,
                        "type" to "INCOME",
                        "category" to "Saldo Awal",
                        "amount" to 755000.0,
                        "description" to "Saldo Awal Kas KB SPASI",
                        "date" to System.currentTimeMillis() - 12 * 86400000L,
                    )
                    fs.collection("transactions").document("9999").set(startTxMap)

                    val expTxMap = mapOf(
                        "id" to 8888L,
                        "periodId" to 1L,
                        "period_id" to 1L,
                        "type" to "EXPENSE",
                        "category" to "Pengeluaran",
                        "amount" to 573000.0,
                        "description" to "Pengeluaran Kas Belanja & Operasional",
                        "date" to System.currentTimeMillis() - 8 * 86400000L,
                    )
                    fs.collection("transactions").document("8888").set(expTxMap)

                    sharedPrefs.edit().putBoolean("spreadsheet_synced_2025_v2", true).apply()
                    android.util.Log.d("FinanceViewModel", "Successfully completed Firestore cloud seeding!")
                }
                
                sharedPrefs.edit().putBoolean("spreadsheet_synced_2025_v2", true).apply()
                isSeeding = false
            } catch (e: Exception) {
                android.util.Log.e("FinanceViewModel", "Error while seeding spreadsheet data", e)
                isSeeding = false
            }
        }
    }

    // Database Reset
    fun clearAllData() {
        viewModelScope.launch {
            val fs = firestore
            if (fs != null) {
                try {
                    fs.collection("payments").get().addOnSuccessListener { snapshot ->
                        snapshot.documents.forEach { it.reference.delete() }
                    }
                    fs.collection("members").get().addOnSuccessListener { snapshot ->
                        snapshot.documents.forEach { it.reference.delete() }
                    }
                    fs.collection("transactions").get().addOnSuccessListener { snapshot ->
                        snapshot.documents.forEach { it.reference.delete() }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FinanceViewModel", "Error in clearAllData resetting Firestore collections", e)
                }
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
