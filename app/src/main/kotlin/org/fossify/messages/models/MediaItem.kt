package org.fossify.messages.models

import android.os.Parcelable
import androidx.core.net.toUri
import kotlinx.parcelize.Parcelize

/** One picture or video, as much of an [Attachment] as the viewer needs to show it. */
@Parcelize
data class MediaItem(
    val uriString: String,
    val mimetype: String,
    val filename: String,
) : Parcelable {
    val uri get() = uriString.toUri()
}
