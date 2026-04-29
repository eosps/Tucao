package me.sweetll.tucao.business.home.viewmodel

import android.view.View
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.business.channel.ChannelDetailActivity
import me.sweetll.tucao.business.home.fragment.BangumiFragment
import me.sweetll.tucao.business.showtimes.ShowtimeActivity
import me.sweetll.tucao.business.today.TodayActivity
import me.sweetll.tucao.extension.sanitizeHtml
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.model.json.Channel
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.model.raw.Bangumi
import me.sweetll.tucao.model.raw.Banner
import me.sweetll.tucao.util.parseBanner
import me.sweetll.tucao.util.parseChannelListFromSiblings
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*

class BangumiViewModel(val fragment: BangumiFragment): BaseViewModel() {

    companion object {
        const val TODAY_PREVIEW_COUNT = 6
    }

    // 保存完整的今天更新列表，供"更多"页面使用
    var allTodayVideos: List<Video> = emptyList()

    fun loadData() {
        fragment.setRefreshing(true)
        rawApiService.list(24)
                .bindToLifecycle(fragment)
                .sanitizeHtml {
                    val banners = parseBanners(this)
                    val recommends = parseRecommends(this)
                    Bangumi(banners, recommends)
                }
                .doAfterTerminate { fragment.setRefreshing(false) }
                .subscribe({
                    bangumi ->
                    fragment.loadBangumi(bangumi)
                }, {
                    error ->
                    error.printStackTrace()
                    error.message?.toast()
                    fragment.loadError()
                })

        // 同时加载"今天更新"数据
        loadTodayUpdates()
    }

    /**
     * 从本周新番页面提取今天的番组更新
     */
    fun loadTodayUpdates() {
        rawApiService.weekBgm()
                .bindToLifecycle(fragment)
                .sanitizeHtml {
                    parseTodayBgm(this)
                }
                .subscribe({
                    todayVideos ->
                    allTodayVideos = todayVideos
                    // 默认只显示前 6 个
                    fragment.loadTodayUpdates(todayVideos.take(TODAY_PREVIEW_COUNT))
                }, {
                    it.printStackTrace()
                })
    }

    /**
     * 从 week_bgm 页面提取今天日期的番组
     */
    fun parseTodayBgm(doc: Document): List<Video> {
        val items = doc.select("div.list > div.item")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = dateFormat.format(Date())

        return items.filter { item ->
            item.attr("date") == todayStr
        }.mapNotNull { item ->
            try {
                val linkElement = item.select("a.p.vp").first() ?: return@mapNotNull null
                val style = linkElement.attr("style")
                val thumb = extractBackgroundImageUrl(style)

                val titleElement = item.select("a.t").first() ?: return@mapNotNull null
                val title = titleElement.text().trim()

                val href = linkElement.attr("href")
                val hidRegex = Regex("/play/(h\\d+)/")
                val hid = hidRegex.find(href)?.groupValues?.get(1) ?: ""

                Video(thumb = thumb, title = title, hid = hid)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun extractBackgroundImageUrl(style: String): String {
        val regex = Regex("background-image:url\\(([^)]+)\\)")
        return regex.find(style)?.groupValues?.get(1) ?: ""
    }

    fun parseBanners(doc: Document): List<Banner> {
        val slides = doc.select("div.slide")
        return parseBanner(slides)
    }

    fun parseRecommends(doc: Document): List<Pair<Channel, List<Video>>> {
        return parseChannelListFromSiblings(doc.body())
    }

    fun onClickChannel(view: View) {
        ChannelDetailActivity.intentTo(fragment.activity!!, (view.tag as String).toInt())
    }

    fun onClickShowtime(view: View) {
        ShowtimeActivity.intentTo(fragment.activity!!)
    }

    fun onClickTodayMore(view: View) {
        TodayActivity.intentTo(fragment.activity!!)
    }
}
