package my.fuelledger.security

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec

class LocalVaultTest {
    private fun vault(seed: Int = 1) = LocalVault { SecretKeySpec(ByteArray(32) { (it + seed).toByte() }, "AES") }

    @Test fun roundTripUsesFreshNoncesAndDoesNotExposePlaintext() {
        val vault = vault()
        val message = "Private financial record: salary RM 12,345.67".toByteArray()
        val a = vault.encrypt(message, "test:financial-record")
        val b = vault.encrypt(message, "test:financial-record")
        assertFalse(a.contentEquals(b))
        assertFalse(a.copyOfRange(8, 20).contentEquals(b.copyOfRange(8, 20)))
        assertFalse(a.toString(Charsets.ISO_8859_1).contains("Private financial record"))
        assertArrayEquals(message, vault.decrypt(a, "test:financial-record"))
        assertArrayEquals(message, vault.decrypt(b, "test:financial-record"))
    }

    @Test fun wrongPurposeWrongKeyAndModifiedCiphertextFailAuthentication() {
        val vault = vault()
        val encrypted = vault.encrypt("receipt information".toByteArray(), "receipt:one")
        assertThrows(AEADBadTagException::class.java) { vault.decrypt(encrypted, "receipt:two") }
        assertThrows(AEADBadTagException::class.java) { vault(2).decrypt(encrypted, "receipt:one") }
        listOf(8, 20, encrypted.lastIndex).forEach { index ->
            val tampered = encrypted.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertThrows(AEADBadTagException::class.java) { vault.decrypt(tampered, "receipt:one") }
        }
    }

    @Test fun malformedVersionTruncatedEnvelopeAndMissingPurposeAreRejected() {
        val vault = vault()
        val encrypted = vault.encrypt(ByteArray(0), "empty-record")
        assertArrayEquals(ByteArray(0), vault.decrypt(encrypted, "empty-record"))
        assertThrows(IOException::class.java) { vault.decrypt(encrypted.copyOf(encrypted.size - 1), "empty-record") }
        assertThrows(IOException::class.java) {
            vault.decrypt(encrypted.copyOf().also { it[7] = 99 }, "empty-record")
        }
        assertThrows(IOException::class.java) { vault.decrypt("plaintext".toByteArray(), "empty-record") }
        assertThrows(IllegalArgumentException::class.java) { vault.encrypt(ByteArray(0), " ") }
        assertThrows(IllegalArgumentException::class.java) { vault.decrypt(encrypted, "x".repeat(501)) }
    }
}
