package com.example.service

import android.util.Log
import com.example.BuildConfig
import com.example.data.Member
import com.example.data.Period
import com.example.data.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class GeminiRequest(
    val contents: List<RequestContent>,
    val systemInstruction: RequestContent? = null
)

@Serializable
data class RequestContent(
    val parts: List<RequestPart>
)

@Serializable
data class RequestPart(
    val text: String
)

@Serializable
data class GeminiResponse(
    val candidates: List<CandidateResponse>? = null
)

@Serializable
data class CandidateResponse(
    val content: ContentResponse? = null
)

@Serializable
data class ContentResponse(
    val parts: List<PartResponse>? = null
)

@Serializable
data class PartResponse(
    val text: String? = null
)

object GeminiContextBuilder {
    fun buildContext(
        periods: List<Period>,
        members: List<Member>,
        transactions: List<Transaction>
    ): String {
        val sb = StringBuilder()
        
        sb.append("DOKUMEN KEUANGAN KOMUNITAS KAS\n")
        sb.append("=============================\n\n")

        // 1. Periods overview
        sb.append("1. DAFTAR PERIODE KAS:\n")
        if (periods.isEmpty()) {
            sb.append("- Tidak ada periode kas.\n")
        } else {
            periods.forEach { period ->
                val status = if (period.isActive) "AKTIF (Periode Saat Ini)" else "Non-Aktif"
                sb.append("- ID: ${period.id} | Nama: ${period.name} | Saldo Awal: Rp${formatNumber(period.startingBalance)} | Status: $status\n")
            }
        }
        sb.append("\n")

        // 2. Members list
        sb.append("2. DAFTAR ANGGOTA KOMUNITAS:\n")
        if (members.isEmpty()) {
            sb.append("- Tidak ada anggota.\n")
        } else {
            members.forEach { member ->
                val status = if (member.isActive) "Aktif" else "Non-Aktif"
                sb.append("- ID: ${member.id} | Nama: ${member.name} | Catatan: ${member.notes} | Status: $status\n")
            }
        }
        sb.append("\n")

        // 3. Transactions details
        sb.append("3. SEJARAH TRANSAKSI (Iuran Pembayaran & Pengeluaran):\n")
        if (transactions.isEmpty()) {
            sb.append("- Tidak ada transaksi keuangan.\n")
        } else {
            // Sort by date descending to give Gemini latest transactions first, but up to 200 items for context size limits
            val sortedTxs = transactions.sortedByDescending { it.date }
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
            sortedTxs.take(200).forEach { tx ->
                val dateStr = dateFormat.format(Date(tx.date))
                val typeLabel = if (tx.type == "INCOME") "PEMASUKAN (Iuran)" else "PENGELUARAN"
                val memberLabel = if (tx.memberName != null) " | Dari Anggota: ${tx.memberName} (ID Member: ${tx.memberId})" else ""
                val periodName = periods.find { it.id == tx.periodId }?.name ?: "ID Periode: ${tx.periodId}"
                sb.append("- Tanggal: $dateStr|Periode: $periodName|Jenis: $typeLabel|Kategori: ${tx.category}|Nominal: Rp${formatNumber(tx.amount)}|Deskripsi: ${tx.description}$memberLabel\n")
            }
        }
        sb.append("\n")

        // 4. Financial metrics of current selection
        val activePeriod = periods.find { it.isActive }
        
        sb.append("4. RINGKASAN KEUANGAN AKTIF:\n")
        if (activePeriod != null) {
            sb.append("- Periode Aktif Saat Ini: ${activePeriod.name}\n")
            val activeTxs = transactions.filter { it.periodId == activePeriod.id }
            val income = activeTxs.filter { it.type == "INCOME" }.sumOf { it.amount }
            val expenses = activeTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val saldoAwal = activePeriod.startingBalance
            val saldoAkhir = saldoAwal + income - expenses
            sb.append("  * Saldo Awal: Rp${formatNumber(saldoAwal)}\n")
            sb.append("  * Total Pemasukan Periode Ini: Rp${formatNumber(income)}\n")
            sb.append("  * Total Pengeluaran Periode Ini: Rp${formatNumber(expenses)}\n")
            sb.append("  * Saldo Kas Saat Ini (Periode ${activePeriod.name}): Rp${formatNumber(saldoAkhir)}\n")
        } else {
            sb.append("- Tidak ada periode aktif saat ini.\n")
        }

        // All-time Metrics
        val allIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val allExpenses = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        sb.append("- Keseluruhan Semua Periode:\n")
        sb.append("  * Total Seluruh Pemasukan: Rp${formatNumber(allIncome)}\n")
        sb.append("  * Total Seluruh Pengeluaran: Rp${formatNumber(allExpenses)}\n")
        sb.append("  * Sisa Saldo/Uang Kas Seluruhnya: Rp${formatNumber(allIncome - allExpenses)}\n")
        sb.append("\n")

        // 5. Unpaid members details per period
        sb.append("5. STATUS PEMBAYARAN IURAN ANGGOTA PER PERIODE:\n")
        if (periods.isEmpty() || members.isEmpty()) {
            sb.append("- Informasi tidak lengkap untuk menghitung status iuran.\n")
        } else {
            periods.forEach { period ->
                sb.append("- Periode: ${period.name}\n")
                
                // Active members
                val activeMembers = members.filter { it.isActive }
                val paidMembers = mutableListOf<Member>()
                val unpaidMembers = mutableListOf<Member>()

                activeMembers.forEach { member ->
                    val isPaid = transactions.any { 
                        it.memberId == member.id && it.periodId == period.id && it.type == "INCOME" 
                    }
                    if (isPaid) {
                        paidMembers.add(member)
                    } else {
                        unpaidMembers.add(member)
                    }
                }

                sb.append("  * Sudah Bayar (${paidMembers.size} org): ${paidMembers.joinToString { it.name }.ifEmpty { "Tidak ada" }}\n")
                sb.append("  * Belum Bayar (${unpaidMembers.size} org): ${unpaidMembers.joinToString { it.name }.ifEmpty { "Tidak ada" }}\n")
            }
        }
        sb.append("\n")

        return sb.toString()
    }

    private fun formatNumber(num: Double): String {
        return String.format(Locale("id", "ID"), "%,.0f", num)
    }
}

object GeminiService {
    private const val TAG = "GeminiService"

    private val jsonHelper = Json {
        ignoreUnknownKeys = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getSystemInstruction(): String {
        return """
            Anda adalah "Ask AI", asisten AI keuangan pintar untuk aplikasi Kas Komunitas "KB SPASI".
            Tugas Anda adalah menganalisis data keuangan komunitas yang disediakan dalam konteks untuk menjawab pertanyaan pengguna dengan ramah, jelas, natural, ringkas, dan menggunakan Bahasa Indonesia yang mudah dipahami. Sampaikan jawaban secara langsung dan elegan, hindari format robotik yang berlebihan.

            Batasan penting:
            1. Jawab HANYA berdasarkan data keuangan/keanggotaan komunitas yang disediakan. Jangan mengarang data atau mencantumkan informasi dari luar data konteks.
            2. Jika informasi data yang ditanyakan pengguna tidak tersedia atau data yang dibutuhkan untuk menjawab belum diinput ke sistem, jawablah dengan sopan: "Data yang dibutuhkan belum tersedia."
            3. Berbicaralah dengan gaya santai namun sopan seperti asisten pengelola kas sesungguhnya.
        """.trimIndent()
    }

    suspend fun getAnswer(contextData: String, userQuestion: String): String = withContext(Dispatchers.IO) {
        val apiKeysToTry = mutableListOf<String>()
        val configKey = BuildConfig.GEMINI_API_KEY
        if (configKey.isNotEmpty() && configKey != "YOUR_GEMINI_API_KEY") {
            apiKeysToTry.add(configKey)
        }
        val fallbackKey = BuildConfig.GEMINI_API_KEY_FALLBACK_1
        if (fallbackKey.isNotEmpty() && fallbackKey != "YOUR_GEMINI_API_KEY_FALLBACK_1") {
            apiKeysToTry.add(fallbackKey)
        }

        // Models to try in sequential order (flexible and resilient approach)
        val modelsToTry = listOf(
            "gemini-3.5-flash",
            "gemini-flash-latest",
            "gemini-2.5-flash",
            "gemini-1.5-flash",
            "gemini-2.0-flash"
        )

        var lastErrorDetails = ""

        for (apiKey in apiKeysToTry) {
            val keyPreview = if (apiKey.length > 8) "${apiKey.take(6)}...${apiKey.takeLast(4)} (Length: ${apiKey.length})" else "Invalid (${apiKey.length})"
            Log.d(TAG, "Trying with API key prefix: $keyPreview")

            for (model in modelsToTry) {
                // Style 1: with systemInstruction parameter
                val payloadWithSysInstance = GeminiRequest(
                    contents = listOf(
                        RequestContent(parts = listOf(RequestPart(text = "Konteks Laporan Data Keuangan:\n\n$contextData\n\nPertanyaan Pengguna: \"$userQuestion\"\n\nJawaban Asisten AI Anda:")))
                    ),
                    systemInstruction = RequestContent(parts = listOf(RequestPart(text = getSystemInstruction())))
                )

                // Style 2: without systemInstruction parameter (just append instructions to payload context to avoid 400 on some models)
                val payloadWithoutSysInstance = GeminiRequest(
                    contents = listOf(
                        RequestContent(parts = listOf(
                            RequestPart(text = "Sistem Instruksi:\n${getSystemInstruction()}\n\nKonteks Laporan Data Keuangan:\n\n$contextData\n\nPertanyaan Pengguna: \"$userQuestion\"\n\nJawaban Asisten AI Anda:")
                        ))
                    ),
                    systemInstruction = null
                )

                val payloads = listOf(payloadWithSysInstance, payloadWithoutSysInstance)

                for ((index, payload) in payloads.withIndex()) {
                    val requestBodyString = try {
                        jsonHelper.encodeToString(payload)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed serialization", e)
                        continue
                    }

                    Log.d(TAG, "Attempting Model: $model | Key: $keyPreview | Payload Style: ${if (index == 0) "With Sys" else "Without Sys"}")
                    
                    val targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val httpRequestBody = requestBodyString.toRequestBody("application/json; charset=utf-8".toMediaType())

                    val request = Request.Builder()
                        .url(targetUrl)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("X-goog-api-key", apiKey)
                        .post(httpRequestBody)
                        .build()

                    try {
                        client.newCall(request).execute().use { response ->
                            val responseBodyStr = response.body?.string() ?: ""
                            Log.d(TAG, "Model: $model | Style: $index | HTTP status: ${response.code}")

                            if (response.isSuccessful) {
                                Log.d(TAG, "Response successfully received for model: $model")
                                val apiResponse = jsonHelper.decodeFromString<GeminiResponse>(responseBodyStr)
                                val text = apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                if (!text.isNullOrBlank()) {
                                    return@withContext text.trim()
                                } else {
                                    return@withContext "Data yang dibutuhkan belum tersedia."
                                }
                            } else {
                                Log.e(TAG, "Model $model returned error code ${response.code}. Response: $responseBodyStr")
                                lastErrorDetails = "Endpoint: v1beta/models/$model\nPayload Style: ${if (index == 0) "with systemInstruction" else "appended prompts"}\nHTTP Code: ${response.code}\nResponse: $responseBodyStr"
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Network timeout / error for model $model", e)
                        lastErrorDetails = "Network error: ${e.localizedMessage}"
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected parse exception for model $model", e)
                        lastErrorDetails = "Unexpected execution error: ${e.localizedMessage}"
                    }
                }
            }
        }

        // Return error details to UI if all fallback models failed
        return@withContext "Gagal menghubungkan ke Gemini API. Detail kesalahan terakhir:\n$lastErrorDetails"
    }
}
