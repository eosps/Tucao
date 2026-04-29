package me.sweetll.tucao.business.home.adapter

import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import me.sweetll.tucao.business.home.fragment.*

class HomePagerAdapter(fm : FragmentManager) : FragmentPagerAdapter(fm) {
    val tabTitles = listOf("推荐", "新番", "影剧", "综合")

    override fun getItem(position: Int) =
        when (position) {
            0 -> RecommendFragment()
            1 -> BangumiFragment()
            2 -> MovieFragment()
            else -> AnimationFragment()
        }

    override fun getCount() = tabTitles.size

    override fun getPageTitle(position: Int) = tabTitles[position]
}
