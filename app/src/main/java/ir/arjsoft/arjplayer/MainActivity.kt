package ir.arjsoft.arjplayer

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import java.util.Locale

private val navy = Color(0xFF003D5B)
private val bronze = Color(0xFFEDAE49)
private val rose = Color(0xFFD1495B)
private val teal = Color(0xFF00798C)
private val blue = Color(0xFF30638E)
private val panel = Color(0xFF10394B)

data class AudioTrack(val id: Long, val title: String, val artist: String, val album: String, val duration: Long, val uri: Uri)

class MainActivity : ComponentActivity() {
    private var tracks by mutableStateOf<List<AudioTrack>>(emptyList())
    private var favorites by mutableStateOf<Set<Long>>(emptySet())
    private var controller by mutableStateOf<MediaController?>(null)
    private var scanned by mutableStateOf(false)
    private var future: ListenableFuture<MediaController>? = null

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) scan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favorites = getSharedPreferences("arj_player", MODE_PRIVATE).getStringSet("favorites", emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = bronze, secondary = teal, background = navy, surface = panel,
                onPrimary = navy, onBackground = Color.White, onSurface = Color.White)) {
                PlayerScreen(tracks, favorites, controller, scanned, { requestAudio() }, { play(it) }, { favorite(it) }, { scan() })
            }
        }
        if (hasPermission()) scan()
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, com.honey.music.player.MusicPlaybackService::class.java))
        future = MediaController.Builder(this, token).buildAsync()
        future?.addListener({ try { controller = future?.get() } catch (_: Exception) { controller = null } },
            ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        super.onStop()
    }

    private fun hasPermission(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    }
    private fun requestAudio() {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        permission.launch(p)
    }
    private fun scan() {
        if (!hasPermission()) return
        val result = mutableListOf<AudioTrack>()
        val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
        try {
            contentResolver.query(collection, columns, MediaStore.Audio.Media.IS_MUSIC + " != 0", null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val songId = c.getLong(id)
                    result.add(AudioTrack(songId, c.getString(title) ?: "آهنگ بدون نام",
                        c.getString(artist)?.takeUnless { it == "<unknown>" } ?: "هنرمند ناشناس",
                        c.getString(album)?.takeUnless { it == "<unknown>" } ?: "آلبوم نامشخص",
                        c.getLong(duration), ContentUris.withAppendedId(collection, songId)))
                }
            }
        } catch (_: Exception) { }
        tracks = result
        scanned = true
    }
    private fun play(index: Int) {
        val c = controller ?: return
        val queue = tracks.map { t -> MediaItem.Builder().setUri(t.uri).setMediaMetadata(
            MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build()).build() }
        c.setMediaItems(queue, index, 0L); c.prepare(); c.play()
    }
    private fun favorite(id: Long) {
        val next = favorites.toMutableSet()
        if (!next.add(id)) next.remove(id)
        favorites = next
        getSharedPreferences("arj_player", MODE_PRIVATE).edit().putStringSet("favorites", next.map { it.toString() }.toSet()).apply()
    }
}

@Composable
private fun PlayerScreen(
    tracks: List<AudioTrack>, favorites: Set<Long>, player: MediaController?, scanned: Boolean,
    requestPermission: () -> Unit, play: (Int) -> Unit, toggleFavorite: (Long) -> Unit, refresh: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var nowPlaying by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("انتخاب آهنگ") }
    var artist by remember { mutableStateOf("کتابخانه موسیقی") }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                title = item?.mediaMetadata?.title?.toString() ?: "بدون عنوان"
                artist = item?.mediaMetadata?.artist?.toString() ?: "هنرمند ناشناس"
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (player != null) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    val shown = tracks.withIndex().filter { x ->
        val t = x.value
        (tab != 1 || t.id in favorites) && (search.isBlank() || t.title.contains(search, true) || t.artist.contains(search, true) || t.album.contains(search, true))
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(navy, Color(0xFF061C29))))) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Vinyl(46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("ARJ PLAYER", fontWeight = FontWeight.Black, fontSize = 19.sp, letterSpacing = 1.5.sp)
                Text("MUSIC • YOUR WAY", color = bronze, fontSize = 9.sp, letterSpacing = 2.sp)
            }
            IconButton(onClick = refresh) { Icon(Icons.Default.Refresh, "به‌روزرسانی", tint = bronze) }
        }
        if (nowPlaying) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nowPlaying = false }) { Icon(Icons.Default.KeyboardArrowDown, "بازگشت") }
                Text("در حال پخش", color = bronze, modifier = Modifier.weight(1f))
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Vinyl(250)
                Spacer(Modifier.height(30.dp))
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = Color.White.copy(.6f))
                Spacer(Modifier.height(20.dp))
                Slider(value = if (duration > 0) position.coerceIn(0L, duration).toFloat() else 0f,
                    onValueChange = { player?.seekTo(it.toLong()) }, valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = bronze, activeTrackColor = bronze))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(time(position), fontSize = 11.sp, color = Color.LightGray)
                    Text(time(duration), fontSize = 11.sp, color = Color.LightGray)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { player?.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "قبلی", modifier = Modifier.size(38.dp)) }
                    Surface(Modifier.size(74.dp), shape = CircleShape, color = bronze) {
                        IconButton(onClick = { if (player?.isPlaying == true) player.pause() else player?.play() }) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "پخش/توقف", tint = navy, modifier = Modifier.size(42.dp))
                        }
                    }
                    IconButton(onClick = { player?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "بعدی", modifier = Modifier.size(38.dp)) }
                }
                Spacer(Modifier.weight(1f))
            }
        } else if (tab == 2) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Vinyl(100); Spacer(Modifier.height(16.dp))
                Text("ARJ Player", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("پخش‌کننده موسیقی آفلاین", color = bronze)
                Spacer(Modifier.height(12.dp))
                Text("نسخه 2.1", color = Color.LightGray)
                Text("ساخته شده توسط علیرضا ارجمند نیا", fontSize = 12.sp, color = Color.LightGray)
            }
        } else {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Text(if (tab == 1) "آهنگ‌های محبوب" else "کتابخانه موسیقی", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(tracks.size.toString() + " آهنگ در دستگاه شما", color = Color.White.copy(.5f), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text("جست‌وجوی آهنگ، هنرمند یا آلبوم…", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = bronze) }, singleLine = true,
                    shape = RoundedCornerShape(15.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = bronze, unfocusedBorderColor = Color.White.copy(.15f),
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            }
            if (!scanned || tracks.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Vinyl(90)
                    Spacer(Modifier.height(16.dp))
                    Text(if (!scanned) "برای شروع دسترسی موسیقی را فعال کن" else "موسیقی‌ای پیدا نشد")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = requestPermission, colors = ButtonDefaults.buttonColors(containerColor = bronze)) {
                        Text("دسترسی به موسیقی", color = navy)
                    }
                }
            } else if (shown.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("موردی پیدا نشد", color = Color.LightGray) }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    items(shown, key = { it.value.id }) { indexed ->
                        val song = indexed.value
                        Row(Modifier.fillMaxWidth().clickable { play(indexed.index); nowPlaying = true }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(blue, teal))), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MusicNote, null, tint = bronze)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                Text(song.artist, color = Color.White.copy(.55f), fontSize = 11.sp, maxLines = 1)
                            }
                            Text(time(song.duration), color = Color.White.copy(.45f), fontSize = 10.sp)
                            IconButton(onClick = { toggleFavorite(song.id) }) {
                                Icon(if (song.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
                                    tint = if (song.id in favorites) rose else Color.LightGray, modifier = Modifier.size(19.dp))
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(.07f), thickness = .5.dp)
                    }
                }
            }
        }
        if (!nowPlaying && player?.currentMediaItem != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp).clip(RoundedCornerShape(15.dp)).background(panel).clickable { nowPlaying = true }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Vinyl(38); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    Text(artist, fontSize = 10.sp, color = Color.LightGray)
                }
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = bronze)
                }
                IconButton(onClick = { player.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, null) }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFF082635)).padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            BottomTab("کتابخانه", Icons.Default.LibraryMusic, tab == 0) { tab = 0; nowPlaying = false }
            BottomTab("علاقه‌مندی", Icons.Default.Favorite, tab == 1) { tab = 1; nowPlaying = false }
            BottomTab("درباره", Icons.Default.Info, tab == 2) { tab = 2; nowPlaying = false }
        }
    }
}

@Composable
private fun BottomTab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, click: () -> Unit) {
    Column(Modifier.clickable(onClick = click).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (selected) bronze else Color.LightGray, modifier = Modifier.size(21.dp))
        Text(label, fontSize = 10.sp, color = if (selected) bronze else Color.LightGray)
    }
}

@Composable
private fun Vinyl(size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF466575), Color(0xFF17232B), Color.Black))), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize(.78f).clip(CircleShape).background(Color(0xFF253B47)), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize(.7f).clip(CircleShape).background(Color(0xFF101820)), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize(.42f).clip(CircleShape).background(Brush.linearGradient(listOf(bronze, rose))), contentAlignment = Alignment.Center) {
                    Box(Modifier.size((size * .05f).dp).clip(CircleShape).background(navy))
                }
            }
        }
    }
}
private fun time(ms: Long): String {
    val s = ms.coerceAtLeast(0L) / 1000L
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}
