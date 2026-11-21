package com.moraswood.qande.assets

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Owner PIN for the asset review queue: PBKDF2WithHmacSHA256, random 16-byte salt, 120 000 iterations,
 * 256-bit hash; only salt + hash are stored (kv `asset-owner-pin-v1`), compared in constant time.
 */
@Serializable
data class PinRecord(val salt: String, val hash: String, val iterations: Int)

object OwnerPin {
    const val KEY = "asset-owner-pin-v1"
    const val ITERATIONS = 120_000
    private val PIN = Regex("^[0-9]{4,8}$")

    fun valid(pin: String) = PIN.matches(pin)

    fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    fun create(pin: String, random: SecureRandom = SecureRandom(), iterations: Int = ITERATIONS): PinRecord {
        require(valid(pin)) { "PIN must be 4 to 8 digits." }
        val salt = ByteArray(16).also(random::nextBytes)
        return PinRecord(hex(salt), hex(derive(pin, salt, iterations)), iterations)
    }

    fun verify(pin: String, r: PinRecord): Boolean =
        valid(pin) && MessageDigest.isEqual(derive(pin, unhex(r.salt), r.iterations), unhex(r.hash))

    fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
