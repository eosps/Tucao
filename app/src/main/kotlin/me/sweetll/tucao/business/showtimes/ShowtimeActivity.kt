package me.sweetll.tucao.business.showtimes

import android.content.Context
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.Toolbar
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.GridLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.listener.OnItemClickListener
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseActivity
import me.sweetll.tucao.business.search.SearchActivity
import me.sweetll.tucao.business.showtimes.adapter.ShowtimeAdapter
import me.sweetll.tucao.business.showtimes.viewmodel.ShowtimeViewModel
import me.sweetll.tucao.databinding.ActivityShowtimeBinding
import me.sweetll.tucao.extension.dp2px
import me.sweetll.tucao.model.raw.ShowtimeSection
import java.util.*

class ShowtimeActivity : BaseActivity() {
    lateinit var binding: ActivityShowtimeBinding
    val viewModel: ShowtimeViewModel by lazy { ShowtimeViewModel(this) }

    val showtimeAdapter = ShowtimeAdapter(null)
    val headerOffsets: MutableList<Int> = mutableListOf()
    var totalScroll = 0
    var currentHeaderView: View? = null

    override fun getToolbar(): Toolbar = binding.toolbar

    override fun getStatusBar(): View = binding.statusBar

    companion object {
        fun intentTo(context: Context) {
            context.startActivity(Intent(context, ShowtimeActivity::class.java))
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_showtime)
        binding.viewModel = viewModel

        binding.indicatorFrame.viewTreeObserver.addOnGlobalLayoutListener( object: ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.indicatorFrame.viewTreeObserver.removeOnGlobalLayoutListener(this)
                // 默认选中"今"标签（第一个子视图）
                val targetView = binding.weekLinear.getChildAt(0)
                moveIndicatorView(targetView)
            }
        })

        setupRecyclerView()
    }

    fun setupRecyclerView() {
        // 使用 GridLayoutManager 替代 StaggeredGridLayoutManager，让 header 跨全宽
        val gridLayoutManager = GridLayoutManager(this, 3)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                // header 占满整行（3列），普通 item 占 1 列
                return if (showtimeAdapter.data.size > position && showtimeAdapter.getItem(position)?.isHeader == true) 3 else 1
            }
        }
        binding.showtimeRecycler.layoutManager = gridLayoutManager
        binding.showtimeRecycler.adapter = showtimeAdapter

        binding.showtimeRecycler.addOnItemTouchListener(object: OnItemClickListener() {
            override fun onSimpleItemClick(helper: BaseQuickAdapter<*, *>, view: View, position: Int) {
                val showtimeSection = helper.getItem(position) as ShowtimeSection
                if (!showtimeSection.isHeader) {
                    SearchActivity.intentTo(this@ShowtimeActivity, showtimeSection.t.title, 24)
                }
            }

        })

        binding.showtimeRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                totalScroll += dy
                val currentHeaderIndex = headerOffsets.indexOfLast {
                    it <= totalScroll
                }
                val targetView = binding.weekLinear.getChildAt(currentHeaderIndex)
                targetView?.let {
                    moveIndicatorView(it)
                }
            }
        })

        binding.swipeRefresh.setColorSchemeResources(R.color.colorPrimary)
    }

    override fun initToolbar() {
        super.initToolbar()
        supportActionBar?.let {
            it.title = "本季新番"
            it.setDisplayHomeAsUpEnabled(true)
        }
    }

    fun setRefreshing(isRefreshing: Boolean) {
        binding.swipeRefresh.isEnabled = isRefreshing
        binding.swipeRefresh.isRefreshing = isRefreshing
    }

    fun loadShowtime(data: MutableList<ShowtimeSection>) {
        showtimeAdapter.setNewData(data)
        headerOffsets.clear()

        var yOffset = 0f
        var itemCount = 0
        data.forEachIndexed {
            _, showtimeSection ->

            if (showtimeSection.isHeader) {
                itemCount = 0
                headerOffsets.add(yOffset.toInt())
                yOffset += 60f.dp2px()
            } else {
                itemCount = (itemCount % 3) + 1
                if (itemCount == 1) {
                    yOffset += 120f.dp2px()
                }
            }
        }

        // 默认滚动到"今天更新"
        showWeek(0)

        binding.indicatorFrame.visibility = View.VISIBLE
    }

    fun moveIndicatorView(targetView: View) {
        if (targetView != currentHeaderView) {
            currentHeaderView = targetView
            binding.indicatorView.animate()
                    .x(targetView.x + targetView.width / 2f - binding.indicatorView.width / 2f)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .setDuration(200)
                    .start()
        }
    }

    fun showWeek(week: Int, view: View? = null) {
        view?.let {
            moveIndicatorView(it)
        }
        if (headerOffsets.isNotEmpty()) {
            binding.showtimeRecycler.smoothScrollBy(0, headerOffsets[week] - totalScroll)
        }
    }

}
