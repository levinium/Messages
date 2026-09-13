package org.fossify.messages.helpers

import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps track of which messaging screen is currently in the foreground, so incoming messages don't
 * raise a notification the user would only have to dismiss: the conversations list already shows
 * the message, and a conversation scrolled to its end shows it inline.
 *
 * A conversation that is scrolled up does not count, the new message lands below the fold there and
 * is just as easy to miss as it would be with the app closed.
 */
object VisibleScreenTracker {
    private val visibleConversationLists = AtomicInteger(0)

    @Volatile
    private var visibleThreadId: Long? = null

    @Volatile
    private var isVisibleThreadAtBottom = false

    fun onConversationsListResumed() {
        visibleConversationLists.incrementAndGet()
    }

    fun onConversationsListPaused() {
        visibleConversationLists.updateAndGet { count -> if (count > 0) count - 1 else 0 }
    }

    fun onThreadResumed(threadId: Long, isAtBottom: Boolean) {
        visibleThreadId = threadId
        isVisibleThreadAtBottom = isAtBottom
    }

    fun onThreadPaused(threadId: Long) {
        if (visibleThreadId == threadId) {
            visibleThreadId = null
            isVisibleThreadAtBottom = false
        }
    }

    fun onThreadScrolled(threadId: Long, isAtBottom: Boolean) {
        if (visibleThreadId == threadId) {
            isVisibleThreadAtBottom = isAtBottom
        }
    }

    fun isConversationsListVisible(): Boolean = visibleConversationLists.get() > 0

    /** Whether [threadId] is open and scrolled to its end, where a new message lands in view. */
    fun isThreadShowingNewMessages(threadId: Long): Boolean {
        return visibleThreadId == threadId && isVisibleThreadAtBottom
    }
}
