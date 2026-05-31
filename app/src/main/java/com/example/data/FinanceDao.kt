package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {

    // --- PERIODS ---
    @Query("SELECT * FROM periods ORDER BY name DESC")
    fun getAllPeriods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods WHERE isActive = 1 LIMIT 1")
    fun getActivePeriodFlow(): Flow<PeriodEntity?>

    @Query("SELECT * FROM periods WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePeriod(): PeriodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriod(period: PeriodEntity): Long

    @Update
    suspend fun updatePeriod(period: PeriodEntity)

    @Query("UPDATE periods SET isActive = 0")
    suspend fun deactivateAllPeriods()

    @Transaction
    suspend fun setActivePeriod(periodId: Int) {
        deactivateAllPeriods()
        // Find compiling period or update locally
        // We'll run a raw update to make it active
        updateActiveStatus(periodId, true)
    }

    @Query("UPDATE periods SET isActive = :isActive WHERE id = :id")
    suspend fun updateActiveStatus(id: Int, isActive: Boolean)

    @Delete
    suspend fun deletePeriod(period: PeriodEntity)

    // --- CONTRIBUTIONS ---
    @Query("SELECT * FROM contributions ORDER BY date DESC")
    fun getAllContributions(): Flow<List<ContributionEntity>>

    @Query("SELECT * FROM contributions WHERE periodId = :periodId ORDER BY date DESC")
    fun getContributionsForPeriod(periodId: Int): Flow<List<ContributionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContribution(contribution: ContributionEntity): Long

    @Update
    suspend fun updateContribution(contribution: ContributionEntity)

    @Delete
    suspend fun deleteContribution(contribution: ContributionEntity)


    // --- EXPENSES ---
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE periodId = :periodId ORDER BY date DESC")
    fun getExpensesForPeriod(periodId: Int): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM periods")
    suspend fun clearPeriods()

    @Query("DELETE FROM contributions")
    suspend fun clearContributions()

    @Query("DELETE FROM expenses")
    suspend fun clearExpenses()
}
