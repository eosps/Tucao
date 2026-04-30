package me.sweetll.tucao

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import me.sweetll.tucao.utils.PlayerConfig
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import com.shuyu.gsyvideoplayer.cache.CacheFactory
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager
import tv.danmaku.ijk.media.exo2.ExoPlayerCacheManager
import com.umeng.analytics.MobclickAgent
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import me.sweetll.tucao.di.component.ApiComponent
import me.sweetll.tucao.di.component.DaggerNetworkComponent
import me.sweetll.tucao.di.component.UserComponent
import me.sweetll.tucao.di.service.ApiConfig
import me.sweetll.tucao.extension.UpdateHelpers
import javax.inject.Inject


class AppApplication : MultiDexApplication(), HasAndroidInjector {
    companion object {
        const val PRIMARY_CHANNEL = "${BuildConfig.APPLICATION_ID}_CHANNEL_PRIMARY"

        private lateinit var INSTANCE: AppApplication

        fun get(): AppApplication {
            return INSTANCE
        }

        init {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
    }

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    private lateinit var apiComponent: ApiComponent
    private lateinit var userComponent: UserComponent

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this

        // For performance analysis
        /*
        CrashWoodpecker.instance()
                .setPatchMode(PatchMode.SHOW_LOG_PAGE)
                .flyTo(this)
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return
        }
        LeakCanary.install(this)
        */

        // disableHiddenApiCheck()

        MobclickAgent.setScenarioType(this, MobclickAgent.EScenarioType.E_UM_NORMAL)
        PlayerConfig.init(this)
        // 使用 ExoPlayer 作为播放引擎（替代 IJKPlayer）
        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)
        CacheFactory.setCacheManager(ExoPlayerCacheManager::class.java)
        initComponent()
        initChannel()
        // 清理过期的分享临时文件（超过 5 天自动删除）
        me.sweetll.tucao.extension.DownloadHelpers.cleanExpiredShareFiles()

        postUpdate()
    }

    private fun postUpdate() {
        if (UpdateHelpers.newVersion()) {
            UpdateHelpers.updateVersion()
        }
    }

    private fun initComponent() {
        val networkComponent = DaggerNetworkComponent.factory().create(ApiConfig.API_KEY)
        apiComponent = networkComponent.apiComponent().create()
        userComponent = apiComponent.userComponent().create()

        userComponent.inject(this)
    }

    fun getApiComponent(): ApiComponent = apiComponent

    fun getUserComponent(): UserComponent = userComponent

    private fun initChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(PRIMARY_CHANNEL, "默认通知", NotificationManager.IMPORTANCE_DEFAULT)
            channel.enableVibration(false)
            channel.vibrationPattern = longArrayOf(0L)

            val notifyMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notifyMgr.createNotificationChannel(channel)
        }
    }

    override fun androidInjector(): AndroidInjector<Any> {
        return dispatchingAndroidInjector
    }

}
