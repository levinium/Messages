package org.fossify.messages.helpers

import org.fossify.messages.models.Message

/**
 * Recognising one-time passcodes well enough to act on them.
 *
 * Every rule here exists to avoid a false positive, because the cost is lopsided: failing to spot
 * a verification code wastes nothing, while mistaking a friend's message for one risks deleting
 * something irreplaceable. A message has to clear all three tests.
 */

/**
 * Short codes run to six digits. Seven is where local phone numbers start, so the ceiling has to
 * stay below it or every seven-digit number gets mistaken for a robot.
 */
private val SHORT_CODE = Regex("^\\d{3,6}$")

/** Codes are runs of digits, long enough not to be a house number and short enough not to be a year range. */
private val CODE = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

private val KEYWORDS = Regex(
    "\\b(code|otp|passcode|password|pin|verify|verification|verifying|authenticate|" +
            "authentication|2fa|one[- ]?time|security key|login|log in|sign[- ]?in)\\b",
    RegexOption.IGNORE_CASE
)

/**
 * Whether [this] reads like a verification code from an automated sender.
 *
 * [isKnownContact] must say whether the sender is in the address book. A message from somebody
 * saved is never treated as a passcode, however much it looks like one, because "my code is
 * 123456" from a friend is an ordinary message.
 */
fun Message.looksLikeVerificationCode(isKnownContact: Boolean): Boolean {
    if (isKnownContact || !isReceivedMessage()) {
        return false
    }

    return senderPhoneNumber.isAutomatedSender() && isVerificationCodeText(body)
}

/** A run of digits that a sentence is plainly presenting as a code, rather than merely containing. */
fun isVerificationCodeText(body: String): Boolean {
    return body.contains(CODE) && body.contains(KEYWORDS)
}

/**
 * Short codes and alphanumeric sender IDs are the addresses banks and services send from. A normal
 * phone number is long enough to be dialled, and is never treated as automated.
 */
fun String.isAutomatedSender(): Boolean {
    val trimmed = trim()
    if (trimmed.isEmpty()) {
        return false
    }

    // An alphabetic sender ID such as "VERIFY" cannot be a person's number.
    if (trimmed.any { it.isLetter() }) {
        return !trimmed.contains('@') // but an email-to-SMS gateway might be
    }

    return SHORT_CODE.matches(trimmed.filter { it.isDigit() })
}

/**
 * Whether a whole conversation is one of those passcode threads: an automated sender, and messages
 * that are mostly codes rather than the occasional one mentioning a PIN.
 */
fun List<Message>.isVerificationCodeThread(isKnownContact: Boolean): Boolean {
    val received = filter { it.isReceivedMessage() }
    if (received.size < MIN_MESSAGES_TO_JUDGE_THREAD) {
        return false
    }

    val codes = received.count { it.looksLikeVerificationCode(isKnownContact) }
    return codes >= received.size * MAJORITY
}

private const val MIN_MESSAGES_TO_JUDGE_THREAD = 2
private const val MAJORITY = 0.6
