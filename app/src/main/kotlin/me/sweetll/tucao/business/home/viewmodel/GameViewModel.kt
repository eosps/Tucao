package me.sweetll.tucao.business.home.viewmodel

import android.view.View
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.business.channel.ChannelDetailActivity
import me.sweetll.tucao.business.home.fragment.GameFragment
import me.sweetll.tucao.extension.sanitizeHtml
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.model.json.Channel
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.model.raw.Game
import me.sweetll.tucao.util.parseChannelList
import org.jsoup.nodes.Document

class GameViewModel(val fragment: GameFragment): BaseViewModel() {

    fun loadData() {
        fragment.setRefreshing(true)
        // 综合页面 tid=19，包含动画、音乐、游戏等子频道
        rawApiService.list(19)
                .bindToLifecycle(fragment)
                .sanitizeHtml {
                    val recommends = parseRecommends(this)
                    Game(recommends)
                }
                .doAfterTerminate { fragment.setRefreshing(false) }
                .subscribe({
                    game ->
                    fragment.loadGame(game)
                }, {
                    error ->
                    error.printStackTrace()
                    error.message?.toast()
                    fragment.loadError()
                })
    }

    fun parseRecommends(doc: Document): List<Pair<Channel, List<Video>>> {
        // 和影剧一样，综合页面的子频道数据在 #loop_num 容器中
        val listParentNode = doc.getElementById("loop_num")
        return if (listParentNode != null) {
            parseChannelList(listParentNode)
        } else {
            emptyList()
        }
    }

    fun onClickChannel(view: View) {
        ChannelDetailActivity.intentTo(fragment.activity!!, (view.tag as String).toInt())
    }
}
