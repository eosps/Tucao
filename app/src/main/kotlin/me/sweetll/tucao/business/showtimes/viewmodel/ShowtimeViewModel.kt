package me.sweetll.tucao.business.showtimes.viewmodel

import android.view.View
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.business.showtimes.ShowtimeActivity
import me.sweetll.tucao.extension.sanitizeHtml
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.model.raw.ShowtimeSection
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*

class ShowtimeViewModel(val activity: ShowtimeActivity): BaseViewModel() {

    // 星期名：月火水木金土日（Calendar.DAY_OF_WEEK: 2=Mon ... 1=Sun）
    private val weekDayNames = mapOf(
            Calendar.MONDAY to "月曜日",
            Calendar.TUESDAY to "火曜日",
            Calendar.WEDNESDAY to "水曜日",
            Calendar.THURSDAY to "木曜日",
            Calendar.FRIDAY to "金曜日",
            Calendar.SATURDAY to "土曜日",
            Calendar.SUNDAY to "日曜日"
    )

    // 按星期几排序：周一(2) → 周日(1)
    private val weekOrder = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )

    init {
        loadData()
    }

    fun loadData() {
        activity.setRefreshing(true)

        // 从"本周新番"页面获取数据，一个请求包含整周番组
        rawApiService.weekBgm()
                .bindToLifecycle(activity)
                .sanitizeHtml {
                    parseWeekBgm(this)
                }
                .doAfterTerminate { activity.setRefreshing(false) }
                .subscribe({
                    showtime ->
                    activity.loadShowtime(showtime)
                }, {
                    error ->
                    error.printStackTrace()
                    error.message?.toast()
                })
    }

    /**
     * 解析本周新番页面
     * 页面结构：div.list > div.item[date="2026-04-28"]
     * 每个 item 的 date 属性表示更新日期，按日期分组后映射到星期几
     */
    fun parseWeekBgm(doc: Document): MutableList<ShowtimeSection> {
        val items = doc.select("div.list > div.item")

        // 按日期分组
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val groupedByDay = mutableMapOf<Int, MutableList<Video>>()

        for (item in items) {
            try {
                val dateStr = item.attr("date")
                if (dateStr.isEmpty()) continue

                val date = dateFormat.parse(dateStr) ?: continue
                val calendar = Calendar.getInstance()
                calendar.time = date
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

                // 从 a.p.vp 提取封面图
                val linkElement = item.select("a.p.vp").first()
                val thumb = linkElement?.let { extractBackgroundImageUrl(it.attr("style")) } ?: ""

                // 从 a.t 提取标题
                val titleElement = item.select("a.t").first()
                val title = titleElement?.text()?.trim() ?: continue

                // 从 href 提取 hid
                val href = linkElement?.attr("href") ?: ""
                val hidRegex = Regex("/play/(h\\d+)/")
                val hid = hidRegex.find(href)?.groupValues?.get(1) ?: ""

                val video = Video(thumb = thumb, title = title, hid = hid)
                groupedByDay.getOrPut(dayOfWeek) { mutableListOf() }.add(video)
            } catch (e: Exception) {
                // 跳过解析失败的条目
            }
        }

        // 按周一到周日顺序生成结果，最前面加"今天更新"
        val showtime = mutableListOf<ShowtimeSection>()

        // 计算今天星期几
        val today = Calendar.getInstance()
        val todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK)

        // "今天更新"分组
        showtime.add(ShowtimeSection("今天更新"))
        groupedByDay[todayDayOfWeek]?.forEach { video ->
            showtime.add(ShowtimeSection(video))
        }

        // 月火水木金土日
        for (dayOfWeek in weekOrder) {
            val dayName = weekDayNames[dayOfWeek] ?: continue
            showtime.add(ShowtimeSection(dayName))
            groupedByDay[dayOfWeek]?.forEach { video ->
                showtime.add(ShowtimeSection(video))
            }
        }
        return showtime
    }

    /**
     * 从 CSS style 属性中提取 background-image 的 URL
     */
    private fun extractBackgroundImageUrl(style: String): String {
        val regex = Regex("background-image:url\\(([^)]+)\\)")
        return regex.find(style)?.groupValues?.get(1) ?: ""
    }

    fun onClickWeek(view: View) {
        activity.showWeek((view.tag as String).toInt(), view)
    }
}
