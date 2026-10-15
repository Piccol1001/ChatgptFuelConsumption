package my.fuelledger.data

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKey
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import com.google.crypto.tink.util.SecretBytes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password-derived, segmented authenticated encryption using Google's maintained Tink primitive.
 * Envelope version 1 fixes all algorithms and costs; the complete header is authenticated AAD.
 * Callers must consume decrypting streams through EOF before accepting any restored records.
 */
internal object BackupCrypto {
    private val magic = "FUELBKUP".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val ALGORITHM = 1 // PBKDF2-SHA256 + AES256-GCM-HKDF-SHA256 streaming, 64 KiB segments.
    private const val ITERATIONS = 600_000
    private const val SALT_BYTES = 32
    private const val HEADER_BYTES = 8 + 1 + 1 + 4 + SALT_BYTES
    private const val SEGMENT_BYTES = 64 * 1024
    private val parameters = AesGcmHkdfStreamingParameters.builder()
        .setKeySizeBytes(32)
        .setDerivedAesGcmKeySizeBytes(32)
        .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
        .setCiphertextSegmentSizeBytes(SEGMENT_BYTES)
        .build()

    fun encryptingStream(output: OutputStream, password: CharArray): OutputStream {
        require(password.size in 12..1024) { "Use a backup password containing 12–1024 characters." }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val header = ByteArrayOutputStream(HEADER_BYTES).also { bytes ->
            DataOutputStream(bytes).apply {
                write(magic)
                writeByte(VERSION)
                writeByte(ALGORITHM)
                writeInt(ITERATIONS)
                write(salt)
            }
        }.toByteArray()
        val primitive = primitive(password, salt)
        output.write(header)
        return primitive.newEncryptingStream(output, header)
    }

    fun decryptingStream(input: InputStream, password: CharArray): InputStream {
        require(password.size in 1..1024) { "Enter the backup password." }
        val header = ByteArray(HEADER_BYTES)
        DataInputStream(input).readFully(header)
        val data = DataInputStream(ByteArrayInputStream(header))
        val prefix = ByteArray(magic.size).also { data.readFully(it) }
        require(prefix.contentEquals(magic)) { "This is not an encrypted backup. Use Import legacy backup for old JSON or ZIP files." }
        require(data.readUnsignedByte() == VERSION && data.readUnsignedByte() == ALGORITHM) {
            "This encrypted backup uses an unsupported format version."
        }
        // Do not let an untrusted header request arbitrary KDF work or weaker parameters.
        require(data.readInt() == ITERATIONS) { "This encrypted backup uses unsupported password parameters." }
        val salt = ByteArray(SALT_BYTES).also { data.readFully(it) }
        return primitive(password, salt).newDecryptingStream(input, header)
    }

    private fun primitive(password: CharArray, salt: ByteArray): StreamingAead {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        } finally {
            spec.clearPassword()
        }
        val bytes = key.encoded
        return try {
            val material = SecretBytes.copyFrom(bytes, InsecureSecretKeyAccess.get())
            AesGcmHkdfStreaming.create(AesGcmHkdfStreamingKey.create(parameters, material))
        } finally {
            bytes.fill(0)
            runCatching { key.destroy() }
        }
    }
}
