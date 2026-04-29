package me.sweetll.tucao.extension

import android.content.Context
import me.sweetll.tucao.AppApplication
import me.sweetll.tucao.BuildConfig

object UpdateHelpers {

    private val sp by lazy {
        AppApplication.get().getSharedPreferences("update", Context.MODE_PRIVATE)
    }

    fun newVersion(): Boolean {
        return sp.getInt("version_code", 0) != BuildConfig.VERSION_CODE
    }

    fun updateVersion() {
        sp.edit {
            putInt("version_code", BuildConfig.VERSION_CODE)
        }
    }
}
