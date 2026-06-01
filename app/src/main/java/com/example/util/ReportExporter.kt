package com.example.util

import com.example.data.Period
import com.example.data.Transaction
import java.text.NumberFormat
import java.util.Locale

object ReportExporter {
    fun formatIDR(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        var res = format.format(amount)
        res = res.replace("Rp", "Rp ")
        if (res.endsWith(",00")) {
            res = res.substring(0, res.length - 3)
        }
        return res
    }

    fun makeTextReport(period: Period?, transactions: List<Transaction>, totalIncome: Double, totalExpenses: Double): String {
        val sb = StringBuilder()
        sb.append("🧾 *LAPORAN KEUANGAN - KB SPASI KAS*\n")
        if (period != null) {
            sb.append("Periode: *${period.name}*\n")
            sb.append("Saldo Awal: *${formatIDR(period.startingBalance)}*\n")
        } else {
            sb.append("Periode: *Semua Waktu*\n")
        }
        sb.append("------------------------------------\n")
        sb.append("📈 Total Pemasukan: *${formatIDR(totalIncome)}*\n")
        sb.append("📉 Total Pengeluaran: *${formatIDR(totalExpenses)}*\n")
        val finalBalance = (period?.startingBalance ?: 0.0) + totalIncome - totalExpenses
        sb.append("💰 *Saldo Kas Saat Ini: ${formatIDR(finalBalance)}*\n")
        sb.append("------------------------------------\n\n")

        if (transactions.isNotEmpty()) {
            sb.append("*CATATAN TRANSAKSI TERAKHIR:*\n")
            // Take up to 15 transactions to keep messages clean
            transactions.take(15).forEach { item ->
                val typeIcon = if (item.type == "INCOME") "📥 [Masuk]" else "📤 [Keluar]"
                val details = if (!item.memberName.isNullOrEmpty()) " (${item.memberName})" else ""
                sb.append("• $typeIcon *${formatIDR(item.amount)}* - ${item.description}${details}\n")
            }
        } else {
            sb.append("_Belum ada catatan transaksi._\n")
        }
        sb.append("\n_Dibuat otomatis oleh Aplikasi KB SPASI KAS_")
        return sb.toString()
    }

    fun makeHtmlReport(period: Period?, transactions: List<Transaction>, totalIncome: Double, totalExpenses: Double): String {
        val finalBalance = (period?.startingBalance ?: 0.0) + totalIncome - totalExpenses
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Laporan Keuangan - KB SPASI KAS</title>
                <style>
                    body { font-family: sans-serif; color: #333; margin: 20px; line-height: 1.4; }
                    h1 { color: #00668B; text-align: center; border-bottom: 2px solid #00668B; padding-bottom: 10px; }
                    .metadata { margin-bottom: 20px; background: #F1F5F9; padding: 15px; border-radius: 8px; }
                    .metadata table { width: 100%; border-collapse: collapse; }
                    .metadata td { padding: 5px; }
                    .metrics { display: flex; justify-content: space-between; margin-bottom: 30px; gap: 15px; }
                    .metric-box { flex: 1; padding: 15px; border-radius: 8px; color: #fff; text-align: center; }
                    .starting { background-color: #4C6171; }
                    .income { background-color: #047857; }
                    .expenses { background-color: #B91C1C; }
                    .balance { background-color: #00668B; }
                    .metric-title { font-size: 11px; text-transform: uppercase; opacity: 0.9; margin-bottom: 5px; }
                    .metric-value { font-size: 18px; font-weight: bold; }
                    table.transactions { width: 100%; border-collapse: collapse; margin-top: 15px; }
                    table.transactions th, table.transactions td { border: 1px solid #CBD5E1; padding: 10px; text-align: left; }
                    table.transactions th { background-color: #F8FAFC; color: #0f172a; }
                    .badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 11px; font-weight: bold; color: #fff; }
                    .badge-income { background-color: #34D399; color: #064E3B; }
                    .badge-expense { background-color: #FCA5A5; color: #7F1D1D; }
                </style>
            </head>
            <body>
                <h1>KB SPASI KAS — LAPORAN KEUANGAN</h1>
                <div class="metadata">
                    <table>
                        <tr>
                            <td><strong>Laporan Periode:</strong></td>
                            <td>${period?.name ?: "Semua Waktu"}</td>
                            <td><strong>Tanggal Cetak:</strong></td>
                            <td>${java.text.SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(java.util.Date())}</td>
                        </tr>
                    </table>
                </div>

                <div class="metrics">
                    <div class="metric-box starting">
                        <div class="metric-title">Saldo Awal</div>
                        <div class="metric-value">${formatIDR(period?.startingBalance ?: 0.0)}</div>
                    </div>
                    <div class="metric-box income">
                        <div class="metric-title">Total Pemasukan</div>
                        <div class="metric-value">${formatIDR(totalIncome)}</div>
                    </div>
                    <div class="metric-box expenses">
                        <div class="metric-title">Total Pengeluaran</div>
                        <div class="metric-value">${formatIDR(totalExpenses)}</div>
                    </div>
                    <div class="metric-box balance">
                        <div class="metric-title">Saldo Kas Saat Ini</div>
                        <div class="metric-value">${formatIDR(finalBalance)}</div>
                    </div>
                </div>

                <h3>Rincian Transaksi</h3>
                <table class="transactions">
                    <thead>
                        <tr>
                            <th>No</th>
                            <th>Tanggal</th>
                            <th>Jenis</th>
                            <th>Kategori</th>
                            <th>Keterangan</th>
                            <th>Nominal</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${
                            transactions.mapIndexed { index, tr ->
                                val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(java.util.Date(tr.date))
                                val badgeClass = if (tr.type == "INCOME") "badge badge-income" else "badge badge-expense"
                                val typeLabel = if (tr.type == "INCOME") "Pemasukan" else "Pengeluaran"
                                val memberSuffix = if (!tr.memberName.isNullOrEmpty()) " (${tr.memberName})" else ""
                                """
                                <tr>
                                    <td>${index + 1}</td>
                                    <td>$dateStr</td>
                                    <td><span class="$badgeClass">$typeLabel</span></td>
                                    <td>${tr.category}</td>
                                    <td>${tr.description}$memberSuffix</td>
                                    <td><strong>${formatIDR(tr.amount)}</strong></td>
                                </tr>
                                """
                            }.joinToString("")
                        }
                    </tbody>
                </table>
                <p style="text-align: center; font-size: 11px; color: #64748B; margin-top: 40px;">Laporan keuangan ini digenerate secara digital oleh Aplikasi KB SPASI KAS.</p>
            </body>
            </html>
        """
    }
}
