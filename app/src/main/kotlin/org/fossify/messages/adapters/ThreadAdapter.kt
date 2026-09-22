package org.fossify.messages.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import android.text.style.URLSpan
import android.view.MotionEvent
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.shareTextIntent
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.usableScreenSize
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.commons.views.MyTextView
import org.fossify.messages.R
import org.fossify.messages.activities.NewConversationActivity
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.activities.VCardViewerActivity
import org.fossify.messages.databinding.ItemAttachmentDocumentBinding
import org.fossify.messages.databinding.ItemAttachmentImageBinding
import org.fossify.messages.databinding.ItemAttachmentVcardBinding
import org.fossify.messages.databinding.ItemMessageBinding
import org.fossify.messages.databinding.ItemMessageReactionBinding
import org.fossify.messages.databinding.ItemMessageReactionMoreBinding
import org.fossify.messages.databinding.ItemMessageReactionOptionBinding
import org.fossify.messages.databinding.ItemThreadDateTimeBinding
import org.fossify.messages.databinding.ItemThreadErrorBinding
import org.fossify.messages.databinding.ItemThreadSendingBinding
import org.fossify.messages.databinding.ItemThreadSuccessBinding
import org.fossify.messages.dialogs.DeleteConfirmationDialog
import org.fossify.messages.dialogs.LinkOptionsDialog
import org.fossify.messages.dialogs.MessageAction
import org.fossify.messages.dialogs.MessageActionsPopup
import org.fossify.messages.dialogs.MessageDetailsDialog
import org.fossify.messages.dialogs.MessageReactionRow
import org.fossify.messages.dialogs.ReactionPickerDialog
import org.fossify.messages.dialogs.SelectTextDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getContactFromAddress
import org.fossify.messages.extensions.isImageMimeType
import org.fossify.messages.extensions.isVCardMimeType
import org.fossify.messages.extensions.isVideoMimeType
import org.fossify.messages.extensions.launchViewIntent
import org.fossify.messages.extensions.openMediaViewer
import org.fossify.messages.extensions.startContactDetailsIntent
import org.fossify.messages.extensions.subscriptionManagerCompat
import org.fossify.messages.helpers.EXTRA_VCARD_URI
import org.fossify.messages.helpers.MAX_ENLARGED_EMOJI
import org.fossify.messages.helpers.ReactionKind
import org.fossify.messages.helpers.acceptsReactions
import org.fossify.messages.helpers.THREAD_DATE_TIME
import org.fossify.messages.helpers.THREAD_RECEIVED_MESSAGE
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE_ERROR
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE_SENDING
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE_SENT
import org.fossify.messages.helpers.emojiCount
import org.fossify.messages.helpers.formatMessageDateTime
import org.fossify.messages.helpers.formatMessageDateTimeCompact
import org.fossify.messages.helpers.generateStableId
import org.fossify.messages.helpers.isEmojiOnly
import org.fossify.messages.helpers.setupDocumentPreview
import org.fossify.messages.helpers.setupVCardPreview
import org.fossify.messages.models.Attachment
import org.fossify.messages.models.Message
import org.fossify.messages.models.MessageReaction
import org.fossify.messages.models.ThreadItem
import org.fossify.messages.models.ThreadItem.ThreadDateTime
import org.fossify.messages.models.ThreadItem.ThreadError
import org.fossify.messages.models.ThreadItem.ThreadSending
import org.fossify.messages.models.ThreadItem.ThreadSent
import org.fossify.messages.models.toMediaItems
import org.joda.time.DateTime

class ThreadAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
    val retryMessage: (messageId: Long) -> Unit,
    val isRecycleBin: Boolean,
    val reactionActions: ThreadReactionActions,
    val deleteMessages: (messages: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) -> Unit
) : MyRecyclerViewListAdapter<ThreadItem>(activity, recyclerView, ThreadItemDiffCallback(), itemClick) {
    private var fontSize = activity.getTextSize()

    @SuppressLint("MissingPermission")
    private val hasMultipleSIMCards = (activity.subscriptionManagerCompat().activeSubscriptionInfoList?.size ?: 0) > 1
    /**
     * How wide an attachment may be drawn.
     *
     * The bubble is 80% of the row, and the row is inset by an activity margin on each side, so
     * measuring 80% of the whole screen overflows the container by most of that inset and the
     * right edge of every image gets clipped. Received messages lose the width of the sender's
     * photo on top of that.
     */
    private val chatBubbleInset =
        activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin) * 2

    private val senderPhotoWidth =
        activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.list_icon_size_medium) +
                activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin)

    private val maxChatBubbleWidth =
        ((activity.usableScreenSize.x - chatBubbleInset) * BUBBLE_WIDTH_RATIO).toInt()

    /** The one message currently tapped open, showing its timestamp and selectable at a larger size. */
    private var expandedMessageId: Long? = null

    /** The search match the user is standing on, tinted so the jump is obvious. */
    private var highlightedMessageId: Long? = null

    companion object {
        private const val MAX_MEDIA_HEIGHT_RATIO = 3

        /** Bubbles take four fifths of the row, matching layout_constraintWidth_percent. */
        private const val BUBBLE_WIDTH_RATIO = 0.8f
        private const val SIM_BITS = 21
        private const val SIM_MASK = (1L shl SIM_BITS) - 1

        /** How much a tapped message grows, enough to read comfortably without reflowing the thread. */
        private const val READING_SCALE = 1.25f

        /** A handful of emoji on their own get the big treatment; a wall of them stays readable. */
        private const val EMOJI_SCALE = 2.0f

        /** The timestamp under an open message, deliberately quieter than the message itself. */
        private const val SMALL_TEXT_SCALE = 0.8f

        /** A tapback is a footnote on the message, not a second message. */
        private const val REACTION_TEXT_SCALE = 0.8f
        private const val REACTION_FILL_ALPHA = 0.15f
        private const val REACTION_STROKE_ALPHA = 0.35f

        /** Status lines sit beneath the message and should never compete with it. */
        private const val STATUS_TEXT_SCALE = 0.8f
        private const val STATUS_ALPHA = 0.7f

        /** Just enough tint to find the message, not so much that it obscures the text. */
        private const val HIGHLIGHT_ALPHA = 0.25f
    }

    init {
        setupDragListener(true)
        setHasStableIds(true)
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    override fun getActionMenuId() = R.menu.cab_thread

    override fun prepareActionMode(menu: Menu) {
        val isOneItemSelected = isOneItemSelected()
        val selectedMessages = getSelectedItems().filterIsInstance<Message>()
        val hasText = selectedMessages.any { it.body.isNotEmpty() }
        val showSaveAs = getSelectedItems().all {
            it is Message && (it.attachment?.attachments?.size ?: 0) > 0
        } && getSelectedAttachments().isNotEmpty()

        menu.apply {
            findItem(R.id.cab_copy_to_clipboard).isVisible = hasText
            findItem(R.id.cab_save_as).isVisible = showSaveAs
            findItem(R.id.cab_share).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_forward_message).isVisible = isOneItemSelected
            findItem(R.id.cab_select_text).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_properties).isVisible = isOneItemSelected
            findItem(R.id.cab_restore).isVisible = isRecycleBin
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        val selected = getSelectedMessages()
        when (id) {
            R.id.cab_copy_to_clipboard -> copyToClipboard(selected)
            R.id.cab_save_as -> saveAs(getSelectedAttachments())
            R.id.cab_delete -> askConfirmDelete(selected)
            R.id.cab_restore -> askConfirmRestore(selected)
            R.id.cab_select_all -> selectAll()
            else -> oneMessageActionPressed(id, selected.firstOrNull() ?: return)
        }
    }

    /** The menu items the toolbar only offers while exactly one message is selected. */
    private fun oneMessageActionPressed(id: Int, message: Message) {
        when (id) {
            R.id.cab_share -> shareText(message)
            R.id.cab_forward_message -> forwardMessage(message)
            R.id.cab_select_text -> selectText(message)
            R.id.cab_properties -> showMessageDetails(message)
        }
    }

    override fun getSelectableItemCount() = currentList.filterIsInstance<Message>().size

    override fun getIsItemSelectable(position: Int) = !isThreadDateTime(position)

    override fun getItemSelectionKey(position: Int): Int? {
        return (currentList.getOrNull(position) as? Message)?.getSelectionKey()
    }

    override fun getItemKeyPosition(key: Int): Int {
        return currentList.indexOfFirst { (it as? Message)?.getSelectionKey() == key }
    }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when (viewType) {
            THREAD_DATE_TIME -> ItemThreadDateTimeBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_ERROR -> ItemThreadErrorBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_SENT -> ItemThreadSuccessBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_SENDING -> ItemThreadSendingBinding.inflate(layoutInflater, parent, false)
            else -> ItemMessageBinding.inflate(layoutInflater, parent, false)
        }

        return ThreadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val isClickable = item is ThreadError || item is Message
        val isLongClickable = item is Message
        holder.bindView(item, isClickable, isLongClickable) { itemView, _ ->
            when (item) {
                is ThreadDateTime -> setupDateTime(itemView, item)
                is ThreadError -> setupThreadError(itemView, item)
                is ThreadSent -> setupThreadSuccess(itemView, item.delivered)
                is ThreadSending -> setupThreadSending(itemView)
                is Message -> setupView(holder, itemView, item)
            }
        }

        // bindView installs its own long press listener on the row after running the callback
        // above, so ours has to go on afterwards or it would be the one that got thrown away
        if (item is Message) {
            setupMessageLongPress(holder, item)
        }

        bindViewHolder(holder)
    }

    private fun setupMessageLongPress(holder: ViewHolder, message: Message) {
        val binding = ItemMessageBinding.bind(holder.itemView)
        // the bubble is what the menu belongs beside, but a picture on its own has no bubble
        val anchor = if (message.body.isNotEmpty()) {
            binding.threadMessageBody
        } else {
            binding.threadMessageAttachmentsHolder
        }

        holder.itemView.setOnLongClickListener {
            longPressed(holder, message, anchor)
            true
        }
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItem(position)) {
            is Message -> item.getStableId()
            is ThreadDateTime -> {
                val sim = (item.simID.hashCode().toLong() and SIM_MASK)
                val key = (item.date.toLong() shl SIM_BITS) or sim
                generateStableId(THREAD_DATE_TIME, key)
            }
            is ThreadError -> generateStableId(THREAD_SENT_MESSAGE_ERROR, item.messageId)
            is ThreadSending -> generateStableId(THREAD_SENT_MESSAGE_SENDING, item.messageId)
            is ThreadSent -> generateStableId(THREAD_SENT_MESSAGE_SENT, item.messageId)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ThreadDateTime -> THREAD_DATE_TIME
            is ThreadError -> THREAD_SENT_MESSAGE_ERROR
            is ThreadSent -> THREAD_SENT_MESSAGE_SENT
            is ThreadSending -> THREAD_SENT_MESSAGE_SENDING
            is Message -> if (item.isReceivedMessage()) THREAD_RECEIVED_MESSAGE else THREAD_SENT_MESSAGE
        }
    }

    private fun copyToClipboard(messages: List<Message>) {
        if (messages.isEmpty()) return

        val textToCopy = if (messages.size == 1) {
            messages.first().body
        } else {
            messages.filter { it.body.isNotEmpty() }.joinToString("\n\n") { message ->
                val format = "${activity.config.dateFormat}, ${activity.getTimeFormat()}"
                val dateTime = DateTime(message.millis()).toString(format)
                val sender = if (message.isReceivedMessage()) message.senderName else activity.getString(R.string.me)
                "[$dateTime] $sender: ${message.body}"
            }
        }

        if (textToCopy.isNotEmpty()) {
            activity.copyToClipboard(textToCopy)
        }
    }

    private fun getSelectedMessages() = getSelectedItems().filterIsInstance<Message>()

    private fun getSelectedAttachments(): List<Attachment> {
        return getSelectedMessages().flatMap { it.attachment?.attachments.orEmpty() }
    }

    private fun saveAs(attachments: List<Attachment>) {
        if (attachments.isNotEmpty()) {
            (activity as ThreadActivity).saveMMS(attachments)
        }
    }

    private fun shareText(message: Message) {
        activity.shareTextIntent(message.body)
    }

    private fun selectText(message: Message) {
        if (message.body.trim().isNotEmpty()) {
            SelectTextDialog(activity, message.body)
        }
    }

    private fun showMessageDetails(message: Message) {
        MessageDetailsDialog(activity, message)
    }

    private fun askConfirmDelete(messages: List<Message>) {
        val itemsCnt = messages.size

        // not sure how we can get UnknownFormatConversionException here, so show the error and hope that someone reports it
        val items = try {
            resources.getQuantityString(R.plurals.delete_messages, itemsCnt, itemsCnt)
        } catch (e: Exception) {
            activity.showErrorToast(e)
            return
        }

        val baseString = if (activity.config.useRecycleBin && !isRecycleBin) {
            org.fossify.commons.R.string.move_to_recycle_bin_confirmation
        } else {
            org.fossify.commons.R.string.deletion_confirmation
        }
        val question = String.format(resources.getString(baseString), items)

        DeleteConfirmationDialog(activity, question, activity.config.useRecycleBin && !isRecycleBin) { skipRecycleBin ->
            ensureBackgroundThread {
                if (messages.isNotEmpty()) {
                    val toRecycleBin = !skipRecycleBin && activity.config.useRecycleBin && !isRecycleBin
                    deleteMessages(messages, toRecycleBin, false)
                }
            }
        }
    }

    private fun askConfirmRestore(messages: List<Message>) {
        val itemsCnt = messages.size

        // not sure how we can get UnknownFormatConversionException here, so show the error and hope that someone reports it
        val items = try {
            resources.getQuantityString(R.plurals.delete_messages, itemsCnt, itemsCnt)
        } catch (e: Exception) {
            activity.showErrorToast(e)
            return
        }

        val baseString = R.string.restore_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                if (messages.isNotEmpty()) {
                    deleteMessages(messages, false, true)
                }
            }
        }
    }

    private fun forwardMessage(message: Message) {
        val attachment = message.attachment?.attachments?.firstOrNull()
        Intent(activity, NewConversationActivity::class.java).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message.body)

            if (attachment != null) {
                putExtra(Intent.EXTRA_STREAM, attachment.getUri())
            }

            activity.startActivity(this)
        }
    }

    private fun getSelectedItems(): ArrayList<ThreadItem> {
        return currentList.filter {
            selectedKeys.contains((it as? Message)?.getSelectionKey() ?: 0)
        } as ArrayList<ThreadItem>
    }

    private fun isThreadDateTime(position: Int) = currentList.getOrNull(position) is ThreadDateTime

    fun updateMessages(
        newMessages: List<ThreadItem>,
        scrollPosition: Int = -1,
        smoothScroll: Boolean = false
    ) {
        val latestMessages = newMessages.toMutableList()
        submitList(latestMessages) {
            if (scrollPosition != -1) {
                if (smoothScroll) {
                    recyclerView.smoothScrollToPosition(scrollPosition)
                } else {
                    recyclerView.scrollToPosition(scrollPosition)
                }
            }
        }
    }

    /** Tapping a message opens it: bigger text, its timestamp, and selectable for partial copying. */
    fun toggleExpanded(message: Message) {
        val previouslyExpanded = expandedMessageId
        expandedMessageId = if (previouslyExpanded == message.id) null else message.id

        rebindInPlace(listOfNotNull(previouslyExpanded, expandedMessageId).distinct())
    }

    /**
     * Resizes a message and shifts everything below it in the same frame.
     *
     * Left to the default animator the message grows at once while its neighbours slide into
     * their new places over a quarter of a second, and for those frames the message below is
     * drawn on top of the one that just opened.
     */
    private fun rebindInPlace(messageIds: List<Long>) {
        val positions = messageIds
            .map { id -> currentList.indexOfFirst { (it as? Message)?.id == id } }
            .filter { it != -1 }
        if (positions.isEmpty()) {
            return
        }

        val animator = recyclerView.itemAnimator
        recyclerView.itemAnimator = null
        positions.forEach { notifyItemChanged(it) }
        recyclerView.doOnPreDraw { recyclerView.itemAnimator = animator }
    }

    /** Marks the search match the user is currently standing on, so the jump lands somewhere visible. */
    fun highlightMessage(messageId: Long?) {
        val previous = highlightedMessageId
        highlightedMessageId = messageId

        listOfNotNull(previous, messageId).distinct().forEach { id ->
            val position = currentList.indexOfFirst { (it as? Message)?.id == id }
            if (position != -1) {
                notifyItemChanged(position)
            }
        }
    }

    /** Called when focus moves elsewhere, so an open message doesn't stay open behind the keyboard. */
    fun collapseExpanded() {
        val expanded = expandedMessageId ?: return
        expandedMessageId = null
        rebindInPlace(listOf(expanded))
    }

    private fun bodyTextSize(isExpanded: Boolean, isEmojiOnly: Boolean) = when {
        isEmojiOnly -> fontSize * EMOJI_SCALE
        isExpanded -> fontSize * READING_SCALE
        else -> fontSize
    }

    private fun Message.formatSentOrReceivedAt(): String {
        return DateTime(date * 1000L)
            .let { it.millis.formatMessageDateTime(activity) }
    }

    /**
     * Long press on the text of a message, which means the same thing whether it is open or not:
     * the message's own menu, and on a link the link's.
     *
     * Android's own selection handles used to have the open state to themselves, so that part of
     * a message could be copied in place. Select text in the menu does that job now, and one
     * gesture doing one thing is worth more than the shortcut was.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupBodyGestures(body: MyTextView, message: Message, holder: ViewHolder) {
        var touchX = 0f
        var touchY = 0f
        var heldALink = false

        body.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchX = event.x
                    touchY = event.y
                    heldALink = false
                    false // passing it on keeps a tap on a link opening the link
                }

                // A link opens on the finger coming up, however long it was held down for, so
                // that last event has to be swallowed or the page opens over the menu.
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> heldALink
                else -> false
            }
        }

        body.setOnLongClickListener {
            val url = body.urlAt(touchX, touchY)
            if (url != null) {
                heldALink = true
                body.isPressed = false
                showLinkOptions(url)
            } else {
                longPressed(holder, message, body)
            }
            true
        }
    }

    private fun MyTextView.urlAt(x: Float, y: Float): String? {
        val spanned = text as? Spanned ?: return null
        val textLayout = layout ?: return null
        val line = textLayout.getLineForVertical((y - totalPaddingTop + scrollY).toInt())
        val offset = textLayout.getOffsetForHorizontal(line, x - totalPaddingLeft + scrollX)
        return spanned.getSpans(offset, offset, URLSpan::class.java).firstOrNull()?.url
    }

    private fun showLinkOptions(url: String) {
        LinkOptionsDialog(activity, url)
    }

    private fun setupView(holder: ViewHolder, view: View, message: Message) {
        ItemMessageBinding.bind(view).apply {
            threadMessageHolder.isSelected = selectedKeys.contains(message.getSelectionKey())
            val isExpanded = message.id == expandedMessageId
            val isHighlighted = message.id == highlightedMessageId
            val isEmojiOnly = message.body.isEmojiOnly() &&
                    message.body.emojiCount() <= MAX_ENLARGED_EMOJI

            threadMessageBody.apply {
                text = message.body
                setTextSize(TypedValue.COMPLEX_UNIT_PX, bodyTextSize(isExpanded, isEmojiOnly))
                beVisibleIf(message.body.isNotEmpty())
                setupBodyGestures(this, message, holder)

                setOnClickListener {
                    holder.viewClicked(message)
                }
            }

            threadMessageHolder.setBackgroundColor(
                if (isHighlighted) properPrimaryColor.adjustAlpha(HIGHLIGHT_ALPHA) else Color.TRANSPARENT
            )

            threadMessageDetails.apply {
                beVisibleIf(isExpanded)
                if (isExpanded) {
                    text = message.formatSentOrReceivedAt()
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * SMALL_TEXT_SCALE)
                }
            }

            if (message.isReceivedMessage()) {
                setupReceivedMessageView(messageBinding = this, message = message)
            } else {
                setupSentMessageView(messageBinding = this, message = message)
            }

            setupReactions(messageBinding = this, message = message)

            if (message.attachment?.attachments?.isNotEmpty() == true) {
                threadMessageAttachmentsHolder.beVisible()
                threadMessageAttachmentsHolder.removeAllViews()
                for (attachment in message.attachment.attachments) {
                    val mimetype = attachment.mimetype
                    when {
                        mimetype.isImageMimeType() || mimetype.isVideoMimeType() -> setupImageView(holder, binding = this, message, attachment)
                        mimetype.isVCardMimeType() -> setupVCardView(holder, threadMessageAttachmentsHolder, message, attachment)
                        else -> setupFileView(holder, threadMessageAttachmentsHolder, message, attachment)
                    }

                    threadMessagePlayOutline.beVisibleIf(mimetype.startsWith("video/"))
                }
            } else {
                threadMessageAttachmentsHolder.beGone()
                threadMessagePlayOutline.beGone()
            }
        }
    }

    private fun setupReceivedMessageView(messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.apply {
            with(ConstraintSet()) {
                clone(threadMessageHolder)
                clear(threadMessageWrapper.id, ConstraintSet.END)
                connect(threadMessageWrapper.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                applyTo(threadMessageHolder)
            }

            threadMessageSenderPhoto.beVisible()
            threadMessageSenderPhoto.setOnClickListener {
                val contact = message.getSender()!!
                activity.getContactFromAddress(contact.phoneNumbers.first().normalizedNumber) {
                    if (it != null) {
                        activity.startContactDetailsIntent(it)
                    }
                }
            }

            threadMessageBody.apply {
                background = AppCompatResources.getDrawable(activity, R.drawable.item_received_background)
                setTextColor(textColor)
                setLinkTextColor(activity.getProperPrimaryColor())
            }

            if (!activity.isFinishing && !activity.isDestroyed) {
                val contactLetterIcon = SimpleContactsHelper(activity).getContactLetterIcon(message.senderName)
                val placeholder = contactLetterIcon.toDrawable(activity.resources)

                val options = RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .error(placeholder)
                    .centerCrop()

                Glide.with(activity)
                    .load(message.senderPhotoUri)
                    .placeholder(placeholder)
                    .apply(options)
                    .apply(RequestOptions.circleCropTransform())
                    .into(threadMessageSenderPhoto)
            }
        }
    }

    private fun setupSentMessageView(messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.apply {
            with(ConstraintSet()) {
                clone(threadMessageHolder)
                clear(threadMessageWrapper.id, ConstraintSet.START)
                connect(threadMessageWrapper.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                applyTo(threadMessageHolder)
            }

            val primaryColor = activity.getProperPrimaryColor()
            val contrastColor = primaryColor.getContrastColor()

            // The timestamp and the tapbacks both belong under the bubble they describe, which for
            // a sent message is over on the right; left where the layout puts them, they read as
            // the other person's.
            arrayOf(threadMessageDetails, threadMessageReactions).forEach { view ->
                view.updateLayoutParams<RelativeLayout.LayoutParams> {
                    removeRule(RelativeLayout.END_OF)
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                }
            }

            threadMessageBody.apply {
                updateLayoutParams<RelativeLayout.LayoutParams> {
                    removeRule(RelativeLayout.END_OF)
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                }

                background = AppCompatResources.getDrawable(activity, R.drawable.item_sent_background)
                background.applyColorFilter(primaryColor)
                setTextColor(contrastColor)
                setLinkTextColor(contrastColor)

                if (message.isScheduled) {
                    typeface = Typeface.create(FontHelper.getTypeface(activity), Typeface.ITALIC)
                    val scheduledDrawable = AppCompatResources.getDrawable(activity, org.fossify.commons.R.drawable.ic_clock_vector)?.apply {
                        applyColorFilter(contrastColor)
                        val size = lineHeight
                        setBounds(0, 0, size, size)
                    }

                    setCompoundDrawables(null, null, scheduledDrawable, null)
                } else {
                    typeface = FontHelper.getTypeface(activity)
                    setCompoundDrawables(null, null, null, null)
                }
            }
        }
    }

    /** Draws the tapbacks a message is carrying, tucked under the corner of its bubble. */
    private fun setupReactions(messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.threadMessageReactions.apply {
            removeAllViews()
            beVisibleIf(message.reactions.isNotEmpty())
            message.reactions
                .groupBy { it.emoji }
                .forEach { (emoji, reactions) -> addView(buildReactionChip(this, emoji, reactions)) }
        }
    }

    private fun buildReactionChip(
        parent: LinearLayout,
        emoji: String,
        reactions: List<MessageReaction>,
    ): View {
        val chip = ItemMessageReactionBinding.inflate(layoutInflater, parent, false).reactionChip
        chip.text = if (reactions.size > 1) "$emoji ${reactions.size}" else emoji
        chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * REACTION_TEXT_SCALE)
        chip.setTextColor(textColor)
        chip.contentDescription = describeReaction(emoji, reactions)
        chip.background = (chip.background.mutate() as GradientDrawable).apply {
            setColor(properPrimaryColor.adjustAlpha(REACTION_FILL_ALPHA))
            setStroke(
                resources.getDimensionPixelSize(R.dimen.reaction_chip_stroke_width),
                properPrimaryColor.adjustAlpha(REACTION_STROKE_ALPHA)
            )
        }
        chip.updateLayoutParams<LinearLayout.LayoutParams> {
            marginEnd = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.tiny_margin)
        }

        return chip
    }

    private fun describeReaction(emoji: String, reactions: List<MessageReaction>): String {
        val names = reactions.joinToString(", ") {
            if (it.isFromMe) activity.getString(R.string.me) else it.senderName
        }

        return activity.getString(R.string.reaction_by, emoji, names)
    }

    /** Picking the reaction you already have takes it back off, exactly as an iPhone does. */
    private fun react(message: Message, emoji: String, mine: String?) {
        val kind = ReactionKind.entries.firstOrNull { it.emoji == emoji }
        reactionActions.send(message, emoji, kind, emoji == mine)
    }

    /**
     * Opens everything you can do to one message, beside the message itself.
     *
     * Long press used to go straight into multi-select. That is now one line in this menu, which
     * keeps the gesture doing the obvious thing - showing you what this message can do - and the
     * range selection that follows a long press while already selecting still works.
     */
    private fun showMessageActions(holder: ViewHolder, message: Message, anchor: View) {
        val mine = message.reactions.firstOrNull { it.isFromMe }?.emoji
        val canReact = !isRecycleBin && message.acceptsReactions() && reactionActions.isAvailable()
        val reactionRow = if (canReact) {
            MessageReactionRow(
                chosen = mine,
                onPick = { emoji -> react(message, emoji, mine) },
                onPickOther = {
                    ReactionPickerDialog(activity, mine) { emoji -> react(message, emoji, mine) }
                }
            )
        } else {
            null
        }

        MessageActionsPopup(
            activity = activity,
            anchor = anchor,
            alignToEnd = !message.isReceivedMessage(),
            reactions = reactionRow,
            actions = buildMessageActions(holder, message)
        ).show()
    }

    private fun buildMessageActions(holder: ViewHolder, message: Message): List<MessageAction> {
        val hasText = message.body.isNotEmpty()
        val attachments = message.attachment?.attachments.orEmpty()
        val actions = mutableListOf<MessageAction>()

        if (hasText) {
            actions += MessageAction(
                org.fossify.commons.R.drawable.ic_copy_vector,
                org.fossify.commons.R.string.copy_to_clipboard
            ) { copyToClipboard(listOf(message)) }
        }

        actions += MessageAction(
            org.fossify.commons.R.drawable.ic_arrow_right_vector,
            R.string.forward_message
        ) { forwardMessage(message) }

        if (hasText) {
            actions += MessageAction(
                org.fossify.commons.R.drawable.ic_share_vector,
                org.fossify.commons.R.string.share
            ) { shareText(message) }

            actions += MessageAction(
                org.fossify.commons.R.drawable.ic_article_outline_vector,
                org.fossify.commons.R.string.select_text
            ) { selectText(message) }
        }

        if (attachments.isNotEmpty()) {
            actions += MessageAction(
                org.fossify.commons.R.drawable.ic_save_vector,
                org.fossify.commons.R.string.save_as
            ) { saveAs(attachments) }
        }

        actions += MessageAction(
            org.fossify.commons.R.drawable.ic_info_vector,
            org.fossify.commons.R.string.properties
        ) { showMessageDetails(message) }

        actions += MessageAction(
            org.fossify.commons.R.drawable.ic_select_all_vector,
            R.string.select_messages
        ) { holder.viewLongClicked() }

        if (isRecycleBin) {
            actions += MessageAction(
                org.fossify.commons.R.drawable.ic_undo_vector,
                R.string.restore
            ) { askConfirmRestore(listOf(message)) }
        }

        actions += MessageAction(
            org.fossify.commons.R.drawable.ic_delete_vector,
            org.fossify.commons.R.string.delete
        ) { askConfirmDelete(listOf(message)) }

        return actions
    }

    /**
     * What a long press does, which depends on whether anything is already selected.
     *
     * While selecting, it still extends the selection the way it always has. Otherwise it opens
     * the message's own menu.
     */
    private fun longPressed(holder: ViewHolder, message: Message, anchor: View) {
        if (actModeCallback.isSelectable) {
            holder.viewLongClicked()
        } else {
            showMessageActions(holder, message, anchor)
        }
    }

    private fun setupImageView(holder: ViewHolder, binding: ItemMessageBinding, message: Message, attachment: Attachment) = binding.apply {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()

        val attachmentWidth = maxChatBubbleWidth -
            if (message.isReceivedMessage()) senderPhotoWidth else 0

        val imageView = ItemAttachmentImageBinding.inflate(layoutInflater)
        threadMessageAttachmentsHolder.addView(imageView.root)

        val placeholderDrawable = Color.TRANSPARENT.toDrawable()
        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(placeholderDrawable)
            .transform(FitCenter())

        Glide.with(root.context)
            .load(uri)
            .apply(options)
            .dontAnimate()
            .override(attachmentWidth, attachmentWidth * MAX_MEDIA_HEIGHT_RATIO)
            .downsample(DownsampleStrategy.AT_MOST)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    threadMessagePlayOutline.beGone()
                    threadMessageAttachmentsHolder.removeView(imageView.root)
                    return false
                }

                override fun onResourceReady(dr: Drawable, a: Any, t: Target<Drawable>, d: DataSource, i: Boolean) = false
            })
            .into(imageView.attachmentImage)

        imageView.attachmentImage.updateLayoutParams<ViewGroup.LayoutParams> {
            width = attachmentWidth
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        imageView.attachmentImage.setOnClickListener {
            if (actModeCallback.isSelectable) {
                holder.viewClicked(message)
            } else if (activity.config.openMediaInApp) {
                openInMediaViewer(attachment)
            } else {
                activity.launchViewIntent(uri, mimetype, attachment.filename)
            }
        }
        imageView.root.setOnLongClickListener {
            longPressed(holder, message, imageView.root)
            true
        }
    }

    /**
     * Opens the viewer on the tapped attachment, but hands it every picture and video in the
     * conversation, so swiping sideways walks the thread's media instead of dead-ending.
     */
    private fun openInMediaViewer(tapped: Attachment) {
        val items = currentList.filterIsInstance<Message>().toMediaItems()
        val tappedUri = tapped.getUri().toString()
        val startIndex = items.indexOfFirst { it.uriString == tappedUri }.coerceAtLeast(0)
        activity.openMediaViewer(items, startIndex)
    }

    private fun setupVCardView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val uri = attachment.getUri()
        lateinit var card: View
        val vCardView = ItemAttachmentVcardBinding.inflate(layoutInflater).apply {
            setupVCardPreview(
                activity = activity,
                uri = uri,
                onClick = {
                    if (actModeCallback.isSelectable) {
                        holder.viewClicked(message)
                    } else {
                        val intent = Intent(activity, VCardViewerActivity::class.java).also {
                            it.putExtra(EXTRA_VCARD_URI, uri)
                        }
                        activity.startActivity(intent)
                    }
                },
                onLongClick = { longPressed(holder, message, card) }
            )
        }.root

        card = vCardView
        parent.addView(vCardView)
    }

    private fun setupFileView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()
        lateinit var file: View
        val attachmentView = ItemAttachmentDocumentBinding.inflate(layoutInflater).apply {
            setupDocumentPreview(
                uri = uri,
                title = attachment.filename,
                mimeType = attachment.mimetype,
                onClick = {
                    if (actModeCallback.isSelectable) {
                        holder.viewClicked(message)
                    } else {
                        activity.launchViewIntent(uri, mimetype, attachment.filename)
                    }
                },
                onLongClick = { longPressed(holder, message, file) }
            )
        }.root

        file = attachmentView
        parent.addView(attachmentView)
    }

    private fun setupDateTime(view: View, dateTime: ThreadDateTime) {
        ItemThreadDateTimeBinding.bind(view).apply {
            threadDateTime.apply {
                text = (dateTime.date * 1000L).formatMessageDateTimeCompact(context)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }
            threadDateTime.setTextColor(textColor)

            threadSimIcon.beVisibleIf(hasMultipleSIMCards)
            threadSimNumber.beVisibleIf(hasMultipleSIMCards)
            if (hasMultipleSIMCards) {
                threadSimNumber.text = dateTime.simID
                threadSimNumber.setTextColor(textColor.getContrastColor())
                threadSimIcon.applyColorFilter(textColor)
            }
        }
    }

    private fun setupThreadSuccess(view: View, isDelivered: Boolean) {
        ItemThreadSuccessBinding.bind(view).apply {
            val statusLabel = if (isDelivered) R.string.message_delivered else R.string.message_sent
            threadSuccessLabel.apply {
                text = activity.getString(statusLabel)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * STATUS_TEXT_SCALE)
                setTextColor(textColor)
                alpha = STATUS_ALPHA
            }

            threadSuccess.setImageResource(
                if (isDelivered) {
                    R.drawable.ic_check_double_vector
                } else {
                    org.fossify.commons.R.drawable.ic_check_vector
                }
            )
            threadSuccess.applyColorFilter(textColor)
            threadSuccess.alpha = STATUS_ALPHA
        }
    }

    private fun setupThreadError(view: View, item: ThreadError) {
        ItemThreadErrorBinding.bind(view).apply {
            val errorColor = activity.getColor(R.color.message_error)
            threadError.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * STATUS_TEXT_SCALE)
                setTextColor(errorColor)
            }

            threadErrorIcon.applyColorFilter(errorColor)

            threadErrorRetry.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * STATUS_TEXT_SCALE)
                setTextColor(properPrimaryColor)
                setOnClickListener { retryMessage(item.messageId) }
            }
        }
    }

    private fun setupThreadSending(view: View) {
        ItemThreadSendingBinding.bind(view).apply {
            threadSending.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * STATUS_TEXT_SCALE)
                setTextColor(textColor)
                alpha = STATUS_ALPHA
            }

            threadSendingIcon.applyColorFilter(textColor)
            threadSendingIcon.alpha = STATUS_ALPHA
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val binding = (holder as ThreadViewHolder).binding
            if (binding is ItemMessageBinding) {
                Glide.with(activity).clear(binding.threadMessageSenderPhoto)
            }
        }
    }

    inner class ThreadViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)
}

private class ThreadItemDiffCallback : DiffUtil.ItemCallback<ThreadItem>() {

    override fun areItemsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadError -> oldItem.messageId == (newItem as ThreadError).messageId
            is ThreadSent -> oldItem.messageId == (newItem as ThreadSent).messageId
            is ThreadSending -> oldItem.messageId == (newItem as ThreadSending).messageId
            is Message -> Message.areItemsTheSame(oldItem, newItem as Message)
            is ThreadDateTime -> {
                val new = newItem as ThreadDateTime
                oldItem.date == new.date && oldItem.simID == new.simID
            }
        }
    }

    override fun areContentsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadSending -> true
            is ThreadDateTime -> oldItem.simID == (newItem as ThreadDateTime).simID
            is ThreadError -> oldItem.messageText == (newItem as ThreadError).messageText
            is ThreadSent -> oldItem.delivered == (newItem as ThreadSent).delivered
            is Message -> Message.areContentsTheSame(oldItem, newItem as Message)
        }
    }
}
