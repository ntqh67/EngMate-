package com.engmate.app.di

import com.engmate.app.feature.dictionary.data.ApiDictionaryRepository
import com.engmate.app.feature.dictionary.data.remote.DictionaryApi
import com.engmate.app.feature.dictionary.domain.DictionaryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module @InstallIn(SingletonComponent::class)
object DictionaryNetworkModule {
    // NFR02: a lookup should answer within ~2 s; fail fast instead of hanging the search.
    @Provides @Singleton fun dictionaryApi(): DictionaryApi = Retrofit.Builder()
        .baseUrl(DictionaryApi.BASE_URL)
        .client(OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(DictionaryApi::class.java)
}

@Module @InstallIn(SingletonComponent::class)
abstract class DictionaryRepositoryModule {
    @Binds @Singleton abstract fun dictionary(implementation: ApiDictionaryRepository): DictionaryRepository
}
