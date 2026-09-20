package org.fossify.messages.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These rules gate a delete button, so the tests that matter most are the ones asserting what is
 * *not* a verification code. A missed code costs nothing; a false positive risks somebody's
 * messages.
 */
class TwoFactorDetectionTest {

    @Test
    fun `recognises the codes services actually send`() {
        listOf(
            "Your Apple Account Code is: 161283. Don't share it with anyone.",
            "483920 is your Facebook code",
            "Capital One won't call you for this code. The temporary code is 227898",
            "Your Proton verification code is: 163833",
            "G-728391 is your Google verification code",
            "Use 4821 to verify your phone number",
            "Your one-time passcode is 99201",
            "PIN: 5567 - do not share",
        ).forEach {
            assertTrue("should be a code: $it", isVerificationCodeText(it))
        }
    }

    @Test
    fun `leaves ordinary messages containing numbers alone`() {
        listOf(
            "See you at 7",
            "I'll be there in 15 minutes",
            "My address is 4821 Oak Street",
            "Call me back on 5551234",
            "The total came to 2350 which is more than I expected",
            "Flight AA1234 lands at 8:15",
            "Booked, table for two at 7",
            "Running about 10 minutes late, sorry!",
        ).forEach {
            assertFalse("should not be a code: $it", isVerificationCodeText(it))
        }
    }

    @Test
    fun `needs both a code and a word saying it is one`() {
        // digits with no context
        assertFalse(isVerificationCodeText("483920"))
        // the word without any digits
        assertFalse(isVerificationCodeText("Please verify your account"))
        // both present
        assertTrue(isVerificationCodeText("Please verify with 483920"))
    }

    @Test
    fun `ignores digit runs that are too short or too long to be codes`() {
        assertFalse(isVerificationCodeText("Your code is 12"))
        assertFalse(isVerificationCodeText("Your code is 1234567890123"))
        assertTrue(isVerificationCodeText("Your code is 1234"))
        assertTrue(isVerificationCodeText("Your code is 12345678"))
    }

    @Test
    fun `treats short codes and sender ids as automated`() {
        listOf("262966", "30368", "22395", "VERIFY", "Amazon", "PayPal").forEach {
            assertTrue("should be automated: $it", it.isAutomatedSender())
        }
    }

    @Test
    fun `treats real phone numbers and email gateways as people`() {
        listOf(
            "+15550101",
            "5550101",
            "+442071838750",
            "15551234567",
            "someone@example.com",
        ).forEach {
            assertFalse("should not be automated: $it", it.isAutomatedSender())
        }
    }
}
