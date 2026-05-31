package com.example.data

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FinanceRepository(private val financeDao: FinanceDao) {

    private val firebaseDb: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val periodsRef = firebaseDb.getReference("periods")
    private val contributionsRef = firebaseDb.getReference("contributions")
    private val expensesRef = firebaseDb.getReference("expenses")

    val allPeriods: Flow<List<PeriodEntity>> = financeDao.getAllPeriods()
    val activePeriodFlow: Flow<PeriodEntity?> = financeDao.getActivePeriodFlow()

    init {
        try {
            firebaseDb.setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Persistence can only be initialized once
        }
        startRealtimeSync()
    }

    fun startRealtimeSync() {
        periodsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                savePeriodFromSnapshot(snapshot)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                savePeriodFromSnapshot(snapshot)
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                deletePeriodFromSnapshot(snapshot)
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })

        contributionsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                saveContributionFromSnapshot(snapshot)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                saveContributionFromSnapshot(snapshot)
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                deleteContributionFromSnapshot(snapshot)
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })

        expensesRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                saveExpenseFromSnapshot(snapshot)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                saveExpenseFromSnapshot(snapshot)
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                deleteExpenseFromSnapshot(snapshot)
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun savePeriodFromSnapshot(snapshot: DataSnapshot) {
        val id = snapshot.key?.toIntOrNull() ?: return
        val name = snapshot.child("name").getValue(String::class.java) ?: ""
        val startingBalance = (snapshot.child("startingBalance").value as? Number)?.toDouble() ?: 0.0
        val isActive = (snapshot.child("active").value as? Boolean) ?: (snapshot.child("isActive").value as? Boolean) ?: false

        CoroutineScope(Dispatchers.IO).launch {
            val existing = financeDao.getAllPeriods().first().find { it.id == id }
            val incoming = PeriodEntity(id, name, startingBalance, isActive)
            if (existing != incoming) {
                financeDao.insertPeriod(incoming)
            }
        }
    }

    private fun deletePeriodFromSnapshot(snapshot: DataSnapshot) {
        val id = snapshot.key?.toIntOrNull() ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val existing = financeDao.getAllPeriods().first().find { it.id == id }
            if (existing != null) {
                financeDao.deletePeriod(existing)
            }
        }
    }

    private fun saveContributionFromSnapshot(snapshot: DataSnapshot) {
        val id = snapshot.key?.toIntOrNull() ?: return
        val periodId = (snapshot.child("periodId").value as? Number)?.toInt() ?: 0
        val memberName = snapshot.child("memberName").getValue(String::class.java) ?: ""
        val amount = (snapshot.child("amount").value as? Number)?.toDouble() ?: 0.0
        val date = (snapshot.child("date").value as? Number)?.toLong() ?: 0L
        val isPaid = (snapshot.child("paid").value as? Boolean) ?: (snapshot.child("isPaid").value as? Boolean) ?: false
        val notes = snapshot.child("notes").getValue(String::class.java) ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            val existing = financeDao.getAllContributions().first().find { it.id == id }
            val incoming = ContributionEntity(id, periodId, memberName, amount, date, isPaid, notes)
            if (existing != incoming) {
                financeDao.insertContribution(incoming)
            }
        }
    }

    private fun deleteContributionFromSnapshot(snapshot: DataSnapshot) {
        val id = snapshot.key?.toIntOrNull() ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val existing = financeDao.getAllContributions().first().find { it.id == id }
            if (existing != null) {
                financeDao.deleteContribution(existing)
            }
        }
    }

    private fun saveExpenseFromSnapshot(snapshot: DataSnapshot) {
        val id = snapshot.key?.toIntOrNull() ?: return
        val periodId = (snapshot.child("periodId").value as? Number)?.toInt() ?: 0
        val date = (snapshot.child("date").value as? Number)?.toLong() ?: 0L
        val amount = (snapshot.child("amount").value as? Number)?.toDouble() ?: 0.0
        val purpose = snapshot.child("purpose").getValue(String::class.java) ?: ""
        val notes = snapshot.child("notes").getValue(String::class.java) ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            val existing = financeDao.getAllExpenses().first().find { it.id == id }
            val incoming = ExpenseEntity(id, periodId, date, amount, purpose, notes)
            if (existing != incoming) {
                financeDao.insertExpense(incoming)
            }
        }
    }

    private fun deleteExpenseFromSnapshot(snapshot: DataSnapshot) {
        val id = snapshot.key?.toIntOrNull() ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val existing = financeDao.getAllExpenses().first().find { it.id == id }
            if (existing != null) {
                financeDao.deleteExpense(existing)
            }
        }
    }

    fun syncLocalToFirebaseBackground() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val periods = financeDao.getAllPeriods().first()
                for (p in periods) {
                    periodsRef.child(p.id.toString()).setValue(p)
                }

                val contributions = financeDao.getAllContributions().first()
                for (c in contributions) {
                    contributionsRef.child(c.id.toString()).setValue(c)
                }

                val expenses = financeDao.getAllExpenses().first()
                for (e in expenses) {
                    expensesRef.child(e.id.toString()).setValue(e)
                }
            } catch (e: Exception) {
                // Ignore background sync exceptions
            }
        }
    }

    fun getContributionsForPeriod(periodId: Int): Flow<List<ContributionEntity>> {
        return financeDao.getContributionsForPeriod(periodId)
    }

    fun getExpensesForPeriod(periodId: Int): Flow<List<ExpenseEntity>> {
        return financeDao.getExpensesForPeriod(periodId)
    }

    suspend fun insertPeriod(period: PeriodEntity): Long {
        val id = financeDao.insertPeriod(period)
        periodsRef.child(id.toString()).setValue(period.copy(id = id.toInt()))
        return id
    }

    suspend fun updatePeriod(period: PeriodEntity) {
        financeDao.updatePeriod(period)
        periodsRef.child(period.id.toString()).setValue(period)
    }

    suspend fun setActivePeriod(periodId: Int) {
        financeDao.setActivePeriod(periodId)
        val periods = financeDao.getAllPeriods().first()
        for (p in periods) {
            periodsRef.child(p.id.toString()).setValue(p)
        }
    }

    suspend fun deletePeriod(period: PeriodEntity) {
        financeDao.deletePeriod(period)
        periodsRef.child(period.id.toString()).removeValue()
    }

    suspend fun insertContribution(contribution: ContributionEntity): Long {
        val id = financeDao.insertContribution(contribution)
        contributionsRef.child(id.toString()).setValue(contribution.copy(id = id.toInt()))
        return id
    }

    suspend fun updateContribution(contribution: ContributionEntity) {
        financeDao.updateContribution(contribution)
        contributionsRef.child(contribution.id.toString()).setValue(contribution)
    }

    suspend fun deleteContribution(contribution: ContributionEntity) {
        financeDao.deleteContribution(contribution)
        contributionsRef.child(contribution.id.toString()).removeValue()
    }

    suspend fun insertExpense(expense: ExpenseEntity): Long {
        val id = financeDao.insertExpense(expense)
        expensesRef.child(id.toString()).setValue(expense.copy(id = id.toInt()))
        return id
    }

    suspend fun updateExpense(expense: ExpenseEntity) {
        financeDao.updateExpense(expense)
        expensesRef.child(expense.id.toString()).setValue(expense)
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        financeDao.deleteExpense(expense)
        expensesRef.child(expense.id.toString()).removeValue()
    }

    suspend fun seedInitialDataIfNecessary() {
        // First check if local already has data
        val localPeriods = financeDao.getAllPeriods().first()
        if (localPeriods.isNotEmpty()) {
            syncLocalToFirebaseBackground()
            return
        }

        // Local has no data. Let's check Firebase first!
        try {
            val task = periodsRef.get()
            while (!task.isComplete) {
                kotlinx.coroutines.delay(50)
            }
            val snapshot = task.result
            if (snapshot != null && snapshot.exists()) {
                // Remote already contains populated periods. Don't seed, let the real-time listener download everything.
                return
            }
        } catch (e: Exception) {
            // Timeout or offline. Default to local seed
        }

        // Neither remote nor local contains any data! Seed starting records.
        val periods = financeDao.getAllPeriods().first()
        val hasActualPeriod = periods.any { it.name == "2025/2026" }
        if (!hasActualPeriod) {
            financeDao.clearPeriods()
            financeDao.clearContributions()
            financeDao.clearExpenses()

            val periodId2025 = financeDao.insertPeriod(
                PeriodEntity(name = "2025/2026", startingBalance = 755000.0, isActive = true)
            ).toInt()

            financeDao.insertPeriod(
                PeriodEntity(name = "2026/2027", startingBalance = 512000.0, isActive = false)
            )

            // Month indices
            val JAN = 0
            val FEB = 1
            val MAR = 2
            val APR = 3
            val MAY = 4
            val JUN = 5
            val JUL = 6
            val AUG = 7

            suspend fun addC(memberName: String, month: Int, isPaid: Boolean, notes: String) {
                val contribution = ContributionEntity(
                    periodId = periodId2025,
                    memberName = memberName,
                    amount = 10000.0,
                    date = getMonthTime(month),
                    isPaid = isPaid,
                    notes = notes
                )
                insertContribution(contribution)
            }

            // BG TANTO (Jan - Jun)
            for (m in listOf(JAN, FEB, MAR, APR, MAY, JUN)) addC("BG TANTO", m, true, "Iuran Kas")
            addC("BG TANTO", JUL, false, "Tunggakan Kas")
            addC("BG TANTO", AUG, false, "Tunggakan Kas")

            // BG NANANG (Jan - Feb)
            for (m in listOf(JAN, FEB)) addC("BG NANANG", m, true, "Iuran Kas")
            for (m in listOf(MAR, APR)) addC("BG NANANG", m, false, "Tunggakan Kas")

            // BG YUDI (Jan - Mar)
            for (m in listOf(JAN, FEB, MAR)) addC("BG YUDI", m, true, "Iuran Kas")

            // BG TEMIM (Jan - Feb)
            for (m in listOf(JAN, FEB)) addC("BG TEMIM", m, true, "Iuran Kas")

            // BG EPI (Jan - Feb)
            for (m in listOf(JAN, FEB)) addC("BG EPI", m, true, "Iuran Kas")

            // BG RELLY (Unpaid Jan, Feb)
            addC("BG RELLY", JAN, false, "Tunggakan Kas")
            addC("BG RELLY", FEB, false, "Tunggakan Kas")

            // BG JAKA (Jan - Mar)
            for (m in listOf(JAN, FEB, MAR)) addC("BG JAKA", m, true, "Iuran Kas")

            // BG BUDI (Jan - Feb)
            for (m in listOf(JAN, FEB)) addC("BG BUDI", m, true, "Iuran Kas")

            // BG COKI (Jan - Mar)
            for (m in listOf(JAN, FEB, MAR)) addC("BG COKI", m, true, "Iuran Kas")

            // BG SAMSURI (Jan - Apr)
            for (m in listOf(JAN, FEB, MAR, APR)) addC("BG SAMSURI", m, true, "Iuran Kas")

            // BG ROHMAN (Unpaid Jan, Feb)
            addC("BG ROHMAN", JAN, false, "Tunggakan Kas")
            addC("BG ROHMAN", FEB, false, "Tunggakan Kas")

            // BG HERU (Apr - May)
            for (m in listOf(APR, MAY)) addC("BG HERU", m, true, "Iuran Kas")

            // BG TONY (Jan - Apr)
            for (m in listOf(JAN, FEB, MAR, APR)) addC("BG TONY", m, true, "Iuran Kas")

            // 2. Seed Expenses summing up to exactly 573.000
            val expense1 = ExpenseEntity(
                periodId = periodId2025,
                date = getMonthTime(JAN) + 5 * 24 * 60 * 60 * 1000L,
                amount = 250000.0,
                purpose = "Inventaris Kelas",
                notes = "Pembelian peralatan kebersihan & kipas angin kecil"
            )
            insertExpense(expense1)

            val expense2 = ExpenseEntity(
                periodId = periodId2025,
                date = getMonthTime(FEB) + 5 * 24 * 60 * 60 * 1000L,
                amount = 175000.0,
                purpose = "Administrasi & Cetak Laporan",
                notes = "Cetak modul belajar dan fotokopi absensi"
            )
            insertExpense(expense2)

            val expense3 = ExpenseEntity(
                periodId = periodId2025,
                date = getMonthTime(MAR) + 5 * 24 * 60 * 60 * 1000L,
                amount = 148000.0,
                purpose = "Konsumsi Rapat Kas",
                notes = "Snack dan konsumsi pertemuan spasi"
            )
            insertExpense(expense3)

            syncLocalToFirebaseBackground()
        }
    }

    private fun getMonthTime(month: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.YEAR, 2025)
        cal.set(java.util.Calendar.MONTH, month)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 15)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
