package me.sweetll.tucao.business.today

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.listener.OnItemClickListener
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseActivity
import me.sweetll.tucao.business.search.SearchActivity
import me.sweetll.tucao.business.today.adapter.TodayGridAdapter
import me.sweetll.tucao.databinding.ActivityTodayBinding
import me.sweetll.tucao.di.service.RawApiService
import me.sweetll.tucao.extension.sanitizeHtml
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.model.json.Video
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import dagger.android.AndroidInjection

/**
 * "今天更新"完整列表页面
 * 分批加载保证流畅体验
 */
class TodayActivity : BaseActivity() {

    @Inject
    lateinit var rawApiService: RawApiService

    lateinit var binding: ActivityTodayBinding
    val todayGridAdapter = TodayGridAdapter(mutableListOf())

    // 分批加载配置
    private val batchSize = 6
    private val batchDelayMs = 80L
    private var allVideos: List<Video> = emptyList()
    private var loadedCount = 0
    private val handler = Handler(Looper.getMainLooper())

    // 当前日期字符串，用于解析
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val todayStr = dateFormat.format(Date())

    override fun getToolbar(): Toolbar = binding.toolbar

    override fun getStatusBar(): View = binding.statusBar

    companion object {
        fun intentTo(context: Context) {
            val intent = Intent(context, TodayActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_today)

        val gridLayoutManager = GridLayoutManager(this, 3)
        binding.todayRecycler.layoutManager = gridLayoutManager
        binding.todayRecycler.adapter = todayGridAdapter

        // 点击跳转搜索
        binding.todayRecycler.addOnItemTouchListener(object : OnItemClickListener() {
            override fun onSimpleItemClick(adapter: BaseQuickAdapter<*, *>, view: View, position: Int) {
                val video = adapter.getItem(position) as Video
                SearchActivity.intentTo(this@TodayActivity, video.title, 24)
            }
        })

        binding.swipeRefresh.setColorSchemeResources(R.color.colorPrimary)
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }

        loadData()
    }

    override fun initToolbar() {
        super.initToolbar()
        supportActionBar?.let {
            it.title = "今天更新"
            it.setDisplayHomeAsUpEnabled(true)
        }
    }

    fun loadData() {
        if (!binding.swipeRefresh.isRefreshing) {
            binding.swipeRefresh.isRefreshing = true
        }

        // 清空旧数据
        handler.removeCallbacksAndMessages(null)
        todayGridAdapter.setNewData(null)
        loadedCount = 0

        rawApiService.weekBgm()
                .bindToLifecycle(this)
                .sanitizeHtml {
                    parseTodayVideos(this)
                }
                .doAfterTerminate {
                    binding.swipeRefresh.isRefreshing = false
                }
                .subscribe({ videos ->
                    allVideos = videos
                    // 分批加载，先显示第一批
                    loadNextBatch()
                }, { error ->
                    error.message?.toast()
                })
    }

    /**
     * 从 week_bgm 页面解析今天的番组
     */
    private fun parseTodayVideos(doc: Document): List<Video> {
        val items = doc.select("div.list > div.item")
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

    /**
     * 分批添加数据到适配器，每批之间有短暂延迟
     * 利用 RecyclerView 的 item animation 实现流畅的逐批出现效果
     */
    private fun loadNextBatch() {
        if (loadedCount >= allVideos.size) return

        val end = Math.min(loadedCount + batchSize, allVideos.size)
        val batch = allVideos.subList(loadedCount, end)
        todayGridAdapter.addData(batch)
        loadedCount = end

        // 如果还有剩余数据，延迟加载下一批
        if (loadedCount < allVideos.size) {
            handler.postDelayed({ loadNextBatch() }, batchDelayMs)
        }
    }

    private fun extractBackgroundImageUrl(style: String): String {
        val regex = Regex("background-image:url\\(([^)]+)\\)")
        return regex.find(style)?.groupValues?.get(1) ?: ""
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
