package org.fossify.messages.helpers

/**
 * Keeps track of the conversation the user currently has on screen, so an incoming message that
 * lands in view doesn't also raise a notification the user would only have to dismiss.
 *
 * A conversation that is scrolled up does not count: the new message arrives below the fold there
 * and is just as easy to miss as it would be with the app closed.
 */
object VisibleScreenTracker {
    @Volatile
    private var visibleThreadId: Long? = null

    @Volatile
    private var isVisibleThreadAtBottom = false

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

    /** Whether [threadId] is open and scrolled to its end, where a new message lands in view. */
    fun isThreadShowingNewMessages(threadId: Long): Boolean {
        return visibleThreadId == threadId && isVisibleThreadAtBottom
    }
}
