package com.example.data

import kotlinx.coroutines.flow.Flow

class FinanceRepository(private val financeDao: FinanceDao) {
    val allPeriods: Flow<List<Period>> = financeDao.getAllPeriodsFlow()
    val allMembers: Flow<List<Member>> = financeDao.getAllMembersFlow()
    val allTransactions: Flow<List<Transaction>> = financeDao.getAllTransactionsFlow()

    fun getTransactionsByPeriod(periodId: Long): Flow<List<Transaction>> {
        return financeDao.getTransactionsByPeriodFlow(periodId)
    }

    fun getTransactionsByMember(memberId: Long): Flow<List<Transaction>> {
        return financeDao.getTransactionsByMemberFlow(memberId)
    }

    suspend fun getActivePeriod(): Period? {
        return financeDao.getActivePeriod()
    }

    suspend fun insertPeriod(period: Period): Long {
        return financeDao.insertPeriod(period)
    }

    suspend fun updatePeriod(period: Period) {
        financeDao.updatePeriod(period)
    }

    suspend fun deletePeriod(period: Period) {
        financeDao.deletePeriod(period)
    }

    suspend fun selectActivePeriod(periodId: Long) {
        financeDao.deactivateAllPeriods()
        financeDao.setActivePeriod(periodId)
    }

    suspend fun insertMember(member: Member): Long {
        return financeDao.insertMember(member)
    }

    suspend fun updateMember(member: Member) {
        financeDao.updateMember(member)
    }

    suspend fun deleteMember(member: Member) {
        financeDao.deleteMember(member)
    }

    suspend fun insertTransaction(transaction: Transaction): Long {
        return financeDao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: Transaction) {
        financeDao.updateTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        financeDao.deleteTransaction(transaction)
    }

    suspend fun clearAllData() {
        financeDao.clearTransactions()
        financeDao.clearMembers()
        financeDao.clearPeriods()
    }
}
