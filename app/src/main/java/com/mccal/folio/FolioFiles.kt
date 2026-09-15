package com.mccal.folio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.time.LocalDate

/**
 * Files Folio saves for you (layout backups, themes) go in one place: Download/Folio. Android lets an app add its own
 * files to Downloads without any permission, and they show up in the Files app like anything else you download.
 */
internal object FolioFiles {
    const val FOLDER = "Folio"
    val displayPath = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"

    /** A dated file name, like folio-layout-2026-09-15.json. Android adds (1), (2)… if the name is taken. */
    fun datedName(prefix: String, extension: String = "json") = "$prefix-${LocalDate.now()}.$extension"

    /**
     * A safe file name from what the user typed (letters, numbers, spaces, - and _ kept; ".json" added), or a dated
     * default when it's blank.
     */
    fun fileName(typed: String?, prefix: String, extension: String = "json"): String {
        val clean = typed.orEmpty().removeSuffix(".$extension").replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(60)
        return if (clean.isEmpty()) datedName(prefix, extension) else "$clean.$extension"
    }

    /** Writes [bytes] to Download/Folio and returns the new file, or null if it couldn't be saved. Call off the main thread. */
    fun save(context: Context, name: String, mimeType: String, bytes: ByteArray): Uri? = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "$displayPath/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri, "w")!!.use { it.write(bytes) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }.getOrNull()
}
