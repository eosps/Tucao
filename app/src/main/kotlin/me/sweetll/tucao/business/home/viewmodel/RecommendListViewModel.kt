package me.sweetll.tucao.business.home.viewmodel

import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.business.home.RecommendListActivity
import me.sweetll.tucao.extension.sanitizeHtml
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.util.HID_PATTERN
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class RecommendListViewModel(val activity: RecommendListActivity) : BaseViewModel() {

    var pageIndex = 1
    private val pageSize = 50

    // 加载第一页数据（下拉刷新）
    fun loadData() {
        pageIndex = 1
        activity.setRefreshing(true)
        rawApiService.pos(pageIndex)
                .bindToLifecycle(activity)
                .sanitizeHtml { parsePosVideos(this) }
                .doAfterTerminate { activity.setRefreshing(false) }
                .subscribe({ videos ->
                    pageIndex++
                    activity.setData(videos)
                }, { error ->
                    error.printStackTrace()
                    error.message?.toast()
                })
    }

    // 加载更多（滚动到底部）
    fun loadMoreData() {
        rawApiService.pos(pageIndex)
                .bindToLifecycle(activity)
                .sanitizeHtml { parsePosVideos(this) }
                .subscribe({ videos ->
                    pageIndex++
                    activity.addData(videos)
                    if (videos.size < pageSize) {
                        activity.loadMoreEnd()
                    } else {
                        activity.loadMoreComplete()
                    }
                }, { error ->
                    error.printStackTrace()
                    error.message?.toast()
                    activity.loadMoreFail()
                })
    }

    /**
     * 从 pos.html 页面解析视频列表，提取完整的 hid、标题、播放数、UP主、缩略图
     * HTML 结构：div.list > div.item > div.b > a.p(缩略图) + a.t(标题) + div.i(span播放数 + a UP主)
     */
    private fun parsePosVideos(doc: Document): List<Video> {
        val listElement = doc.select("div.list").first() ?: return emptyList()
        val items = listElement.getElementsByClass("item")
        return items.mapNotNull { item ->
            try {
                val bElement = item.getElementsByClass("b").first() ?: return@mapNotNull null

                // 第一个 <a> 是缩略图链接，href 包含 hid
                val linkElement = bElement.getElementsByTag("a").first() ?: return@mapNotNull null
                val linkUrl = linkElement.attr("href")
                val hid = HID_PATTERN.find(linkUrl)?.groupValues?.get(1) ?: return@mapNotNull null

                // 缩略图：从 img 的 style 属性 background-image:url(...) 中提取
                val imgElement = linkElement.getElementsByTag("img").first()
                val thumb = extractThumb(imgElement)

                // 标题：class="t" 的 <a> 标签
                val title = bElement.getElementsByClass("t").first()?.text()?.trim() ?: ""

                // 播放数和 UP 主：div.i > span(播放数) + a(UP主)
                val infoElement = bElement.getElementsByClass("i").first()
                val play = infoElement?.getElementsByTag("span")?.first()
                        ?.text()?.replace(",", "")?.trim()?.toIntOrNull() ?: 0
                val user = infoElement?.getElementsByTag("a")?.first()?.text()?.trim() ?: ""

                Video(hid = hid, title = title, play = play, thumb = thumb, user = user)
            } catch (e: Exception) {
                null
            }
        }
    }

    /** 从 img 元素提取缩略图 URL，优先从 style 的 background-image 取 */
    private fun extractThumb(img: Element?): String {
        if (img == null) return ""
        val style = img.attr("style") ?: ""
        if (style.contains("background-image")) {
            return style.substringAfter("url(").substringBeforeLast(')')
        }
        val src = img.attr("src") ?: ""
        return src
    }
}
