package com.example.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.File
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

enum class SecurityBacking {
    STRONGBOX,
    TEE_HARDWARE,
    SOFTWARE_FALLBACK,
    UNAVAILABLE,
}

data class KeyVaultStatus(
    val hasKey: Boolean,
    val maskedPreview: String?,
    val backing: SecurityBacking,
    val selectedModelId: String,
)

/**
 * Encrypts and stores the user's Gemini API key using a non-exportable AES-256-GCM key
 * held inside the device's Android Keystore (StrongBox Secure Element when available,
 * falling back to Hardware TEE). Ciphertext is persisted in noBackupFilesDir so it never
 * leaves the device via cloud backup or device transfer, and no API key is ever hardcoded.
 */
class SecureEnclaveKeyStore(private val context: Context) {

    private val vaultDir = File(context.noBackupFilesDir, VAULT_DIR_NAME)
    private val encryptedKeyFile = File(vaultDir, ENCRYPTED_KEY_FILENAME)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStatus(): KeyVaultStatus {
        val hasKey = hasApiKey()
        val masked = if (hasKey) {
            runCatching { getApiKey() }.getOrNull()?.let { maskKey(it) }
        } else {
            null
        }
        val backing = if (hasKey) {
            inspectKeyBacking()
        } else {
            detectDeviceEnclaveCapability()
        }
        return KeyVaultStatus(
            hasKey = hasKey,
            maskedPreview = masked,
            backing = backing,
            selectedModelId = getSelectedModelId(),
        )
    }

    fun hasApiKey(): Boolean = encryptedKeyFile.exists() && encryptedKeyFile.length() > 16L

    fun saveApiKey(rawKey: String): KeyVaultStatus {
        val trimmed = rawKey.trim()
        require(trimmed.isNotEmpty()) { "API key cannot be blank." }
        require(trimmed.length >= 16) { "API key appears too short." }

        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val plaintextBytes = trimmed.toByteArray(Charsets.UTF_8)
        val ciphertext = try {
            cipher.doFinal(plaintextBytes)
        } finally {
            plaintextBytes.fill(0)
        }

        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }

        val payload = ByteArray(1 + iv.size + ciphertext.size)
        payload[0] = iv.size.toByte()
        System.arraycopy(iv, 0, payload, 1, iv.size)
        System.arraycopy(ciphertext, 0, payload, 1 + iv.size, ciphertext.size)
        encryptedKeyFile.writeBytes(payload)

        return getStatus()
    }

    fun getApiKey(): String? {
        if (!hasApiKey()) return null
        val payload = encryptedKeyFile.readBytes()
        if (payload.size < 2) return null

        val ivLength = payload[0].toInt() and 0xFF
        if (ivLength <= 0 || payload.size <= 1 + ivLength) return null

        val iv = payload.copyOfRange(1, 1 + ivLength)
        val ciphertext = payload.copyOfRange(1 + ivLength, payload.size)

        val secretKey = getExistingMasterKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plaintextBytes = cipher.doFinal(ciphertext)
        return try {
            String(plaintextBytes, Charsets.UTF_8)
        } finally {
            plaintextBytes.fill(0)
        }
    }

    fun clearApiKey(): KeyVaultStatus {
        if (encryptedKeyFile.exists()) {
            encryptedKeyFile.delete()
        }
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        }
        return getStatus()
    }

    fun saveSelectedModelId(modelId: String): KeyVaultStatus {
        prefs.edit().putString(KEY_SELECTED_MODEL, modelId).apply()
        return getStatus()
    }

    fun getSelectedModelId(): String {
        return prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID
    }

    private fun getExistingMasterKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey
    }

    private fun getOrCreateMasterKey(): SecretKey {
        getExistingMasterKey()?.let { return it }

        val supportsStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        if (supportsStrongBox) {
            try {
                return generateKey(useStrongBox = true)
            } catch (_: StrongBoxUnavailableException) {
                // Fall back to TEE hardware-backed keystore
            } catch (_: ProviderException) {
                // Fall back to TEE hardware-backed keystore on devices where StrongBox fails
            }
        }
        return generateKey(useStrongBox = false)
    }

    private fun generateKey(useStrongBox: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun inspectKeyBacking(): SecurityBacking {
        return runCatching {
            val key = getExistingMasterKey() ?: return SecurityBacking.UNAVAILABLE
            val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> SecurityBacking.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> SecurityBacking.TEE_HARDWARE
                    else -> SecurityBacking.SOFTWARE_FALLBACK
                }
            } else {
                @Suppress("DEPRECATION")
                if (keyInfo.isInsideSecureHardware) {
                    SecurityBacking.TEE_HARDWARE
                } else {
                    SecurityBacking.SOFTWARE_FALLBACK
                }
            }
        }.getOrDefault(SecurityBacking.UNAVAILABLE)
    }

    private fun detectDeviceEnclaveCapability(): SecurityBacking {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        ) {
            SecurityBacking.STRONGBOX
        } else {
            SecurityBacking.TEE_HARDWARE
        }
    }

    private fun maskKey(key: String): String {
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••••••${key.takeLast(4)}"
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "inkclean_gemini_api_key_aes_gcm_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val VAULT_DIR_NAME = "vault"
        private const val ENCRYPTED_KEY_FILENAME = "gemini_key.enc"
        private const val PREFS_NAME = "inkclean_vault_prefs"
        private const val KEY_SELECTED_MODEL = "selected_model_id"
        const val DEFAULT_MODEL_ID = "gemini-nano-banana-2.1"
    }
}
