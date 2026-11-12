package kio.chat

import kio.chat.domain.MockRepositoryImpl
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionTest {
    @Test
    fun logoutRevokesOnlyCurrentSession() = runBlocking {
        val repo = MockRepositoryImpl()
        val user = repo.createUser("session-test", "password")!!
        val current = repo.createSession(user.id)
        val other = repo.createSession(user.id)
        repo.deleteSession(current)
        assertNull(repo.getSessionById(current))
        assertNotNull(repo.getSessionById(other))
        repo.deleteSession(current)
    }

    @Test
    fun sessionCookieRenewalAndRemoval() {
        val cookie = sessionCookie("session-id")
        assertEquals(30 * 24 * 60 * 60, cookie.maxAge)
        assertEquals("/", cookie.path)
        assertEquals(true, cookie.httpOnly)
        assertEquals("Lax", cookie.extensions["SameSite"])
        val expired = sessionCookie("").copy(maxAge = 0)
        assertEquals("", expired.value)
        assertEquals(0, expired.maxAge)
        assertEquals(cookie.path, expired.path)
        assertEquals(cookie.secure, expired.secure)
    }
}
