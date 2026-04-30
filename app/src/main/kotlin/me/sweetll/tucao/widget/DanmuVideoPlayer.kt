package me.sweetll.tucao.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import com.google.android.material.tabs.TabLayout
import androidx.viewpager.widget.ViewPager
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever

import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.utils.CommonUtil
import com.shuyu.gsyvideoplayer.utils.OrientationUtils

import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import com.shuyu.gsyvideoplayer.video.base.GSYBaseVideoPlayer
import com.shuyu.gsyvideoplayer.video.base.GSYVideoView
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.loader.IllegalDataException
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.ui.widget.DanmakuView

import me.sweetll.tucao.R
import me.sweetll.tucao.utils.PlayerConfig
import me.sweetll.tucao.base.BaseActivity
import me.sweetll.tucao.business.video.adapter.SettingPagerAdapter
import me.sweetll.tucao.extension.dp2px
import me.sweetll.tucao.extension.formatDanmuOpacityToFloat
import me.sweetll.tucao.extension.formatDanmuSizeToFloat
import me.sweetll.tucao.extension.formatDanmuSpeedToFloat
import me.sweetll.tucao.di.service.ApiConfig

class DanmuVideoPlayer : StandardGSYVideoPlayer {
    var loadText: TextView? = null
    var danmakuContext: DanmakuContext? = null
    var danmuUri: String? = null
    var danmuParser: BaseDanmakuParser? = null

    lateinit var danmakuContainer: FrameLayout
    var danmakuView: DanmakuView? = null

    lateinit var settingLayout: View
    lateinit var switchDanmu: TextView
    lateinit var settingButton: Button
    lateinit var settingTab: TabLayout
    lateinit var settingPager: ViewPager

    lateinit var closeImg: ImageView
    lateinit var sendDanmuText: TextView
    lateinit var sendDanmuLinear: LinearLayout
    lateinit var danmuEdit: EditText
    lateinit var sendDanmuImg: ImageView

    lateinit var jumpLinear: LinearLayout
    lateinit var closeJumpImg: ImageView
    lateinit var jumpTimeText: TextView
    lateinit var jumpText: TextView

    var mLastState = -1
    var needCorrectDanmu = false
    var isShowDanmu = true

    // 进度条预览相关
    private var previewImageView: ImageView? = null
    private var previewFrameLayout: FrameLayout? = null  // PreviewSeekBarLayout 内置的预览框
    private var previewDisposable: Disposable? = null
    // 按时间戳存储预览帧：TextureView 截图（可靠）+ MediaMetadataRetriever（尽力而为）
    private val previewFrames = java.util.TreeMap<Long, Bitmap>()
    private var lastCaptureTimeMs = -10000L  // 上次截图时间，初始负值以立即触发首次截图

    companion object {
        const val TAP = 1
        const val PREVIEW_FRAME_INTERVAL_MS = 10_000L  // MediaMetadataRetriever 每 10 秒提取一帧
        const val PREVIEW_FRAME_MAX = 200               // 最大帧数（防止长视频占用过多内存）
        const val PREVIEW_CAPTURE_INTERVAL = 3000L      // TextureView 播放中截图间隔（毫秒）
        const val PREVIEW_FRAME_WIDTH = 160             // 预览帧宽度（像素）
        const val PREVIEW_FRAME_HEIGHT = 90             // 预览帧高度（像素）

        const val DOUBLE_TAP_TIMEOUT = 250L
        const val DOUBLE_TAP_MIN_TIME = 40L
        const val DOUBLE_TAP_SLOP = 100L
        const val DOUBLE_TAP_SLOP_SQUARE = DOUBLE_TAP_SLOP * DOUBLE_TAP_SLOP
    }

    private var isDoubleTapping = false
    private var isStillDown = false
    private var deferConfirmSingleTap = false
    private var currentDownEvent: MotionEvent? = null
    private var previousUpEvent: MotionEvent? = null

    val gestureHandler = @SuppressLint("HandlerLeak")
    object: Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                TAP -> {
                    if (!isStillDown) {
                        onSingleTapConfirmed()
                    } else {
                        deferConfirmSingleTap = true
                    }
                }
            }
        }
    }

    constructor(context: Context, fullFlag: Boolean?) : super(context, fullFlag)

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun init(context: Context) {
        super.init(context)
        initView()
    }

    fun showDanmu(show: Boolean) {
        isShowDanmu = show
        if (danmakuView != null && danmakuView!!.isPrepared) {
            if (show) {
                switchDanmu.text = "弹幕开"
                danmakuView!!.show()
            } else {
                switchDanmu.text = "弹幕关"
                danmakuView!!.hide()
            }
        }
    }

    fun showSetting() {
        settingLayout.animate()
                .translationX(0f)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        settingLayout.visibility = View.VISIBLE
                    }
                })
                .start()
        cancelDismissControlViewTimer()
    }

    fun hideSetting() {
            settingLayout.animate()
                .translationX((280f).dp2px())
                .setDuration(200)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        settingLayout.visibility = View.INVISIBLE
                    }
                })
                .start()
    }

    fun showJump(position: Int) {
        val minute: Int = position / 1000 / 60
        val seconds: Int = position / 1000 % 60
        jumpTimeText.text = "记忆您上次播放到%d:%02d".format(minute, seconds)
        jumpText.setOnClickListener {
            GSYVideoManager.instance().seekTo(position.toLong())
            hideJump()
        }
        jumpLinear.animate()
                .translationX(0f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        jumpLinear.visibility = View.VISIBLE
                    }
                })
                .start()
    }

    fun hideJump() {
        jumpLinear.animate()
            .translationX((-280f).dp2px())
            .setDuration(400)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    jumpLinear.visibility = View.INVISIBLE
                }
            })
            .start()
    }

    // 重新修改弹幕样式
    fun configDanmuStyle() {
        danmakuContext?.setDanmakuTransparency(PlayerConfig.loadDanmuOpacity().formatDanmuOpacityToFloat())
        danmakuContext?.setScaleTextSize(PlayerConfig.loadDanmuSize().formatDanmuSizeToFloat())
        danmakuContext?.setScrollSpeedFactor(PlayerConfig.loadDanmuSpeed().formatDanmuSpeedToFloat())
    }

    private fun initView() {
        //初始化弹幕控件
        danmakuContainer = findViewById(R.id.danmaku_container)

        jumpLinear = findViewById(R.id.linear_jump)
        closeJumpImg = findViewById(R.id.img_close_jump)
        jumpTimeText = findViewById(R.id.text_jump_time)
        jumpText = findViewById(R.id.text_jump)
        if (!isIfCurrentIsFullscreen) {
            loadText = findViewById(R.id.text_load)
        } else {
            settingLayout = findViewById(R.id.setting_layout)

            settingButton = findViewById(R.id.btn_setting)
            settingButton.visibility = View.VISIBLE
            settingButton.setOnClickListener {
                showSetting()
            }

            settingPager = findViewById(R.id.pager_setting)
            settingPager.adapter = SettingPagerAdapter(this)
            settingPager.offscreenPageLimit = 3

            settingTab = findViewById(R.id.tab_setting)
            settingTab.setupWithViewPager(settingPager)

            // 顶部发送弹幕栏
            sendDanmuText = findViewById(R.id.text_send_danmu)
            sendDanmuLinear = findViewById(R.id.linear_send_danmu)
            danmuEdit = findViewById(R.id.edit_danmu)
            sendDanmuImg = findViewById(R.id.img_send_danmu)
            closeImg = findViewById(R.id.img_close)

            sendDanmuText.setOnClickListener {
                if (sendDanmuLinear.visibility == View.VISIBLE) {
                    sendDanmuLinear.visibility = View.GONE
                } else {
                    danmuEdit.setText("")
                    sendDanmuLinear.visibility = View.VISIBLE
                    danmuEdit.requestFocus()
                    cancelDismissControlViewTimer()
                }
            }

            danmuEdit.setOnEditorActionListener {
                _, _, _ ->
                val danmuContent = danmuEdit.editableText.toString()
                if (danmuContent.isNotEmpty()) {
                    sendDanmu(danmuContent)
                }
                false
            }

            sendDanmuImg.setOnClickListener {
                val danmuContent = danmuEdit.editableText.toString()
                if (danmuContent.isNotEmpty()) {
                    sendDanmu(danmuContent)
                }
            }

            closeImg.setOnClickListener {
                hideAllWidget()
                hideSoftKeyBoard()
            }

            // 进度条拖动预览：使用 PreviewSeekBarLayout 内置的预览框
            // previewFrameLayout 是外层带边框的 FrameLayout（id=previewFrameLayout）
            // preview_layout 是内层放置预览图的 FrameLayout
            previewFrameLayout = findViewById(R.id.previewFrameLayout)
            val innerPreviewLayout: FrameLayout? = findViewById(R.id.preview_layout)
            if (innerPreviewLayout != null && previewFrameLayout != null) {
                previewImageView = ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                innerPreviewLayout.addView(previewImageView)
                Log.d("Preview", "预览框初始化完成 pfl=${previewFrameLayout != null} img=${previewImageView != null}")
            }

        }

        // 左侧跳转栏
        closeJumpImg.setOnClickListener {
            hideJump()
        }

        loadText?.let {
            it.visibility = View.VISIBLE
        }

        switchDanmu = findViewById<TextView>(R.id.switchDanmu)
        switchDanmu.setOnClickListener {
            showDanmu(!isShowDanmu)
        }
    }

    private fun sendDanmu(content: String) {
        (context as DanmuPlayerHolder).onSendDanmu(currentPositionWhenPlaying/1000f, content)

        danmakuView?.let {
            val danmaku = danmakuContext!!.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL)
            danmaku.text = content
            danmaku.padding = 5
            danmaku.priority = 1  // 一定会显示
            danmaku.isLive = false
            danmaku.time = it.currentTime + 1200
            danmaku.textSize = 25f * (danmuParser!!.displayer.density - 0.6f)
            danmaku.textColor = Color.RED
            danmaku.textShadowColor = Color.WHITE
            danmaku.borderColor = Color.GREEN
            it.addDanmaku(danmaku)
        }

        hideAllWidget()
        hideSoftKeyBoard()
    }

    fun setOrientationUtils(orientationUtils: OrientationUtils) {
        mOrientationUtils = orientationUtils
    }

    fun getOrientationUtils() = mOrientationUtils!!

    // ===== 进度条拖动预览 =====
    // GSY 的 GSYVideoControlView 用 setOnSeekBarChangeListener 覆盖所有监听器，
    // 所以 PreviewSeekBarLayout 的监听不生效。在这里覆写 GSY 的回调来驱动预览。

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        super.onStartTrackingTouch(seekBar)
        val pfl = previewFrameLayout
        if (pfl != null && previewFrames.isNotEmpty()) {
            // 移除 PreviewDelegate 添加的 frameView（带背景色的遮挡层），
            // 让 previewImageView 直接可见，不被 circular reveal 动画干扰
            for (i in pfl.childCount - 1 downTo 0) {
                val child = pfl.getChildAt(i)
                if (child !is FrameLayout) {
                    pfl.removeViewAt(i)
                    Log.d("Preview", "移除遮挡层: ${child.javaClass.simpleName}")
                }
            }
            pfl.visibility = View.VISIBLE
            pfl.alpha = 1f
            Log.d("Preview", "onStart frames=${previewFrames.size} vis=${pfl.visibility} w=${pfl.width} h=${pfl.height}")
        }
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        super.onProgressChanged(seekBar, progress, fromUser)
        if (!fromUser) return
        val videoDuration = duration
        if (videoDuration <= 0 || previewFrames.isEmpty()) return

        val targetTimeMs = progress.toLong() * videoDuration / 100
        showNearestPreviewFrame(targetTimeMs)

        // 预览框跟随滑块位置
        val pfl = previewFrameLayout ?: return
        val parentWidth = (pfl.parent as? View)?.width?.toFloat() ?: 0f
        val frameWidth = pfl.width.toFloat()
        if (parentWidth > 0f && frameWidth > 0f) {
            val ratio = progress.toFloat() / 100f
            pfl.translationX = ratio * (parentWidth - frameWidth)
        }
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        super.onStopTrackingTouch(seekBar)
        Log.d("Preview", "onStopTrackingTouch")
        previewFrameLayout?.let {
            it.visibility = View.INVISIBLE
            it.translationX = 0f
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (mIfCurrentIsFullscreen && mLockCurScreen && mNeedLockFull) {
            return super.onTouch(v, event)
        }

        if (v.id == R.id.surface_container) {
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    val hadTapMessage = gestureHandler.hasMessages(TAP)
                    if (hadTapMessage) {
                        gestureHandler.removeMessages(TAP)
                    }

                    if (currentDownEvent != null && previousUpEvent != null && hadTapMessage &&
                            isConsideredDoubleTap(currentDownEvent!!, previousUpEvent!!, event)) {
                        isDoubleTapping = true
                    } else {
                        gestureHandler.sendEmptyMessageDelayed(TAP, DOUBLE_TAP_TIMEOUT)
                    }
                    currentDownEvent?.recycle()
                    currentDownEvent = MotionEvent.obtain(event)

                    isStillDown = true
                    deferConfirmSingleTap = false
                }
                MotionEvent.ACTION_UP -> {
                    isStillDown = false
                    if (isDoubleTapping) {
                        onDoubleTap()
                    } else if (deferConfirmSingleTap) {
                        onSingleTapConfirmed()
                    }
                    previousUpEvent?.recycle()
                    previousUpEvent = MotionEvent.obtain(event)
                    isDoubleTapping = false
                    deferConfirmSingleTap = false
                }
            }
        }

        return super.onTouch(v, event)
    }

    private fun isConsideredDoubleTap(firstDown: MotionEvent, firstUp: MotionEvent,
                                      secondDown: MotionEvent): Boolean {
        val deltaTime = secondDown.eventTime - firstUp.eventTime
        if (deltaTime > DOUBLE_TAP_TIMEOUT || deltaTime < DOUBLE_TAP_MIN_TIME) {
            return false
        }

        val deltaX = firstDown.x - secondDown.x
        val deltaY = firstDown.y - secondDown.y
        return (deltaX * deltaX + deltaY * deltaY < DOUBLE_TAP_SLOP_SQUARE)
    }

    private fun onDoubleTap() {
        // 基类 GestureDetector 已处理双击（播放/暂停）
    }

    private fun onSingleTapConfirmed() {
        // 基类 GSYVideoControlView 的 GestureDetector 已处理单击切换控件
        // 基类 OnClickListener 已启动自动隐藏定时器，此处无需额外操作
    }

    override fun setUp(url: String, cacheWithPlay: Boolean, title: String): Boolean {
        var fixedUrl = if (url.contains("_layouts/52/")) {
            url.replace("_layouts/52/", "_layouts/15/")
        } else {
            url
        }
        // 本地文件路径需要 file:// 前缀，否则 ExoPlayer 会当 HTTP URL 处理报 MalformedURLException
        if (fixedUrl.startsWith("/") && !fixedUrl.startsWith("file://")) {
            fixedUrl = "file://$fixedUrl"
        }
        // 只为网络 URL 设置请求头，本地文件不需要
        if (fixedUrl.startsWith("http")) {
            val baseUrl = ApiConfig.getBaseUrl()
            mapHeadData = mapOf(
                "User-Agent" to ApiConfig.CHROME_USER_AGENT,
                "Referer" to "https://$baseUrl/"
            )
        }
        // 本地文件不走 GSY 缓存路径（CacheDataSource 用 DefaultHttpDataSource，无法处理 file://），
        // 直接用 DefaultDataSource.Factory 播放本地文件
        val useCache = if (fixedUrl.startsWith("file://")) false else cacheWithPlay
        return super.setUp(fixedUrl, useCache, title)
    }

    fun setUpDanmu(uri: String) {
        danmakuView = DanmakuView(context)
        val lp = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        danmakuContainer.removeAllViews()
        danmakuContainer.addView(danmakuView, lp)

        danmuUri = uri
        val overlappingEnablePair = mapOf(
                BaseDanmaku.TYPE_SCROLL_RL to true,
                BaseDanmaku.TYPE_FIX_TOP to true
        )

        danmakuContext = DanmakuContext.create()
        danmakuContext!!.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f)
                .setDuplicateMergingEnabled(false)
                .preventOverlapping(overlappingEnablePair)
                .setScaleTextSize(PlayerConfig.loadDanmuSize().formatDanmuSizeToFloat())
                .setScrollSpeedFactor(PlayerConfig.loadDanmuSpeed().formatDanmuSpeedToFloat())
                .setDanmakuTransparency(PlayerConfig.loadDanmuOpacity().formatDanmuOpacityToFloat())

        if (!mIfCurrentIsFullscreen) {
            val maxLinesPair = mapOf(BaseDanmaku.TYPE_SCROLL_RL to 5)
            danmakuContext!!.setMaximumLines(maxLinesPair)
        }

        danmuParser = createParser(uri)
        danmakuView!!.setCallback(object : DrawHandler.Callback {
            override fun danmakuShown(danmaku: BaseDanmaku?) {

            }

            override fun updateTimer(timer: DanmakuTimer) {

            }

            override fun drawingFinished() {

            }

            override fun prepared() {
                loadText?.let {
                    it.post {
                        it.text = it.text.replace("全舰弹幕装填...".toRegex(), "全舰弹幕装填...[完成]")
                    }
                }
                // Media3 要求在主线程访问播放器位置，post 到主线程启动弹幕
                danmakuView?.post {
                    danmakuView?.start(currentPositionWhenPlaying)
                    if (currentState != GSYVideoView.CURRENT_STATE_PLAYING) {
                        danmakuView?.pause()
                    }
                }
            }

        })
        danmakuView!!.prepare(danmuParser, danmakuContext)
        danmakuView!!.enableDanmakuDrawingCache(true)
        configDanmuStyle()
    }

    private fun createParser(uri: String): BaseDanmakuParser {
        val loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI)

        try {
            loader.load(uri)
        } catch (e: IllegalDataException) {
            e.printStackTrace()
        }
        val parser = TucaoDanmukuParser()
        val dataSource = loader.dataSource
        parser.load(dataSource)
        return parser
    }

    override fun startWindowFullscreen(context: Context?, actionBar: Boolean, statusBar: Boolean): GSYBaseVideoPlayer {
        danmakuView?.hide()

        val player = super.startWindowFullscreen(context, actionBar, statusBar) as DanmuVideoPlayer
        player.speed = speed
        // 在布局前设置 OrientationUtils，避免全屏 ViewPager 初始化时 NPE
        player.setOrientationUtils(mOrientationUtils)

        danmuUri?.let {
            player.showDanmu(isShowDanmu)
            player.setUpDanmu(it)
            player.configDanmuStyle()
        }

        return player
    }

    override fun resolveFullVideoShow(context: Context?, gsyVideoPlayer: GSYBaseVideoPlayer?, frameLayout: FrameLayout?) {
        (gsyVideoPlayer as DanmuVideoPlayer).setOrientationUtils(mOrientationUtils)
        super.resolveFullVideoShow(context, gsyVideoPlayer, frameLayout)
    }

    override fun getLayoutId(): Int {
        if (mIfCurrentIsFullscreen) {
            return R.layout.danmu_video_land
        }
        return R.layout.danmu_video
    }

    override fun updateStartImage() {
        if (mStartButton is ImageView) {
            val imageView = mStartButton as ImageView
            if (mCurrentState == GSYVideoView.CURRENT_STATE_PLAYING) {
                imageView.setImageResource(com.shuyu.gsyvideoplayer.R.drawable.video_click_pause_selector)
            } else if (mCurrentState == GSYVideoView.CURRENT_STATE_ERROR) {
                imageView.setImageResource(com.shuyu.gsyvideoplayer.R.drawable.video_click_play_selector)
            } else {
                imageView.setImageResource(com.shuyu.gsyvideoplayer.R.drawable.video_click_play_selector)
            }
        } else {
            super.updateStartImage()
        }
    }

    override fun resolveNormalVideoShow(oldF: View?, vp: ViewGroup?, gsyVideoPlayer: com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer?) {
        gsyVideoPlayer?.let {
            (it as DanmuVideoPlayer)
            showDanmu(it.isShowDanmu)

            configDanmuStyle()

            speed = it.speed
        }
        super.resolveNormalVideoShow(oldF, vp, gsyVideoPlayer)
    }

    override fun onClickUiToggle(event: MotionEvent?) {
        super.onClickUiToggle(event)
        if (mIfCurrentIsFullscreen && ::sendDanmuLinear.isInitialized) {
            if (mBottomContainer.visibility != View.GONE) {
                if (::settingLayout.isInitialized && settingLayout.visibility == View.VISIBLE) {
                    hideSetting()
                }
                if (sendDanmuLinear.visibility == View.VISIBLE) {
                    sendDanmuLinear.visibility = View.GONE
                }
            }
        }
    }

    override fun hideAllWidget() {
        super.hideAllWidget()

        if (mIfCurrentIsFullscreen && ::sendDanmuLinear.isInitialized) {
            sendDanmuLinear.visibility = View.GONE
            if (mBottomContainer.visibility != View.GONE && ::settingLayout.isInitialized && settingLayout.visibility == View.VISIBLE) {
                hideSetting()
            }
        }
    }

    private fun hideSoftKeyBoard() {
            val imm = context.getSystemService(Service.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(windowToken, 0)
            if (mIfCurrentIsFullscreen) {
                CommonUtil.hideNavKey(context)
            }
    }

    fun onVideoPause(isPlay: Boolean, isComplete: Boolean = false) {
        onVideoPause()
        if (isPlay) {
            // 在这里保存播放进度
            if (isComplete) {
                (context as DanmuPlayerHolder).onSavePlayHistory(0)
            } else {
                (context as DanmuPlayerHolder).onSavePlayHistory(currentPositionWhenPlaying.toInt())
            }
        }
    }

    override fun onVideoPause() {
        super.onVideoPause()
        if (danmakuView != null && danmakuView!!.isPrepared) {
            danmakuView!!.pause()
        }
    }

    override fun onVideoResume() {
        super.onVideoResume()
        if (mCurrentState == GSYVideoView.CURRENT_STATE_PLAYING && danmakuView != null && danmakuView!!.isPrepared && danmakuView!!.isPaused) {
            danmakuView!!.resume()
        }
    }

    fun onVideoDestroy() {
        danmakuView?.release()
        previewDisposable?.dispose()
        previewFrames.forEach { it.value.recycle() }
        previewFrames.clear()
    }

    /**
     * 查找 TreeMap 中距离 targetTimeMs 最近的帧并显示到预览 ImageView
     */
    private fun showNearestPreviewFrame(targetTimeMs: Long) {
        val floor = previewFrames.floorEntry(targetTimeMs)
        val ceiling = previewFrames.ceilingEntry(targetTimeMs)
        val nearest = when {
            floor == null -> ceiling
            ceiling == null -> floor
            else -> if (targetTimeMs - floor.key <= ceiling.key - targetTimeMs) floor else ceiling
        }
        nearest?.value?.let { bitmap ->
            previewImageView?.setImageBitmap(bitmap)
            Log.d("Preview", "showNearest target=$targetTimeMs nearest=${nearest.key} " +
                    "bitmap=${bitmap.width}x${bitmap.height} recycled=${bitmap.isRecycled}")
        }
    }

    /**
     * 从当前播放画面的 TextureView 截图，存入帧缓存
     * 主线程调用，使用小尺寸 (160x90) 保证性能
     */
    private fun captureCurrentFrame(timeMs: Long) {
        try {
            val textureView = findTextureView(this)
            val bitmap = textureView?.getBitmap(PREVIEW_FRAME_WIDTH, PREVIEW_FRAME_HEIGHT)
            if (bitmap != null) {
                previewFrames[timeMs] = bitmap
                Log.d("Preview", "captureCurrentFrame time=$timeMs success total=${previewFrames.size}")
            } else {
                Log.d("Preview", "captureCurrentFrame time=$timeMs bitmap=null textureView=${textureView != null}")
            }
        } catch (e: Exception) {
            Log.d("Preview", "captureCurrentFrame time=$timeMs error=${e.message}")
        }
    }

    /**
     * 递归查找 View 树中指定类型的 View（用于定位 TextureView）
     */
    private fun findTextureView(view: View): android.view.TextureView? {
        if (view is android.view.TextureView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findTextureView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /**
     * 视频准备就绪后的预览帧初始化：
     * 1. 立即截取首帧（TextureView 100% 可靠）
     * 2. 本地文件用 MediaMetadataRetriever 提取全片帧（覆盖未播放区域）
     *    在线视频网站不提供 seek 预览支持，仅靠播放中 TextureView 截图积累
     */
    private fun startPreviewExtraction() {
        // 立即截取当前帧作为初始预览（onPrepared 时视频首帧已渲染）
        captureCurrentFrame(0L)
        lastCaptureTimeMs = 0L
        Log.d("Preview", "startPreviewExtraction url=$mOriginUrl frames=${previewFrames.size}")

        val url = mOriginUrl ?: return
        // 仅对本地文件使用 MediaMetadataRetriever（在线视频不支持 seek 预览）
        if (!url.startsWith("file://") && !url.startsWith("/")) {
            Log.d("Preview", "非本地文件，跳过 MediaMetadataRetriever")
            return
        }

        val videoDuration = duration
        if (videoDuration <= 5000) return

        previewDisposable?.dispose()

        previewDisposable = Observable.fromCallable {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(url.removePrefix("file://"))

                val result = ArrayList<Pair<Long, Bitmap>>()
                // 每 10 秒提取一帧，最多 PREVIEW_FRAME_MAX 帧
                val frameCount = (videoDuration / PREVIEW_FRAME_INTERVAL_MS).toInt().coerceAtMost(PREVIEW_FRAME_MAX) + 1
                for (i in 0 until frameCount) {
                    val timeUs = i * PREVIEW_FRAME_INTERVAL_MS * 1000L
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { original ->
                            val scaled = Bitmap.createScaledBitmap(original, PREVIEW_FRAME_WIDTH, PREVIEW_FRAME_HEIGHT, true)
                            if (scaled !== original) original.recycle()
                            result.add(Pair(timeUs / 1000, scaled))
                        }
                }
                result
            } catch (_: Exception) {
                ArrayList()
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe({ frames ->
            Log.d("Preview", "MediaMetadataRetriever 提取完成 frames=${frames.size} 已有=${previewFrames.size}")
            if (frames.isNotEmpty()) {
                // 合并：保留 TextureView 已有帧，MediaMetadataRetriever 帧只补充空缺位置
                // 避免清空 TextureView 帧导致用户拖动时无帧可用
                frames.forEach { (time, bitmap) ->
                    // 只在没有该时间点附近的帧时才添加
                    val existing = previewFrames.floorEntry(time)
                    if (existing == null || Math.abs(existing.key - time) > PREVIEW_FRAME_INTERVAL_MS / 2) {
                        previewFrames[time] = bitmap
                    } else {
                        bitmap.recycle()
                    }
                }
            }
        }, { e ->
            Log.d("Preview", "MediaMetadataRetriever 失败: ${e.message}")
            // 预生成失败不影响播放，TextureView 截图仍在积累中
        })
    }

    // v11 的 setTextAndProgress 需要 int 参数（播放进度百分比）
    override fun setTextAndProgress(progress: Int) {
        super.setTextAndProgress(progress)
        if (needCorrectDanmu) {
            needCorrectDanmu = false
            seekDanmu()
        }
        // 播放中每隔 N 秒从 TextureView 截图，积累帧缓存
        if (mCurrentState == GSYVideoView.CURRENT_STATE_PLAYING) {
            val currentTime = currentPositionWhenPlaying
            if (currentTime - lastCaptureTimeMs >= PREVIEW_CAPTURE_INTERVAL) {
                lastCaptureTimeMs = currentTime
                captureCurrentFrame(currentTime)
            }
        }
        when (mCurrentState) {
            GSYVideoView.CURRENT_STATE_PLAYING_BUFFERING_START, GSYVideoView.CURRENT_STATE_PAUSE -> {
                if (mLastState == mCurrentState) {
                    pauseDanmu()
                }
            }
            GSYVideoView.CURRENT_STATE_PLAYING  -> {
                if (mLastState != mCurrentState) {
                    resumeDanmu()
                }
            }
        }
        mLastState = mCurrentState
    }

    override fun onPrepared() {
        super.onPrepared()
        Log.d("Preview", "onPrepared called")
        // 非全屏模式下保留状态栏，全屏模式由 startWindowFullscreen 自行处理
        // 初始化预览帧：截取首帧 + 后台尝试全片提取
        startPreviewExtraction()
    }

    fun resumeDanmu() {
        if (danmakuView != null && danmakuView!!.isPrepared) {
            danmakuView!!.resume()
        }
    }

    fun pauseDanmu() {
        if (danmakuView != null && danmakuView!!.isPrepared) {
            danmakuView!!.pause()
        }
    }

    fun seekDanmu() {
        if (danmakuView != null && danmakuView!!.isPrepared) {
            danmakuView!!.seekTo(currentPositionWhenPlaying)
        }
    }

    override fun onSeekComplete() {
        super.onSeekComplete()
        needCorrectDanmu = true
    }

    interface DanmuPlayerHolder {
        fun onSendDanmu(stime: Float, message: String)
        fun onSavePlayHistory(position: Int)
    }
}
