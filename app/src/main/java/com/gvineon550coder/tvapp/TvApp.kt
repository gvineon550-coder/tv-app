package com.gvineon550coder.tvapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.gvineon550coder.tvapp.util.ProxyUtil
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TvApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                ProxyUtil.buildClient(null)
            }
            .build()
    }
}
