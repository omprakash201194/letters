package com.ogautam.letters.data.avatars

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.util.UUID

/**
 * Character avatars, stored as files under `filesDir/avatars`.
 *
 * The web app inlined a base64 data URL of the full image onto the character *and* onto
 * every message that character sent, which meant a scene carried the same image a dozen
 * times over. Here a message holds a path, and the bitmap is loaded once and cached.
 *
 * Images are downscaled on import: an avatar is drawn at 44dp at the largest, so keeping a
 * 12-megapixel camera photo would be storing four thousand pixels to show forty.
 */
class AvatarStore(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, "avatars").apply { mkdirs() }

    private val cache = object : LruCache<String, Bitmap>(CACHE_ENTRIES) {}

    /** Copies the picked image in, returning the stored file's absolute path. */
    fun import(uri: Uri): String? {
        val decoded = decodeScaled(uri) ?: return null
        val file = File(directory, "${UUID.randomUUID()}.png")
        return runCatching {
            file.outputStream().use { decoded.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file.absolutePath
        }.getOrNull()
    }

    fun load(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        cache.get(path)?.let { return it }
        val bitmap = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return null
        cache.put(path, bitmap)
        return bitmap
    }

    /** Removes an avatar file no character refers to any more. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        cache.remove(path)
        runCatching { File(path).delete() }
    }

    private fun decodeScaled(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = generateSequence(1) { it * 2 }
                .first { largest / it <= MAX_EDGE_PX }
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.getOrNull()

    companion object {
        /** Comfortably above the 44dp the largest avatar is drawn at, even on a 4x screen. */
        private const val MAX_EDGE_PX = 256
        private const val CACHE_ENTRIES = 16
    }
}
