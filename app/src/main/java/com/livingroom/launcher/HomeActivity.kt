package com.livingroom.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : ComponentActivity() {
    private var apps by mutableStateOf<List<AppEntry>?>(null)
    private var shelf by mutableStateOf<List<ShelfItem>>(emptyList())
    private var weather by mutableStateOf<List<CityWeather>>(emptyList())
    private var showHidden = false
    private val prefs by lazy { getSharedPreferences("home", MODE_PRIVATE) }

    private fun savedOrder() = prefs.getString("order", null)?.split('\n').orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lay out on a fixed 1280-unit-wide canvas so the design scales the same on any TV density
        val canvas = Density(resources.displayMetrics.widthPixels / 1280f, fontScale = 1f)
        setContent {
            val notes by NotesStore.flow.collectAsState()
            val nowPlaying by NowPlayingStore.flow.collectAsState()
            CompositionLocalProvider(LocalDensity provides canvas) {
                HomeScreen(apps, shelf, weather, notes, nowPlaying, ::open, ::launch, ::reorder)
            }
        }
        // Weather every 30 minutes while Home is on screen
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    withContext(Dispatchers.IO) { loadWeather() }.takeIf { it.isNotEmpty() }?.let { weather = it }
                    delay(30 * 60_000L)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ponytail: reload on every return to Home (covers installs, removals and new progress); add a package receiver if this gets slow
        reload()
        lifecycleScope.launch { shelf = withContext(Dispatchers.IO) { loadShelf(this@HomeActivity) } }
    }

    private fun reload() {
        lifecycleScope.launch { apps = withContext(Dispatchers.Default) { loadApps(this@HomeActivity, showHidden, savedOrder()) } }
    }

    /** Saves the arrangement (keys not on screen, e.g. hidden apps, keep their old place) and applies it without reloading banners. */
    private fun reorder(keys: List<String>) {
        prefs.edit().putString("order", (keys + savedOrder().filter { it !in keys }).joinToString("\n")).apply()
        apps = apps?.let { cur -> keys.mapNotNull { k -> cur.find { it.key == k } } + cur.filter { it.key !in keys } }
    }

    private fun open(app: AppEntry) {
        if (app.launch.action == ACTION_TOGGLE_HIDDEN) {
            showHidden = !showHidden
            reload()
            return
        }
        launch(app.launch, app.label)
    }

    private fun launch(
        intent: Intent,
        label: String = getString(R.string.app_name),
    ) {
        try {
            startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.cannot_open, label), Toast.LENGTH_SHORT).show()
        }
    }
}
