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
        binding.rankVideoRecycler.addOnItemTouchListener(object : OnItemClickListener() {
            override fun onSimpleItemClick(helper: BaseQuickAdapter<*, *>, view: View, position: Int) {
                val video: Video = helper.getItem(position) as Video
                VideoActivity.intentTo(activity!!, video)
            }
        })
        binding.rankVideoRecycler.layoutManager = LinearLayoutManager(activity)
        binding.rankVideoRecycler.adapter = rankVideoAdapter
        binding.swipeRefresh.setColorSchemeResources(R.color.colorPrimary)
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }

        loadData()
    }

    fun loadData() {
        if (!binding.swipeRefresh.isRefreshing) {
            binding.swipeRefresh.isRefreshing = true
        }
        // 从 HTML 排行榜页面解析视频列表
        rawApiService.rankHtml(tid)
                .bindToLifecycle(this)
                .sanitizeHtml {
                    parseRankVideos(this)
                }
                .doAfterTerminate { binding.swipeRefresh.isRefreshing = false }
                .subscribe({ data ->
                    rankVideoAdapter.setNewData(data)
                }, { error ->
                    error.message?.toast()
                })
    }

    /**
     * 从排行榜 HTML 页面解析视频列表
     * 页面结构：div.list > div.item（与首页列表一致）
     */
    fun parseRankVideos(doc: Document): List<Video> {
        val listElements = doc.getElementsByClass("list")
        if (listElements.isEmpty()) return emptyList()
        // 排行榜页面通常有一个 list 容器包含所有 item
        return parseListVideo(listElements.last() ?: listElements.first())
    }
}
