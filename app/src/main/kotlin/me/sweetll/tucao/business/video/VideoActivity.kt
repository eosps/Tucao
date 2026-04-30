package me.sweetll.tucao.business.video

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.SharedElementCallback
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.databinding.DataBindingUtil
import android.os.Build
import android.os.Bundle
import androidx.core.view.ViewCompat
import android.text.format.DateFormat
import android.transition.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.Toolbar
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.appbar.AppBarLayout
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.utils.OrientationUtils
import com.shuyu.gsyvideoplayer.video.base.GSYVideoView
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseActivity
import me.sweetll.tucao.model.json.Part
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.business.video.adapter.StandardVideoAllCallBackAdapter
import me.sweetll.tucao.business.video.adapter.VideoPagerAdapter
import me.sweetll.tucao.business.video.viewmodel.VideoViewModel
import me.sweetll.tucao.databinding.ActivityVideoBinding
import me.sweetll.tucao.extension.*
import me.sweetll.tucao.model.xml.Durl
import me.sweetll.tucao.rxdownload.entity.DownloadStatus
import me.sweetll.tucao.widget.DanmuVideoPlayer
import java.util.*

class VideoActivity : BaseActivity(), DanmuVideoPlayer.DanmuPlayerHolder {
    lateinit var viewModel: VideoViewModel
    lateinit var binding: ActivityVideoBinding

    lateinit var orientationUtils: OrientationUtils

    lateinit var videoPagerAdapter: VideoPagerAdapter

    var isPlay = false
    var isPause = false

    var transitionIn = true

    lateinit var video: Video
    lateinit var selectedPart: Part
    var firstPlay = true

    override fun getToolbar(): Toolbar = binding.toolbar
    override fun getStatusBar(): View = binding.statusBar

    companion object {
        private val ARG_VIDEO = "video"
        private val ARG_HID = "hid"
        private val ARG_COVER = "cover"

        fun intentTo(context: Context, video: Video) {
            val intent = Intent(context, VideoActivity::class.java)
            intent.putExtra(ARG_VIDEO, video)
            context.startActivity(intent)
        }

        fun intentTo(context: Context, video: Video, cover: String, bundle: Bundle?) {
            val intent = Intent(context, VideoActivity::class.java)
            intent.putExtra(ARG_VIDEO, video)
            intent.putExtra(ARG_COVER, cover)
            context.startActivity(intent, bundle)
        }

        fun intentTo(context: Context, hid: String) {
            val intent = Intent(context, VideoActivity::class.java)
            intent.putExtra(ARG_HID, hid)
            context.startActivity(intent)
        }

        fun intentTo(context: Context, hid: String, cover: String, bundle: Bundle?) {
            val intent = Intent(context, VideoActivity::class.java)
            intent.putExtra(ARG_HID, hid)
            intent.putExtra(ARG_COVER, cover)
            context.startActivity(intent, bundle)
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_video)
        val hid = intent.getStringExtra(ARG_HID)
        val cover = intent.getStringExtra(ARG_COVER)

        // 默认按 16:9 比例设置播放器高度，视频加载后会按实际比例自适应
        setPlayerHeightByRatio(16f / 9f)

        videoPagerAdapter = VideoPagerAdapter(supportFragmentManager)
        binding.viewPager.adapter = videoPagerAdapter
        binding.viewPager.offscreenPageLimit = 3
        binding.tab.setupWithViewPager(binding.viewPager)

        if (hid != null) {
            viewModel = VideoViewModel(this)
            viewModel.queryVideo(hid)
        } else {
            val video: Video = intent.getParcelableExtra(ARG_VIDEO)!!
            viewModel = VideoViewModel(this, video)
            loadVideo(video)
        }

        if (!cover.isNullOrEmpty()) {
            val thumbImg = ImageView(this)
            thumbImg.scaleType = ImageView.ScaleType.CENTER_CROP
            ViewCompat.setTransitionName(thumbImg, "cover");
            binding.player.setThumbImageView(thumbImg)

            ViewCompat.setTransitionName(binding.mainLinear, "bg")

            initTransition()
            supportPostponeEnterTransition()
            thumbImg.load(this, cover) {
                supportStartPostponedEnterTransition()
            }
        } else {
            // 5.0以下加载
            binding.player.visibility = View.VISIBLE
            binding.tab.alpha = 1f
            binding.viewPager.alpha = 1f
        }

        binding.viewModel = viewModel

        // v11 的 OrientationUtils 需要传入 Activity 和 Player
        orientationUtils = OrientationUtils(this, binding.player)
        binding.player.setOrientationUtils(orientationUtils)

        binding.playBtn.setOnClickListener {
            binding.appBar.setExpanded(true)
        }
        binding.appBar.addOnOffsetChangedListener(object: AppBarLayout.OnOffsetChangedListener {
            val EXPANDED = 1 shl 0
            val COLLAPSED = 1 shl 1
            val IDLE = 1 shl 2

            var currentState = IDLE

            override fun onOffsetChanged(appBarLayout: AppBarLayout, i: Int) {
                if (i == 0) {
                    if (currentState != EXPANDED) {

                    }
                    currentState = EXPANDED
                } else if (Math.abs(i) >= appBarLayout.totalScrollRange) {
                    if (currentState != COLLAPSED) {
                        binding.playBtn.visibility = View.VISIBLE
                    }
                    currentState = COLLAPSED
                } else {
                    if (currentState != IDLE) {
                        binding.playBtn.visibility = View.GONE
                    }
                    currentState = IDLE
                }
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun initTransition() {
        val changeBounds = ChangeBounds()
        changeBounds.pathMotion = ArcMotion()

        window.sharedElementReturnTransition = null
        window.sharedElementExitTransition = changeBounds

        // FIXME: Not work
        val sharedEnterTransition = TransitionSet()
        sharedEnterTransition.ordering = TransitionSet.ORDERING_TOGETHER
        sharedEnterTransition.addTransition(ChangeBounds())
        sharedEnterTransition.addTransition(ChangeTransform())
        sharedEnterTransition.addTransition(ChangeClipBounds())
        sharedEnterTransition.addTransition(ChangeImageTransform())
        sharedEnterTransition.interpolator = FastOutSlowInInterpolator()
        window.sharedElementEnterTransition = sharedEnterTransition

        val slideUp = Slide(Gravity.TOP)
        slideUp.addTarget(binding.appBar)
        val slideDown = Slide(Gravity.BOTTOM)
        slideDown.addTarget(binding.mainLinear)
        val slideAll = TransitionSet()
        slideAll.addTransition(slideUp)
        slideAll.addTransition(slideDown)
        slideAll.ordering = TransitionSet.ORDERING_TOGETHER

        window.returnTransition = slideAll

        window.sharedElementEnterTransition.addListener(object: Transition.TransitionListener {
            override fun onTransitionEnd(transition: Transition?) {
                val slideBottomAnimator = ObjectAnimator.ofFloat(binding.viewPager, "translationY", -50f.dp2px(), 0f)
                val fadeIn1Animator = ObjectAnimator.ofFloat(binding.viewPager, "alpha", 0f, 1f)
                val fadeIn2Animator = ObjectAnimator.ofFloat(binding.tab, "alpha", 0f, 1f)
                fadeIn2Animator.startDelay = 150
                val enterAnimatorSet = AnimatorSet()
                enterAnimatorSet.addListener(object: AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        binding.tab.alpha = 0f
                    }
                })

                enterAnimatorSet.playTogether(slideBottomAnimator, fadeIn1Animator, fadeIn2Animator)
                enterAnimatorSet.start()
            }

            override fun onTransitionResume(transition: Transition?) {

            }

            override fun onTransitionPause(transition: Transition?) {

            }

            override fun onTransitionCancel(transition: Transition?) {

            }

            override fun onTransitionStart(transition: Transition?) {

            }

        })

        setEnterSharedElementCallback(object: SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>?, sharedElements: MutableMap<String, View>?) {
                if (transitionIn) {
                    transitionIn = false
                } else {
                    sharedElements?.clear()
                }
            }
        })
    }

    fun loadVideo(video: Video) {
        this.video = video
        setupPlayer()
        videoPagerAdapter.bindVideo(video)
    }

    fun setupPlayer() {
        // 是否可以滑动界面改变进度，声音
        binding.player.setIsTouchWiget(true)
        //关闭自动旋转
        binding.player.setLockLand(false)
        binding.player.setNeedLockFull(true)
        binding.player.getFullscreenButton().setOnClickListener {
            //直接横屏
            orientationUtils.resolveByClick()
        }

        // ExoPlayer 自动处理硬解，无需手动设置 MediaCodec

        binding.player.setSpeed(1f)

        binding.player.setVideoAllCallBack(object: StandardVideoAllCallBackAdapter() {
            override fun onPrepared(url: String?, vararg objects: Any?) {
                super.onPrepared(url, *objects)
                binding.player.loadText?.let {
                    it.visibility = View.GONE
                }
                // 视频加载完毕，根据实际宽高比自适应播放器高度
                val videoWidth = GSYVideoManager.instance().videoWidth
                val videoHeight = GSYVideoManager.instance().videoHeight
                if (videoWidth > 0 && videoHeight > 0) {
                    setPlayerHeightByRatio(videoWidth.toFloat() / videoHeight.toFloat())
                }
            }

            override fun onClickStartIcon(url: String?, vararg objects: Any?) {
                super.onClickStartIcon(url, *objects)
                isPlay = true
                if (firstPlay) {
                    firstPlay = false
                    if (selectedPart.lastPlayPosition != 0) {
                        binding.player.showJump(selectedPart.lastPlayPosition)
                    }
                }
            }

            // 播放完了
            override fun onAutoComplete(url: String?, vararg objects: Any?) {
                super.onAutoComplete(url, *objects)
                isPlay = false
                binding.player.onVideoPause(true, true)
            }

        })
    }

    fun loadDurls(durls: MutableList<Durl>?) {
        durls?.isNotEmpty().let {
            binding.player.loadText?.let {
                it.text = it.text.replace("解析视频地址...".toRegex(), "解析视频地址...[完成]")
                binding.player.getStartButton().visibility = View.VISIBLE
            }
            if (durls!!.size == 1) {
                val url = if (selectedPart.flag == DownloadStatus.COMPLETED) durls[0].getCacheAbsolutePath() else durls[0].url
                binding.player.setUp(url, true, "")
            } else {
                // 多段视频：已缓存时将 durl.url 更新为本地文件路径
                if (selectedPart.flag == DownloadStatus.COMPLETED) {
                    durls.forEach { durl ->
                        if (durl.url.isEmpty() && durl.cacheFolderPath.isNotEmpty()) {
                            durl.url = "file://${durl.cacheFolderPath}/${durl.cacheFileName}"
                        }
                    }
                }
                binding.player.setUp(durls, selectedPart.flag == DownloadStatus.COMPLETED)
            }
            firstPlay = true
        }
    }

    fun loadDanmuUri(uri: String) {
        binding.player.setUpDanmu(uri)
    }

    fun selectPart(selectedPart: Part) {
        isPlay = false
        this.selectedPart = selectedPart
        binding.player.onVideoPause()
        binding.player.loadText?.let {
            binding.player.getStartButton().visibility = View.GONE
            it.visibility = View.VISIBLE
            it.text = "播放器初始化...[完成]\n获取视频信息...[完成]\n解析视频地址...\n全舰弹幕装填..."
        }
        if (selectedPart.vid.isNotEmpty()) {
            viewModel.queryPlayUrls(video.hid, selectedPart)
        } else {
            "所选视频已失效".toast()
        }
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

    override fun onBackPressed() {
        orientationUtils.backToProtVideo()
        if (GSYVideoManager.backFromWindowFull(this)) {
            return
        }
        super.onBackPressed()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (!binding.player.isIfCurrentIsFullscreen) {
                binding.player.startWindowFullscreen(this, true, true)
            }
        }
    }

    override fun onSendDanmu(stime: Float, message: String) {
        viewModel.sendDanmu(stime, message)
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

    /**
     * 根据视频宽高比自适应播放器容器高度
     * - 默认 16:9，加载后按实际比例调整
     * - 上限屏幕高度 45%（防止 4:3 等高视频挤压下方内容）
     * - 下限屏幕高度 25%（防止超宽视频播放区过扁）
     */
    private fun setPlayerHeightByRatio(widthToHeightRatio: Float) {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        // 按视频实际宽高比计算高度
        var playerHeight = (screenWidth / widthToHeightRatio).toInt()
        // 设置上下限保护下方内容区域
        val maxHeight = screenHeight * 45 / 100
        val minHeight = screenHeight * 25 / 100
        playerHeight = playerHeight.coerceIn(minHeight, maxHeight)

        binding.player.layoutParams.height = playerHeight
        binding.playerFrame.layoutParams.height = playerHeight
    }
}
