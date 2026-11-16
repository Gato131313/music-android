package io.github.gato131313.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gato131313.music.ui.AppRoot
import io.github.gato131313.music.ui.theme.MusicTheme
import io.github.gato131313.music.ui.theme.appColorsFor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = intent?.getStringExtra(EXTRA_ROUTE)
        // Solo en la versión de medición del CI: elegir estilo desde la línea de comandos
        if (packageName.endsWith(".bench")) intent?.getStringExtra("estilo")?.let { st ->
            kotlinx.coroutines.runBlocking { MusicApp.graph.settings.update { it.copy(style = st) } }
        }
        setContent {
            val settings by MusicApp.graph.settings.flow.collectAsStateWithLifecycle()
            MusicTheme(appColorsFor(settings)) {
                AppRoot(start)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { io.github.gato131313.music.ui.NavRequests.route.value = it }
    }

    override fun onResume() {
        super.onResume()
        MusicApp.graph.library.onPermissionsChanged()
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
