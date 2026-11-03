package org.fossify.messages.helpers

import org.fossify.messages.helpers.BlockRuleMatching.Problem
import org.fossify.messages.models.BlockCondition
import org.fossify.messages.models.BlockCondition.Type
import org.fossify.messages.models.BlockRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blocking is silent, so the cases that matter most are the ones that must get through: a name in
 * an ordinary message, a word inside a longer one, and anything a broken or runaway pattern meets.
 *
 * The spam texts are made up, in the shapes real ones take.
 */
class BlockRuleMatchingTest {

    private fun rule(vararg conditions: Pair<Type, String>) =
        BlockRule(conditions = conditions.map { BlockCondition(it.first, it.second) })

    private fun blocks(rule: BlockRule, body: String) = BlockRuleMatching.blocks(rule, body) { true }

    private val spam = "Hey Alex, it's Sam from Lucky Draw. You are a winner, claim your prize today!"
    private val pickup = "Your prize is waiting at the pickup\nlocation on MAIN STREET"

    @Test
    fun `every condition has to hold`() {
        val alexAndPrize = rule(Type.CONTAINS to "alex", Type.CONTAINS to "prize")
        assertTrue(blocks(alexAndPrize, spam))
        assertFalse(blocks(alexAndPrize, "Alex says hi, dinner at 7?"))
        assertFalse(blocks(alexAndPrize, "We won a prize at bingo"))
    }

    @Test
    fun `any value of a condition is enough, and every condition still has to hold`() {
        val nameAndPrize = BlockRule(
            conditions = listOf(
                BlockCondition(Type.WORD, listOf("alex", "bob")),
                BlockCondition(Type.CONTAINS, listOf("prize", "draw")),
            )
        )
        assertTrue(blocks(nameAndPrize, spam))
        assertTrue(blocks(nameAndPrize, "Bob, the draw is tonight"))
        assertFalse(blocks(nameAndPrize, "Bob, dinner at 7?"))
        assertFalse(blocks(nameAndPrize, "Winners announced at 9, claim your prize"))
    }

    @Test
    fun `doesn't contain with several values holds only when none of them appear`() {
        val exceptFamily = BlockRule(
            conditions = listOf(
                BlockCondition(Type.WORD, "alex"),
                BlockCondition(Type.NOT_CONTAINS, listOf("love", "mom")),
            )
        )
        assertTrue(blocks(exceptFamily, spam))
        assertFalse(blocks(exceptFamily, "Alex, Mom says hi"))
        assertFalse(blocks(exceptFamily, "Alex, love you"))
    }

    @Test
    fun `blank values are ignored, and a condition of only blanks is empty`() {
        val withBlanks = BlockCondition(Type.CONTAINS, listOf("", "prize", " "))
        assertTrue(blocks(BlockRule(conditions = listOf(withBlanks)), spam))
        assertEquals(Problem.EMPTY, BlockRuleMatching.problemWith(BlockCondition(Type.CONTAINS, listOf("", "  "))))
        val broken = BlockCondition(Type.REGEX, listOf("prize", "(unclosed"))
        assertEquals(Problem.INVALID_PATTERN, BlockRuleMatching.problemWith(broken))
        assertFalse(blocks(BlockRule(conditions = listOf(broken)), spam))
    }

    @Test
    fun `a phrase matches across any whitespace, in any case`() {
        assertTrue(blocks(rule(Type.CONTAINS to "pickup location"), pickup))
        assertTrue(blocks(rule(Type.CONTAINS to "  Pickup   LOCATION "), pickup))
        assertFalse(blocks(rule(Type.CONTAINS to "pickup location"), "the pickup point location"))
    }

    @Test
    fun `contains matches inside words, the word kind does not`() {
        assertTrue(blocks(rule(Type.CONTAINS to "alex"), "Alexandra is here"))
        assertFalse(blocks(rule(Type.WORD to "alex"), "Alexandra is here"))
        assertTrue(blocks(rule(Type.WORD to "alex"), "Hey Alex, it's me"))
        assertTrue(blocks(rule(Type.WORD to "maría"), "Hola MARÍA!"))
        assertTrue(blocks(rule(Type.WORD to "Lucky Draw"), spam))
    }

    @Test
    fun `text that looks like a pattern is matched literally outside the pattern kind`() {
        assertTrue(blocks(rule(Type.CONTAINS to "C++"), "I write C++ for a living"))
        assertFalse(blocks(rule(Type.CONTAINS to "a.b"), "axb"))
        assertTrue(blocks(rule(Type.WORD to "+1 800"), "Call +1 800 555 0100"))
    }

    @Test
    fun `a rule for unknown senders leaves contacts alone, and only looks them up when the text matches`() {
        val unknownOnly = rule(Type.WORD to "alex").copy(onlyUnknownSenders = true)
        assertTrue(BlockRuleMatching.blocks(unknownOnly, spam) { true })
        assertFalse(BlockRuleMatching.blocks(unknownOnly, spam) { false })

        var lookedUp = false
        assertFalse(BlockRuleMatching.blocks(unknownOnly, "See you at 7") { lookedUp = true; true })
        assertFalse("looked up a sender for a message that did not match", lookedUp)

        val anySender = rule(Type.WORD to "alex")
        assertTrue(BlockRuleMatching.blocks(anySender, spam) { error("should not look up the sender") })
        assertFalse(BlockRuleMatching.isSameRule(unknownOnly, anySender))
    }

    @Test
    fun `doesn't contain narrows a rule`() {
        val fromStrangers = rule(Type.WORD to "alex", Type.NOT_CONTAINS to "love")
        assertTrue(blocks(fromStrangers, spam))
        assertFalse(blocks(fromStrangers, "Alex, love you, see you soon"))
    }

    @Test
    fun `a rule of only doesn't contain is never used`() {
        val dangerous = rule(Type.NOT_CONTAINS to "hello")
        assertFalse(BlockRuleMatching.hasPositiveCondition(dangerous))
        assertFalse(BlockRuleMatching.isUsable(dangerous))
        assertFalse(blocks(dangerous, "anything at all"))
    }

    @Test
    fun `match case makes every kind of condition respect capitals`() {
        val shouting = rule(Type.WORD to "CLAIM").copy(matchCase = true)
        assertTrue(blocks(shouting, "Please CLAIM today"))
        assertFalse(blocks(shouting, "Please claim today"))
        assertFalse(blocks(rule(Type.REGEX to "Alex").copy(matchCase = true), "alex"))
        assertTrue(blocks(rule(Type.REGEX to "(?i)Alex").copy(matchCase = true), "alex"))
        assertFalse(BlockRuleMatching.isSameRule(shouting, rule(Type.WORD to "CLAIM")))
        assertFalse(BlockRuleMatching.isSameRule(shouting, rule(Type.WORD to "claim").copy(matchCase = true)))
    }

    @Test
    fun `patterns ignore case unless told otherwise`() {
        assertTrue(blocks(rule(Type.REGEX to "lucky-?draw"), "See LUCKYDRAW.com"))
        assertFalse(blocks(rule(Type.REGEX to "(?-i)PRIZE"), "prize now"))
        assertTrue(blocks(rule(Type.REGEX to "\\bpickup\\s+location\\b"), pickup))
    }

    @Test
    fun `broken and match-everything patterns are refused`() {
        fun problem(type: Type, text: String) = BlockRuleMatching.problemWith(BlockCondition(type, text))
        assertEquals(Problem.INVALID_PATTERN, problem(Type.REGEX, "(unclosed"))
        assertEquals(Problem.MATCHES_EVERYTHING, problem(Type.REGEX, ".*"))
        assertEquals(Problem.MATCHES_EVERYTHING, problem(Type.REGEX, "prize|"))
        assertEquals(Problem.EMPTY, problem(Type.CONTAINS, "   "))
        assertFalse(blocks(rule(Type.REGEX to "(unclosed"), "(unclosed"))
        assertFalse(BlockRuleMatching.isUsable(BlockRule(conditions = emptyList())))
    }

    @Test
    fun `a runaway pattern gives up and lets the message through`() {
        // Java shortcuts the textbook (a+)+b, but not this: every way of splitting 45 a's twelve times.
        val catastrophic = BlockCondition(Type.REGEX, "(.*a){12}!")
        val body = "a".repeat(45)
        val started = System.nanoTime()
        assertNull(BlockRuleMatching.isSatisfied(catastrophic, body))
        assertTrue("took too long", System.nanoTime() - started < 2_000_000_000L)
        assertFalse(blocks(BlockRule(conditions = listOf(catastrophic)), body))
    }

    @Test
    fun `the same conditions in another order are the same rule`() {
        val first = rule(Type.CONTAINS to "alex", Type.WORD to "prize")
        val second = rule(Type.WORD to "PRIZE ", Type.CONTAINS to "Alex")
        assertTrue(BlockRuleMatching.isSameRule(first, second))
        val either = BlockRule(conditions = listOf(BlockCondition(Type.WORD, listOf("alex", "bob"))))
        val reordered = BlockRule(conditions = listOf(BlockCondition(Type.WORD, listOf("Bob", "", "alex"))))
        assertTrue(BlockRuleMatching.isSameRule(either, reordered))
        assertFalse(BlockRuleMatching.isSameRule(first, rule(Type.CONTAINS to "alex", Type.CONTAINS to "prize")))
    }

    @Test
    fun `fossify keyword exports and this app's rule files both import`() {
        val legacy = BlockedKeywordsImporter.parseBlockRules("alex,pickup location,")
        assertEquals(listOf("alex", "pickup location"), legacy.map { it.conditions.single().values.single() })
        assertTrue(legacy.all { it.conditions.single().type == Type.CONTAINS })

        val exported = """
            {"version":1,"rules":[{"id":"x","conditions":[
              {"type":"word","values":["alex","bob"]},{"type":"regex","values":["priz(e|es)"]}]}]}
        """
        val rules = BlockedKeywordsImporter.parseBlockRules(exported)
        assertEquals(listOf(Type.WORD, Type.REGEX), rules.single().conditions.map { it.type })
        assertEquals(listOf("alex", "bob"), rules.single().conditions.first().values)
    }
}
