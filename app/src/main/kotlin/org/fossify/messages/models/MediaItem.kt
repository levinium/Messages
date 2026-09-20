package org.fossify.messages.models

import android.os.Parcelable
import androidx.core.net.toUri
import kotlinx.parcelize.Parcelize
import org.fossify.messages.extensions.isImageMimeType
import org.fossify.messages.extensions.isVideoMimeType

/** One picture or video, as much of an [Attachment] as the viewer needs to show it. */
@Parcelize
data class MediaItem(
    val uriString: String,
    val mimetype: String,
    val filename: String,
    /** When the message carrying it was sent or received, in milliseconds. */
    val dateMillis: Long,
) : Parcelable {
    val uri get() = uriString.toUri()
}

/** Every picture and video in a conversation, oldest first, the way the thread reads. */
fun List<Message>.toMediaItems(): List<MediaItem> {
    return flatMap { message ->
        message.attachment?.attachments.orEmpty()
            .filter { it.mimetype.isImageMimeType() || it.mimetype.isVideoMimeType() }
            .map {
                MediaItem(
                    uriString = it.getUri().toString(),
                    mimetype = it.mimetype,
                    filename = it.filename,
                    dateMillis = message.date * MILLIS_PER_SECOND,
                )
            }
    }
}

private const val MILLIS_PER_SECOND = 1000L
