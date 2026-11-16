package io.github.gato131313.music

import android.app.Application
import io.github.gato131313.music.data.LibraryRepository
import io.github.gato131313.music.data.UserDataRepository
import io.github.gato131313.music.data.db.AppDatabase
import io.github.gato131313.music.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Dependencias de toda la app (sin framework de inyección: es una app pequeña). */
class AppGraph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val db: AppDatabase = AppDatabase.create(app)
    val settings = SettingsStore(app, scope)
    val library = LibraryRepository(app, db, settings, scope)
    val user = UserDataRepository(db, scope)
}
