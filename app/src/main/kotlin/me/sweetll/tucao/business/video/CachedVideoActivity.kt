package me.sweetll.tucao.business.video

import android.content.Context
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.WindowManager
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.utils.OrientationUtils

import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseActivity
import me.sweetll.tucao.model.json.Part
import me.sweetll.tucao.business.video.adapter.StandardVideoAllCallBackAdapter
import me.sweetll.tucao.databinding.ActivityCachedVideoBinding
import me.sweetll.tucao.extension.HistoryHelpers
import me.sweetll.tucao.extension.setUp
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.widget.DanmuVideoPlayer
import java.io.File
import java.util.*

class CachedVideoActivity : BaseActivity(), DanmuVideoPlayer.DanmuPlayerHolder {
    lateinit var binding: ActivityCachedVideoBinding

    lateinit var orientationUtils: OrientationUtils

    var isPlay = false
    var isPause = false

    var firstPlay = true

    lateinit var video: Video
    lateinit var selectedPart: Part

    companion object {
        private val ARG_VIDEO = "video"

        fun intentTo(context: Context, video: Video) {
            val intent = Intent(context, CachedVideoActivity::class.java)
            intent.putExtra(ARG_VIDEO, video)
            context.startActivity(intent)
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_cached_video)
        video = intent.getParcelableExtra(ARG_VIDEO)!!
        selectedPart = video.parts[0]

        HistoryHelpers.loadPlayHistory()
                .flatMap { it.parts }
                .find { it.vid == selectedPart.vid }
                ?.let {
                    selectedPart.hadPlay = true
                    selectedPart.lastPlayPosition = it.lastPlayPosition
                }


        setupPlayer()
        loadPart(selectedPart)

        val danmuFile = File(selectedPart.durls[0].cacheFolderPath, "danmu.xml")
        if (danmuFile.exists()) {
            loadDanmuUri(danmuFile.absolutePath)
        } else {
            "未发现弹幕文件，请更新弹幕".toast()
        }

        binding.player.getStartButton().performClick()

        // v11 的 OrientationUtils 需要传入 Activity 和 Player
        orientationUtils = OrientationUtils(this, binding.player)
        binding.player.setOrientationUtils(orientationUtils)
    }

    fun setupPlayer() {
        binding.player.loadText?.let {
            it.text = it.text.replace("获取视频信息...".toRegex(), "获取视频信息...[完成]")
            it.text = it.text.replace("全舰弹幕装填...".toRegex(), "")
        }

        binding.player.setLockLand(true)
        binding.player.setNeedLockFull(true)
        binding.player.setNeedShowWifiTip(false)

        // ExoPlayer 自动处理硬解，无需手动设置 MediaCodec

        binding.player.setVideoAllCallBack(object: StandardVideoAllCallBackAdapter() {
            override fun onPrepared(url: String?, vararg objects: Any?) {
                super.onPrepared(url, *objects)
                isPlay = true
                if (firstPlay) {
                    firstPlay = false
                    if (selectedPart.lastPlayPosition != 0) {
                        binding.player.showJump(selectedPart.lastPlayPosition)
                    }
                }
            }

        })

        binding.player.getFullscreenButton().visibility = View.GONE
        binding.player.getBackButton().setOnClickListener {
            onBackPressed()
        }
    }

    fun loadPart(part: Part) {
        val durls = part.durls
        durls.isNotEmpty().let {
            binding.player.loadText?.let {
                it.text = it.text.replace("解析视频地址...".toRegex(), "解析视频地址...[完成]")
                binding.player.getStartButton().visibility = View.VISIBLE
            }
            if (durls.size == 1) {
                val url = durls[0].getCacheAbsolutePath()
                binding.player.setUp(url, true, "")
            } else {
                // 多段视频：将每个 Durl 的 url 更新为本地缓存路径（加 file:// 前缀）
                durls.forEach { durl ->
                    if (durl.url.isEmpty() && durl.cacheFolderPath.isNotEmpty()) {
                        durl.url = "file://${durl.cacheFolderPath}/${durl.cacheFileName}"
                    }
                }
                binding.player.setUp(durls, true)
            }
        }
    }

    fun loadDanmuUri(uri: String) {
        binding.player.setUpDanmu(uri)
    }

    override fun onPause() {
        super.onPause()
        binding.player.onVideoPause(isPlay)
        isPause = true
    }

    override fun onResume() {
        super.onResume()
        binding.player.onVideoResume()
        isPause = false
    }

    override fun onDestroy() {
        super.onDestroy()
        GSYVideoManager.releaseAllVideos()
        binding.player.onVideoDestroy()
    }

    override fun onSendDanmu(stime: Float, message: String) {
        // Do nothing
    }

    override fun onSavePlayHistory(position: Int) {
        HistoryHelpers.savePlayHistory(
        video.copy(create = DateFormat.format("yyyy-MM-dd hh:mm:ss", Date()).toString())
                .also {
                    it.parts = video.parts.filter {
                        it.vid == selectedPart.vid
                    }.map {
                        it.lastPlayPosition = position
                        it
                    }.toMutableList()
                }
        )
    }
}
