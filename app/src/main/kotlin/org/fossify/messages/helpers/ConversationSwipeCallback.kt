package org.fossify.messages.helpers

import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.messages.R

/**
 * Swiping a conversation left or right to act on it, rather than long pressing it and finding the
 * action in a menu. Which action belongs to which direction is the user's to decide, so the
 * callback is built fresh whenever the settings might have changed.
 */
class ConversationSwipeCallback(
    private val activity: BaseSimpleActivity,
    rightAction: Int,
    leftAction: Int,
    private val resolveAction: (position: Int, swipingRight: Boolean) -> Int,
    private val onSwipe: (position: Int, action: Int) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, swipeDirections(rightAction, leftAction)) {

    private val background = ColorDrawable()
    private val iconMargin =
        activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.absoluteAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            onSwipe(position, resolveAction(position, direction == ItemTouchHelper.RIGHT))
        }
    }

    @Suppress("LongParameterList")
    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        val position = viewHolder.absoluteAdapterPosition
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE &&
            dX != 0f &&
            position != RecyclerView.NO_POSITION
        ) {
            drawAction(canvas, viewHolder, dX, resolveAction(position, dX > 0))
        }

        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    /** What is coming is drawn behind the row as it moves, so the swipe says what it will do. */
    private fun drawAction(canvas: Canvas, holder: RecyclerView.ViewHolder, dX: Float, action: Int) {
        if (action == SWIPE_ACTION_NONE) {
            return
        }

        val itemView = holder.itemView
        val isRight = dX > 0
        val color = colorFor(action)

        background.color = color
        if (isRight) {
            background.setBounds(itemView.left, itemView.top, dX.toInt(), itemView.bottom)
        } else {
            background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
        }
        background.draw(canvas)

        val icon = AppCompatResources.getDrawable(activity, iconFor(action))?.mutate() ?: return
        icon.setTint(color.getContrastColor())

        val size = icon.intrinsicHeight
        if (dX.toInt().absoluteSize() < size + iconMargin * 2) {
            // Nothing legible fits yet; the colour alone carries the hint.
            return
        }

        val top = itemView.top + (itemView.height - size) / 2
        val left = if (isRight) {
            itemView.left + iconMargin
        } else {
            itemView.right - iconMargin - size
        }

        icon.setBounds(left, top, left + size, top + size)
        icon.draw(canvas)
    }

    private fun colorFor(action: Int) = when (action) {
        SWIPE_ACTION_DELETE -> activity.getColor(R.color.message_error)
        else -> activity.getProperPrimaryColor()
    }

    private fun iconFor(action: Int) = when (action) {
        SWIPE_ACTION_DELETE -> org.fossify.commons.R.drawable.ic_delete_vector
        SWIPE_ACTION_ARCHIVE -> R.drawable.ic_archive_vector
        SWIPE_ACTION_UNARCHIVE -> R.drawable.ic_unarchive_vector
        else -> R.drawable.ic_check_double_vector
    }

    private fun Int.absoluteSize() = if (this < 0) -this else this

    companion object {
        /** A direction whose action is "do nothing" should not swipe at all. */
        private fun swipeDirections(rightAction: Int, leftAction: Int): Int {
            var directions = 0
            if (rightAction != SWIPE_ACTION_NONE) {
                directions = directions or ItemTouchHelper.RIGHT
            }
            if (leftAction != SWIPE_ACTION_NONE) {
                directions = directions or ItemTouchHelper.LEFT
            }
            return directions
        }
    }
}
