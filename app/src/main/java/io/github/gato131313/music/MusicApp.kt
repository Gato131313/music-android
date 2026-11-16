package io.github.gato131313.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import io.github.gato131313.music.data.art.ArtFetcher
import io.github.gato131313.music.data.art.ArtKeyer

class MusicApp : Application(), ImageLoaderFactory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        graph = AppGraph(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(ArtKeyer())
            add(ArtFetcher.Factory(this@MusicApp))
            add(VideoFrameDecoder.Factory())
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("coil")).maxSizeBytes(200L * 1024 * 1024).build() }
        .crossfade(120)
        .respectCacheHeaders(false)
        .build()

    companion object {
        lateinit var instance: MusicApp
            private set
        val graph: AppGraph get() = instance.graph
    }
}
