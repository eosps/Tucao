package me.sweetll.tucao.business.rank

import android.content.Context
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import android.view.View
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseActivity
import me.sweetll.tucao.business.rank.adapter.RankPagerAdapter
import me.sweetll.tucao.business.rank.viewmodel.RankViewModel
import me.sweetll.tucao.databinding.ActivityRankBinding
import me.sweetll.tucao.model.json.Channel

class RankActivity : BaseActivity() {

    lateinit var binding: ActivityRankBinding
    val viewModel = RankViewModel(this)

    // 排行榜可用的频道列表（只有这些 tid 在 /html/hot.html?p=X 页面有数据）
    private val rankChannels = listOf(
            Channel(0, "全站"),
            Channel(11, "连载新番"),
            Channel(10, "完结番组"),
            Channel(26, "剧场版"),
            Channel(38, "电影"),
            Channel(39, "电视剧"),
            Channel(43, "天朝出品")
    )

    override fun getToolbar(): Toolbar = binding.toolbar

    override fun getStatusBar(): View = binding.statusBar

    companion object {
        fun intentTo(context: Context) {
            val intent = Intent(context, RankActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_rank)
        binding.viewModel = viewModel

        binding.viewPager.adapter = RankPagerAdapter(supportFragmentManager, rankChannels)
        binding.viewPager.offscreenPageLimit = rankChannels.size
        binding.tab.setupWithViewPager(binding.viewPager)
    }

    override fun initToolbar() {
        super.initToolbar()
        supportActionBar?.let {
            it.setDisplayShowTitleEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
        }
    }
}
