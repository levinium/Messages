package org.fossify.messages.models

/**
 * One tapback sitting on a message, already matched to the message it was aimed at.
 *
 * Reactions travel as ordinary texts, so nothing about this is stored: it is worked out again
 * from the conversation every time the thread is drawn.
 */
data class MessageReaction(
    val emoji: String,
    /** The text that carried it, so deleting a message can take its tapbacks along. */
    val messageId: Long,
    val senderKey: String,
    val senderName: String,
    val isFromMe: Boolean,
    val date: Int,
)
