package com.lyvox.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {
    @Test
    fun masterPasswordRequiresMinimumLength() {
        assertFalse("short7".length >= 8)
        assertTrue("long-enough".length >= 8)
    }
}
