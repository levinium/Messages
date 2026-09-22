package org.fossify.messages.adapters

import org.fossify.messages.helpers.ReactionKind
import org.fossify.messages.models.Message

/**
 * What the thread can do about tapbacks.
 *
 * Both answers belong to the conversation rather than to any one message - whether it can be
 * replied to at all, and where a picked reaction goes - so they travel together.
 */
class ThreadReactionActions(
    val isAvailable: () -> Boolean,
    val send: (message: Message, emoji: String, kind: ReactionKind?, remove: Boolean) -> Unit,
)
