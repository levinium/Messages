package org.fossify.messages.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.NotificationManager.IMPORTANCE_NONE
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isSPlus
import org.fossify.messages.R
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.shortcutHelper
import org.fossify.messages.messaging.isShortCodeWithLetters
import org.fossify.messages.receivers.DeleteSmsReceiver
import org.fossify.messages.receivers.DirectReplyReceiver
import org.fossify.messages.receivers.MarkAsReadReceiver

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.notificationManager
    private val soundUri get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    private val user = Person.Builder()
        .setName(context.getString(R.string.me))
        .build()

    private val notificationAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
        .build()

    @SuppressLint("NewApi")
    fun showMessageNotification(
        messageId: Long,
        isMms: Boolean,
        address: String,
        body: String,
        threadId: Long,
        bitmap: Bitmap?,
        sender: String?,
        alertOnlyOnce: Boolean = false
    ) {
        val notificationChannelId = getOrCreateMessageChannelId(threadId)
        val notificationId = threadId.hashCode()
        val contentIntent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

        val markAsReadIntent = Intent(context, MarkAsReadReceiver::class.java).apply {
            action = MARK_AS_READ
            putExtra(THREAD_ID, threadId)
        }
        val markAsReadPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                markAsReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val deleteSmsIntent = Intent(context, DeleteSmsReceiver::class.java).apply {
            putExtra(THREAD_ID, threadId)
            putExtra(MESSAGE_ID, messageId)
            putExtra(IS_MMS, isMms)
        }
        val deleteSmsPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                deleteSmsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        var replyAction: NotificationCompat.Action? = null
        val isNoReplySms = isShortCodeWithLetters(address)
        if (!isNoReplySms) {
            val replyLabel = context.getString(R.string.reply)
            val remoteInput = RemoteInput.Builder(REPLY)
                .setLabel(replyLabel)
                .build()

            val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
                putExtra(THREAD_ID, threadId)
                putExtra(THREAD_NUMBER, address)
            }

            val replyPendingIntent =
                PendingIntent.getBroadcast(
                    context.applicationContext,
                    notificationId,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_send_vector,
                replyLabel,
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .build()
        }

        val largeIcon = bitmap ?: if (sender != null) {
            SimpleContactsHelper(context).getContactLetterIcon(sender)
        } else {
            null
        }
        val builder = NotificationCompat.Builder(context, notificationChannelId).apply {
            when (context.config.lockScreenVisibilitySetting) {
                LOCK_SCREEN_SENDER_MESSAGE -> {
                    setLargeIcon(largeIcon)
                    setStyle(getMessagesStyle(address, body, notificationId, sender))
                }

                LOCK_SCREEN_SENDER -> {
                    setContentTitle(sender)
                    setLargeIcon(largeIcon)
                    val summaryText = context.getString(R.string.new_message)
                    setStyle(
                        NotificationCompat.BigTextStyle().setSummaryText(summaryText).bigText(body)
                    )
                }
            }

            color = context.getProperPrimaryColor()
            setSmallIcon(R.drawable.ic_messenger)
            setContentIntent(contentPendingIntent)
            priority = NotificationCompat.PRIORITY_MAX
            setDefaults(Notification.DEFAULT_LIGHTS)
            setCategory(Notification.CATEGORY_MESSAGE)
            setAutoCancel(true)
            setOnlyAlertOnce(alertOnlyOnce)
            setSound(soundUri, AudioManager.STREAM_NOTIFICATION)
        }

        if (replyAction != null && context.config.lockScreenVisibilitySetting == LOCK_SCREEN_SENDER_MESSAGE) {
            builder.addAction(replyAction)
        }

        builder.addAction(
            org.fossify.commons.R.drawable.ic_check_vector,
            context.getString(R.string.mark_as_read),
            markAsReadPendingIntent
        )
            .setChannelId(notificationChannelId)
        if (isNoReplySms) {
            builder.addAction(
                org.fossify.commons.R.drawable.ic_delete_vector,
                context.getString(org.fossify.commons.R.string.delete),
                deleteSmsPendingIntent
            ).setChannelId(notificationChannelId)
        }

        var shortcut = context.shortcutHelper.getShortcut(threadId)
        if (shortcut == null) {
            ensureBackgroundThread {
                shortcut = context.shortcutHelper.createOrUpdateShortcut(threadId)
                builder.setShortcutInfo(shortcut)
                notificationManager.notify(notificationId, builder.build())
                context.shortcutHelper.reportReceiveMessageUsage(threadId)
            }
        } else {
            builder.setShortcutInfo(shortcut)
            notificationManager.notify(notificationId, builder.build())
            ensureBackgroundThread {
                context.shortcutHelper.reportReceiveMessageUsage(threadId)
            }
        }
    }

    @SuppressLint("NewApi")
    fun showSendingFailedNotification(recipientName: String, threadId: Long) {
        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())
        val notificationChannelId =
            if (hasCustomNotifications) threadId.toString() else NOTIFICATION_CHANNEL_ID
        if (!hasCustomNotifications) {
            createChannel(notificationChannelId, context.getString(R.string.message_not_sent_short))
        }

        val notificationId = generateRandomId().hashCode()
        val intent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val summaryText =
            String.format(context.getString(R.string.message_sending_error), recipientName)
        val largeIcon = SimpleContactsHelper(context).getContactLetterIcon(recipientName)
        val builder = NotificationCompat.Builder(context, notificationChannelId)
            .setContentTitle(context.getString(R.string.message_not_sent_short))
            .setContentText(summaryText)
            .setColor(context.getProperPrimaryColor())
            .setSmallIcon(R.drawable.ic_messenger)
            .setLargeIcon(largeIcon)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setChannelId(notificationChannelId)

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * Plays the alert the notification would have played, for messages that arrive while the user
     * is already looking at them. Honors Do Not Disturb, the ringer mode and the channel's own
     * sound and vibration settings, so a silenced conversation stays silent.
     */
    @SuppressLint("NewApi")
    fun playMessageAlert(threadId: Long) {
        if (!notificationManager.areNotificationsEnabled()) {
            return
        }

        if (notificationManager.currentInterruptionFilter != INTERRUPTION_FILTER_ALL) {
            return
        }

        val channelId = getOrCreateMessageChannelId(threadId)
        val channel = notificationManager.getNotificationChannel(channelId) ?: return
        if (channel.importance == IMPORTANCE_NONE) {
            return
        }

        when (context.getSystemService(AudioManager::class.java)?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> return
            AudioManager.RINGER_MODE_VIBRATE -> vibrate(channel)
            else -> {
                playAlertSound(channel)
                if (channel.shouldVibrate()) {
                    vibrate(channel)
                }
            }
        }
    }

    private fun playAlertSound(channel: NotificationChannel) {
        val alertUri = channel.sound ?: return
        try {
            val ringtone =
                RingtoneManager.getRingtone(context.applicationContext, alertUri) ?: return
            ringtone.audioAttributes = channel.audioAttributes ?: notificationAudioAttributes

            // a Ringtone that gets collected mid playback goes silent, so hold on to the last one
            currentAlert?.takeIf { it.isPlaying }?.stop()
            currentAlert = ringtone
            ringtone.play()
        } catch (_: Exception) {
            // a missing or unplayable ringtone is not worth bothering the user about
        }
    }

    private fun vibrate(channel: NotificationChannel) {
        val vibrator = if (isSPlus()) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

        if (vibrator?.hasVibrator() != true) {
            return
        }

        val pattern = channel.vibrationPattern
        val effect = if (pattern != null && pattern.isNotEmpty()) {
            VibrationEffect.createWaveform(pattern, -1)
        } else {
            VibrationEffect.createOneShot(ALERT_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }

        try {
            vibrator.vibrate(effect, notificationAudioAttributes)
        } catch (_: Exception) {
            // some devices refuse to vibrate while in a call or similar
        }
    }

    private fun getOrCreateMessageChannelId(threadId: Long): String {
        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())
        val channelId =
            if (hasCustomNotifications) threadId.toString() else NOTIFICATION_CHANNEL_ID
        if (!hasCustomNotifications) {
            createChannel(channelId, context.getString(R.string.channel_received_sms))
        }

        return channelId
    }

    private fun createChannel(id: String, name: String) {
        val importance = IMPORTANCE_HIGH
        NotificationChannel(id, name, importance).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(soundUri, notificationAudioAttributes)
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }

    private fun getMessagesStyle(
        address: String,
        body: String,
        notificationId: Int,
        name: String?
    ): NotificationCompat.MessagingStyle {
        val sender = if (name != null) {
            Person.Builder()
                .setName(name)
                .setKey(address)
                .build()
        } else {
            null
        }

        return NotificationCompat.MessagingStyle(user).also { style ->
            getOldMessages(notificationId).forEach {
                style.addMessage(it)
            }
            val newMessage =
                NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), sender)
            style.addMessage(newMessage)
        }
    }

    private fun getOldMessages(notificationId: Int): List<NotificationCompat.MessagingStyle.Message> {
        val currentNotification =
            notificationManager.activeNotifications.find { it.id == notificationId }
        return if (currentNotification != null) {
            val activeStyle =
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(
                    currentNotification.notification
                )
            activeStyle?.messages.orEmpty()
        } else {
            emptyList()
        }
    }

    private companion object {
        const val ALERT_VIBRATION_MS = 150L

        @Volatile
        var currentAlert: Ringtone? = null
    }
}
