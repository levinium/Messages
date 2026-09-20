package org.fossify.messages.extensions

import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.helpers.SWIPE_ACTION_ARCHIVE
import org.fossify.messages.helpers.SWIPE_ACTION_DELETE
import org.fossify.messages.helpers.SWIPE_ACTION_TOGGLE_READ
import org.fossify.messages.helpers.isVerificationCodeThread
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.models.Conversation

/** Carries out whatever swiping a conversation was configured to mean. */
fun SimpleActivity.runSwipeAction(conversation: Conversation, action: Int) {
    when (action) {
        SWIPE_ACTION_TOGGLE_READ -> toggleConversationRead(conversation)
        SWIPE_ACTION_ARCHIVE -> archiveOrDeleteConversation(conversation)
        SWIPE_ACTION_DELETE -> askDeleteConversation(conversation)
    }
}

private fun SimpleActivity.toggleConversationRead(conversation: Conversation) {
    ensureBackgroundThread {
        if (conversation.read) {
            markThreadMessagesUnread(conversation.threadId)
        } else {
            markThreadMessagesRead(conversation.threadId)
            notificationManager.cancel(conversation.threadId.hashCode())
        }

        runOnUiThread {
            refreshConversations()
        }
    }
}

/**
 * A conversation that exists only to deliver passcodes holds nothing worth filing away, so
 * archiving one can mean offering to delete it instead. Reading the thread takes a moment, hence
 * the trip to the background before anything is decided.
 */
private fun SimpleActivity.archiveOrDeleteConversation(conversation: Conversation) {
    if (!config.deletePasscodeThreadsOnSwipe) {
        archiveConversation(conversation)
        return
    }

    ensureBackgroundThread {
        val isPasscodeThread = isPasscodeThread(conversation)
        runOnUiThread {
            if (isPasscodeThread) {
                askDeleteConversation(conversation)
            } else {
                archiveConversation(conversation)
            }
        }
    }
}

private fun SimpleActivity.isPasscodeThread(conversation: Conversation): Boolean {
    if (conversation.isGroupConversation) {
        return false
    }

    val isKnownContact = conversation.title != conversation.phoneNumber
    return messagesDB.getThreadMessages(conversation.threadId)
        .isVerificationCodeThread(isKnownContact)
}

private fun SimpleActivity.archiveConversation(conversation: Conversation) {
    ensureBackgroundThread {
        updateConversationArchivedStatus(conversation.threadId, true)
        notificationManager.cancel(conversation.threadId.hashCode())
        runOnUiThread {
            refreshConversations()
        }
    }
}

/** Deleting a conversation cannot be taken back, so a swipe asks before it happens. */
private fun SimpleActivity.askDeleteConversation(conversation: Conversation) {
    val question = String.format(
        getString(org.fossify.commons.R.string.deletion_confirmation),
        conversation.title
    )

    ConfirmationDialog(this, question) {
        ensureBackgroundThread {
            deleteConversation(conversation.threadId)
            notificationManager.cancel(conversation.threadId.hashCode())
            runOnUiThread {
                refreshConversations()
            }
        }
    }
}
