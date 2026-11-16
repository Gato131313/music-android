package io.github.gato131313.music.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import android.view.KeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.gato131313.music.MainActivity
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.R
import io.github.gato131313.music.data.scan.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private data class WidgetState(val title: String, val artist: String, val playing: Boolean, val art: Bitmap?)

/** Widget de la pantalla de inicio: portada, canción y controles. */
class MusicWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = load(context)
        provideContent { Content(context, s) }
    }

    private suspend fun load(ctx: Context): WidgetState {
        val g = MusicApp.graph
        val (id, playing) = withContext(Dispatchers.Main) {
            val p = g.hub.player
            (p?.currentMediaItem?.mediaId?.toLongOrNull() ?: g.hub.loadQueue()?.let { q -> q.ids.getOrNull(q.index) }) to (p?.isPlaying == true)
        }
        val lib = withTimeoutOrNull(4000) { g.library.libraryOrNull.filterNotNull().first() }
        val t = id?.let { lib?.byId?.get(it) }
        val art = t?.let { tr ->
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= 29) runCatching { ctx.contentResolver.loadThumbnail(MediaScanner.contentUri(tr.id, tr.isVideo), Size(256, 256), null) }.getOrNull() else null
            }
        }
        return WidgetState(t?.title ?: "MUSIC", t?.displayArtist ?: "Toca para abrir", playing, art)
    }

    @androidx.compose.runtime.Composable
    private fun Content(ctx: Context, s: WidgetState) {
        val white = ColorProvider(Color.White)
        Row(
            GlanceModifier.fillMaxSize().background(Color(0xE6111114)).cornerRadius(22.dp).padding(10.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.size(64.dp).cornerRadius(12.dp).background(Color(0xFF2A2A2E)), contentAlignment = Alignment.Center) {
                if (s.art != null) Image(ImageProvider(s.art), "Portada", GlanceModifier.size(64.dp).cornerRadius(12.dp), contentScale = ContentScale.Crop)
                else Image(ImageProvider(R.drawable.ic_u_mark), "MUSIC", GlanceModifier.size(30.dp))
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(s.title, maxLines = 1, style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Text(s.artist, maxLines = 1, style = TextStyle(color = ColorProvider(Color(0xB3FFFFFF)), fontSize = 13.sp))
                Spacer(GlanceModifier.size(6.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Btn(ctx, R.drawable.ic_w_prev, "Anterior", KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    Spacer(GlanceModifier.width(14.dp))
                    Btn(ctx, if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play, if (s.playing) "Pausa" else "Reproducir", if (s.playing) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY)
                    Spacer(GlanceModifier.width(14.dp))
                    Btn(ctx, R.drawable.ic_w_next, "Siguiente", KeyEvent.KEYCODE_MEDIA_NEXT)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Btn(ctx: Context, icon: Int, desc: String, key: Int) {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(ctx, androidx.media3.session.MediaButtonReceiver::class.java))
            .setData(Uri.parse("music://widget/$key"))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, key))
        Box(GlanceModifier.size(38.dp).cornerRadius(19.dp).background(Color(0x26FFFFFF)).clickable(actionSendBroadcast(intent)), contentAlignment = Alignment.Center) {
            Image(ImageProvider(icon), desc, GlanceModifier.size(22.dp))
        }
    }

    companion object {
        suspend fun refresh(ctx: Context) = runCatching { MusicWidget().updateAll(ctx) }
    }
}

class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}
