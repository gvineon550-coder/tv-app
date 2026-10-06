package com.gvineon550coder.tvapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.gvineon550coder.tvapp.util.ProxyUtil
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TvApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                ProxyUtil.buildClient(null)
            }
            // Логотипы каналов — мелкие (5–20 КБ),
            // 15 МБ RAM хватает на ~750–3000 картинок.
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(15 * 1024 * 1024)
                    .build()
            }
            // Дисковый кэш тоже ограничиваем: 20 МБ хватает с запасом.
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(20 * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
