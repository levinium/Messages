package org.fossify.messages.extensions

import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.helpers.isVerificationCodeThread
import org.fossify.messages.models.Conversation

/**
 * The work behind a swipe. Each one reports back when the store has caught up, so the list can
 * change the single row that moved instead of reloading every conversation the phone holds -
 * a full reload took long enough to see, and flashed the whole list on its way past.
 */
fun SimpleActivity.toggleConversationRead(
    conversation: Conversation,
    callback: (updated: Conversation?) -> Unit,
) {
    ensureBackgroundThread {
        if (conversation.read) {
            markThreadMessagesUnread(conversation.threadId)
        } else {
            markThreadMessagesRead(conversation.threadId)
            notificationManager.cancel(conversation.threadId.hashCode())
        }

        val updated = conversationsDB.getConversationWithThreadId(conversation.threadId)
        runOnUiThread {
            callback(updated)
        }
    }
}

fun SimpleActivity.setConversationArchived(
    conversation: Conversation,
    archived: Boolean,
    callback: () -> Unit,
) {
    ensureBackgroundThread {
        updateConversationArchivedStatus(conversation.threadId, archived)
        if (archived) {
            notificationManager.cancel(conversation.threadId.hashCode())
        }

        runOnUiThread {
            callback()
        }
    }
}

/** Deleting a conversation cannot be taken back, so a swipe asks before it happens. */
fun SimpleActivity.askDeleteConversation(conversation: Conversation, callback: () -> Unit) {
    val question = String.format(
        getString(org.fossify.commons.R.string.deletion_confirmation),
        conversation.title
    )

    ConfirmationDialog(this, question) {
        ensureBackgroundThread {
            deleteConversation(conversation.threadId)
            notificationManager.cancel(conversation.threadId.hashCode())
            runOnUiThread {
                callback()
            }
        }
    }
}

/**
 * Which conversations only ever delivered passcodes. Worked out ahead of the gesture so the swipe
 * can show the delete icon on those rows while it is still moving, rather than deciding after.
 */
fun SimpleActivity.findPasscodeThreadIds(
    conversations: List<Conversation>,
    callback: (ids: Set<Long>) -> Unit,
) {
    ensureBackgroundThread {
        val ids = conversations
            .filterNot { it.isGroupConversation }
            .filter { isPasscodeThread(it) }
            .map { it.threadId }
            .toSet()

        runOnUiThread {
            callback(ids)
        }
    }
}

private fun SimpleActivity.isPasscodeThread(conversation: Conversation): Boolean {
    val isKnownContact = conversation.title != conversation.phoneNumber
    return messagesDB.getThreadMessages(conversation.threadId)
        .isVerificationCodeThread(isKnownContact)
}
