package kio.chat.util

import org.kotlincrypto.hash.sha2.SHA256

// Matches kio-note's hash format; only used by the development Mock repository.
fun hashPassword(password: String): String {
    val digest = SHA256()
    digest.update(password.encodeToByteArray())
    return digest.digest().toHexString()
}
