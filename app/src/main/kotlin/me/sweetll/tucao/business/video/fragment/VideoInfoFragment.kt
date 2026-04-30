package me.sweetll.tucao.business.video.fragment

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.listener.OnItemClickListener
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseFragment
import me.sweetll.tucao.business.video.VideoActivity
import me.sweetll.tucao.business.video.adapter.PartAdapter
import me.sweetll.tucao.business.video.viewmodel.VideoInfoViewModel
import me.sweetll.tucao.databinding.FragmentVideoInfoBinding
import me.sweetll.tucao.extension.DownloadHelpers
import me.sweetll.tucao.extension.HistoryHelpers
import me.sweetll.tucao.extension.observe
import me.sweetll.tucao.model.json.Part
import me.sweetll.tucao.model.json.Video


class VideoInfoFragment: BaseFragment() {
    lateinit var binding: FragmentVideoInfoBinding
    lateinit var viewModel: VideoInfoViewModel
    lateinit var video: Video

    lateinit var parts: MutableList<Part>
    lateinit var selectedPart: Part

    lateinit var partAdapter: PartAdapter

    var canInit = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_video_info, container, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            binding.starImg.setImageResource(R.drawable.asl_fab_heart_21)
        } else {
            binding.starImg.setImageResource(R.drawable.asl_fab_heart)
        }
        viewModel = VideoInfoViewModel(this)
        binding.viewModel = viewModel
        binding.user = viewModel.user
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        canInit = canInit or 1
        checkInit()
        viewModel.video.observe(lifecycle) {
            binding.tvPlayCount.text = "播放：${it.play}"
            binding.tvDanmuCount.text = "弹幕：${it.mukio}"
        }
        viewModel.isStar.observe(lifecycle) {
            binding.tvStar.text = if (it) "已收藏" else "收藏"
        }
    }

    fun bindVideo(video: Video) {
        this.video = video
        canInit = canInit or 2
        checkInit()
    }

    private fun checkInit() {
        if (canInit != 3) {
            return
        }
        viewModel.bindResult(video)
        video.parts.forEachIndexed {
            index, part ->
            part.order = index
            // 标记直传
            if (part.durls.isEmpty() && part.file.isNotEmpty()) {
                part.vid = "${video.hid}${part.order}"
            }
        }

        // 按 hid 查找当前视频的下载记录，避免跨视频误匹配
        val downloadVideo = DownloadHelpers.loadDownloadVideos()
                .find { it.hid == video.hid }
        val downloadParts = downloadVideo?.parts ?: emptyList()
        val videoHistory = HistoryHelpers.loadPlayHistory()
                .find { it.hid == video.hid }

        parts = video.parts.map { part ->
            // 优先按 vid 匹配（正常下载流程 vid 一致）
            // 其次按 order 匹配（文件扫描恢复的缓存 vid 格式为 "${hid}_$order"，与 API 不同）
            val downloadPart = downloadParts.find { it.vid == part.vid }
                    ?: downloadParts.find { it.order == part.order }
            if (downloadPart != null) {
                // 将缓存状态合并到 API 返回的 part 上（保留 API 的标题等字段）
                part.flag = downloadPart.flag
                part.durls = downloadPart.durls
                part.downloadSize = downloadPart.downloadSize
                part.totalSize = downloadPart.totalSize
            }
            part
        }.map {
            it.checked = false
            // 加载历史播放进度
            if (videoHistory != null) {
                val historyVideo = videoHistory.parts.find { v -> v.vid == it.vid }
                if (historyVideo != null) {
                    it.hadPlay = true
                    it.lastPlayPosition = historyVideo.lastPlayPosition
                } else {
                    it.hadPlay = false
                    it.lastPlayPosition = 0
                }
            }
            it
        }.toMutableList()
        parts[0].checked = true
        parts[0].hadPlay = true
        selectedPart = parts[0]

        partAdapter = PartAdapter(parts)
        (activity as VideoActivity).selectPart(selectedPart)

        setupRecyclerView()
    }

    fun setupRecyclerView() {
        binding.partRecycler.addOnItemTouchListener(object : OnItemClickListener() {
            override fun onSimpleItemClick(helper: BaseQuickAdapter<*, *>, view: View, position: Int) {
                selectedPart = helper.getItem(position) as Part
                if (!selectedPart.checked) {
                    partAdapter.data.forEach { it.checked = false }
                    selectedPart.hadPlay = true
                    selectedPart.checked = true
                    partAdapter.notifyDataSetChanged()

                    (activity as VideoActivity).selectPart(selectedPart)
                }
            }
        })
        binding.partRecycler.layoutManager = StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL)
        binding.partRecycler.adapter = partAdapter
    }
}