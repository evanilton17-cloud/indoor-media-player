package com.indoor.media

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseConfig {

    var configured = false
        private set

    fun init(context: Context) {
        if (configured || FirebaseApp.getApps(context).isNotEmpty()) {
            configured = FirebaseApp.getApps(context).isNotEmpty()
            return
        }
        if (API_KEY.isBlank() || APP_ID.isBlank()) return
        try {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(APP_ID)
                    .setApiKey(API_KEY)
                    .setDatabaseUrl(DATABASE_URL)
                    .setStorageBucket(STORAGE_BUCKET)
                    .build()
            )
            configured = true
        } catch (e: Exception) {
            configured = false
        }
    }

    private const val API_KEY = ""
    private const val APP_ID = ""
    private const val DATABASE_URL = "https://seu-projeto-default-rtdb.firebaseio.com"
    private const val STORAGE_BUCKET = "seu-projeto.appspot.com"
}