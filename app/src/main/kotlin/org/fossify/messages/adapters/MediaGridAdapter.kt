package org.fossify.messages.adapters

import android.graphics.drawable.GradientDrawable
import android.view.Menu
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemMediaGridBinding
import org.fossify.messages.extensions.isVideoMimeType
import org.fossify.messages.models.MediaItem

/**
 * The conversation's pictures and videos as a grid. Selecting several at once is the point: the
 * things people do with media in a thread - sharing, saving, copying - are rarely done one at a
 * time, and doing them from the thread meant scrolling back through the conversation for each.
 */
class MediaGridAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    private val onAction: (id: Int, items: List<MediaItem>) -> Unit,
    itemClick: (Any) -> Unit,
) : MyRecyclerViewListAdapter<MediaItem>(
    activity = activity,
    recyclerView = recyclerView,
    diffUtil = MediaItemDiffCallback(),
    itemClick = itemClick
) {
    private val cornerRadius =
        activity.resources.getDimensionPixelSize(R.dimen.media_thumbnail_corner_radius)

    private val selectionOverlay by lazy {
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = this@MediaGridAdapter.cornerRadius.toFloat()
            setColor(activity.getColor(R.color.media_selection_scrim))
            setStroke(
                activity.resources.getDimensionPixelSize(R.dimen.media_selection_outline_width),
                properPrimaryColor
            )
        }
    }

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_media_grid

    override fun prepareActionMode(menu: Menu) {
        menu.findItem(R.id.cab_open_with).isVisible = isOneItemSelected()
    }

    override fun actionItemPressed(id: Int) {
        if (id == R.id.cab_select_all) {
            selectAll()
            return
        }

        val items = getSelectedItems()
        if (items.isEmpty()) {
            return
        }

        onAction(id, items)
        finishActMode()
    }

    override fun getSelectableItemCount() = currentList.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMediaGridBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bindView(item, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            ItemMediaGridBinding.bind(itemView).apply {
                val isSelected = selectedKeys.contains(item.hashCode())
                mediaGridCheck.beVisibleIf(isSelected)
                mediaGridPlay.beVisibleIf(item.mimetype.isVideoMimeType())

                // A picture is busy enough that a corner check alone is easy to miss, so a
                // selected one is dimmed and ringed in the user's own colour. Both ride on the
                // thumbnail's foreground, which is the only thing guaranteed to match its bounds.
                mediaGridThumbnail.foreground = if (isSelected) selectionOverlay else null
                mediaGridCheck.applyColorFilter(properPrimaryColor)

                Glide.with(activity)
                    .load(item.uri)
                    .apply(
                        RequestOptions()
                            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                            .transform(CenterCrop(), RoundedCorners(cornerRadius))
                    )
                    .into(mediaGridThumbnail)
            }
        }

        bindViewHolder(holder)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(ItemMediaGridBinding.bind(holder.itemView).mediaGridThumbnail)
        }
    }

    private fun getSelectedItems() = currentList.filter { selectedKeys.contains(it.hashCode()) }
}

private class MediaItemDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
    override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem) =
        oldItem.uriString == newItem.uriString

    override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem) = oldItem == newItem
}
