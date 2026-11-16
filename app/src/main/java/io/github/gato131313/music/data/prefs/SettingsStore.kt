package io.github.gato131313.music.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("settings")

@Serializable
data class Settings(
    /** "music" (cristal líquido), "paisaje" (como su app anterior), "neon", "solido" */
    val style: String = "music",
    /** "auto", "dark", "light" */
    val mode: String = "dark",
    val accent: Long = 0xFFFA2D48,
    /** Usar los colores de la portada en el reproductor y fondos. */
    val coverColors: Boolean = true,
    /** Fondo para el estilo paisaje: "atardecer", "bosque", "noche", "oceano", "aurora", "desierto" */
    val background: String = "atardecer",
    /** "disco", "cuadrada", "aurora" */
    val coverStyle: String = "disco",
    /** Fusión entre canciones (0 = apagada). */
    val crossfadeSec: Int = 6,
    /** Velocidad 0.5–2.0 y tono en semitonos (independientes). */
    val speed: Float = 1f,
    val pitchSemis: Float = 0f,
    val minDurationSec: Int = 30,
    /** Efectos de sonido globales. */
    val fx: io.github.gato131313.music.fx.FxParams = io.github.gato131313.music.fx.FxParams(),
    /** Preset por salida: "audifonos" / "bocina" / "bluetooth" -> nombre de preset (vacío = el global). */
    val fxByOutput: Map<String, String> = emptyMap(),
    /** Al haber pareja, abrir en modo Video. */
    val preferVideo: Boolean = false,
    val translateLyrics: Boolean = true,
    val showLyricsDefault: Boolean = false,
    /** Ordenar canciones: "titulo", "artista", "reciente", "duracion" */
    val songSort: String = "titulo",
    val gridColumns: Int = 3,
    val keepScreenOnDriver: Boolean = true,
    val resumeOnHeadset: Boolean = true,
    val checkUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0,
    val deviceId: String = "",
    val firstRunDone: Boolean = false,
)

class SettingsStore(private val ctx: Context, scope: CoroutineScope) {
    private val key = stringPreferencesKey("json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun decode(s: String?): Settings = s?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()

    /** Lectura inicial síncrona (pequeña) para evitar un parpadeo de tema al arrancar. */
    private val initial: Settings = runBlocking { decode(ctx.dataStore.data.first()[key]) }

    val flow: StateFlow<Settings> = ctx.dataStore.data.map { decode(it[key]) }
        .stateIn(scope, SharingStarted.Eagerly, initial)

    val value: Settings get() = flow.value

    suspend fun update(f: (Settings) -> Settings) {
        ctx.dataStore.edit { p -> p[key] = json.encodeToString(Settings.serializer(), f(decode(p[key]))) }
    }

    fun export(): String = json.encodeToString(Settings.serializer(), value)
    suspend fun import(s: String) {
        val parsed = decode(s)
        update { parsed.copy(deviceId = it.deviceId.ifBlank { parsed.deviceId }) }
    }
}
