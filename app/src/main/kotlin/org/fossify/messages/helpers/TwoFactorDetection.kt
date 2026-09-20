package org.fossify.messages.helpers

import org.fossify.messages.models.Message

/**
 * Recognising one-time passcodes well enough to act on them.
 *
 * Every rule here exists to avoid a false positive, because the cost is lopsided: failing to spot
 * a verification code wastes nothing, while mistaking a friend's message for one risks deleting
 * something irreplaceable.
 *
 * The sender's number is deliberately not one of the rules. Services send passcodes from short
 * codes, from toll-free numbers and from ordinary-looking mobile numbers, so the format says very
 * little. What does separate them is that nobody ever replies to a robot: a thread you have sent
 * anything into is a conversation, whatever it contains.
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
 * Whether [this] reads like a verification code.
 *
 * [isKnownContact] must say whether the sender is in the address book. A message from somebody
 * saved is never treated as a passcode, however much it looks like one, because "my code is
 * 123456" from a friend is an ordinary message.
 */
fun Message.looksLikeVerificationCode(isKnownContact: Boolean): Boolean {
    if (isKnownContact || !isReceivedMessage()) {
        return false
    }

    return isVerificationCodeText(body)
}

/** A run of digits that a sentence is plainly presenting as a code, rather than merely containing. */
fun isVerificationCodeText(body: String): Boolean {
    return body.contains(CODE) && body.contains(KEYWORDS)
}

/**
 * An address no person could be sending from: a short code, or a name in place of a number.
 *
 * This is a reliable positive signal but an unreliable negative one, since plenty of services send
 * from ordinary-looking numbers. Worth asking, never worth requiring.
 */
fun String.isAutomatedSender(): Boolean {
    val trimmed = trim()
    if (trimmed.isEmpty()) {
        return false
    }

    // An alphabetic sender ID such as "VERIFY" cannot be a person's number, but an
    // email-to-SMS gateway is somebody writing to you.
    if (trimmed.any { it.isLetter() }) {
        return !trimmed.contains('@')
    }

    return SHORT_CODE.matches(trimmed.filter { it.isDigit() })
}

/**
 * Whether a whole conversation is one of those passcode threads.
 *
 * Requires that nothing has ever been sent into it, that the other party is not a saved contact,
 * and that a fair share of what arrived were codes rather than one stray message mentioning a PIN.
 */
fun List<Message>.isVerificationCodeThread(isKnownContact: Boolean): Boolean {
    if (!isUnansweredMachineThread(isKnownContact)) {
        return false
    }

    val received = filter { it.isReceivedMessage() }
    val codes = received.count { it.looksLikeVerificationCode(isKnownContact) }
    return codes > 0 && codes >= received.size * MIN_CODE_SHARE
}

/**
 * Whether the conversation is with a machine of any kind: passcodes, delivery notices, alerts.
 *
 * Broader than [isVerificationCodeThread] on purpose. Nobody dials a short code or searches their
 * own delivery notifications, so the toolbar can drop those actions for the whole category, while
 * anything that destroys messages stays keyed to the narrower passcode test.
 */
fun List<Message>.isAutomatedThread(isKnownContact: Boolean): Boolean {
    if (!isUnansweredMachineThread(isKnownContact)) {
        return false
    }

    val sendsFromAnAddressNoPersonHas = filter { it.isReceivedMessage() }
        .any { it.senderPhoneNumber.isAutomatedSender() }

    return sendsFromAnAddressNoPersonHas || isVerificationCodeThread(isKnownContact)
}

/** The conditions common to both: a stranger, and one you have never answered. */
private fun List<Message>.isUnansweredMachineThread(isKnownContact: Boolean): Boolean {
    if (isKnownContact) {
        return false
    }

    // Replying makes it a conversation, and conversations are not disposable.
    if (any { !it.isReceivedMessage() && !it.isScheduled }) {
        return false
    }

    return any { it.isReceivedMessage() }
}

/**
 * Services mix passcodes with other notices - a bank sends both codes and balance alerts - so a
 * thread does not have to be all codes to be a passcode thread. It does have to be more than a
 * single mention buried in a marketing feed.
 */
private const val MIN_CODE_SHARE = 1.0 / 3.0
