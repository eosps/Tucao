package me.sweetll.tucao.business.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseActivity
import me.sweetll.tucao.business.home.adapter.RecommendListAdapter
import me.sweetll.tucao.business.home.viewmodel.RecommendListViewModel
import me.sweetll.tucao.business.video.VideoActivity
import me.sweetll.tucao.databinding.ActivityRecommendListBinding
import me.sweetll.tucao.model.json.Video

class RecommendListActivity : BaseActivity() {

    lateinit var binding: ActivityRecommendListBinding
    val viewModel = RecommendListViewModel(this)

    val videoAdapter = RecommendListAdapter(null)

    companion object {
        fun intentTo(context: Context) {
            val intent = Intent(context, RecommendListActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun getToolbar(): Toolbar = binding.toolbar

    override fun getStatusBar(): View = binding.statusBar

    override fun initView(savedInstanceState: Bundle?) {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_recommend_list)
        binding.viewModel = viewModel

        binding.swipeRefresh.setColorSchemeResources(R.color.colorPrimary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadData()
        }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = videoAdapter

        // 点击整个 item 跳转播放页
        videoAdapter.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, _, position ->
            val video = videoAdapter.data[position]
            VideoActivity.intentTo(this, video.hid)
        }

        // 启用加载更多
        videoAdapter.setOnLoadMoreListener({
            viewModel.loadMoreData()
        }, binding.recycler)

        // 首次加载
        viewModel.loadData()
    }

    override fun initToolbar() {
        super.initToolbar()
        supportActionBar?.let {
            it.setDisplayShowTitleEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
        }
    }

    fun setData(videos: List<Video>) {
        videoAdapter.setNewData(videos)
        if (videos.isEmpty()) {
            videoAdapter.loadMoreEnd()
        }
    }

    fun addData(videos: List<Video>) {
        videoAdapter.addData(videos)
    }

    fun loadMoreEnd() {
        videoAdapter.loadMoreEnd()
    }

    fun loadMoreComplete() {
        videoAdapter.loadMoreComplete()
    }

    fun loadMoreFail() {
        videoAdapter.loadMoreFail()
    }

    fun setRefreshing(refreshing: Boolean) {
        binding.swipeRefresh.isRefreshing = refreshing
    }
}
