package com.example.util

import java.util.Locale

object Translations {
    private val idMap = mapOf(
        // General / Header
        "app_title" to "KB SPASI COMMUNITY",
        "toggle_dark_mode" to "Ubah Mode Gelap",
        "logged_out_admin" to "Keluar dari mode Admin",
        "admin" to "Admin",
        "visitor" to "Pengunjung",

        // Navigation Tabs
        "tab_dashboard" to "Dasbor",
        "tab_spreadsheet" to "Lembar Kerja",
        "tab_members" to "Anggota",
        "tab_expenses" to "Pengeluaran",
        "tab_settings" to "Pengaturan",

        // Month Names
        "month_jan" to "Januari",
        "month_feb" to "Februari",
        "month_mar" to "Maret",
        "month_apr" to "April",
        "month_may" to "Mei",
        "month_jun" to "Juni",
        "month_jul" to "Juli",
        "month_aug" to "Agustus",
        "month_sep" to "September",
        "month_oct" to "Oktober",
        "month_nov" to "November",
        "month_dec" to "Desember",
        "all_months" to "Semua Bulan",

        // Dashboard Tab
        "current_cash_balance" to "SALDO KAS SAAT INI",
        "starting_pool" to "Saldo Awal",
        "total_income" to "Total Pemasukan",
        "total_expenses" to "Total Pengeluaran",
        "income_expense_trend" to "Tren Pemasukan & Pengeluaran",
        "income" to "Pemasukan",
        "expenses" to "Pengeluaran",
        "share_via_whatsapp" to "Bagikan via WhatsApp/Telegram",
        "members_tracking_quick_recap" to "PELACAKAN ANGGOTA & REKAP CEPAT",
        "search_member_placeholder" to "Cari nama anggota...",
        "status_all" to "Semua",
        "status_paid" to "Lunas",
        "status_unpaid" to "Belum Lunas",
        "total_paid_pending" to "Total lunas: %d | Belum lunas: %d anggota",
        "no_contributions_match" to "Tidak ada kontribusi anggota yang cocok dengan kriteria.",
        "recent_system_activities" to "AKTIVITAS SISTEM TERBARU",
        "no_recent_activities" to "Belum ada aktivitas terbaru dalam periode ini.",
        "total_members" to "Total Anggota",
        "paid_members" to "Anggota Lunas",
        "unpaid_members" to "Belum Lunas",
        "member_payment_summary" to "RINGKASAN PEMBAYARAN ANGGOTA",
        "view_all_members_btn" to "Lihat Detail di Tab Anggota",

        // Spreadsheet Tab
        "spreadsheet_ledger" to "Lembar Kerja Buku Besar",
        "export_pdf_print" to "Ekspor PDF / Cetak",
        "table_header_no" to "No",
        "table_header_date" to "Tanggal",
        "table_header_member" to "Nama Anggota / Detail",
        "table_header_status" to "Status",
        "table_header_amount" to "Jumlah",
        "no_transactions_recorded" to "Tidak ada transaksi tercatat di lembar kerja.",
        "footer_starting" to "Saldo awal kas",
        "footer_total_income" to "Total Pendapatan Terverifikasi",
        "footer_total_expenses" to "Total Pengeluaran Tercatat",
        "footer_remaining" to "Sisa Saldo Bersih",

        // Contributions Tab
        "income_contributions_header" to "Iuran Pendapatan",
        "income_contributions_sub" to "Pantau kumpulan kas komunitas dan verifikasi daftar pembayaran.",
        "warning_unpaid_title" to "Iuran Belum Lunas",
        "warning_unpaid_desc" to "Terdapat beberapa iuran belum lunas yang memerlukan tindak lanjut.",
        "member_label" to "Anggota",
        "no_contribution_recorded" to "Tidak ada data iuran yang tercatat pada periode ini.",
        "search_by_member_name" to "Cari nama anggota...",

        // Expenses Tab
        "logged_expenses_header" to "Daftar Pengeluaran",
        "logged_expenses_sub" to "Catatan pertanggungjawaban untuk aliran kas keluar komunitas.",
        "search_expense_placeholder" to "Cari tujuan pengeluaran...",
        "no_expense_recorded" to "Tidak ada pengeluaran tercatat pada periode ini.",

        // Settings Tab
        "tab_settings_header" to "Konfigurasi & Administrasi",
        "role_access_management" to "Manajemen Hak Akses",
        "role_access_desc" to "Ubah hak akses peran dengan mudah. Pengunjung hanya dapat melihat laporan sementara admin dapat mengedit data.",
        "role_admin_active" to "Peran Aktif: MODE ADMINISTRATOR",
        "role_active_ops_enabled" to "Operasi aman diaktifkan",
        "role_visitor_active" to "Peran Aktif: PENGUNJUNG VIEW-ONLY",
        "role_visitor_desc" to "Fungsi modifikasi terkunci",
        "logout_admin_button" to "Keluar Admin",
        "login_admin_button" to "Masuk Admin",
        "change_pin_button" to "Ubah PIN/Sandi",
        "enter_new_pin" to "Masukkan PIN/Sandi Baru:",
        "new_passcode_placeholder" to "Kata sandi baru",
        "button_apply" to "Terapkan",
        "button_cancel" to "Batal",
        "financial_periods" to "Periode Keuangan",
        "financial_periods_desc" to "Buat dan periksa beberapa periode keuangan/sekolah",
        "badge_active" to "Aktif",
        "prev_year_remaining" to "Sisa Saldo Tahun Lalu: %s",
        "set_active_button" to "Jadikan Aktif",
        "database_copy_backup" to "Salinan Cadangan Database",
        "database_copy_desc" to "Cadangan ledger offline yang aman. Ekspor file cadangan database atau impor salinan riwayat kapan saja.",
        "audit_backup_ledger" to "Cadangkan & Impor Database",
        "app_language" to "Bahasa & Lokalisasi",
        "app_language_desc" to "Pilih bahasa yang Anda inginkan untuk menu, tombol, laporan, dan daftar data.",
        "expense_tracking" to "Daftar Pengeluaran",
        "transparent_expenditures" to "Catatan pertanggungjawaban untuk aliran kas keluar komunitas.",
        "add_expense" to "Tambah Pengeluaran",
        "date_label" to "Tanggal: %s",
        "prev_remaining" to "Sisa Saldo Tahun Lalu: %s",
        "set_active" to "Jadikan Aktif",
        "database_backup" to "Salinan Cadangan Database",
        "database_backup_desc" to "Cadangan ledger offline yang aman. Ekspor file cadangan database atau impor salinan riwayat kapan saja.",
        "audit_backup" to "Cadangkan & Impor Database",

        // Language settings section
        "language_localization" to "Bahasa & Lokalisasi",
        "language_localization_desc" to "Pilih bahasa yang Anda inginkan untuk menu, tombol, laporan, dan daftar data.",
        "language_indonesian" to "Bahasa Indonesia",
        "language_english" to "English",

        // Dialogs & Fields (Add Period, Add Contribution, Add Expense, Login)
        "dialog_login_title" to "Verifikasi Administrator",
        "dialog_login_desc" to "Masukkan kode sandi/PIN yang benar untuk mengaktifkan fungsi edit data.",
        "dialog_login_placeholder" to "Masukkan sandi PIN...",
        "dialog_login_incorrect" to "Sandi PIN yang dimasukkan salah!",
        "dialog_login_button" to "Masuk",

        "dialog_period_title" to "Buat Periode Keuangan",
        "dialog_period_desc" to "Masukkan nama periode (misal: 2025/2026) dan sisa saldo kas bawaan tahun fiskal sebelumnya.",
        "dialog_period_name" to "Nama Periode (misal: 2025/2026)",
        "dialog_period_starting" to "Saldo awal kas pembukaan (IDR)",
        "dialog_period_set_active" to "Jadikan langsung sebagai periode aktif utama",

        "dialog_contribution_add" to "Tambah Iuran Baru",
        "dialog_contribution_edit" to "Ubah Iuran",
        "dialog_contribution_member" to "Nama Anggota",
        "dialog_contribution_month" to "Periode Bulan",
        "dialog_contribution_amount" to "Jumlah Iuran",
        "dialog_contribution_status" to "Tandai Sudah Lunas Terverifikasi",
        "dialog_contribution_notes" to "Catatan tambahan/pembayaran...",

        "dialog_expense_add" to "Catat Pengeluaran Baru",
        "dialog_expense_edit" to "Ubah Pengeluaran",
        "dialog_expense_purpose" to "Tujuan Pengeluaran / Nama",
        "dialog_expense_amount" to "Jumlah Pengeluaran (IDR)",
        "dialog_expense_notes" to "Catatan pendukung tambahan...",

        // Shared Button Labels
        "button_save" to "Simpan",
        "button_delete" to "Hapus",

        // Toast & Notification Messages
        "toast_logged_out" to "Berhasil keluar dari mode Admin",
        "toast_logged_in" to "Berhasil masuk sebagai Admin",
        "toast_pass_changed" to "PIN Admin berhasil diubah!",
        "toast_backup_exported" to "Data cadangan berhasil disalin ke Clipboard!",
        "toast_backup_imported" to "Data cadangan berhasil dipulihkan!",
        "toast_backup_import_failed" to "Pemulihan Gagal! Format JSON tidak valid.",
        "toast_contribution_added" to "Iuran baru berhasil ditambahkan!",
        "toast_contribution_updated" to "Iuran berhasil diperbarui!",
        "toast_contribution_deleted" to "Iuran berhasil dihapus!",
        "toast_expense_added" to "Pengeluaran baru berhasil dicatat!",
        "toast_expense_updated" to "Pengeluaran berhasil diperbarui!",
        "toast_expense_deleted" to "Pengeluaran berhasil dihapus!",
        "toast_period_created" to "Periode keuangan baru berhasil dibuat!",
        "toast_period_deleted" to "Periode berhasil dihapus!",
        "toast_lang_changed" to "Bahasa diubah ke Bahasa Indonesia!",

        // Dynamic System Notifications Template parts (for display translation)
        "notif_verified_paid_phrase" to "%s membayar iuran %s (%s)",
        "notif_marked_unpaid_phrase" to "%s ditandai belum lunas untuk %s",
        "notif_added_expense_phrase" to "Pengeluaran baru dicatat: %s sebesar %s",
        "notif_deleted_expense_phrase" to "Menghapus pengeluaran: %s sebesar %s",
        "notif_deleted_contribution_phrase" to "Menghapus iuran %s sebesar %s (%s)",
        "notif_password_changed_phrase" to "Membatalkan atau mengubah sandi PIN administrator utama",
        "notif_period_created_phrase" to "Membuka periode keuangan baru: %s",
        "notif_period_set_active_phrase" to "Mengaktifkan periode keuangan: %s"
    )

    private val enMap = mapOf(
        // General / Header
        "app_title" to "KB SPASI COMMUNITY",
        "toggle_dark_mode" to "Toggle Dark Mode",
        "logged_out_admin" to "Logged out of Admin mode",
        "admin" to "Admin",
        "visitor" to "Visitor",

        // Navigation Tabs
        "tab_dashboard" to "Dashboard",
        "tab_spreadsheet" to "Spreadsheet",
        "tab_members" to "Members",
        "tab_expenses" to "Expenses",
        "tab_settings" to "Settings",

        // Month Names
        "month_jan" to "January",
        "month_feb" to "February",
        "month_mar" to "March",
        "month_apr" to "April",
        "month_may" to "May",
        "month_jun" to "June",
        "month_jul" to "July",
        "month_aug" to "August",
        "month_sep" to "September",
        "month_oct" to "October",
        "month_nov" to "November",
        "month_dec" to "December",
        "all_months" to "All Months",

        // Dashboard Tab
        "current_cash_balance" to "CURRENT CASH BALANCE",
        "starting_pool" to "Starting pool",
        "total_income" to "Total Income",
        "total_expenses" to "Total Expenses",
        "income_expense_trend" to "Income & Expense Trend",
        "income" to "Income",
        "expenses" to "Expenses",
        "share_via_whatsapp" to "WhatsApp/Telegram Share",
        "members_tracking_quick_recap" to "MEMBERS TRACKING & QUICK RECAP",
        "search_member_placeholder" to "Search by member name...",
        "status_all" to "All",
        "status_paid" to "Paid",
        "status_unpaid" to "Unpaid",
        "total_paid_pending" to "Total paid: %d | Pending payment: %d members",
        "no_contributions_match" to "No recorded member contributions match criteria.",
        "recent_system_activities" to "RECENT SYSTEM ACTIVITIES",
        "no_recent_activities" to "No recent active logs within this period.",
        "total_members" to "Total Members",
        "paid_members" to "Paid Members",
        "unpaid_members" to "Unpaid Members",
        "member_payment_summary" to "MEMBER PAYMENT SUMMARY",
        "view_all_members_btn" to "View Details in Members Tab",

        // Spreadsheet Tab
        "spreadsheet_ledger" to "Spreadsheet Ledger",
        "export_pdf_print" to "Export PDF / Print",
        "table_header_no" to "No",
        "table_header_date" to "Date",
        "table_header_member" to "Member Name / Detail",
        "table_header_status" to "Status",
        "table_header_amount" to "Amount",
        "no_transactions_recorded" to "No transactions recorded in spreadsheet.",
        "footer_starting" to "Starting pool balance",
        "footer_total_income" to "Total Verified Income",
        "footer_total_expenses" to "Total Logged Expenses",
        "footer_remaining" to "Remaining Net Balance",

        // Contributions Tab
        "income_contributions_header" to "Income Contributions",
        "income_contributions_sub" to "Monitor community cash pools and payment list verification.",
        "warning_unpaid_title" to "Unpaid Contributions",
        "warning_unpaid_desc" to "There are unpaid contributions that need follow-up.",
        "member_label" to "Member",
        "no_contribution_recorded" to "No contribution data recorded in this period.",
        "search_by_member_name" to "Search by member name...",

        // Expenses Tab
        "logged_expenses_header" to "Logged Expenses",
        "logged_expenses_sub" to "Accountability logs for community outgoing cash flow investments.",
        "search_expense_placeholder" to "Search expenses purpose...",
        "no_expense_recorded" to "No expenses recorded in this period.",

        // Settings Tab
        "tab_settings_header" to "Config & Period Administration",
        "role_access_management" to "Role Access Management",
        "role_access_desc" to "Switch role privileges easily. Visitors can solely audit reports while administrators can operate data edits.",
        "role_admin_active" to "Current: ADMINISTRATOR MODE",
        "role_active_ops_enabled" to "Secure operations enabled",
        "role_visitor_active" to "Current: VISITOR VIEW-ONLY",
        "role_visitor_desc" to "Modification functions locked",
        "logout_admin_button" to "Logout Admin",
        "login_admin_button" to "Login Admin",
        "change_pin_button" to "Change Pin/Password",
        "enter_new_pin" to "Enter New Password/Pin:",
        "new_passcode_placeholder" to "New passcode",
        "button_apply" to "Apply",
        "button_cancel" to "Cancel",
        "financial_periods" to "Financial Periods",
        "financial_periods_desc" to "Create and audit multiple school/financial periods",
        "badge_active" to "Active",
        "prev_year_remaining" to "Prev Year Remaining: %s",
        "set_active_button" to "Set Active",
        "database_copy_backup" to "Database Copy Backup",
        "database_copy_desc" to "Safety-first offline ledger backups. Backup database queries easily or load/import historical copies anytime.",
        "audit_backup_ledger" to "Audit Backup Ledger",
        "app_language" to "Language & Localization",
        "app_language_desc" to "Choose your preferred language for menus, buttons, reports, and data lists.",
        "expense_tracking" to "Logged Expenses",
        "transparent_expenditures" to "Accountability logs for community outgoing cash flow investments.",
        "add_expense" to "Add Expense",
        "date_label" to "Date: %s",
        "prev_remaining" to "Prev Year Remaining: %s",
        "set_active" to "Set Active",
        "database_backup" to "Database Copy Backup",
        "database_backup_desc" to "Safety-first offline ledger backups. Backup database queries easily or load/import historical copies anytime.",
        "audit_backup" to "Audit Backup Ledger",

        // Language settings section
        "language_localization" to "Language & Localization",
        "language_localization_desc" to "Choose your preferred language for menus, buttons, reports, and data lists.",
        "language_indonesian" to "Indonesian",
        "language_english" to "English",

        // Dialogs & Fields (Add Period, Add Contribution, Add Expense, Login)
        "dialog_login_title" to "Administrator Verification",
        "dialog_login_desc" to "Provide correct pass code PIN to enable data modifying actions.",
        "dialog_login_placeholder" to "Enter PIN password...",
        "dialog_login_incorrect" to "Incorrect PIN passcode entry!",
        "dialog_login_button" to "Login",

        "dialog_period_title" to "Create School Financial Period",
        "dialog_period_desc" to "Enter specific name (e.g. 2025/2026) and previous year's carried-over remaining pool balance.",
        "dialog_period_name" to "Period Name (e.g., 2025/2026)",
        "dialog_period_starting" to "First opening starting cash balance (IDR)",
        "dialog_period_set_active" to "Directly set as active primary period",

        "dialog_contribution_add" to "Add New Contribution",
        "dialog_contribution_edit" to "Edit Contribution",
        "dialog_contribution_member" to "Member Name",
        "dialog_contribution_month" to "Month Period",
        "dialog_contribution_amount" to "Contribution Amount",
        "dialog_contribution_status" to "Mark as Verified Paid",
        "dialog_contribution_notes" to "Additional notes...",

        // Expenses Dialog
        "dialog_expense_add" to "Log New Outgoing Expense",
        "dialog_expense_edit" to "Edit Expense Dialog",
        "dialog_expense_purpose" to "Spending Purpose / Name",
        "dialog_expense_amount" to "Amount",
        "dialog_expense_notes" to "Additional notes...",

        // Shared Button Labels
        "button_save" to "Save",
        "button_delete" to "Delete",

        // Toast & Notification Messages
        "toast_logged_out" to "Logged out of Admin mode",
        "toast_logged_in" to "Successfully logged in as Admin",
        "toast_pass_changed" to "Password changed!",
        "toast_backup_exported" to "Backup data saved to Clipboard successfully!",
        "toast_backup_imported" to "Backup data restored successfully!",
        "toast_backup_import_failed" to "Database Restore Failed! Invalid JSON string.",
        "toast_contribution_added" to "New contribution added!",
        "toast_contribution_updated" to "Contribution updated!",
        "toast_contribution_deleted" to "Contribution deleted!",
        "toast_expense_added" to "New expense transaction logged!",
        "toast_expense_updated" to "Expense updated!",
        "toast_expense_deleted" to "Expense deleted!",
        "toast_period_created" to "New active period created!",
        "toast_period_deleted" to "Period deleted!",
        "toast_lang_changed" to "Language changed to English!",

        // Dynamic System Notifications Template parts (for display translation)
        "notif_verified_paid_phrase" to "%s paid contribution of %s (%s)",
        "notif_marked_unpaid_phrase" to "%s marked unpaid for %s",
        "notif_added_expense_phrase" to "Logged a new expense: %s of %s",
        "notif_deleted_expense_phrase" to "Deleted expense log: %s of %s",
        "notif_deleted_contribution_phrase" to "Deleted contribution of %s: %s (%s)",
        "notif_password_changed_phrase" to "Updated administrator PIN authentication pass",
        "notif_period_created_phrase" to "Opened new financial period: %s",
        "notif_period_set_active_phrase" to "Activated financial period: %s"
    )

    fun get(key: String, lang: String): String {
        return if (lang == "en") {
            enMap[key] ?: idMap[key] ?: key
        } else {
            idMap[key] ?: key
        }
    }

    fun get(key: String, lang: String, vararg args: Any): String {
        val template = if (lang == "en") {
            enMap[key] ?: idMap[key] ?: key
        } else {
            idMap[key] ?: key
        }
        return try {
            String.format(template, *args)
        } catch (e: Exception) {
            template
        }
    }
}
