package com.livingroom.launcher

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Date

private val TileShape = RoundedCornerShape(12.dp)
private val Gutter = 48.dp

@Composable
fun HomeScreen(
    apps: List<AppEntry>?,
    onOpen: (AppEntry) -> Unit,
) {
    var focused by remember { mutableStateOf<AppEntry?>(null) }
    val firstTile = remember { FocusRequester() }
    BackHandler {} // Home has nowhere to go back to

    Column(Modifier.fillMaxSize().background(Ink0)) {
        TopShelf(focused ?: apps?.firstOrNull(), Modifier.fillMaxWidth().height(320.dp))
        if (apps != null) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 20.dp, bottom = Gutter),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(apps, key = { _, app -> app.key + app.label }) { index, app ->
                    AppTile(
                        app = app,
                        onFocused = { focused = app },
                        onClick = { onOpen(app) },
                        modifier = if (index == 0) Modifier.focusRequester(firstTile) else Modifier,
                    )
                }
            }
            LaunchedEffect(Unit) {
                withFrameNanos { } // let the first grid row lay out before focusing it
                runCatching { firstTile.requestFocus() }
            }
        }
    }
}

@Composable
private fun TopShelf(
    app: AppEntry?,
    modifier: Modifier,
) {
    val reduce = reduceMotion()
    val tint by animateColorAsState(app?.tint ?: Ink2, tween(if (reduce) 0 else 500), label = "shelfTint")
    Box(modifier.background(Brush.verticalGradient(listOf(tint.copy(alpha = .9f), tint.copy(alpha = .35f), Ink0)))) {
        Clock(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = Gutter))
        if (app != null) {
            Crossfade(
                targetState = app,
                animationSpec = tween(if (reduce) 0 else 300),
                label = "shelf",
                modifier = Modifier.align(Alignment.BottomStart).padding(start = Gutter, bottom = 20.dp),
            ) { shown ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Artwork(shown, Modifier.height(190.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)))
                    Spacer(Modifier.width(36.dp))
                    Column(Modifier.padding(bottom = 8.dp)) {
                        Text(
                            text = shown.label,
                            fontFamily = Sora,
                            fontWeight = FontWeight.Bold,
                            fontSize = 44.sp,
                            color = Fg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.open_hint),
                            fontFamily = Figtree,
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp,
                            color = Fg2,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    app: AppEntry,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(TileShape),
            scale = CardDefaults.scale(focusedScale = 1.1f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Fg), shape = TileShape)),
            glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = Fg.copy(alpha = .3f), elevation = 16.dp)),
            colors = CardDefaults.colors(containerColor = Ink2),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        if (it.isFocused) onFocused()
                    },
        ) {
            Artwork(app, Modifier.fillMaxSize())
        }
        Text(
            text = app.label,
            fontFamily = Figtree,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = Fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp).alpha(if (isFocused) 1f else 0f),
        )
    }
}

/** App banner, or its icon centred on ink when the app has no banner. */
@Composable
private fun Artwork(
    app: AppEntry,
    modifier: Modifier,
) {
    Box(modifier.background(Ink2), contentAlignment = Alignment.Center) {
        when {
            app.banner != null -> {
                Image(app.banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }

            app.icon != null -> {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(64.dp))
            }
        }
    }
}

@Composable
private fun Clock(modifier: Modifier) {
    val context = LocalContext.current
    val time by produceState(DateFormat.getTimeFormat(context).format(Date())) {
        while (true) {
            value = DateFormat.getTimeFormat(context).format(Date())
            delay(10_000)
        }
    }
    Text(time, fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = Fg2, modifier = modifier)
}
