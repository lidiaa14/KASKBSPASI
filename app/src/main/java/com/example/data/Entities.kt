package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "periods")
data class PeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val startingBalance: Double = 0.0,
    val isActive: Boolean = false
)

@Entity(tableName = "contributions")
data class ContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val periodId: Int,
    val memberName: String,
    val amount: Double,
    val date: Long,
    val isPaid: Boolean,
    val notes: String = ""
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val periodId: Int,
    val date: Long,
    val amount: Double,
    val purpose: String,
    val notes: String = ""
)
