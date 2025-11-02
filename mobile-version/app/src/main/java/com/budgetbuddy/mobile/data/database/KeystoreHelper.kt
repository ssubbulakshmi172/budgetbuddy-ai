package com.budgetbuddy.mobile.data.database

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Helper class for Android Keystore operations
 */
class KeystoreHelper(private val context: Context) {
    
    private val keyStoreAlias = "budgetbuddy_keystore"
    
    /**
     * Gets or creates a key from Android Keystore
     */
    suspend fun getOrCreateKey(keyAlias: String): String? = withContext(Dispatchers.IO) {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            if (keyStore.containsAlias(keyAlias)) {
                // Key exists, retrieve it
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                
                val encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    "secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                
                encryptedPrefs.getString(keyAlias, null)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Stores a key securely using Android Keystore
     */
    suspend fun storeKey(keyAlias: String, value: String) = withContext(Dispatchers.IO) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                "secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            encryptedPrefs.edit().putString(keyAlias, value).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

