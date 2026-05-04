package me.sweetll.tucao.business.rank.fragment

import android.content.Context
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.listener.OnItemClickListener
import com.google.android.material.tabs.TabLayout
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import dagger.android.support.AndroidSupportInjection
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseFragment
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.business.rank.adapter.RankVideoAdapter
import me.sweetll.tucao.business.video.VideoActivity
import me.sweetll.tucao.databinding.FragmentRankDetailBinding
import me.sweetll.tucao.di.service.RawApiService
import me.sweetll.tucao.extension.sanitizeHtml
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.util.parseListVideo
import org.jsoup.nodes.Document
import javax.inject.Inject

class RankDetailFragment : BaseFragment() {
    lateinit var binding: FragmentRankDetailBinding
    var tid = 0

    val rankVideoAdapter = RankVideoAdapter(null)

    @Inject
    lateinit var rawApiService: RawApiService

    // 按时间段存储排行榜数据，key 为 "今天"/"本周"/"本月"/"今年"
    private val periodData = LinkedHashMap<String, List<Video>>()

    companion object {
        private val ARG_TID = "tid"

        fun newInstance(tid: Int) : RankDetailFragment {
            val fragment = RankDetailFragment()
            val args = Bundle()
            args.putInt(ARG_TID, tid)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tid = arguments!!.getInt(ARG_TID, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_rank_detail, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 视频列表点击跳转播放页，传 hid 让 VideoActivity 从 API 获取完整数据
        binding.rankVideoRecycler.addOnItemTouchListener(object : OnItemClickListener() {
            override fun onSimpleItemClick(helper: BaseQuickAdapter<*, *>, view: View, position: Int) {
                val video: Video = helper.getItem(position) as Video
                VideoActivity.intentTo(activity!!, video.hid)
            }
        })
        binding.rankVideoRecycler.layoutManager = LinearLayoutManager(activity)
        binding.rankVideoRecycler.adapter = rankVideoAdapter
        binding.swipeRefresh.setColorSchemeResources(R.color.colorPrimary)
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }

        // 时间段 Tab 切换：直接切换 adapter 数据，无需重新请求
        binding.timeTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val period = tab.text?.toString() ?: return
                rankVideoAdapter.setNewData(periodData[period] ?: emptyList())
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        loadData()
    }

    fun loadData() {
        if (!binding.swipeRefresh.isRefreshing) {
            binding.swipeRefresh.isRefreshing = true
        }
        // 一次请求获取全部 4 个时间段的排行榜数据
        rawApiService.rankHtml(tid)
                .bindToLifecycle(this)
                .sanitizeHtml {
                    parseRankVideos(this)
                }
                .doAfterTerminate { binding.swipeRefresh.isRefreshing = false }
                .subscribe({ data ->
                    periodData.clear()
                    periodData.putAll(data)
                    // 更新时间段 Tab
                    setupTimeTabs()
                    // 默认显示第一个时间段的数据
                    if (periodData.isNotEmpty()) {
                        val firstKey = periodData.keys.first()
                        rankVideoAdapter.setNewData(periodData[firstKey] ?: emptyList())
                    }
                }, { error ->
                    error.message?.toast()
                })
    }

    /**
     * 根据解析到的时间段数据创建 Tab
     * 只在数据变化时重建，避免重复添加
     */
    private fun setupTimeTabs() {
        binding.timeTab.removeAllTabs()
        for (key in periodData.keys) {
            binding.timeTab.addTab(binding.timeTab.newTab().setText(key))
        }
    }

    /**
     * 从排行榜 HTML 页面解析全部 4 个时间段的数据
     * 页面结构：div.time_item > div.time_title + div.list > div.item
     */
    fun parseRankVideos(doc: Document): Map<String, List<Video>> {
        val result = LinkedHashMap<String, List<Video>>()
        val timeItems = doc.getElementsByClass("time_item")
        for (timeItem in timeItems) {
            val title = timeItem.getElementsByClass("time_title").first()?.text() ?: continue
            val list = timeItem.getElementsByClass("list").first() ?: continue
            result[title] = parseListVideo(list)
        }
        return result
    }
}
