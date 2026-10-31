package com.livingroom.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** One tile on the home screen. */
data class AppEntry(
    val key: String,
    val label: String,
    val launch: Intent,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    /** False for the Settings, switch-back and toggle tiles: they stay at the end of the grid. */
    val movable: Boolean = true,
)

// Dock favourites in this order when installed (ShaddaiTV first); everything else goes to All apps.
val DOCK =
    listOf("com.livingroom.tv", "com.livingroom.tv.debug") +
        listOf(
            "com.google.android.youtube.tv",
            "com.netflix.mediaclient",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android",
        )

// Utility apps kept off the grid until "Show hidden apps" is selected.
private val HIDDEN = setOf("com.softwinner.TvdFileManager", "com.android.traceur")

/** Launch action of the tile that shows or hides [HIDDEN] apps; handled by HomeActivity, not Android. */
const val ACTION_TOGGLE_HIDDEN = "com.livingroom.launcher.TOGGLE_HIDDEN"

/** Lists launchable TV apps plus Settings tiles. [order] is the user's saved arrangement. Slow (decodes banners): call off the main thread. */
fun loadApps(
    context: Context,
    showHidden: Boolean,
    order: List<String>,
): List<AppEntry> {
    val pm = context.packageManager
    val apps =
        pm
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && (showHidden || it.packageName !in HIDDEN) }
            .distinctBy { it.packageName }
            .map { info ->
                entry(
                    pm = pm,
                    info = info,
                    launch =
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                            .setComponent(ComponentName(info.packageName, info.name)),
                )
            }.sortedWith(
                compareBy<AppEntry>(
                    { order.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                    { DOCK.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                    { it.label.lowercase() },
                ),
            )
    val settings =
        Intent(Settings.ACTION_SETTINGS).let { intent ->
            pm.resolveActivity(intent, 0)?.activityInfo?.let { info ->
                entry(pm, info, intent, label = context.getString(R.string.settings)).copy(movable = false)
            }
        }
    // This firmware hard-wires Home to Google's launcher, so this launcher only becomes Home when that
    // one is disabled. The tile opens its app page, where "Enable" switches back without ADB.
    val switchBack =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", GOOGLE_TV_HOME, null)).let { intent ->
            pm.resolveActivity(intent, 0)?.activityInfo?.let { info ->
                // Text tile: borrowing the Settings app's artwork made it identical to the Settings tile
                entry(pm, info, intent, label = context.getString(R.string.switch_back)).copy(banner = null, icon = null, movable = false)
            }
        }
    val toggleHidden =
        AppEntry(
            key = ACTION_TOGGLE_HIDDEN,
            label = context.getString(if (showHidden) R.string.hide_apps else R.string.show_hidden_apps),
            launch = Intent(ACTION_TOGGLE_HIDDEN),
            banner = null,
            icon = null,
            movable = false,
        )
    return apps + listOfNotNull(settings, switchBack, toggleHidden)
}

private fun entry(
    pm: PackageManager,
    info: ActivityInfo,
    launch: Intent,
    label: String = info.loadLabel(pm).toString(),
): AppEntry {
    // Decoding ~30 banners is the slow part of a cold start; skip the icon when a banner exists
    val banner = (info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm))?.render(640, 360)
    val icon = if (banner == null) info.loadIcon(pm)?.render(192, 192) else null
    return AppEntry(
        key = info.packageName,
        label = label,
        launch = launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        banner = banner?.asImageBitmap(),
        icon = icon?.asImageBitmap(),
    )
}
private const val GOOGLE_TV_HOME = "com.google.android.tvlauncher"

private fun Drawable.render(
    fallbackWidth: Int,
    fallbackHeight: Int,
) = toBitmap(
    width = intrinsicWidth.takeIf { it > 0 } ?: fallbackWidth,
    height = intrinsicHeight.takeIf { it > 0 } ?: fallbackHeight,
)
