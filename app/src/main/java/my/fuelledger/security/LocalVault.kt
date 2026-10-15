package my.fuelledger.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Android's AES-GCM primitive, with a non-exportable Android Keystore master key. */
class LocalVault(private val keyProvider: () -> SecretKey) {
    constructor(context: Context) : this({ androidKey(context.applicationContext.packageName) })

    fun encrypt(bytes: ByteArray, purpose: String): ByteArray {
        val aad = purposeBytes(purpose)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Android Keystore supplies a fresh cryptographically random nonce for every encryption.
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        check(cipher.iv.size == NONCE_SIZE) { "Unsupported encryption nonce." }
        cipher.updateAAD(MAGIC)
        cipher.updateAAD(aad)
        return MAGIC + cipher.iv + cipher.doFinal(bytes)
    }

    fun decrypt(bytes: ByteArray, purpose: String): ByteArray {
        val aad = purposeBytes(purpose)
        if (bytes.size < MAGIC.size + NONCE_SIZE + TAG_SIZE ||
            !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IOException("Unsupported encrypted local file.")
        }
        val nonce = bytes.copyOfRange(MAGIC.size, MAGIC.size + NONCE_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_SIZE * 8, nonce))
        cipher.updateAAD(MAGIC)
        cipher.updateAAD(aad)
        return cipher.doFinal(bytes, MAGIC.size + NONCE_SIZE, bytes.size - MAGIC.size - NONCE_SIZE)
    }

    private fun purposeBytes(purpose: String): ByteArray {
        require(purpose.isNotBlank() && purpose.length <= 500) { "Encryption purpose is required." }
        return purpose.toByteArray(Charsets.UTF_8)
    }

    companion object {
        private val MAGIC = byteArrayOf(0x46, 0x4c, 0x56, 0x41, 0x55, 0x4c, 0x54, 1)
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
        private val keyLock = Any()

        private fun androidKey(packageName: String): SecretKey = synchronized(keyLock) {
            val alias = "$packageName.local-vault.v1"
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            ).apply {
                init(KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build())
            }.generateKey()
        }
    }
}
