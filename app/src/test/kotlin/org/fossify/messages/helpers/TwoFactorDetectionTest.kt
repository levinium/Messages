package org.fossify.messages.helpers

import android.provider.Telephony
import org.fossify.messages.models.Message
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These rules gate a delete button, so the tests that matter most are the ones asserting what is
 * *not* a verification code. A missed code costs nothing; a false positive risks somebody's
 * messages.
 *
 * The thread cases are modelled on real shapes: single-message passcode threads, a service that
 * mixes codes with other notices, a delivery service that mentions numbers but never a code, and
 * an ordinary conversation.
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
            "Arriving Tomorrow: Your Fresh order for 1:00 PM - 3:00 PM",
            "WhatsApp: Tap to create your account. Don't share this with anyone. 4821",
        ).forEach {
            assertFalse("should not be a code: $it", isVerificationCodeText(it))
        }
    }

    @Test
    fun `needs both a code and a word saying it is one`() {
        assertFalse(isVerificationCodeText("483920"))
        assertFalse(isVerificationCodeText("Please verify your account"))
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
    fun `a single passcode message is a passcode thread`() {
        val thread = listOf(received("Your Apple Account Code is: 161283"))
        assertTrue(thread.isVerificationCodeThread(isKnownContact = false))
    }

    @Test
    fun `a service mixing codes with other notices still counts`() {
        val thread = listOf(
            received("Your verification code is 483920"),
            received("Your verification code is 118234"),
            received("Your verification code is 552190"),
            received("Your verification code is 771004"),
            received("Your account balance is low"),
            received("Our offices are closed Monday"),
            received("Thank you for banking with us"),
            received("Your statement is ready"),
        )
        assertTrue(thread.isVerificationCodeThread(isKnownContact = false))
    }

    @Test
    fun `a thread you have replied to is a conversation`() {
        val thread = listOf(
            received("Your verification code is 483920"),
            sent("thanks!"),
        )
        assertFalse(thread.isVerificationCodeThread(isKnownContact = false))
    }

    @Test
    fun `a saved contact is never a passcode thread`() {
        val thread = listOf(received("Your verification code is 483920"))
        assertFalse(thread.isVerificationCodeThread(isKnownContact = true))
    }

    @Test
    fun `a service that never sends codes is left alone`() {
        val thread = listOf(
            received("Arriving Tomorrow: Your Fresh order for 1:00 PM - 3:00 PM"),
            received("It's early! Your order from COMPTON'S is on the way"),
            received("Your order has been delivered"),
        )
        assertFalse(thread.isVerificationCodeThread(isKnownContact = false))
    }

    @Test
    fun `one stray mention in a long marketing thread is not enough`() {
        val thread = buildList {
            add(received("Use code 483920 at checkout"))
            repeat(NON_CODE_MESSAGES) { add(received("Half price this weekend only")) }
        }
        assertFalse(thread.isVerificationCodeThread(isKnownContact = false))
    }

    @Test
    fun `treats short codes and sender ids as addresses no person has`() {
        listOf("29283", "262966", "30368", "129", "VERIFY", "Amazon").forEach {
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

    @Test
    fun `a short code is a machine even when it never sends a code`() {
        // WhatsApp's registration notice carries no passcode, but 29283 is nobody's phone number.
        val thread = listOf(
            received("Your WhatsApp account is being registered on a new device", from = "29283")
        )
        assertTrue(thread.isAutomatedThread(isKnownContact = false))
        assertFalse(thread.isVerificationCodeThread(isKnownContact = false))
    }

    @Test
    fun `a long number sending codes is a machine too`() {
        val thread = listOf(
            received("Your MSU Verification Code is: 213565", from = "+18442009065")
        )
        assertTrue(thread.isAutomatedThread(isKnownContact = false))
    }

    @Test
    fun `a stranger texting from a real number is not a machine`() {
        val thread = listOf(received("hey, is this still your number?", from = "+13475551234"))
        assertFalse(thread.isAutomatedThread(isKnownContact = false))
    }

    private fun received(body: String, from: String = "12345") =
        message(body, Telephony.Sms.MESSAGE_TYPE_INBOX, from)

    private fun sent(body: String) =
        message(body, Telephony.Sms.MESSAGE_TYPE_SENT, "12345")

    private fun message(body: String, type: Int, from: String) = Message(
        id = body.hashCode().toLong(),
        body = body,
        type = type,
        status = Telephony.Sms.STATUS_NONE,
        participants = ArrayList(),
        date = 0,
        read = true,
        threadId = 1L,
        isMMS = false,
        attachment = null,
        senderPhoneNumber = from,
        senderName = from,
        senderPhotoUri = "",
        subscriptionId = -1,
    )

    private companion object {
        const val NON_CODE_MESSAGES = 10
    }
}
