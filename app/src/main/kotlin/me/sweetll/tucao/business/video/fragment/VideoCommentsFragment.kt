package me.sweetll.tucao.business.video.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import android.transition.ArcMotion
import android.view.LayoutInflater
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.jakewharton.rxbinding2.widget.RxTextView
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import dagger.android.AndroidInjection
import dagger.android.support.AndroidSupportInjection
import me.sweetll.tucao.AppApplication
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseFragment
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.business.home.event.RefreshPersonalEvent
import me.sweetll.tucao.business.login.LoginActivity
import me.sweetll.tucao.business.video.ReplyActivity
import me.sweetll.tucao.business.video.adapter.CommentAdapter
import me.sweetll.tucao.business.video.model.Comment
import me.sweetll.tucao.databinding.FragmentVideoCommentsBinding
import me.sweetll.tucao.di.service.RawApiService
import me.sweetll.tucao.extension.sanitizeHtml
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.model.other.User
import me.sweetll.tucao.transition.FabTransform
import me.sweetll.tucao.widget.HorizontalDividerBuilder
import org.greenrobot.eventbus.EventBus
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class VideoCommentsFragment: BaseFragment() {
    lateinit var binding: FragmentVideoCommentsBinding
    lateinit var video: Video

    val commentAdapter = CommentAdapter(null)

    var commentId = ""

    var page = 1
    val pageSize = 20
    var maxPage = 0

    var canInit = 0

    @Inject
    lateinit var user: User

    @Inject
    lateinit var rawApiService: RawApiService

    companion object {
        const val REQUEST_LOGIN = 1
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_video_comments, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        canInit = canInit or 1
        checkInit()
    }

    fun bindVideo(video: Video) {
        this.video = video
        commentId = "content_${video.typeid}-${video.hid}-1"
        canInit = canInit or 2
        checkInit()
    }

    private fun checkInit() {
        if (canInit != 3) {
            return
        }

        commentAdapter.setOnLoadMoreListener ({
            loadMoreData()
        }, binding.commentRecycler)

        binding.commentRecycler.layoutManager = LinearLayoutManager(context)
        binding.commentRecycler.adapter = commentAdapter
        binding.commentRecycler.addItemDecoration(
                HorizontalDividerBuilder.newInstance(context!!)
                        .setDivider(R.drawable.divider_small)
                        .build()
        )

        commentAdapter.setOnItemClickListener{
            _, view, position ->
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(activity!!,
                androidx.core.util.Pair.create(view, "transition_background"),
                androidx.core.util.Pair.create(view, "transition_comment"))
            val comment = commentAdapter.getItem(position)!!
            ReplyActivity.intentTo(activity!!, commentId, comment, options.toBundle())
        }
        commentAdapter.setOnItemChildClickListener {
            adapter, view, position ->
            if (view.id == R.id.linear_thumb_up) {
                val comment = commentAdapter.getItem(position)!!
                if (!comment.support) {
                    comment.support = true
                    comment.thumbUp += 1
                    adapter.notifyItemChanged(position)
                    rawApiService.support(commentId, comment.id)
                            .sanitizeHtml {
                                Object()
                            }
                            .subscribe({
                                // Ignored
                            }, {
                                error ->
                                error.printStackTrace()
                            })
                }
            }
        }
        (binding.commentRecycler.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        binding.clickToLoadImg.setOnClickListener {
            binding.clickToLoadImg.visibility = View.GONE
            binding.swipeRefresh.visibility = View.VISIBLE
            binding.commentFab.show()
            loadData()
        }
        binding.swipeRefresh.setColorSchemeResources(R.color.colorPrimary)
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }

        binding.commentFab.setOnClickListener {
            if (user.isValid()) {
                startFabTransform()
            } else {
                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        activity!!, binding.commentFab, "transition_login"
                ).toBundle()
                val intent = Intent(activity, LoginActivity::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    FabTransform.addExtras(intent, ContextCompat.getColor(activity!!, R.color.colorPrimary), R.drawable.ic_comment_white)
                }
                startActivityForResult(intent, REQUEST_LOGIN, options)
            }
        }

        RxTextView.textChanges(binding.commentEdit)
                .map { text -> text.isNotEmpty() }
                .distinctUntilChanged()
                .subscribe {
                    enable ->
                    binding.sendCommentBtn.isEnabled = enable
                }

        binding.sendCommentBtn.setOnClickListener {
            binding.commentEdit.isEnabled = false
            binding.sendCommentBtn.isEnabled = false
            binding.sendCommentBtn.text = "发射中"
            val commentInfo = binding.commentEdit.text.toString()
            val lastFloor: Int = commentAdapter.data.getOrNull(0)?.lch?.replace("[\\D]".toRegex(), "")?.toInt() ?: 0
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val currentDateTime = sdf.format(Date())
            commentAdapter.addData(0, Comment(user.avatar, "lv${user.level}", user.name, 0, "${lastFloor + 1}楼", currentDateTime, commentInfo, "", 0, false))
            binding.commentRecycler.smoothScrollToPosition(0)
            rawApiService.sendComment(commentId, commentInfo)
                    .bindToLifecycle(this)
                    .sanitizeHtml {
                        parseSendCommentResult(this)
                    }
                    .map {
                        (code, msg) ->
                        if (code == 0) {
                            Object()
                        } else {
                            throw Error(msg)
                        }
                    }
                    .doAfterTerminate {
                        binding.commentEdit.isEnabled = true
                        binding.sendCommentBtn.isEnabled = true
                        binding.sendCommentBtn.text = "发射"
                    }
                    .subscribe({
                        // 成功
                        binding.commentEdit.setText("")
                        commentAdapter.data[0].hasSend = true
                        commentAdapter.notifyItemChanged(0)
                    }, {
                        error ->
                        // 失败
                        commentAdapter.remove(0)
                        error.printStackTrace()
                        "发送失败，请检查网络".toast()
                    })
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_LOGIN && Activity.RESULT_OK == resultCode) {
            EventBus.getDefault().post(RefreshPersonalEvent())
        }
    }

    fun startFabTransform() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            binding.commentFab.visibility = View.GONE
            binding.commentContainer.visibility = View.VISIBLE

            val startBounds = Rect(binding.commentFab.left, binding.commentFab.top, binding.commentFab.right, binding.commentFab.bottom)
            val endBounds = Rect(binding.commentContainer.left, binding.commentContainer.top, binding.commentContainer.right, binding.commentContainer.bottom)

            val fabColor = ColorDrawable(ContextCompat.getColor(activity!!, R.color.pink_300))
            fabColor.setBounds(0, 0, endBounds.width(), endBounds.height())
            binding.commentContainer.overlay.add(fabColor)

            val circularReveal = ViewAnimationUtils.createCircularReveal(
                    binding.commentContainer, binding.commentContainer.width / 2, binding.commentContainer.height / 2,
                    binding.commentFab.width / 2f, binding.commentContainer.width / 2f)
            val pathMotion = ArcMotion()
            circularReveal.interpolator = FastOutSlowInInterpolator()
            circularReveal.duration = 240

            val translate = ObjectAnimator.ofFloat(binding.commentContainer, View.TRANSLATION_X, View.TRANSLATION_Y,
                    pathMotion.getPath((startBounds.centerX() - endBounds.centerX()).toFloat(), (startBounds.centerY() - endBounds.centerY()).toFloat(), 0f, 0f))
            translate.interpolator = LinearOutSlowInInterpolator()
            translate.duration = 240

            val colorFade = ObjectAnimator.ofInt(fabColor, "alpha", 0)
            colorFade.duration = 120
            colorFade.interpolator = FastOutSlowInInterpolator()

            val transition = AnimatorSet()
            transition.duration = 240
            transition.playTogether(circularReveal, translate, colorFade)
            transition.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.commentContainer.overlay.clear()
                }
            })

            transition.start()
        } else {
            binding.commentFab.hide()
            binding.commentContainer.visibility = View.VISIBLE
        }
    }

    fun loadData() {
        if (!binding.swipeRefresh.isRefreshing) {
            binding.swipeRefresh.isRefreshing = true
        }
        page = 1
        rawApiService.comment(commentId, page)
                .bindToLifecycle(this)
                .sanitizeHtml {
                    val comments = parseComments(this)
                    maxPage = parseMaxPage(this)
                    comments
                }
                .doAfterTerminate { binding.swipeRefresh.isRefreshing = false }
                .subscribe({
                    comments ->
                    page++
                    commentAdapter.setNewData(comments)
                    if (comments.isEmpty()) {
                        val emptyView = LayoutInflater.from(context).inflate(R.layout.layout_empty_comment, null)
                        commentAdapter.emptyView = emptyView
                    }
                    if (page > maxPage) {
                        commentAdapter.loadMoreEnd()
                    }
                }, {
                    error ->
                    error.printStackTrace()
                    error.message?.toast()
                })
    }

    fun loadMoreData() {
        rawApiService.comment(commentId, page)
            .bindToLifecycle(this)
            .sanitizeHtml {
                val comments = parseComments(this)
                comments
            }
            .subscribe({
                comments ->
                page++
                commentAdapter.addData(comments)
                if (page <= maxPage) {
                    commentAdapter.loadMoreComplete()
                } else {
                    commentAdapter.loadMoreEnd()
                }
            }, {
                error ->
                error.printStackTrace()
                error.message?.toast()
            })
    }

    private fun parseSendCommentResult(doc: Document): Pair<Int, String> {
        val result = doc.body().text()
        if ("成功" in result) {
            return Pair(0, "")
        } else {
            return Pair(1, result)
        }
    }

    /**
     * 解析评论列表
     * 新版 HTML 结构: div.clist > div.item（每个 item 包含 div.left + div.right）
     * 第一个 item 可能是广告（包含外部链接），需要跳过
     */
    private fun parseComments(doc: Document): List<Comment> {
        val comments = mutableListOf<Comment>()

        // 选择所有评论项
        val items = doc.select("div.clist > div.item")
        for (item in items) {
            // 跳过广告：广告项包含指向外部商城的链接
            val adLink = item.select("a[href*='tmall.com'], a[href*='equity']").first()
            if (adLink != null) continue

            // 头像：div.left > a > img
            val avatar = item.select("div.left img").first()?.attr("src") ?: ""

            // 等级：从 groupid 的 background-image URL 中提取数字
            // 格式: background-image:url(/uploadfile/2023/0909/X.png)  其中 X 是等级
            val groupidStyle = item.select("div.left .groupid").first()?.attr("style") ?: ""
            val levelMatch = Regex("""/(\d+)\.png""").find(groupidStyle)
            val level = levelMatch?.groupValues?.get(1) ?: "1"

            // 昵称：div.cu > a 的文字（排除非链接的 span，如广告项）
            val nickname = item.select("div.cu > a").firstOrNull()?.text() ?: ""

            // 评论内容
            val info = item.select("div.cc").first()?.text() ?: ""

            // 从 ct 区域提取信息，格式: "回复 赞 2026-04-27 16:53:23 · 20楼"
            val ctText = item.select("div.ct").first()?.text() ?: ""

            // 楼层：从 ct 文本中提取 "X楼"
            val lchMatch = Regex("""(\d+楼)""").find(ctText)
            val lch = lchMatch?.groupValues?.get(1) ?: ""

            // 时间：从 ct 文本中提取日期时间格式
            val timeMatch = Regex("""(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})""").find(ctText)
            val time = timeMatch?.groupValues?.get(1) ?: ""

            // 点赞数：从 support_XXX 的 <u> 标签提取
            val supportElement = item.select("u[id^=support_]").first()
            val thumbUp = supportElement?.text()?.toIntOrNull() ?: 0

            // 评论 ID：从 support 链接的 onclick 中提取，或从 <u> 的 id 属性
            // id 格式: support_XXXXX
            val commentId = supportElement?.attr("id")?.removePrefix("support_") ?: ""

            // 回复数
            val replyNum = item.select("div.replys").firstOrNull()?.attr("replys")?.toIntOrNull() ?: 0

            val comment = Comment(avatar, level, nickname, thumbUp, lch, time, info, commentId, replyNum)
            comments.add(comment)
        }

        return comments
    }

    /**
     * 解析最大页数
     * 新版评论系统不再使用 div.pages 分页，所有评论在同一页加载
     * 从标题栏 "评论N条" 中提取总数来估算页数
     */
    private fun parseMaxPage(doc: Document): Int {
        // 新版没有分页控件，所有评论在一页显示
        val pages = doc.select("div.pages").first()
        if (pages != null) {
            // 旧版分页逻辑（向后兼容）
            val a = pages.children()
            if (a.size >= 2) {
                return a[a.size - 2].text().toIntOrNull() ?: 1
            }
        }

        // 新版：从标题提取评论总数，按每页数量计算页数
        // 标题格式: "评论53条"
        val titleText = doc.select("div.title .t").first()?.text() ?: ""
        val totalMatch = Regex("""评论(\d+)条""").find(titleText)
        val total = totalMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (total == 0) return 1

        // 每页约 20 条评论（排除广告）
        return (total + pageSize - 1) / pageSize
    }
}
