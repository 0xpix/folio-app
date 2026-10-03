package com.pix.folio.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * App-private encrypted storage for the imported Scalable broker snapshot.
 *
 * The key is generated inside Android Keystore and is non-exportable. The encrypted snapshot is
 * not part of FolioBackup and Android backup is disabled in the manifest.
 */
class SecureScalableStore(context: Context) {
    private val appContext = context.applicationContext
    private val snapshotFile: File
        get() = File(appContext.filesDir, FILE_NAME)

    fun import(raw: String): Result<ScalableSnapshot> = runCatching {
        val snapshot = ScalableSnapshotCodec.parse(raw)
        val sanitized = ScalableSnapshotCodec.encode(snapshot).toByteArray(Charsets.UTF_8)
        writeEncrypted(sanitized)
        snapshot
    }

    fun load(): Result<ScalableSnapshot?> = runCatching {
        val file = snapshotFile
        if (!file.isFile) return@runCatching null
        val plaintext = decrypt(file.readBytes())
        ScalableSnapshotCodec.parse(plaintext.toString(Charsets.UTF_8))
    }

    fun clear() {
        runCatching { snapshotFile.delete() }
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    fun exists(): Boolean = snapshotFile.isFile

    private fun writeEncrypted(plaintext: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv

        val temp = File(appContext.filesDir, "$FILE_NAME.tmp")
        DataOutputStream(temp.outputStream().buffered()).use { out ->
            out.writeInt(FILE_VERSION)
            out.writeInt(iv.size)
            out.write(iv)
            out.writeInt(ciphertext.size)
            out.write(ciphertext)
        }
        check(temp.renameTo(snapshotFile) || runCatching {
            temp.copyTo(snapshotFile, overwrite = true)
            temp.delete()
            true
        }.getOrDefault(false)) {
            "Could not store encrypted Scalable snapshot"
        }
    }

    private fun decrypt(payload: ByteArray): ByteArray {
        DataInputStream(payload.inputStream()).use { input ->
            require(input.readInt() == FILE_VERSION) { "Unsupported encrypted Scalable snapshot" }
            val ivLength = input.readInt()
            require(ivLength in 12..32) { "Invalid encrypted Scalable snapshot" }
            val iv = ByteArray(ivLength).also(input::readFully)
            val ciphertextLength = input.readInt()
            require(ciphertextLength in 1..MAX_CIPHERTEXT_BYTES) {
                "Invalid encrypted Scalable snapshot"
            }
            val ciphertext = ByteArray(ciphertextLength).also(input::readFully)
            require(input.read() == -1) { "Invalid encrypted Scalable snapshot" }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getExistingKey(),
                GCMParameterSpec(128, iv),
            )
            return cipher.doFinal(ciphertext)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setUnlockedDeviceRequired(true)
            }

            init(builder.build())
            generateKey()
        }
    }

    private fun getExistingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: error("Scalable encryption key is unavailable; re-import the snapshot")
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "folio.scalable.snapshot.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FILE_NAME = "scalable_snapshot_v1.enc"
        const val FILE_VERSION = 1
        const val MAX_CIPHERTEXT_BYTES = 2 * 1024 * 1024
    }
}
