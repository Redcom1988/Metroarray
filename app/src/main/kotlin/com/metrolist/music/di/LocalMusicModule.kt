/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.di

import android.content.Context
import com.metrolist.music.localmusic.LocalMusicRepository
import com.metrolist.music.localmusic.LocalMusicScanner
import com.metrolist.music.localmusic.MetadataExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing local music related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object LocalMusicModule {

    @Provides
    @Singleton
    fun provideLocalMusicScanner(
        @ApplicationContext context: Context
    ): LocalMusicScanner {
        return LocalMusicScanner(context)
    }

    @Provides
    @Singleton
    fun provideMetadataExtractor(
        @ApplicationContext context: Context
    ): MetadataExtractor {
        return MetadataExtractor(context)
    }
}
