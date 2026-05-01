package me.sweetll.tucao.business.home.viewmodel

import android.view.View
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.business.home.fragment.RecommendFragment
import me.sweetll.tucao.business.rank.RankActivity
import me.sweetll.tucao.extension.sanitizeHtml
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.model.json.Channel
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.model.raw.Banner
import me.sweetll.tucao.model.raw.Index
import me.sweetll.tucao.util.parseBanner
import me.sweetll.tucao.util.parseChannelListFromSiblings
import me.sweetll.tucao.util.parseListVideo
import org.jsoup.nodes.Document

class RecommendViewModel(val fragment: RecommendFragment): BaseViewModel() {

    fun loadData() {
        fragment.setRefreshing(true)
        rawApiService.index()
                .bindToLifecycle(fragment)
                .sanitizeHtml({
                    val banners = parseBanners(this)
                    val recommends = parseRecommends(this)
                    Index(banners, recommends)
                })
                .doAfterTerminate { fragment.setRefreshing(false) }
                .subscribe({
                    index ->
                    fragment.loadIndex(index)
                }, {
                    error ->
                    error.printStackTrace()
                    error.message?.toast()
                    fragment.loadError()
                })
    }

    fun onClickRank(view: View) {
        RankActivity.intentTo(fragment.activity!!)
    }

    fun parseBanners(doc: Document): List<Banner> {
        val slides = doc.select("div.slide")
        return parseBanner(slides)
    }

    fun parseRecommends(doc: Document): List<Pair<Channel, List<Video>>> {
        val recommends = mutableListOf<Pair<Channel, List<Video>>>()

        // 解析"今天推荐"（list8 区域），展示 4 个卡片
        val list8 = doc.getElementsByClass("list list8").first()
        if (list8 != null) {
            // id=-1 标记为"今天推荐"，适配器据此显示排行榜和"更多"按钮
            val channel = Channel(-1, "今天推荐")
            val videos = parseListVideo(list8)
            if (videos.isNotEmpty()) {
                recommends.add(0, channel to videos.subList(0, minOf(4, videos.size)))
            }
        }

        // 解析其他频道列表（title + list loop_listXX 成对出现）
        val channelList = parseChannelListFromSiblings(doc.body())
        recommends.addAll(channelList)

        return recommends
    }

}
