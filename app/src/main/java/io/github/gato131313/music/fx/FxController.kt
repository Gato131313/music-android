package io.github.gato131313.music.fx

import io.github.gato131313.music.AppGraph
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Decide qué efectos suenan: preset de la canción > preset de la salida > ajustes globales. */
class FxController(private val g: AppGraph) {
    data class Source(val kind: String, val preset: String?)

    @Volatile var source: Source = Source("global", null)
        private set

    fun bandsOf(name: String): List<Float>? = FxPresets.BUILT_IN[name] ?: g.user.eqPresets.value[name]

    init {
        g.scope.launch {
            combine(
                g.settings.flow.map { it.fx to it.fxByOutput }.distinctUntilChanged(),
                g.fx.output,
                g.player.state.map { it.current?.key }.distinctUntilChanged(),
                g.user.songFx,
                g.user.eqPresets,
            ) { (fx, byOut), out, key, songFx, _ ->
                val songPreset = key?.let { songFx[it] }?.takeIf { bandsOf(it) != null }
                val outPreset = byOut[out]?.takeIf { it.isNotBlank() && bandsOf(it) != null }
                when {
                    songPreset != null -> { source = Source("cancion", songPreset); fx.copy(eqOn = true, preset = songPreset, bands = bandsOf(songPreset)!!) }
                    outPreset != null -> { source = Source(out, outPreset); fx.copy(eqOn = true, preset = outPreset, bands = bandsOf(outPreset)!!) }
                    else -> { source = Source("global", null); fx }
                }
            }.collect { g.fx.apply(it) }
        }
    }
}
