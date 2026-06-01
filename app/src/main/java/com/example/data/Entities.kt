package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "periods")
data class Period(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startingBalance: Double = 0.0,
    val isActive: Boolean = false
)

@Entity(tableName = "members")
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val isActive: Boolean = true
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodId: Long,
    val type: String, // "INCOME" or "EXPENSE"
    val category: String, // e.g., "Iuran Bulanan", "Donasi", "Operasional", "Sosial"
    val amount: Double,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val memberId: Long? = null, // associated member if contribution
    val memberName: String? = null // backup/text representation
)
