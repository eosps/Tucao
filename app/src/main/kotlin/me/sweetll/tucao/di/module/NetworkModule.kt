package me.sweetll.tucao.di.module

import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import dagger.Module
import dagger.Provides
import me.sweetll.tucao.AppApplication
import me.sweetll.tucao.di.service.ApiConfig
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
class NetworkModule {

    companion object {
        /**
         * 动态替换请求域名的拦截器
         * 只替换默认域名（www.tucao.my）的请求为用户配置的域名
         * 外部服务（如 45.63.54.11）不受影响
         */
        fun provideBaseUrlInterceptor(): Interceptor = Interceptor { chain ->
            val request = chain.request()
            val baseUrl = ApiConfig.getBaseUrl()
            // 只替换默认域名的请求
            if (request.url.host == ApiConfig.DEFAULT_BASE_URL && baseUrl != ApiConfig.DEFAULT_BASE_URL) {
                val newUrl = request.url.newBuilder()
                        .host(baseUrl)
                        .build()
                chain.proceed(request.newBuilder().url(newUrl).build())
            } else {
                chain.proceed(request)
            }
        }
    }

    @Provides
    @Singleton
    fun provideCookieJar(): CookieJar = PersistentCookieJar(SetCookieCache(), SharedPrefsCookiePersistor(AppApplication.get()))

    @Provides
    @Singleton
    @Named("raw")
    fun provideRawOkHttpClient(cookieJar: CookieJar): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .addInterceptor(provideBaseUrlInterceptor())
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .build()

    @Provides
    @Singleton
    @Named("download")
    fun provideDownloadOkHttpClient(cookieJar: CookieJar): OkHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(provideBaseUrlInterceptor())
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.HEADERS))
            .build()

    @Provides
    @Singleton
    @Named("json")
    fun provideJsonClient(cookieJar: CookieJar, @Named("apiKey") apiKey: String): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .addInterceptor(provideBaseUrlInterceptor())
            .addInterceptor { chain ->
                val url = chain.request().url
                        .newBuilder()
                        .addQueryParameter("apikey", apiKey)
                        .addQueryParameter("type", "json")
                        .build()
                val request = chain.request().newBuilder()
                        .url(url)
                        .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .build()

    @Provides
    @Singleton
    @Named("xml")
    fun provideXmlClient(cookieJar: CookieJar): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .addInterceptor(provideBaseUrlInterceptor())
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .build()

    @Provides
    @Singleton
    @Named("raw")
    fun provideRawRetrofit(@Named("raw") client: OkHttpClient) : Retrofit = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_RAW_API_URL)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .client(client)
            .build()

    @Provides
    @Singleton
    @Named("download")
    fun provideDownloadRetrofit(@Named("download") client: OkHttpClient) : Retrofit = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_RAW_API_URL)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .client(client)
            .build()


    @Provides
    @Singleton
    @Named("json")
    fun provideJsonRetrofit(@Named("json") client: OkHttpClient) : Retrofit = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_JSON_API_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .client(client)
            .build()

    @Provides
    @Singleton
    @Named("xml")
    fun provideXmlRetrofit(@Named("xml") client: OkHttpClient) : Retrofit = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_XML_API_URL)
            .addConverterFactory(SimpleXmlConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .client(client)
            .build()

}
