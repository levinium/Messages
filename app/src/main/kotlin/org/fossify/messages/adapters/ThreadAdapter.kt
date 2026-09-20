package org.fossify.messages.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.text.style.URLSpan
import android.view.GestureDetector
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
import org.fossify.commons.dialogs.RadioGroupDialog
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
import org.fossify.commons.extensions.launchViewIntent
import org.fossify.commons.extensions.shareTextIntent
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.usableScreenSize
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.models.RadioItem
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
import org.fossify.messages.databinding.ItemThreadDateTimeBinding
import org.fossify.messages.databinding.ItemThreadErrorBinding
import org.fossify.messages.databinding.ItemThreadSendingBinding
import org.fossify.messages.databinding.ItemThreadSuccessBinding
import org.fossify.messages.dialogs.DeleteConfirmationDialog
import org.fossify.messages.dialogs.MessageDetailsDialog
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

        private const val LINK_COPY = 0
        private const val LINK_OPEN = 1
        private const val LINK_SHARE = 2

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

        when (id) {
            R.id.cab_copy_to_clipboard -> copyToClipboard()
            R.id.cab_save_as -> saveAs()
            R.id.cab_share -> shareText()
            R.id.cab_forward_message -> forwardMessage()
            R.id.cab_select_text -> selectText()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_restore -> askConfirmRestore()
            R.id.cab_select_all -> selectAll()
            R.id.cab_properties -> showMessageDetails()
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
        bindViewHolder(holder)
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

    private fun copyToClipboard() {
        val selectedMessages = getSelectedItems().filterIsInstance<Message>()
        if (selectedMessages.isEmpty()) return

        val textToCopy = if (selectedMessages.size == 1) {
            selectedMessages.first().body
        } else {
            selectedMessages.filter { it.body.isNotEmpty() }.joinToString("\n\n") { message ->
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

    private fun getSelectedAttachments(): List<Attachment> {
        val selectedMessages = getSelectedItems().filterIsInstance<Message>()
        return selectedMessages.flatMap { it.attachment?.attachments.orEmpty() }
    }

    private fun saveAs() {
        val attachments = getSelectedAttachments()
        if (attachments.isNotEmpty()) {
            (activity as ThreadActivity).saveMMS(attachments)
        }
    }

    private fun shareText() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        activity.shareTextIntent(firstItem.body)
    }

    private fun selectText() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        if (firstItem.body.trim().isNotEmpty()) {
            SelectTextDialog(activity, firstItem.body)
        }
    }

    private fun showMessageDetails() {
        val message = getSelectedItems().firstOrNull() as? Message ?: return
        MessageDetailsDialog(activity, message)
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size

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
                val messagesToRemove = getSelectedItems()
                if (messagesToRemove.isNotEmpty()) {
                    val toRecycleBin = !skipRecycleBin && activity.config.useRecycleBin && !isRecycleBin
                    deleteMessages(messagesToRemove.filterIsInstance<Message>(), toRecycleBin, false)
                }
            }
        }
    }

    private fun askConfirmRestore() {
        val itemsCnt = selectedKeys.size

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
                val messagesToRestore = getSelectedItems()
                if (messagesToRestore.isNotEmpty()) {
                    deleteMessages(messagesToRestore.filterIsInstance<Message>(), false, true)
                }
            }
        }
    }

    private fun forwardMessage() {
        val message = getSelectedItems().firstOrNull() as? Message ?: return
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
     * Long press means two different things depending on whether the message is open.
     *
     * Closed, it starts multi-select, as it always has. Open, the text is selectable, so a long
     * press belongs to Android's own selection handles - except on a link, where copying the link
     * itself is almost always what was wanted.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupBodyGestures(
        body: MyTextView,
        isExpanded: Boolean,
        message: Message,
        holder: ViewHolder,
    ) {
        if (!isExpanded) {
            body.setOnTouchListener(null)
            body.setOnLongClickListener {
                holder.viewLongClicked()
                true
            }
            return
        }

        // Selectable text routes touches through Android's editor, which never calls
        // OnClickListener, so the tap that closes the message has to be spotted here instead.
        val gestureDetector = GestureDetector(
            activity,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    holder.viewClicked(message)
                    return false
                }

                override fun onLongPress(event: MotionEvent) {
                    val url = body.urlAt(event.x, event.y) ?: return
                    showLinkOptions(url)
                }
            }
        )

        var touchX = 0f
        var touchY = 0f
        body.setOnTouchListener { _, event ->
            touchX = event.x
            touchY = event.y
            gestureDetector.onTouchEvent(event)
            false // the editor still gets the event, so selection handles keep working
        }

        body.setOnLongClickListener {
            // Swallow the long press only on a link, where the menu above has already opened.
            body.urlAt(touchX, touchY) != null
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
        val items = arrayListOf(
            RadioItem(LINK_COPY, activity.getString(R.string.copy_link)),
            RadioItem(LINK_OPEN, activity.getString(R.string.open_link)),
            RadioItem(LINK_SHARE, activity.getString(org.fossify.commons.R.string.share)),
        )

        RadioGroupDialog(activity, items) { chosen ->
            when (chosen as Int) {
                LINK_COPY -> activity.copyToClipboard(url)
                LINK_OPEN -> activity.launchViewIntent(url)
                LINK_SHARE -> activity.shareTextIntent(url)
            }
        }
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

                // Selectable text hands the user Android's own selection handles, which is how
                // copying part of a message works. It is only on while expanded, because a
                // selectable view swallows the long press that starts multi-select.
                setTextIsSelectable(isExpanded)
                setupBodyGestures(this, isExpanded, message, holder)

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

            // The timestamp belongs under the bubble it describes, which for a sent message is
            // over on the right; left where the layout puts it, it reads as the other person's.
            threadMessageDetails.updateLayoutParams<RelativeLayout.LayoutParams> {
                removeRule(RelativeLayout.END_OF)
                addRule(RelativeLayout.ALIGN_PARENT_END)
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
            holder.viewLongClicked()
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
                onLongClick = { holder.viewLongClicked() }
            )
        }.root

        parent.addView(vCardView)
    }

    private fun setupFileView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()
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
                onLongClick = { holder.viewLongClicked() }
            )
        }.root

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
