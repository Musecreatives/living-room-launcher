package com.livingroom.launcher

import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

private val Gutter = 80.dp
private val TileShape = RoundedCornerShape(14.dp)
private val Pill = RoundedCornerShape(50)
private val GlassFill = Color.White.copy(alpha = .08f)
private val GlassEdge = Color.White.copy(alpha = .16f)

// Dock favourites in this order when installed (ShaddaiTV first); everything else goes to All apps.
private val DOCK =
    SHADDAI_TV_PACKAGES +
        listOf(
            "com.google.android.youtube.tv",
            "com.netflix.mediaclient",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android",
        )
private const val DOCK_SIZE = 6

private enum class Panel { None, Weather, Notes }

@Composable
fun HomeScreen(
    apps: List<AppEntry>?,
    shelf: List<ShelfItem>,
    weather: List<CityWeather>,
    notes: List<Note>,
    onOpen: (AppEntry) -> Unit,
    onIntent: (Intent) -> Unit,
) {
    var focusedApp by remember { mutableStateOf<AppEntry?>(null) }
    var selected by remember(shelf) { mutableStateOf(shelf.firstOrNull()) }
    var panel by remember { mutableStateOf(Panel.None) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val firstDock = remember { FocusRequester() }
    val reduce = reduceMotion()
    BackHandler { panel = Panel.None }

    val all = apps.orEmpty()
    val dock = remember(all) { all.filter { it.key in DOCK }.sortedBy { DOCK.indexOf(it.key) }.take(DOCK_SIZE) }
    val rest = remember(all, dock) { all - dock.toSet() }
    val appMode = focusedApp?.let { it.key !in SHADDAI_TV_PACKAGES } ?: false
    val item = selected ?: shelf.firstOrNull()
    val backdrop = rememberImage(item?.backdropUrl)
    val wallSource = if (appMode) focusedApp?.banner else backdrop
    val wall = remember(wallSource) { wallSource?.let { blurred(it.asAndroidBitmap()).asImageBitmap() } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ink0)) {
        val screen = maxHeight
        Crossfade(wall, animationSpec = tween(if (reduce) 0 else 600), label = "wall") { w ->
            if (w != null) Image(w, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(.55f))
        }
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(screen)) {
                if (!appMode && backdrop != null) {
                    Image(
                        backdrop,
                        null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(.7f).fillMaxHeight(.72f),
                    )
                }
                // Scrims keep text readable over any artwork
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0f to Ink0.copy(alpha = .94f), .42f to Ink0.copy(alpha = .6f), .72f to Color.Transparent),
                    ),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.4f to Color.Transparent, .72f to Ink0.copy(alpha = .8f), 1f to Ink0)))

                if (appMode) {
                    AppShelf(focusedApp!!, Modifier.padding(start = Gutter, top = 100.dp))
                } else {
                    ShaddaiShelf(
                        item = item,
                        onResume = { item?.let { onIntent(it.resumeIntent()) } },
                        onEpisodes = { item?.let { onIntent(it.detailsIntent()) } },
                        onFocus = { focusedApp = null },
                        modifier = Modifier.padding(start = Gutter, top = 40.dp),
                    )
                    if (shelf.isNotEmpty()) {
                        ContinueRow(
                            items = shelf,
                            onFocus = {
                                focusedApp = null
                                selected = it
                            },
                            onOpen = { onIntent(it.resumeIntent()) },
                            modifier = Modifier.padding(start = Gutter, top = screen * .5f),
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = Gutter, end = Gutter, bottom = 18.dp)
                            .fillMaxWidth()
                            .background(GlassFill, RoundedCornerShape(28.dp))
                            .border(1.dp, GlassEdge, RoundedCornerShape(28.dp))
                            .padding(top = 18.dp, bottom = 8.dp, start = 20.dp, end = 20.dp),
                ) {
                    dock.forEachIndexed { i, app ->
                        Tile(
                            app = app,
                            width = 160.dp,
                            showLabel = true,
                            onFocus = {
                                focusedApp = app
                                scope.launch { scroll.animateScrollTo(0) }
                            },
                            onClick = { onOpen(app) },
                            modifier = if (i == 0) Modifier.focusRequester(firstDock) else Modifier,
                        )
                    }
                }
            }
            AllApps(rest, onFocus = { focusedApp = it }, onOpen = onOpen)
        }

        val widgetsAlpha by animateFloatAsState(if (scroll.value > 120) 0f else 1f, tween(if (reduce) 0 else 300), label = "widgets")
        Widgets(
            weather = weather,
            notes = notes,
            onToggle = { panel = if (panel == it) Panel.None else it },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = Gutter).alpha(widgetsAlpha),
        )
        when (panel) {
            Panel.Weather -> WeatherPanel(weather, Modifier.align(Alignment.TopEnd).padding(top = 88.dp, end = Gutter))
            Panel.Notes -> NotesPanel(notes, Modifier.align(Alignment.TopEnd).padding(top = 88.dp, end = Gutter))
            Panel.None -> {}
        }
    }

    LaunchedEffect(dock.isNotEmpty()) {
        if (dock.isNotEmpty()) {
            withFrameNanos { } // let the dock lay out before focusing it
            runCatching { firstDock.requestFocus() }
        }
    }
}

@Composable
private fun ShaddaiShelf(
    item: ShelfItem?,
    onResume: () -> Unit,
    onEpisodes: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.widthIn(max = 640.dp)) {
        Image(painterResource(R.drawable.shaddaitv_lockup), "ShaddaiTV", modifier = Modifier.height(30.dp))
        if (item == null) {
            Text("Nothing to resume", fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, color = Fg, modifier = Modifier.padding(top = 22.dp))
            Text(
                "Start something in ShaddaiTV and it appears here.",
                fontFamily = Manrope,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                color = Fg2,
                modifier = Modifier.padding(top = 10.dp),
            )
            return@Column
        }
        Text(
            item.title,
            fontFamily = Manrope,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 52.sp,
            lineHeight = 54.sp,
            color = Fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 20.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 12.dp)) {
            item.episodeLine?.let { Text(it, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Fg2) }
            item.episodeTitle?.let { Text(it, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Fg2, maxLines = 1) }
            item.minutesLeft?.let { Text("$it min left", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = BlueLight) }
        }
        item.overview?.let {
            Text(
                it,
                fontFamily = Manrope,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                color = Fg2,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp).widthIn(max = 520.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 18.dp)) {
            PillButton(stringResource(R.string.resume), solid = true, onClick = onResume, onFocus = onFocus, icon = R.drawable.ic_play)
            PillButton(stringResource(R.string.episodes), solid = false, onClick = onEpisodes, onFocus = onFocus)
        }
    }
}

@Composable
private fun AppShelf(
    app: AppEntry,
    modifier: Modifier,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = modifier) {
        Artwork(app, Modifier.width(384.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)))
        Text(app.label, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 50.sp, color = Fg, maxLines = 1, modifier = Modifier.padding(bottom = 10.dp))
    }
}

@Composable
private fun ContinueRow(
    items: List<ShelfItem>,
    onFocus: (ShelfItem) -> Unit,
    onOpen: (ShelfItem) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(stringResource(R.string.continue_watching), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Fg)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 10.dp)) {
            items.take(5).forEach { item ->
                val still = rememberImage(item.stillUrl)
                Card(
                    onClick = { onOpen(item) },
                    shape = CardDefaults.shape(TileShape),
                    scale = CardDefaults.scale(focusedScale = 1.1f),
                    border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Fg), shape = TileShape)),
                    glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = .6f), elevation = 18.dp)),
                    colors = CardDefaults.colors(containerColor = Ink2),
                    modifier = Modifier.width(203.dp).aspectRatio(16f / 9f).onFocusChanged { if (it.isFocused) onFocus(item) },
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (still != null) Image(still, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        item.progress?.let { p ->
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 10.dp, end = 10.dp, bottom = 9.dp)
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(Color.White.copy(alpha = .3f), Pill),
                            ) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0, 100) / 100f).background(Blue, Pill))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllApps(
    apps: List<AppEntry>,
    onFocus: (AppEntry) -> Unit,
    onOpen: (AppEntry) -> Unit,
) {
    Column(Modifier.padding(start = Gutter, end = Gutter, top = 24.dp, bottom = 48.dp)) {
        Text(stringResource(R.string.all_apps), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = Fg)
        apps.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 16.dp)) {
                row.forEach { app -> Tile(app, width = 168.dp, showLabel = false, onFocus = { onFocus(app) }, onClick = { onOpen(app) }) }
            }
        }
    }
}

@Composable
private fun Tile(
    app: AppEntry,
    width: Dp,
    showLabel: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(TileShape),
            scale = CardDefaults.scale(focusedScale = 1.1f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Fg), shape = TileShape)),
            glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = .6f), elevation = 18.dp)),
            colors = CardDefaults.colors(containerColor = if (app.banner == null && app.icon == null) GlassFill else Ink2),
            modifier =
                Modifier.width(width).aspectRatio(16f / 9f).onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                },
        ) {
            Artwork(app, Modifier.fillMaxSize())
        }
        if (showLabel) {
            Text(
                app.label,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp).width(width).alpha(if (focused) 1f else 0f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** App banner, its icon on ink, or its name on glass when it has neither. */
@Composable
private fun Artwork(
    app: AppEntry,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            app.banner != null -> Image(app.banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            app.icon != null -> Image(app.icon, null, modifier = Modifier.size(48.dp))
            else ->
                Text(
                    app.label,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Fg,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(10.dp),
                )
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    solid: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    icon: Int? = null,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(Pill),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (solid) Fg else GlassFill,
                contentColor = if (solid) Ink0 else Fg,
                focusedContainerColor = Fg,
                focusedContentColor = Ink0,
            ),
        border = ClickableSurfaceDefaults.border(border = if (solid) Border.None else Border(BorderStroke(1.dp, GlassEdge), shape = Pill)),
        modifier = Modifier.height(46.dp).onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp).fillMaxHeight()) {
            if (icon != null) {
                Icon(painterResource(icon), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

@Composable
private fun Widgets(
    weather: List<CityWeather>,
    notes: List<Note>,
    onToggle: (Panel) -> Unit,
    modifier: Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        if (weather.isNotEmpty()) {
            WidgetPill(onClick = { onToggle(Panel.Weather) }) {
                weather.forEachIndexed { i, w ->
                    if (i > 0) Spacer(Modifier.width(14.dp))
                    Icon(painterResource(weatherIcon(w.code)), null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    WidgetText("${w.temp}°", 19.sp, FontWeight.Bold)
                    Spacer(Modifier.width(5.dp))
                    WidgetText(w.city, 15.sp, FontWeight.Medium)
                }
            }
        }
        WidgetPill(onClick = { onToggle(Panel.Notes) }) {
            Icon(painterResource(R.drawable.ic_bell_simple), stringResource(R.string.notifications), modifier = Modifier.size(20.dp))
            if (notes.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                WidgetText(notes.size.toString(), 16.sp, FontWeight.ExtraBold)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(40.dp).background(GlassFill, Pill).border(1.dp, GlassEdge, Pill).padding(horizontal = 18.dp),
        ) {
            val context = LocalContext.current
            val now by produceState(Date()) {
                while (true) {
                    value = Date()
                    delay(10_000)
                }
            }
            Text(DateFormat.getTimeFormat(context).format(now), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Fg)
            Spacer(Modifier.width(8.dp))
            Text(DateFormat.format("EEE d MMM", now).toString(), fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Fg2)
        }
    }
}

@Composable
private fun WidgetPill(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(Pill),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = GlassFill,
                contentColor = Fg,
                focusedContainerColor = Fg,
                focusedContentColor = Ink0,
            ),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, GlassEdge), shape = Pill)),
        modifier = Modifier.height(40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight()) { content() }
    }
}

@Composable
private fun WidgetText(
    text: String,
    size: TextUnit,
    weight: FontWeight,
) = Text(text, fontFamily = Manrope, fontWeight = weight, fontSize = size)

@Composable
private fun PanelBox(
    title: String,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    // Panels hold reading text, so the glass is denser than the pills
    Column(
        modifier
            .width(470.dp)
            .background(Ink0.copy(alpha = .9f), RoundedCornerShape(22.dp))
            .border(1.dp, GlassEdge, RoundedCornerShape(22.dp))
            .padding(22.dp),
    ) {
        Text(title, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Fg)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun WeatherPanel(
    weather: List<CityWeather>,
    modifier: Modifier,
) {
    PanelBox(stringResource(R.string.weather), modifier) {
        weather.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Column(Modifier.width(132.dp)) {
                    Text(w.city, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Fg)
                    Text("${w.temp}°  ${weatherText(w.code)}", fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Fg2, maxLines = 1)
                }
                w.days.forEach { d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
                        Text(d.weekday, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Fg2)
                        Icon(painterResource(weatherIcon(d.code)), null, tint = Fg, modifier = Modifier.size(18.dp).padding(vertical = 1.dp))
                        Text("${d.max}° ${d.min}°", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Fg)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesPanel(
    notes: List<Note>,
    modifier: Modifier,
) {
    PanelBox(stringResource(R.string.notifications), modifier) {
        if (notes.isEmpty()) {
            Text(stringResource(R.string.no_notifications), fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Fg2)
        }
        notes.take(6).forEach { n ->
            Column(Modifier.padding(vertical = 7.dp)) {
                Text(n.title, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(n.app, n.text).joinToString(": "),
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = Fg2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Loads artwork off the main thread; null until it arrives or if it fails. */
@Composable
private fun rememberImage(url: String?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, url) {
        value = url?.let { withContext(Dispatchers.IO) { loadImage(it)?.asImageBitmap() } }
    }
    return image
}
