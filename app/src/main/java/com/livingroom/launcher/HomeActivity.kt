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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HomeScreen(apps, ::open) }
    }

    override fun onResume() {
        super.onResume()
        // ponytail: reload on every return to Home (covers installs/removals); add a package receiver if this gets slow
        lifecycleScope.launch { apps = withContext(Dispatchers.Default) { loadApps(this@HomeActivity) } }
    }

    private fun open(app: AppEntry) {
        try {
            startActivity(app.launch)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.cannot_open, app.label), Toast.LENGTH_SHORT).show()
        }
    }
}
