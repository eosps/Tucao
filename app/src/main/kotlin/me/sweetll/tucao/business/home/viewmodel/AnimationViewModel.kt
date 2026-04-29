package me.sweetll.tucao.business.home.viewmodel

import android.view.View
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import io.reactivex.Observable
import io.reactivex.functions.Function3
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.business.channel.ChannelDetailActivity
import me.sweetll.tucao.business.home.fragment.AnimationFragment
import me.sweetll.tucao.extension.sanitizeHtml
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.model.json.Channel
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.model.raw.Animation
import me.sweetll.tucao.util.parseListVideo
import org.jsoup.nodes.Document

class AnimationViewModel(val fragment: AnimationFragment): BaseViewModel() {

    companion object {
        // 动画板块的子频道：MAD·AMV·GMV(28)、MMD·3D(6)、游戏相关(29)
        private val subChannels = listOf(
                Pair(28, "动画"),
                Pair(6, "音乐"),
                Pair(29, "游戏")
        )
        // 每个子频道显示的视频数
        private const val VIDEOS_PER_CHANNEL = 4
    }

    fun loadData() {
        fragment.setRefreshing(true)

        // 并行加载 3 个子频道的视频
        val obs1 = rawApiService.list(28).sanitizeHtml { parseTopVideos(this, VIDEOS_PER_CHANNEL) }
        val obs2 = rawApiService.list(6).sanitizeHtml { parseTopVideos(this, VIDEOS_PER_CHANNEL) }
        val obs3 = rawApiService.list(29).sanitizeHtml { parseTopVideos(this, VIDEOS_PER_CHANNEL) }

        Observable.zip(obs1, obs2, obs3,
                Function3 { list1: List<Video>, list2: List<Video>, list3: List<Video> ->
                    val result = mutableListOf<Pair<Channel, List<Video>>>()
                    val videos = listOf(list1, list2, list3)
                    subChannels.forEachIndexed { index, pair ->
                        val (tid, name) = pair
                        result.add(Channel(tid, name) to videos[index])
                    }
                    result
                }
        )
                .bindToLifecycle(fragment)
                .doAfterTerminate { fragment.setRefreshing(false) }
                .subscribe({
                    recommends ->
                    fragment.loadAnimation(Animation(recommends))
                }, {
                    error ->
                    error.printStackTrace()
                    error.message?.toast()
                    fragment.loadError()
                })
    }

    /**
     * 从列表页面解析前 N 个视频
     */
    private fun parseTopVideos(doc: Document, count: Int): List<Video> {
        val listElements = doc.getElementsByClass("list")
        if (listElements.isEmpty()) return emptyList()
        val videos = parseListVideo(listElements.first())
        return videos.take(count)
    }

    fun onClickChannel(view: View) {
        ChannelDetailActivity.intentTo(fragment.activity!!, (view.tag as String).toInt())
    }
}
