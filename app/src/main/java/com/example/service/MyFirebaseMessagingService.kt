package com.example.service

import androidx.core.app.NotificationCompat
import com.example.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Try extracting standard notification payload strings, or fall back to custom data package
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Pembayaran Kas Baru"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "Ada transaksi kas baru yang tersimpan."

        android.util.Log.d("FCM_SERVICE", "Received messaging payload: Title='$title', Body='$body'")
        
        // Show local push notification using helper
        NotificationHelper.showNotification(applicationContext, title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token received when device initially registers with FCM. 
        // Can be logged or updated on custom backend systems.
        android.util.Log.d("FCM_SERVICE", "Refreshed FCM Device registration token: $token")
    }
}
