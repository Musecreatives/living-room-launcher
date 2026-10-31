package com.livingroom.launcher

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A notification as the bell panel shows it. */
data class Note(
    val key: String,
    val app: String,
    val title: String,
    val text: String?,
    val postTime: Long,
)

/** Live notification list, filled by [NotesService] once the user grants notification access. */
object NotesStore {
    private val notes = MutableStateFlow<List<Note>>(emptyList())
    val flow: StateFlow<List<Note>> = notes

    fun set(list: List<Note>) {
        notes.value = list
    }
}

/** The active media session as the Now Playing pill shows it; [toggle] plays or pauses it. */
data class NowPlaying(
    val title: String,
    val artist: String?,
    val playing: Boolean,
    val toggle: () -> Unit,
)

object NowPlayingStore {
    private val current = MutableStateFlow<NowPlaying?>(null)
    val flow: StateFlow<NowPlaying?> = current

    fun set(value: NowPlaying?) {
        current.value = value
    }
}

class NotesService : NotificationListenerService() {
    private val me by lazy { ComponentName(this, NotesService::class.java) }
    private var controllers = emptyList<MediaController>()
    private val onSessions = MediaSessionManager.OnActiveSessionsChangedListener { watch(it.orEmpty()) }
    private val onMedia =
        object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = refreshMedia()

            override fun onMetadataChanged(metadata: MediaMetadata?) = refreshMedia()
        }

    override fun onListenerConnected() {
        refresh()
        val sessions = getSystemService(MediaSessionManager::class.java)
        sessions.addOnActiveSessionsChangedListener(onSessions, me)
        watch(sessions.getActiveSessions(me))
    }

    override fun onListenerDisconnected() {
        getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(onSessions)
        watch(emptyList())
    }

    private fun watch(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(onMedia) }
        controllers = list
        list.forEach { it.registerCallback(onMedia) }
        refreshMedia()
    }

    // A playing session wins; otherwise the first paused one, so a pause does not make the pill vanish
    private fun refreshMedia() {
        val c =
            controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
        val title = c?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (c == null || title == null) return NowPlayingStore.set(null)
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        NowPlayingStore.set(
            NowPlaying(
                title = title,
                artist = c.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                playing = playing,
                toggle = { if (playing) c.transportControls.pause() else c.transportControls.play() },
            ),
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val pm = packageManager
        val list =
            (activeNotifications ?: emptyArray())
                .filter { !it.isOngoing && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .mapNotNull { sbn ->
                    val extras = sbn.notification.extras
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return@mapNotNull null
                    val app =
                        try {
                            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                        } catch (ex: Exception) {
                            sbn.packageName
                        }
                    Note(sbn.key, app, title, extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(), sbn.postTime)
                }.sortedByDescending { it.postTime }
        NotesStore.set(list)
    }
}
