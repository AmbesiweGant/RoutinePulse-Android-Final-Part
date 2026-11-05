package com.routinepulse.app

import com.routinepulse.app.domain.FieldError
import com.routinepulse.app.domain.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test fun email_valid_addresses_pass() {
        assertNull(Validators.email("student@example.com"))
        assertNull(Validators.email("  first.last+tag@uni.ac.za  ")) // surrounding spaces are trimmed
    }

    @Test fun email_empty_or_blank_is_rejected() {
        assertEquals(FieldError.EMPTY, Validators.email(""))
        assertEquals(FieldError.EMPTY, Validators.email("   "))
    }

    @Test fun email_malformed_is_rejected() {
        assertEquals(FieldError.INVALID_EMAIL, Validators.email("not-an-email"))
        assertEquals(FieldError.INVALID_EMAIL, Validators.email("a@b"))
        assertEquals(FieldError.INVALID_EMAIL, Validators.email("@example.com"))
    }

    @Test fun newPassword_rules() {
        assertNull(Validators.newPassword("Study2026"))
        assertEquals(FieldError.EMPTY, Validators.newPassword(""))
        assertEquals(FieldError.PASSWORD_TOO_SHORT, Validators.newPassword("abc12"))
        assertEquals(FieldError.PASSWORD_WEAK, Validators.newPassword("onlyletters"))
        assertEquals(FieldError.PASSWORD_WEAK, Validators.newPassword("12345678"))
        assertEquals(FieldError.TOO_LONG, Validators.newPassword("a1".repeat(40)))
    }

    @Test fun loginPassword_only_checks_not_empty() {
        assertNull(Validators.loginPassword("x"))
        assertEquals(FieldError.EMPTY, Validators.loginPassword(""))
    }

    @Test fun displayName_rules() {
        assertNull(Validators.displayName("Lindiwe"))
        assertEquals(FieldError.EMPTY, Validators.displayName(" "))
        assertEquals(FieldError.NAME_TOO_SHORT, Validators.displayName("L"))
        assertEquals(FieldError.TOO_LONG, Validators.displayName("x".repeat(51)))
    }

    @Test fun habitTitle_rules() {
        assertNull(Validators.habitTitle("Read 20 min"))
        assertEquals(FieldError.EMPTY, Validators.habitTitle(""))
        assertEquals(FieldError.TOO_LONG, Validators.habitTitle("x".repeat(81)))
    }

    @Test fun customDays_required_only_for_custom_frequency() {
        assertEquals(FieldError.CUSTOM_DAYS_REQUIRED, Validators.customDays(true, emptySet()))
        assertNull(Validators.customDays(true, setOf(1, 3)))
        assertNull(Validators.customDays(false, emptySet()))
    }

    @Test fun noteFields_rules() {
        assertNull(Validators.noteSubject("Biology"))
        assertEquals(FieldError.EMPTY, Validators.noteTitle(""))
        assertEquals(FieldError.TOO_LONG, Validators.noteTitle("x".repeat(121)))
        assertEquals(FieldError.EMPTY, Validators.noteContent("   "))
        assertNull(Validators.noteContent("Some content"))
    }
}
