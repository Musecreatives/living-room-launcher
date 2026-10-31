package com.livingroom.launcher

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : ComponentActivity() {
    private var apps by mutableStateOf<List<AppEntry>?>(null)
    private var showHidden = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HomeScreen(apps, ::open) }
    }

    override fun onResume() {
        super.onResume()
        // ponytail: reload on every return to Home (covers installs/removals); add a package receiver if this gets slow
        reload()
    }

    private fun reload() {
        lifecycleScope.launch { apps = withContext(Dispatchers.Default) { loadApps(this@HomeActivity, showHidden) } }
    }

    private fun open(app: AppEntry) {
        if (app.launch.action == ACTION_TOGGLE_HIDDEN) {
            showHidden = !showHidden
            reload()
            return
        }
        try {
            startActivity(app.launch)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.cannot_open, app.label), Toast.LENGTH_SHORT).show()
        }
    }
}
