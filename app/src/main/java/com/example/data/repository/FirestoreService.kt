package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

/**
 * Central Firestore & Firebase Auth provider for SONA-WANE GOLD.
 *
 * Always connects to the provisioned named Firestore database
 * (`ai-studio-android-sonawane-3bad5ba9-1ba6-446a-acfb-58b1d8b514a7`)
 * and never uses the unprovisioned `(default)` database instance.
 */
object FirestoreService {

    const val DATABASE_ID = "ai-studio-android-sonawane-3bad5ba9-1ba6-446a-acfb-58b1d8b514a7"
    private const val TAG = "FirestoreService"

    fun resolveDatabaseId(context: Context? = null): String {
        val configured = context?.let {
            runCatching { it.applicationContext.getString(R.string.firestore_database_id) }
                .getOrNull()
                ?.trim()
        }
        return configured?.takeIf { it.isNotBlank() } ?: DATABASE_ID
    }

    fun ensureFirebaseApp(context: Context): FirebaseApp {
        val appContext = context.applicationContext
        val existing = runCatching { FirebaseApp.getApps(appContext) }.getOrNull().orEmpty()
        if (existing.isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }
        return FirebaseApp.getInstance()
    }

    fun getDatabase(context: Context): FirebaseFirestore {
        val app = ensureFirebaseApp(context)
        val dbId = resolveDatabaseId(context)
        return FirebaseFirestore.getInstance(app, dbId)
    }

    fun getAuth(context: Context? = null): FirebaseAuth {
        if (context != null) {
            runCatching { ensureFirebaseApp(context) }
        }
        return FirebaseAuth.getInstance()
    }

    fun currentUserOrNull(context: Context? = null): FirebaseUser? {
        return runCatching { getAuth(context).currentUser }.getOrNull()
    }

    inline fun <reified T : Any> DocumentSnapshot.toObjectSafely(
        behavior: ServerTimestampBehavior = ServerTimestampBehavior.ESTIMATE,
    ): T? {
        if (!exists()) return null
        return try {
            toObject(T::class.java, behavior)
        } catch (e: Exception) {
            Log.w("FirestoreService", "Safe deserialization failed for ${T::class.java.simpleName} at $reference: ${e.message}")
            null
        }
    }

    inline fun <reified T : Any> QuerySnapshot.toObjectsSafely(
        behavior: ServerTimestampBehavior = ServerTimestampBehavior.ESTIMATE,
    ): List<T> {
        return documents.mapNotNull { doc ->
            doc.toObjectSafely<T>(behavior)
        }
    }
}
