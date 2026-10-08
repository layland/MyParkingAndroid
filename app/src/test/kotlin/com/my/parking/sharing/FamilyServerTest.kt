package com.my.parking.sharing

import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyServerTest {
    @Test fun defaultServerIsAnAllowedSecureOrigin() {
        assertEquals(FamilyServer.DEFAULT_ORIGIN, FamilyOrigin.validate(FamilyServer.DEFAULT_ORIGIN))
    }
}
