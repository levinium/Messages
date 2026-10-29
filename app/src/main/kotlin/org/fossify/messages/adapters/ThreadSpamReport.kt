package org.fossify.messages.adapters

import org.fossify.messages.models.Message

/**
 * What the thread can do about spam: whether this conversation is one you would report at all,
 * and how a message gets to 7726. Both belong to the conversation, so they travel together.
 */
class ThreadSpamReport(
    val isAvailable: () -> Boolean,
    val report: (message: Message) -> Unit,
)
