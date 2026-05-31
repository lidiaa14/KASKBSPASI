package com.example.util

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.example.data.ContributionEntity
import com.example.data.ExpenseEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object ReportExporter {

    private fun formatRp(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        return format.format(amount).replace("Rp", "Rp ").replace(",00", "")
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        return sdf.format(Date(timestamp))
    }

    // Generates a clean text string perfect for instant copying or sharing to WhatsApp/Telegram
    fun generateShareText(
        periodName: String,
        startingBalance: Double,
        contributions: List<ContributionEntity>,
        expenses: List<ExpenseEntity>
    ): String {
        val paidContributions = contributions.filter { it.isPaid }
        val unpaidContributions = contributions.filter { !it.isPaid }
        
        val totalIncome = paidContributions.sumOf { it.amount }
        val totalExpenses = expenses.sumOf { it.amount }
        val finalBalance = startingBalance + totalIncome - totalExpenses

        val sb = StringBuilder()
        sb.append("🧾 *FINANCIAL REPORT - KB SPASI COMMUNITY*\n")
        sb.append("📅 *Financial Period:* $periodName\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━━\n\n")

        sb.append("💰 *FINANCIAL SUMMARY:*\n")
        sb.append("• Previous Balance: ${formatRp(startingBalance)}\n")
        sb.append("• Total Income (+): ${formatRp(totalIncome)}\n")
        sb.append("• Total Expenses (-): ${formatRp(totalExpenses)}\n")
        sb.append("• *Remaining Balance: ${formatRp(finalBalance)}*\n\n")

        sb.append("📈 *INCOME DETAILS (PAID):*\n")
        if (paidContributions.isEmpty()) {
            sb.append("_No paid contributions recorded_\n")
        } else {
            paidContributions.forEach {
                sb.append("• ${it.memberName}: ${formatRp(it.amount)} (${formatDate(it.date)})\n")
            }
        }
        sb.append("\n")

        sb.append("📉 *EXPENSE DETAILS:*\n")
        if (expenses.isEmpty()) {
            sb.append("_No expenses recorded_\n")
        } else {
            expenses.forEach {
                sb.append("• ${formatDate(it.date)}: ${formatRp(it.amount)} - ${it.purpose}\n")
            }
        }
        sb.append("\n")

        sb.append("⚠️ *UNPAID MEMBERS:*\n")
        if (unpaidContributions.isEmpty()) {
            sb.append("• _All members paid in full!_ 🎉\n")
        } else {
            unpaidContributions.forEach {
                sb.append("• ${it.memberName}: ${formatRp(it.amount)} (Pending)\n")
            }
        }
        sb.append("\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("Generated automatically by *KB SPASI COMMUNITY*")

        return sb.toString()
    }

    // Triggers standard Android share drawer specifically for plain text
    fun shareToGroupChat(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, "Share Report via")
        context.startActivity(chooser)
    }

    // Prints directly or downloads as PDF using Android Print Framework and an inline WebView template
    fun printOrSavePdf(
        context: Context,
        periodName: String,
        startingBalance: Double,
        contributions: List<ContributionEntity>,
        expenses: List<ExpenseEntity>
    ) {
        val paidContributions = contributions.filter { it.isPaid }
        val unpaidContributions = contributions.filter { !it.isPaid }
        val totalIncome = paidContributions.sumOf { it.amount }
        val totalExpenses = expenses.sumOf { it.amount }
        val finalBalance = startingBalance + totalIncome - totalExpenses

        // Build HTML Invoice / Report
        val htmlBuilder = StringBuilder()
        htmlBuilder.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Financial Report</title>
                <style>
                    body {
                        font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                        color: #333;
                        padding: 20px;
                        line-height: 1.4;
                    }
                    .header {
                        text-align: center;
                        border-bottom: 2px solid #2e3d52;
                        padding-bottom: 10px;
                        margin-bottom: 25px;
                    }
                    .header h1 {
                        margin: 0;
                        color: #2e3d52;
                        font-size: 24px;
                        text-transform: uppercase;
                        letter-spacing: 1px;
                    }
                    .header p {
                        margin: 5px 0 0 0;
                        color: #666;
                        font-size: 14px;
                    }
                    .summary-cards {
                        display: flex;
                        justify-content: space-between;
                        margin-bottom: 30px;
                        gap: 10px;
                    }
                    .card {
                        flex: 1;
                        background: #f8fafc;
                        border: 1px solid #e2e8f0;
                        border-radius: 8px;
                        padding: 12px;
                        text-align: center;
                    }
                    .card.highlight {
                        background: #e0f2fe;
                        border-color: #7dd3fc;
                    }
                    .card .label {
                        font-size: 11px;
                        text-transform: uppercase;
                        color: #64748b;
                        margin-bottom: 5px;
                    }
                    .card .value {
                        font-size: 16px;
                        font-weight: bold;
                        color: #0f172a;
                    }
                    .card.highlight .value {
                        color: #0369a1;
                    }
                    h2 {
                        font-size: 15px;
                        color: #334155;
                        border-bottom: 1px solid #cbd5e1;
                        padding-bottom: 5px;
                        margin-top: 25px;
                        margin-bottom: 15px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 20px;
                        font-size: 12px;
                    }
                    th, td {
                        border: 1px solid #e2e8f0;
                        padding: 8px 10px;
                        text-align: left;
                    }
                    th {
                        background-color: #f1f5f9;
                        color: #475569;
                        font-weight: bold;
                    }
                    .status-paid {
                        color: #16a34a;
                        font-weight: bold;
                        background-color: #dcfce7;
                        padding: 2px 6px;
                        border-radius: 4px;
                        display: inline-block;
                    }
                    .status-unpaid {
                        color: #dc2626;
                        font-weight: bold;
                        background-color: #fee2e2;
                        padding: 2px 6px;
                        border-radius: 4px;
                        display: inline-block;
                    }
                    .amount-column {
                        text-align: right;
                    }
                    .footer {
                        margin-top: 40px;
                        text-align: center;
                        font-size: 10px;
                        color: #94a3b8;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>KB Spasi Community</h1>
                    <p>Financial Report — Academic / Financial Period <b>$periodName</b></p>
                    <p style="font-size: 11px;">Generated on: ${formatDate(System.currentTimeMillis())}</p>
                </div>

                <div class="summary-cards">
                    <div class="card">
                        <div class="label">Starting/Prev Balance</div>
                        <div class="value">${formatRp(startingBalance)}</div>
                    </div>
                    <div class="card">
                        <div class="label">Total Income</div>
                        <div class="value" style="color: #16a34a;">+ ${formatRp(totalIncome)}</div>
                    </div>
                    <div class="card">
                        <div class="label">Total Expenses</div>
                        <div class="value" style="color: #dc2626;">- ${formatRp(totalExpenses)}</div>
                    </div>
                    <div class="card highlight">
                        <div class="label">Final Cash Balance</div>
                        <div class="value">${formatRp(finalBalance)}</div>
                    </div>
                </div>

                <h2>Income & Contributions Timeline</h2>
                <table>
                    <thead>
                        <tr>
                            <th>Date</th>
                            <th>Member Name</th>
                            <th>Description / Note</th>
                            <th>Status</th>
                            <th class="amount-column">Amount</th>
                        </tr>
                    </thead>
                    <tbody>
        """.trimIndent())

        if (contributions.isEmpty()) {
            htmlBuilder.append("""
                <tr>
                    <td colspan="5" style="text-align: center; color: #64748b; font-style: italic;">No contributions recorded for this period.</td>
                </tr>
            """.trimIndent())
        } else {
            contributions.forEach { c ->
                val statusClass = if (c.isPaid) "status-paid" else "status-unpaid"
                val statusText = if (c.isPaid) "Paid" else "Unpaid"
                htmlBuilder.append("""
                    <tr>
                        <td>${formatDate(c.date)}</td>
                        <td><b>${c.memberName}</b></td>
                        <td>${c.notes.ifBlank { "-" }}</td>
                        <td><span class="$statusClass">$statusText</span></td>
                        <td class="amount-column">${formatRp(c.amount)}</td>
                    </tr>
                """.trimIndent())
            }
        }

        htmlBuilder.append("""
                    </tbody>
                </table>

                <h2>Expense Timeline</h2>
                <table>
                    <thead>
                        <tr>
                            <th style="width: 20%;">Date</th>
                            <th style="width: 50%;">Purpose / Category</th>
                            <th style="width: 15%;">Note</th>
                            <th style="width: 15%; text-align: right;">Amount</th>
                        </tr>
                    </thead>
                    <tbody>
        """.trimIndent())

        if (expenses.isEmpty()) {
            htmlBuilder.append("""
                <tr>
                    <td colspan="4" style="text-align: center; color: #64748b; font-style: italic;">No expenses recorded for this period.</td>
                </tr>
            """.trimIndent())
        } else {
            expenses.forEach { e ->
                htmlBuilder.append("""
                    <tr>
                        <td>${formatDate(e.date)}</td>
                        <td><b>${e.purpose}</b></td>
                        <td>${e.notes.ifBlank { "-" }}</td>
                        <td class="amount-column" style="color: #dc2626;">${formatRp(e.amount)}</td>
                    </tr>
                """.trimIndent())
            }
        }

        htmlBuilder.append("""
                    </tbody>
                </table>

                <div class="footer">
                    <p>Report generated digitally by KB Spasi Community App.</p>
                </div>
            </body>
            </html>
        """.trimIndent())

        // Load the HTML in a dynamic offscreen WebView and trigger standard PDF Print spooler
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val jobName = "KB_Spasi_Report_Period_${periodName.replace("/", "-")}"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                
                if (printManager != null) {
                    printManager.print(
                        jobName,
                        printAdapter,
                        PrintAttributes.Builder().build()
                    )
                } else {
                    Toast.makeText(context, "Printing not supported on this device", Toast.LENGTH_SHORT).show()
                }
            }
        }
        // Load the styled HTML
        webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "UTF-8", null)
    }
}
