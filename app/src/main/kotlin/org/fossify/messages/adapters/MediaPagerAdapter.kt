package org.fossify.messages.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.github.chrisbanes.photoview.PhotoView
import org.fossify.messages.databinding.ItemMediaPageImageBinding
import org.fossify.messages.databinding.ItemMediaPageVideoBinding
import org.fossify.messages.extensions.isVideoMimeType
import org.fossify.messages.models.MediaItem

/**
 * One page per attachment. Images get pinch and double-tap zoom; videos get a player surface that
 * the activity attaches its single ExoPlayer to, so only the page being looked at ever holds one.
 */
class MediaPagerAdapter(
    private val items: List<MediaItem>,
    private val onVideoPageReady: (position: Int, playerView: PlayerView) -> Unit,
    private val onClick: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = if (items[position].mimetype.isVideoMimeType()) {
        VIDEO
    } else {
        IMAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIDEO) {
            VideoHolder(ItemMediaPageVideoBinding.inflate(inflater, parent, false))
        } else {
            ImageHolder(ItemMediaPageImageBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ImageHolder -> holder.bind(items[position])
            is VideoHolder -> onVideoPageReady(position, holder.playerView)
        }
    }

    inner class ImageHolder(
        private val binding: ItemMediaPageImageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MediaItem) {
            val photoView: PhotoView = binding.mediaPageImage
            photoView.setOnPhotoTapListener { _, _, _ -> onClick() }
            // Tapping outside the picture should still dismiss the chrome.
            photoView.setOnOutsidePhotoTapListener { onClick() }

            Glide.with(photoView)
                .load(item.uri)
                .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.RESOURCE))
                .into(photoView)
        }
    }

    inner class VideoHolder(
        binding: ItemMediaPageVideoBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        val playerView: PlayerView = binding.mediaPageVideo
    }

    private companion object {
        const val IMAGE = 0
        const val VIDEO = 1
    }
}
