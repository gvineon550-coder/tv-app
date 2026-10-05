package com.gvineon550coder.tvapp.di

import android.content.Context
import com.gvineon550coder.tvapp.data.Prefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePrefs(@ApplicationContext ctx: Context): Prefs = Prefs(ctx)
}
