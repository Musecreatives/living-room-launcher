package com.livingroom.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** One Continue Watching entry from ShaddaiTV's home shelf provider. */
data class ShelfItem(
    val itemId: String,
    val showId: String,
    val title: String,
    val episodeLine: String?,
    val episodeTitle: String?,
    val overview: String?,
    val minutesLeft: Int?,
    val progress: Int?,
    val positionMs: Long,
    val backdropUrl: String?,
    val stillUrl: String?,
    val pkg: String,
) {
    /** Resume playback in ShaddaiTV at the saved position. */
    fun resumeIntent(): Intent = shaddai(pkg, "com.github.damontecres.wholphin.PLAYBACK").putExtra("itemId", itemId).putExtra("position", positionMs)

    /** Open the show (or movie) page in ShaddaiTV. */
    fun detailsIntent(): Intent = shaddai(pkg, Intent.ACTION_VIEW).putExtra("itemId", showId)
}

// Release build first, then the debug build used during development.
val SHADDAI_TV_PACKAGES = listOf("com.livingroom.tv", "com.livingroom.tv.debug")

private fun shaddai(
    pkg: String,
    action: String,
) = Intent(action)
    .setComponent(ComponentName(pkg, "com.github.damontecres.wholphin.MainActivity"))
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Reads Continue Watching from the installed ShaddaiTV build. Blocking network call: run off the main thread. */
fun loadShelf(context: Context): List<ShelfItem> {
    for (pkg in SHADDAI_TV_PACKAGES) {
        val uri = Uri.parse("content://$pkg.shelf/continue")
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                fun str(col: String) = c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getString)
                fun int(col: String) = c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getInt)
                val items = mutableListOf<ShelfItem>()
                while (c.moveToNext()) {
                    items +=
                        ShelfItem(
                            itemId = str("item_id") ?: continue,
                            showId = str("show_id") ?: continue,
                            title = str("title") ?: continue,
                            episodeLine = str("episode_line"),
                            episodeTitle = str("episode_title"),
                            overview = str("overview"),
                            minutesLeft = int("minutes_left"),
                            progress = int("progress"),
                            positionMs = c.getColumnIndex("position_ms").takeIf { it >= 0 }?.let(c::getLong) ?: 0L,
                            backdropUrl = str("backdrop_url"),
                            stillUrl = str("still_url"),
                            pkg = pkg,
                        )
                }
                return items
            }
        } catch (ex: Exception) {
            // Not installed, not signed in, or not signed with the same key: try the next build
            Log.w("Shelf", "No shelf from $pkg: ${ex.message}")
        }
    }
    return emptyList()
}
