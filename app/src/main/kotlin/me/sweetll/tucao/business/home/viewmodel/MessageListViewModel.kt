package me.sweetll.tucao.business.home.viewmodel

import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.business.home.MessageListActivity
import me.sweetll.tucao.business.home.model.MessageList
import me.sweetll.tucao.di.service.ApiConfig
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class MessageListViewModel(val activity: MessageListActivity): BaseViewModel() {

    fun loadData() {
        rawApiService.readMessageList()
                .bindToLifecycle(activity)
                .subscribeOn(Schedulers.io())
                .retryWhen(ApiConfig.RetryWithDelay())
                .map {
                    parseMessageList(Jsoup.parse(it.string()))
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    activity.onLoadData(it)
                }, {
                    error ->
                    error.printStackTrace()
                })
    }

    fun parseMessageList(doc: Document): MutableList<MessageList> {
        // 新版网站结构：div.minbox > div.item
        // 每个 item 包含 div.l（头像）和 div.r（消息内容）
        val items = doc.select("div.minbox div.item")
        if (items.isEmpty()) return mutableListOf()

        return items.fold(mutableListOf()) { res, ele ->
            // 头像：div.l a img
            val avatar = ele.selectFirst("div.l img")?.attr("src") ?: ""

            // 消息链接：div.r a（href 包含 messageid）
            val linkA = ele.selectFirst("div.r a[href*=messageid]")
            val href = linkA?.attr("href") ?: ""
            val idMatch = "messageid=(\\d+)".toRegex().find(href)
            val id = idMatch?.groupValues?.get(1) ?: ""

            // 用户名：div.u
            val username = linkA?.selectFirst("div.u")?.text()?.trim() ?: ""

            // 消息内容：div.c
            val message = linkA?.selectFirst("div.c")?.text()?.trim() ?: ""

            // 时间：div.t
            val time = linkA?.selectFirst("div.t")?.text()?.trim() ?: ""

            // 是否已读：旧版用 em.bgblue 标记未读，新版无明确标记，默认已读
            val read = true

            if (id.isNotEmpty()) {
                res.add(MessageList(id, username, avatar, time, message, read))
            }
            res
        }
    }

}