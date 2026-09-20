package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.viewpager2.widget.ViewPager2
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.MediaPagerAdapter
import org.fossify.messages.databinding.ActivityMediaViewerBinding
import org.fossify.messages.extensions.copyToUri
import org.fossify.messages.extensions.isVideoMimeType
import org.fossify.messages.extensions.launchSaveMediaIntent
import org.fossify.messages.extensions.launchViewIntent
import org.fossify.messages.extensions.shareMediaIntent
import org.fossify.messages.helpers.MEDIA_ITEMS
import org.fossify.messages.helpers.MEDIA_START_INDEX
import org.fossify.messages.helpers.PICK_SAVE_FILE_INTENT
import org.fossify.messages.models.MediaItem
import java.io.IOException

/**
 * Shows the pictures and videos from a conversation without handing them to another app.
 *
 * Opening media used to leave the app entirely, which loses the thread, the back stack and any
 * sense of where the picture came from. Swiping moves between everything in the conversation.
 */
class MediaViewerActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityMediaViewerBinding::inflate)

    private var items = emptyList<MediaItem>()
    private var currentIndex = 0

    /**
     * One player for the whole viewer, lent to whichever video page is on screen. Giving every
     * page its own would keep decoders alive for videos nobody is watching.
     */
    private var player: ExoPlayer? = null
    private var attachedPlayerView: PlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge()

        items = intent.getParcelableArrayListExtra<MediaItem>(MEDIA_ITEMS).orEmpty()
        if (items.isEmpty()) {
            finish()
            return
        }

        currentIndex = intent.getIntExtra(MEDIA_START_INDEX, 0).coerceIn(items.indices)
        setupPager()
        setupOptionsMenu()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.mediaViewerAppbar, NavigationIcon.Arrow)
        updateTitle()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        detachPlayer()
        player?.release()
        player = null
    }

    private fun setupPager() {
        binding.mediaViewerPager.apply {
            adapter = MediaPagerAdapter(
                items = items,
                onVideoPageReady = ::bindPlayerIfCurrent,
                // Tapping hides the bar, so the whole screen belongs to what is being looked at.
                onClick = {
                    with(binding.mediaViewerAppbar) {
                        if (isVisible()) beGone() else beVisible()
                    }
                },
            )
            setCurrentItem(currentIndex, false)

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentIndex = position
                    updateTitle()
                    // Leaving a video should stop it, not let it play on out of sight.
                    if (!items[position].mimetype.isVideoMimeType()) {
                        detachPlayer()
                    }
                }
            })
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        val destination = resultData?.data
        if (requestCode != PICK_SAVE_FILE_INTENT || resultCode != RESULT_OK || destination == null) {
            return
        }

        val source = items.getOrNull(currentIndex)?.uri ?: return
        ensureBackgroundThread {
            try {
                copyToUri(src = source, dst = destination)
                toast(org.fossify.commons.R.string.file_saved)
            } catch (e: IOException) {
                showErrorToast(e)
            } catch (e: SecurityException) {
                showErrorToast(e)
            }
        }
    }

    private fun setupOptionsMenu() {
        binding.mediaViewerToolbar.setOnMenuItemClickListener { menuItem ->
            val item = items.getOrNull(currentIndex) ?: return@setOnMenuItemClickListener false
            when (menuItem.itemId) {
                R.id.media_share -> shareMediaIntent(item.uri, item.mimetype)
                R.id.media_open_with -> launchViewIntent(item.uri, item.mimetype, item.filename)
                R.id.media_save_as -> launchSaveMediaIntent(
                    mimetype = item.mimetype,
                    filename = item.filename.ifBlank { item.uriString.getFilenameFromPath() },
                    requestCode = PICK_SAVE_FILE_INTENT,
                )

                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    private fun bindPlayerIfCurrent(position: Int, playerView: PlayerView) {
        if (position != currentIndex) {
            return
        }

        val exoPlayer = player ?: ExoPlayer.Builder(this).build().also { player = it }
        attachedPlayerView?.player = null
        playerView.player = exoPlayer
        attachedPlayerView = playerView

        exoPlayer.setMediaItem(ExoMediaItem.fromUri(items[position].uri))
        exoPlayer.prepare()
    }

    private fun detachPlayer() {
        player?.pause()
        attachedPlayerView?.player = null
        attachedPlayerView = null
    }

    private fun updateTitle() {
        binding.mediaViewerToolbar.title = if (items.size > 1) {
            "${currentIndex + 1} / ${items.size}"
        } else {
            items.getOrNull(currentIndex)?.filename.orEmpty()
        }
    }
}
