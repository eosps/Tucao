package me.sweetll.tucao.business.download.fragment

import androidx.databinding.DataBindingUtil
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import me.sweetll.tucao.BuildConfig
import androidx.recyclerview.widget.LinearLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.listener.OnItemLongClickListener
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseFragment
import me.sweetll.tucao.business.download.DownloadActivity
import me.sweetll.tucao.business.download.adapter.DownloadedVideoAdapter
import me.sweetll.tucao.business.download.event.RefreshDownloadedVideoEvent
import me.sweetll.tucao.model.json.Part
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.databinding.FragmentDownloadedBinding
import me.sweetll.tucao.extension.DownloadHelpers
import me.sweetll.tucao.extension.toast
import org.greenrobot.eventbus.EventBus
import java.io.File
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class DownloadedFragment: BaseFragment(), DownloadActivity.ContextMenuCallback {
    lateinit var binding: FragmentDownloadedBinding

    val videoAdapter: DownloadedVideoAdapter by lazy {
        DownloadedVideoAdapter(activity as DownloadActivity, DownloadHelpers.loadDownloadedVideos())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_downloaded, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
    }

    fun setupRecyclerView() {
        binding.videoRecycler.layoutManager = LinearLayoutManager(activity)
        binding.videoRecycler.adapter = videoAdapter

        binding.videoRecycler.addOnItemTouchListener(object: OnItemLongClickListener() {
            override fun onSimpleItemLongClick(helper: BaseQuickAdapter<*, *>, view: View, position: Int) {
                if ((activity as DownloadActivity).currentActionMode != null) {
                    return
                }
                (activity as DownloadActivity).openContextMenu(this@DownloadedFragment, true)
                // 底部栏高度 45dp，给 RecyclerView 加底部 padding 防止遮挡
                val bottomPadding = (56 * resources.displayMetrics.density).toInt()
                binding.videoRecycler.setPadding(0, 0, 0, bottomPadding)
                videoAdapter.data.forEach {
                    when (it) {
                        is Video -> {
                            it.checkable = true
                            it.checked = false
                            it.subItems.forEach {
                                it.checkable = true
                                it.checked = false
                            }
                        }
                        is Part -> {
                            it.checkable = true
                            it.checked = false
                        }
                    }
                }
                videoAdapter.notifyDataSetChanged()
            }
        })
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onRefreshEvent(event: RefreshDownloadedVideoEvent) {
        videoAdapter.setNewData(DownloadHelpers.loadDownloadedVideos())
    }

    override fun onDestroyContextMenu() {
        // 移除底部 padding
        binding.videoRecycler.setPadding(0, 0, 0, 0)
        videoAdapter.data.forEach {
            when (it) {
                is Video -> {
                    it.checkable = false
                    it.subItems.forEach { it.checkable = false }
                }
                is Part -> it.checkable = false
            }
        }
        videoAdapter.notifyDataSetChanged()
    }

    override fun onClickDelete() {
        DownloadHelpers.cancelDownload(
                videoAdapter.data.flatMap {
                    when (it) {
                        is Video -> it.subItems
                        else -> listOf(it as Part)
                    }
                }.distinctBy(Part::vid).filter(Part::checked)
        )
    }

    override fun onClickUpdate() {
        DownloadHelpers.updateDanmu(
                videoAdapter.data.flatMap {
                    when (it) {
                        is Video -> it.subItems
                        else -> listOf(it as Part)
                    }
                }.distinctBy(Part::vid).filter(Part::checked)
        )
    }

    override fun onClickPickAll(): Boolean {
        if (videoAdapter.data.all {
            when (it) {
                is Video -> it.checked
                is Part -> it.checked
                else -> false
            }
        }) {
            // 取消全选
            videoAdapter.data.forEach {
                when (it) {
                    is Video -> {
                        it.checked = false
                        it.subItems.forEach { it.checked = false }
                    }
                    is Part -> it.checked = false
                }
            }
            videoAdapter.notifyDataSetChanged()
            return false
        } else {
            // 全选
            videoAdapter.data.forEach {
                when (it) {
                    is Video -> {
                        it.checked = true
                        it.subItems.forEach { it.checked = true }
                    }
                    is Part -> it.checked = true
                }
            }
            videoAdapter.notifyDataSetChanged()
            return true
        }
    }

    /**
     * 收集选中的 Part 及其对应的 Video 标题
     */
    private fun collectSelectedParts(): List<Pair<Part, String>> {
        val data = videoAdapter.data
        return data.flatMap { item ->
            when (item) {
                is Video -> item.subItems.filter { it.checked }.map { it to item.title }
                is Part -> if (item.checked) {
                    // 找到所属 Video 获取标题
                    val parentVideo = data.find { v -> (v as Video).subItems.any { it.vid == item.vid } } as? Video
                    listOf(item to (parentVideo?.title ?: ""))
                } else emptyList()
                else -> emptyList()
            }
        }.distinctBy { it.first.vid }
    }

    /**
     * 分享：合并每个选中 Part 为单文件后通过系统分享面板发送
     */
    override fun onClickShare() {
        val selected = collectSelectedParts()
        if (selected.isEmpty()) {
            "请先选择要分享的视频".toast()
            return
        }

        "正在合并视频片段...".toast()

        io.reactivex.Single.fromCallable {
            selected.mapNotNull { (part, title) ->
                DownloadHelpers.mergePartToTempFile(part, title)
            }
        }.subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe({ files ->
                    if (files.isEmpty()) {
                        "未找到可分享的视频文件".toast()
                        return@subscribe
                    }
                    // 通过 FileProvider 分享合并后的文件
                    val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
                    val uris = files.map { FileProvider.getUriForFile(activity!!, authority, it) }
                    val shareIntent = Intent().apply {
                        action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                        type = "video/*"
                        if (uris.size == 1) {
                            putExtra(Intent.EXTRA_STREAM, uris[0])
                        } else {
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "分享视频"))
                }, { error ->
                    error.printStackTrace()
                    "合并视频失败".toast()
                })
    }

    /**
     * 导出：合并每个选中 Part 为单文件后保存到系统 Downloads 目录，带进度显示
     */
    override fun onClickExport() {
        val selected = collectSelectedParts()
        if (selected.isEmpty()) {
            "请先选择要导出的视频".toast()
            return
        }

        // 创建进度对话框
        val progressDialog = android.app.ProgressDialog(activity).apply {
            setTitle("导出视频")
            setMessage("正在准备...")
            setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
            max = selected.size
            progress = 0
            setCancelable(false)
        }
        progressDialog.show()

        io.reactivex.Single.fromCallable {
            val files = mutableListOf<File>()
            selected.forEachIndexed { index, (part, title) ->
                val name = "${title}_${part.title}"
                // 在主线程更新进度
                activity?.runOnUiThread {
                    progressDialog.setMessage("正在导出 (${index + 1}/${selected.size}): $name")
                    progressDialog.progress = index
                }
                DownloadHelpers.exportPartToDownloads(part, title)?.let { files.add(it) }
            }
            activity?.runOnUiThread {
                progressDialog.progress = selected.size
            }
            files
        }.subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe({ files ->
                    progressDialog.dismiss()
                    if (files.isEmpty()) {
                        "未找到可导出的视频文件".toast()
                        return@subscribe
                    }
                    // 通知 MediaScanner 扫描新文件，使其在文件管理器中可见
                    files.forEach { file ->
                        android.media.MediaScannerConnection.scanFile(
                                activity, arrayOf(file.absolutePath), arrayOf("video/mp4"), null)
                    }
                    "已导出 ${files.size} 个视频到 Download 目录".toast()
                }, { error ->
                    progressDialog.dismiss()
                    error.printStackTrace()
                    "导出视频失败".toast()
                })
    }
}