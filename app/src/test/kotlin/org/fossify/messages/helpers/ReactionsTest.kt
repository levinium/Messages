package org.fossify.messages.helpers

import android.provider.Telephony
import org.fossify.messages.models.Attachment
import org.fossify.messages.models.Message
import org.fossify.messages.models.MessageAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wording is the whole protocol between this app and an iPhone, so the cases that matter are
 * the exact sentences iOS sends, and the ones it does not: an ordinary message that happens to
 * start with "Liked" must stay an ordinary message, or the thread quietly swallows it.
 */
class ReactionsTest {

    @Test
    fun `reads the six tapbacks an iPhone sends`() {
        val cases = mapOf(
            "Loved “see you at seven”" to ReactionKind.LOVE,
            "Liked “see you at seven”" to ReactionKind.LIKE,
            "Disliked “see you at seven”" to ReactionKind.DISLIKE,
            "Laughed at “see you at seven”" to ReactionKind.LAUGH,
            "Emphasized “see you at seven”" to ReactionKind.EMPHASIZE,
            "Questioned “see you at seven”" to ReactionKind.QUESTION,
        )

        cases.forEach { (body, kind) ->
            val parsed = body.parseReaction()
            assertEquals("wrong kind for: $body", kind, parsed?.kind)
            assertEquals("see you at seven", parsed?.quoted)
            assertEquals(false, parsed?.isRemoval)
        }
    }

    @Test
    fun `reads straight quotes as well as the curly ones iOS uses`() {
        val parsed = "Loved \"see you at seven\"".parseReaction()
        assertEquals(ReactionKind.LOVE, parsed?.kind)
        assertEquals("see you at seven", parsed?.quoted)
    }

    @Test
    fun `reads a reaction sent as a bare emoji`() {
        val parsed = "Reacted 🎉 to “we got the flat”".parseReaction()
        assertEquals("🎉", parsed?.emoji)
        assertNull("a free emoji has no named kind", parsed?.kind)
        assertEquals("we got the flat", parsed?.quoted)
    }

    @Test
    fun `reads a reaction being taken back`() {
        val named = "Removed a heart from “see you at seven”".parseReaction()
        assertEquals(ReactionKind.LOVE, named?.kind)
        assertEquals(true, named?.isRemoval)

        val emoji = "Removed 🎉 from “we got the flat”".parseReaction()
        assertEquals("🎉", emoji?.emoji)
        assertEquals(true, emoji?.isRemoval)
    }

    @Test
    fun `leaves ordinary messages alone`() {
        listOf(
            "Loved it",
            "Liked the new place, we should go back",
            "Loved “the film”, said nobody",
            "Reacted badly to the news",
            "Removed the old sofa from the flat",
            "“quoted for no reason”",
            "",
        ).forEach {
            assertNull("should not be a reaction: $it", it.parseReaction())
        }
    }

    @Test
    fun `writes what an iPhone would have written`() {
        val target = sent("see you at seven")
        assertEquals(
            "Loved \"see you at seven\"",
            formatReaction(ReactionKind.LOVE, ReactionKind.LOVE.emoji, target, isRemoval = false)
        )
        assertEquals(
            "Removed a heart from \"see you at seven\"",
            formatReaction(ReactionKind.LOVE, ReactionKind.LOVE.emoji, target, isRemoval = true)
        )
        assertEquals(
            "Reacted 🎉 to \"see you at seven\"",
            formatReaction(null, "🎉", target, isRemoval = false)
        )
    }

    @Test
    fun `names an attachment rather than quoting a message that has no text`() {
        val target = sent("", mimetype = "image/jpeg")
        val body = formatReaction(ReactionKind.LOVE, ReactionKind.LOVE.emoji, target, false)
        assertEquals("Loved an image", body)

        val parsed = body.parseReaction()
        assertNull("an attachment reaction quotes nothing", parsed?.quoted)
    }

    @Test
    fun `a reaction it can round trip lands back on the message it quotes`() {
        val target = sent("see you at seven")
        val body = formatReaction(ReactionKind.LOVE, ReactionKind.LOVE.emoji, target, false)
        val thread = listOf(target, received(body)).withReactionsApplied()

        assertEquals("the reaction is no longer a message of its own", 1, thread.size)
        assertEquals(ReactionKind.LOVE.emoji, thread.single().reactions.single().emoji)
        assertEquals(false, thread.single().reactions.single().isFromMe)
    }

    @Test
    fun `a long message is still matched after being shortened to fit one text`() {
        val long = "we should probably talk about the flat before the weekend because " +
                "the agent said they need an answer by Monday at the very latest, so let me know"
        val target = sent(long)
        val body = formatReaction(ReactionKind.LIKE, ReactionKind.LIKE.emoji, target, false)
        assertTrue("the quote should have been shortened", body.contains("…"))

        val thread = listOf(target, received(body)).withReactionsApplied()
        assertEquals(1, thread.size)
        assertEquals(ReactionKind.LIKE.emoji, thread.single().reactions.single().emoji)
    }

    @Test
    fun `one person gets one tapback, and can take it off again`() {
        val target = sent("see you at seven")
        val loved = received("Loved “see you at seven”")
        val liked = received("Liked “see you at seven”")
        val removed = received("Removed a like from “see you at seven”")

        val replaced = listOf(target, loved, liked).withReactionsApplied()
        assertEquals(ReactionKind.LIKE.emoji, replaced.single().reactions.single().emoji)

        val cleared = listOf(target, loved, liked, removed).withReactionsApplied()
        assertTrue("the tapback should be gone", cleared.single().reactions.isEmpty())
    }

    @Test
    fun `both sides of a conversation can react to the same message`() {
        val target = sent("see you at seven")
        val thread = listOf(
            target,
            received("Loved “see you at seven”"),
            sent("Liked “see you at seven”"),
        ).withReactionsApplied()

        val reactions = thread.single().reactions
        assertEquals(2, reactions.size)
        assertEquals(1, reactions.count { it.isFromMe })
    }

    @Test
    fun `a reaction with nothing to land on stays a message`() {
        val thread = listOf(
            sent("see you at eight"),
            received("Loved “see you at seven”"),
        ).withReactionsApplied()

        assertEquals("the orphan is still shown", 2, thread.size)
    }

    @Test
    fun `a reaction that failed to send stays visible so it can be retried`() {
        val target = received("see you at seven")
        val failed = message(
            body = "Loved \"see you at seven\"",
            type = Telephony.Sms.MESSAGE_TYPE_FAILED,
            from = ME,
        )

        val thread = listOf(target, failed).withReactionsApplied()
        assertEquals(2, thread.size)
    }

    @Test
    fun `a tapback lands on the message it quotes, not on the one that carried another`() {
        val first = sent("Loved “the old plan”")
        val second = sent("see you at seven")
        val thread = listOf(first, second, received("Loved “see you at seven”"))
            .withReactionsApplied()

        assertEquals(ReactionKind.LOVE.emoji, thread.last().reactions.single().emoji)
    }

    private fun received(body: String) =
        message(body, Telephony.Sms.MESSAGE_TYPE_INBOX, THEM)

    private fun sent(body: String, mimetype: String? = null) =
        message(body, Telephony.Sms.MESSAGE_TYPE_SENT, ME, mimetype)

    private fun message(
        body: String,
        type: Int,
        from: String,
        mimetype: String? = null,
    ): Message {
        nextId++
        return Message(
            id = nextId,
            body = body,
            type = type,
            status = Telephony.Sms.STATUS_NONE,
            participants = ArrayList(),
            date = nextId.toInt(),
            read = true,
            threadId = 1L,
            isMMS = mimetype != null,
            attachment = mimetype?.let {
                MessageAttachment(
                    id = nextId,
                    text = "",
                    attachments = arrayListOf(
                        Attachment(
                            id = nextId,
                            messageId = nextId,
                            uriString = "content://attachment/$nextId",
                            mimetype = it,
                            width = 0,
                            height = 0,
                            filename = "picture.jpg",
                        )
                    ),
                )
            },
            senderPhoneNumber = from,
            senderName = from,
            senderPhotoUri = "",
            subscriptionId = -1,
        )
    }

    private var nextId = 0L

    private companion object {
        const val ME = "+15550101"
        const val THEM = "+15550102"
    }
}
