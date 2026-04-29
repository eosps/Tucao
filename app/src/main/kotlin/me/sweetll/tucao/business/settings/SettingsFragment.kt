package me.sweetll.tucao.business.settings

import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import me.sweetll.tucao.AppApplication
import me.sweetll.tucao.R
import me.sweetll.tucao.di.service.ApiConfig
import me.sweetll.tucao.extension.edit
import me.sweetll.tucao.extension.toast

class SettingsFragment : PreferenceFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.settings)

        // 网站地址配置
        val baseUrlPref = findPreference("base_url") as EditTextPreference
        val currentUrl = ApiConfig.getBaseUrl()
        baseUrlPref.text = currentUrl
        baseUrlPref.summary = "当前：$currentUrl（修改后需重启应用）"
        baseUrlPref.setOnPreferenceChangeListener { _, newValue ->
            val newUrl = (newValue as String).trim()
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removeSuffix("/")
            if (newUrl.isBlank()) {
                "地址不能为空".toast()
                return@setOnPreferenceChangeListener false
            }
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(AppApplication.get())
            sharedPref.edit { putString(ApiConfig.PREF_BASE_URL, newUrl) }
            baseUrlPref.summary = "当前：$newUrl（修改后需重启应用）"
            "已保存，请重启应用使新地址生效".toast()
            true
        }
    }
}
