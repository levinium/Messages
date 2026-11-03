package org.fossify.messages.helpers

import android.app.Activity
import kotlinx.serialization.json.Json
import org.fossify.commons.extensions.showErrorToast
import org.fossify.messages.extensions.config
import org.fossify.messages.models.BlockCondition
import org.fossify.messages.models.BlockRule
import org.fossify.messages.models.BlockRulesExport

import java.io.File

class BlockedKeywordsImporter(
    private val activity: Activity,
) {
    enum class ImportResult {
        IMPORT_FAIL, IMPORT_OK
    }

    fun importBlockedKeywords(path: String): ImportResult {
        return try {
            val rules = parseBlockRules(File(path).readText()).filter { BlockRuleMatching.isUsable(it) }
            if (rules.isNotEmpty()) {
                // Rules already here are skipped, and each new one gets a fresh id so that a
                // file imported twice, or on another phone, cannot overwrite a different rule.
                val merged = rules.fold(activity.config.blockRules) { kept, rule ->
                    if (kept.any { BlockRuleMatching.isSameRule(it, rule) }) {
                        kept
                    } else {
                        kept + BlockRule(conditions = rule.conditions)
                    }
                }
                activity.config.blockRules = merged
                ImportResult.IMPORT_OK
            } else {
                ImportResult.IMPORT_FAIL
            }
        } catch (e: Exception) {
            activity.showErrorToast(e)
            ImportResult.IMPORT_FAIL
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Reads a rules file exported by this app, or the comma-separated keyword list that
         * Fossify Messages exports, which comes in as one "contains" rule per keyword.
         */
        fun parseBlockRules(content: String): List<BlockRule> {
            val trimmed = content.trim()
            if (trimmed.startsWith("{")) {
                return json.decodeFromString<BlockRulesExport>(trimmed).rules
            }

            return trimmed.split(BLOCKED_KEYWORDS_EXPORT_DELIMITER)
                .filter { it.isNotBlank() }
                .map { BlockRule(conditions = listOf(BlockCondition(BlockCondition.Type.CONTAINS, it))) }
        }
    }
}
