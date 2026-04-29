package me.sweetll.tucao.business.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseActivity
import me.sweetll.tucao.business.settings.SettingsFragment
import me.sweetll.tucao.databinding.ActivitySettingsBinding

class SettingsActivity : BaseActivity() {
    lateinit var binding: ActivitySettingsBinding

    override fun getToolbar(): Toolbar = binding.toolbar

    override fun getStatusBar(): View = binding.statusBar

    override fun initView(savedInstanceState: Bundle?) {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_settings)

        fragmentManager.beginTransaction()
                .replace(R.id.contentFrame, SettingsFragment())
                .commit()
    }

    override fun initToolbar() {
        super.initToolbar()
        delegate.supportActionBar?.let {
            it.title = "设置"
            it.setDisplayHomeAsUpEnabled(true)
        }
    }

    companion object {
        fun intentTo(context: Context) {
            val intent = Intent(context, SettingsActivity::class.java)
            context.startActivity(intent)
        }
    }
}
