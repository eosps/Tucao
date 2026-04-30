package me.sweetll.tucao.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo

/**
 * 播放器配置工具类
 * 原属于本地 GSYVideoPlayer 子模块，迁移到 Maven 版本后保留在本地
 * 用于存储弹幕透明度、大小、速度、硬解码、横屏类型等偏好设置
 */
object PlayerConfig {
    private const val PLAYER_CONFIG_FILE_NAME = "player_config"
    private const val KEY_DANMU_OPACITY = "danmu_opacity"
    private const val KEY_DANMU_SIZE = "danmu_size"
    private const val KEY_DANMU_SPEED = "danmu_speed"
    private const val KEY_HARD_CODEC = "hard_codec"
    private const val KEY_PLAYER_SPEED = "player_speed"
    private const val KEY_LAND_SCREEN_TYPE = "land_screen_type"

    private const val DEFAULT_DANMU_OPACITY = 80
    private const val DEFAULT_DANMU_SIZE = 50
    private const val DEFAULT_DANMU_SPEED = 70
    private const val DEFAULT_PLAYER_SPEED = 1f
    private const val DEFAULT_HARD_CODEC = true
    private const val DEFAULT_LAND_SCREEN_TYPE = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences(PLAYER_CONFIG_FILE_NAME, 0)
    }

    fun saveDanmuOpacity(danmuOpacity: Int) {
        sp.edit().putInt(KEY_DANMU_OPACITY, danmuOpacity).apply()
    }

    fun loadDanmuOpacity(): Int {
        return sp.getInt(KEY_DANMU_OPACITY, DEFAULT_DANMU_OPACITY)
    }

    fun saveDanmuSize(danmuSize: Int) {
        sp.edit().putInt(KEY_DANMU_SIZE, danmuSize).apply()
    }

    fun loadDanmuSize(): Int {
        return sp.getInt(KEY_DANMU_SIZE, DEFAULT_DANMU_SIZE)
    }

    fun saveDanmuSpeed(danmuSpeed: Int) {
        sp.edit().putInt(KEY_DANMU_SPEED, danmuSpeed).apply()
    }

    fun loadDanmuSpeed(): Int {
        return sp.getInt(KEY_DANMU_SPEED, DEFAULT_DANMU_SPEED)
    }

    fun savePlayerSpeed(playerSpeed: Float) {
        sp.edit().putFloat(KEY_PLAYER_SPEED, playerSpeed).apply()
    }

    fun loadPlayerSpeed(): Float {
        return sp.getFloat(KEY_PLAYER_SPEED, DEFAULT_PLAYER_SPEED)
    }

    fun saveHardCodec(hardCodec: Boolean) {
        sp.edit().putBoolean(KEY_HARD_CODEC, hardCodec).apply()
    }

    fun loadHardCodec(): Boolean {
        return sp.getBoolean(KEY_HARD_CODEC, DEFAULT_HARD_CODEC)
    }

    fun saveLandScreenType(screenType: Int) {
        sp.edit().putInt(KEY_LAND_SCREEN_TYPE, screenType).apply()
    }

    fun loadLandScreenType(): Int {
        return sp.getInt(KEY_LAND_SCREEN_TYPE, DEFAULT_LAND_SCREEN_TYPE)
    }
}
