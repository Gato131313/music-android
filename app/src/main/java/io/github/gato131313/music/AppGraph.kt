package io.github.gato131313.music

import android.app.Application
import io.github.gato131313.music.data.LibraryRepository
import io.github.gato131313.music.data.UserDataRepository
import io.github.gato131313.music.data.db.AppDatabase
import io.github.gato131313.music.data.prefs.SettingsStore
import io.github.gato131313.music.playback.PlaybackHub
import io.github.gato131313.music.fx.FxEngine
import io.github.gato131313.music.fx.FxController
import io.github.gato131313.music.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Dependencias de toda la app (sin framework de inyección: es una app pequeña). */
class AppGraph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val db: AppDatabase = AppDatabase.create(app)
    val settings = SettingsStore(app, scope)
    val library = LibraryRepository(app, db, settings, scope)
    val user = UserDataRepository(db, scope)
    val hub = PlaybackHub(app, scope)
    val fx = FxEngine(app, scope).also { e -> hub.processorsFactory = { arrayOf(e.newProcessor()) } }
    val player = PlayerController(app, this)
    val fxController = FxController(this)
    val lyrics = io.github.gato131313.music.lyrics.LyricsRepository(this)
    val backup = io.github.gato131313.music.data.backup.BackupManager(this)
    val updates = io.github.gato131313.music.update.UpdateChecker(this)

    init {
        // Cambiar el estilo o la opacidad del widget en Ajustes lo redibuja
        scope.launch {
            settings.flow.map { it.widgetStyle to it.widgetOpacity }.distinctUntilChanged().drop(1)
                .collect { io.github.gato131313.music.widget.MusicWidget.refresh(app) }
        }
    }
}
