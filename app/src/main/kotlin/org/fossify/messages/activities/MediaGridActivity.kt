package org.fossify.messages.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.GridLayoutManager
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.MediaGridAdapter
import org.fossify.messages.databinding.ActivityMediaGridBinding
import org.fossify.messages.extensions.copyToUri
import org.fossify.messages.extensions.launchViewIntent
import org.fossify.messages.extensions.openMediaViewer
import org.fossify.messages.extensions.shareMediaIntent
import org.fossify.messages.helpers.MEDIA_ITEMS
import org.fossify.messages.helpers.PICK_SAVE_DIR_INTENT
import org.fossify.messages.helpers.PICK_SAVE_FILE_INTENT
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.MediaItem
import java.io.IOException

/**
 * Everything a conversation ever sent or received as pictures and video, in one grid.
 *
 * Tapping opens the viewer on that item; long pressing starts a selection, because sharing or
 * saving media usually means several of them at once.
 */
class MediaGridActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityMediaGridBinding::inflate)

    private var items = emptyList<MediaItem>()
    private var pendingSave = emptyList<MediaItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.mediaGridList))

        items = intent.getParcelableArrayListExtra<MediaItem>(MEDIA_ITEMS).orEmpty()
        intent.getStringExtra(THREAD_TITLE)?.let { binding.mediaGridToolbar.title = it }
        if (items.isEmpty()) {
            toast(org.fossify.commons.R.string.no_items_found)
            finish()
            return
        }

        setupList()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(
            topAppBar = binding.mediaGridAppbar,
            navigationIcon = NavigationIcon.Arrow,
            topBarColor = getProperBackgroundColor()
        )
        updateTextColors(binding.mediaGridCoordinator)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        val destination = resultData?.data
        val isSaveResult = requestCode == PICK_SAVE_FILE_INTENT ||
                requestCode == PICK_SAVE_DIR_INTENT
        if (!isSaveResult || resultCode != RESULT_OK || destination == null) {
            return
        }

        contentResolver.takePersistableUriPermission(
            destination, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val toSave = pendingSave
        pendingSave = emptyList()
        ensureBackgroundThread {
            try {
                saveTo(destination, toSave)
                toast(org.fossify.commons.R.string.file_saved)
            } catch (e: IOException) {
                showErrorToast(e)
            } catch (e: SecurityException) {
                showErrorToast(e)
            }
        }
    }

    private fun setupList() {
        val targetCellWidth = TARGET_CELL_DP * resources.displayMetrics.density
        val spanCount = (resources.displayMetrics.widthPixels / targetCellWidth).toInt()
            .coerceIn(MIN_COLUMNS, MAX_COLUMNS)

        binding.mediaGridList.apply {
            layoutManager = GridLayoutManager(this@MediaGridActivity, spanCount)
            adapter = MediaGridAdapter(
                activity = this@MediaGridActivity,
                recyclerView = this,
                onAction = ::handleAction,
                itemClick = { item ->
                    openMediaViewer(items, items.indexOf(item as MediaItem))
                },
            ).apply {
                submitList(items)
            }
        }
    }

    private fun handleAction(id: Int, selected: List<MediaItem>) {
        when (id) {
            R.id.cab_share -> shareMedia(selected)
            R.id.cab_save_as -> saveMedia(selected)
            R.id.cab_copy_to_clipboard -> copyMedia(selected)
            R.id.cab_open_with -> selected.first().let {
                launchViewIntent(it.uri, it.mimetype, it.filename)
            }
        }
    }

    private fun shareMedia(selected: List<MediaItem>) {
        if (selected.size == 1) {
            shareMediaIntent(selected.first().uri, selected.first().mimetype)
            return
        }

        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = commonMimetype(selected)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(selected.map { it.uri }))
            addFlags(FLAG_GRANT_READ_URI_PERMISSION)
            val title = getString(org.fossify.commons.R.string.share_via)
            startActivity(Intent.createChooser(this, title))
        }
    }

    /** One picture picks its own name and place; several go into a folder the user chooses. */
    private fun saveMedia(selected: List<MediaItem>) {
        pendingSave = selected
        if (selected.size == 1) {
            val item = selected.first()
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = item.mimetype
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, item.displayName())
                startActivityForResult(this, PICK_SAVE_FILE_INTENT)
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                startActivityForResult(this, PICK_SAVE_DIR_INTENT)
            }
        }
    }

    private fun saveTo(destination: Uri, toSave: List<MediaItem>) {
        if (!DocumentsContract.isTreeUri(destination)) {
            copyToUri(src = toSave.first().uri, dst = destination)
            return
        }

        val outputDir = DocumentFile.fromTreeUri(this, destination) ?: return
        toSave.forEach { item ->
            val file = outputDir.createFile(item.mimetype, item.displayName()) ?: return@forEach
            copyToUri(src = item.uri, dst = file.uri)
        }
    }

    private fun copyMedia(selected: List<MediaItem>) {
        val label = getString(R.string.attachment)
        val clip = ClipData.newUri(contentResolver, label, selected.first().uri)
        selected.drop(1).forEach { clip.addItem(ClipData.Item(it.uri)) }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        toast(org.fossify.commons.R.string.value_copied_to_clipboard)
    }

    private fun commonMimetype(selected: List<MediaItem>): String {
        val types = selected.map { it.mimetype.substringBefore('/') }.distinct()
        return if (types.size == 1) "${types.first()}/*" else "*/*"
    }

    private fun MediaItem.displayName() = filename.ifBlank { uriString.getFilenameFromPath() }

    private companion object {
        const val TARGET_CELL_DP = 110
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 6
    }
}
