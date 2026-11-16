package io.github.gato131313.music.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.R
import io.github.gato131313.music.ui.theme.AppBackground
import io.github.gato131313.music.ui.theme.LocalAppColors

fun mediaPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.READ_MEDIA_AUDIO)
        add(Manifest.permission.READ_MEDIA_VIDEO)
        add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}.toTypedArray()

@Composable
fun PermissionScreen() {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    var denied by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        MusicApp.graph.library.onPermissionsChanged()
        if (!MusicApp.graph.library.scanner.hasAudioPermission()) denied++
    }
    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(96.dp).background(Color.Black, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_u_mark), null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("MUSIC", color = c.text, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Para mostrar tu música y tus videos, MUSIC necesita permiso para leerlos. Todo se queda en tu teléfono: sin cuenta y sin nube.",
                color = c.text2, fontSize = 16.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier.fillMaxWidth().height(54.dp).background(c.accent, RoundedCornerShape(27.dp)).clickable {
                    if (denied >= 2) {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                    } else {
                        launcher.launch(mediaPermissions())
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (denied >= 2) "Abrir ajustes del permiso" else "Permitir acceso a música y videos",
                    color = c.onAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
