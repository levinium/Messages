package org.fossify.messages.helpers

import kotlinx.serialization.json.Json
import org.fossify.commons.helpers.ExportResult
import org.fossify.messages.models.BlockRule
import org.fossify.messages.models.BlockRulesExport
import java.io.OutputStream

object BlockedKeywordsExporter {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun exportBlockRules(
        rules: List<BlockRule>,
        outputStream: OutputStream?,
        callback: (result: ExportResult) -> Unit,
    ) {
        if (outputStream == null) {
            callback.invoke(ExportResult.EXPORT_FAIL)
            return
        }

        try {
            outputStream.bufferedWriter().use { out ->
                out.write(json.encodeToString(BlockRulesExport(rules = rules)))
            }
            callback.invoke(ExportResult.EXPORT_OK)
        } catch (e: Exception) {
            callback.invoke(ExportResult.EXPORT_FAIL)
        }
    }
}
