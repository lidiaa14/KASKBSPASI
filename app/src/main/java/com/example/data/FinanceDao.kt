package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    // Periods
    @Query("SELECT * FROM periods ORDER BY id DESC")
    fun getAllPeriodsFlow(): Flow<List<Period>>

    @Query("SELECT * FROM periods WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePeriod(): Period?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriod(period: Period): Long

    @Update
    suspend fun updatePeriod(period: Period)

    @Delete
    suspend fun deletePeriod(period: Period)

    @Query("UPDATE periods SET isActive = 0")
    suspend fun deactivateAllPeriods()

    @Query("UPDATE periods SET isActive = 1 WHERE id = :periodId")
    suspend fun setActivePeriod(periodId: Long)

    // Members
    @Query("SELECT * FROM members ORDER BY name ASC")
    fun getAllMembersFlow(): Flow<List<Member>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: Member): Long

    @Update
    suspend fun updateMember(member: Member)

    @Delete
    suspend fun deleteMember(member: Member)

    // Transactions
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactionsFlow(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE periodId = :periodId ORDER BY date DESC")
    fun getTransactionsByPeriodFlow(periodId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE memberId = :memberId ORDER BY date DESC")
    fun getTransactionsByMemberFlow(memberId: Long): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    // Reset of data
    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM members")
    suspend fun clearMembers()

    @Query("DELETE FROM periods")
    suspend fun clearPeriods()
}
