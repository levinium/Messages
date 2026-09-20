package org.fossify.messages.helpers

import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.adapters.BaseConversationsAdapter
import org.fossify.messages.extensions.askDeleteConversation
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.findPasscodeThreadIds
import org.fossify.messages.extensions.setConversationArchived
import org.fossify.messages.extensions.toggleConversationRead
import org.fossify.messages.models.Conversation

/**
 * Swiping a conversation to act on it, and the bar that offers to take it back.
 *
 * Each action touches the one row that moved rather than reloading every conversation on the
 * phone: a full reload takes long enough to see and flashes the list as it goes past.
 */
class ConversationSwiper(
    private val activity: SimpleActivity,
    private val recyclerView: MyRecyclerView,
    private val adapter: () -> BaseConversationsAdapter,
    private val onListChanged: () -> Unit,
    private val undoAnchor: View? = null,
    /**
     * The conversations list takes these from the settings; the archive has its own pair, since
     * archiving something that is already archived means nothing and putting it back does.
     */
    private val rightAction: () -> Int = { activity.config.swipeRightAction },
    private val leftAction: () -> Int = { activity.config.swipeLeftAction },
) {
    private var helper: ItemTouchHelper? = null
    private var passcodeThreadIds = emptySet<Long>()

    /** Called whenever the screen comes back, since the settings are one of the places you go. */
    fun refresh() {
        helper?.attachToRecyclerView(null)
        helper = null
        refreshPasscodeThreads()

        if (rightAction() == SWIPE_ACTION_NONE && leftAction() == SWIPE_ACTION_NONE) {
            return
        }

        val callback = ConversationSwipeCallback(
            activity = activity,
            rightAction = rightAction(),
            leftAction = leftAction(),
            resolveAction = ::actionForRow,
            onSwipe = ::onSwiped,
        )

        helper = ItemTouchHelper(callback).apply { attachToRecyclerView(recyclerView) }
    }

    /**
     * Archiving a conversation that only ever delivered passcodes means deleting it instead, so
     * the row has to say so while it is still moving rather than surprise you afterwards.
     */
    private fun actionForRow(position: Int, swipingRight: Boolean): Int {
        val action = if (swipingRight) rightAction() else leftAction()
        if (action != SWIPE_ACTION_ARCHIVE || !activity.config.deletePasscodeThreadsOnSwipe) {
            return action
        }

        val threadId = adapter().currentList.getOrNull(position)?.threadId ?: return action
        return if (passcodeThreadIds.contains(threadId)) SWIPE_ACTION_DELETE else action
    }

    /**
     * The list arrives after the screen does, so which conversations are passcode threads cannot
     * be worked out once on resume: it has to follow whatever the list currently holds.
     */
    fun onConversationsChanged() {
        refreshPasscodeThreads()
    }

    private fun refreshPasscodeThreads() {
        if (!activity.config.deletePasscodeThreadsOnSwipe ||
            SWIPE_ACTION_ARCHIVE !in listOf(rightAction(), leftAction())
        ) {
            passcodeThreadIds = emptySet()
            return
        }

        activity.findPasscodeThreadIds(adapter().currentList) {
            passcodeThreadIds = it
        }
    }

    private fun onSwiped(position: Int, action: Int) {
        val conversation = adapter().currentList.getOrNull(position) ?: return
        // A swipe here acts on a row rather than removing it, and the touch helper has no notion
        // of that: left alone it holds the row open at the end of the gesture. Detaching it drops
        // that state and puts the row back, and the list carries on from the action below.
        recyclerView.post {
            helper?.attachToRecyclerView(null)
            helper?.attachToRecyclerView(recyclerView)
            adapter().notifyItemChanged(position)
        }

        when (action) {
            SWIPE_ACTION_TOGGLE_READ -> markRead(conversation)
            SWIPE_ACTION_ARCHIVE -> setArchived(conversation, archived = true)
            SWIPE_ACTION_UNARCHIVE -> setArchived(conversation, archived = false)
            SWIPE_ACTION_DELETE -> activity.askDeleteConversation(conversation) {
                removeConversation(conversation)
            }
        }
    }

    private fun markRead(conversation: Conversation) {
        activity.toggleConversationRead(conversation) { updated ->
            if (updated == null) {
                return@toggleConversationRead
            }

            replaceConversation(updated)
            val message = if (updated.read) R.string.marked_as_read else R.string.marked_as_unread
            showUndoBar(message) {
                activity.toggleConversationRead(updated) { restored ->
                    restored?.let { replaceConversation(it) }
                }
            }
        }
    }

    /** Either way the row leaves the list it is in, and either way it can be put back. */
    private fun setArchived(conversation: Conversation, archived: Boolean) {
        activity.setConversationArchived(conversation, archived) {
            removeConversation(conversation)
            val message = if (archived) {
                R.string.conversation_archived
            } else {
                R.string.conversation_unarchived
            }

            showUndoBar(message) {
                activity.setConversationArchived(conversation, !archived) {
                    refreshConversations()
                }
            }
        }
    }

    private fun replaceConversation(conversation: Conversation) {
        val updated = adapter().currentList.map {
            if (it.threadId == conversation.threadId) conversation else it
        }

        adapter().updateConversations(ArrayList(updated))
    }

    private fun removeConversation(conversation: Conversation) {
        val remaining = adapter().currentList.filterNot { it.threadId == conversation.threadId }
        adapter().updateConversations(ArrayList(remaining))
        onListChanged()
    }

    /** A swipe is easy to make by accident, so it says what it did and offers to take it back. */
    private fun showUndoBar(message: Int, undo: () -> Unit) {
        Snackbar.make(recyclerView, activity.getString(message), Snackbar.LENGTH_LONG)
            .apply { undoAnchor?.let { setAnchorView(it) } }
            .setAction(org.fossify.commons.R.string.undo) { undo() }
            .show()
    }
}
