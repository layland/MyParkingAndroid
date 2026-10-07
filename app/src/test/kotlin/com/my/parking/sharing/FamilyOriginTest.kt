package com.my.parking.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FamilyOriginTest {
    @Test fun normalizesOnlySecureOrigins() {
        assertEquals("https://example.workers.dev",FamilyOrigin.validate(" https://EXAMPLE.workers.dev/ "))
    }
    @Test fun rejectsCredentialBearingOrRedirectingConfigurations() {
        listOf("http://example.com","https://user:secret@example.com","https://example.com/path","https://example.com?token=test","https://example.com#fragment","https://example.com:8080","javascript:alert(1)").forEach { value ->
            assertThrows(Exception::class.java) { FamilyOrigin.validate(value) }
        }
    }
}
