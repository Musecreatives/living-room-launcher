package com.livingroom.launcher

import android.app.Notification
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

class NotesService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()

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
