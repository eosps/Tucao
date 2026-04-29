package me.sweetll.tucao.business.home.viewmodel

import androidx.databinding.ObservableField
import android.view.View
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.business.home.MessageDetailActivity
import me.sweetll.tucao.business.home.model.MessageDetail
import me.sweetll.tucao.di.service.ApiConfig
import me.sweetll.tucao.extension.NonNullObservableField
import me.sweetll.tucao.extension.toast
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*

class MessageDetailViewModel(val activity: MessageDetailActivity, val id: String, val _username: String, val _avatar: String): BaseViewModel() {

    val message = NonNullObservableField<String>("")

    fun loadData() {
        rawApiService.readMessageDetail(id)
                .bindToLifecycle(activity)
                .subscribeOn(Schedulers.io())
                .retryWhen(ApiConfig.RetryWithDelay())
                .map {
                    parseMessageDetail(Jsoup.parse(it.string()))
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    activity.onLoadData(it)
                }, {
                    error ->
                    error.printStackTrace()
                })
    }

    fun parseMessageDetail(doc: Document): MutableList<MessageDetail> {
        // 新版网站结构：div.mread > div.item（排除 div.mform 回复表单）
        val items = doc.select("div.mread > div.item")
        val res = mutableListOf<MessageDetail>()

        // 当前用户的 uid，用于区分左右消息
        val myUid = user.uid

        for (item in items) {
            // 获取发送者 uid：div.l a href="/play/u{uid}/"
            val senderLink = item.selectFirst("div.l a")
            val senderHref = senderLink?.attr("href") ?: ""
            val uidMatch = "/play/u(\\d+)/".toRegex().find(senderHref)
            val senderUid = uidMatch?.groupValues?.get(1) ?: ""

            // 判断消息方向：发送者 uid 与当前用户 uid 一致则为右侧（自己发的）
            val type = if (senderUid == myUid) MessageDetail.TYPE_RIGHT else MessageDetail.TYPE_LEFT

            // 消息内容：div.r div.c
            val message = item.selectFirst("div.r div.c")?.text()?.trim() ?: ""

            // 时间：div.r div.t
            val time = item.selectFirst("div.r div.t")?.text()?.trim() ?: ""

            // 头像：根据方向选择
            val avatar = if (type == MessageDetail.TYPE_RIGHT) user.avatar else _avatar

            res.add(MessageDetail(avatar, message, time, type))
        }

        return res
    }

    fun onClickSendMessage(view: View) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        activity.addMessage(MessageDetail(user.avatar, message.get(), sdf.format(Date()), MessageDetail.TYPE_RIGHT))
        rawApiService.replyMessage(message.get(), id, user.name)
                .bindToLifecycle(activity)
                .doOnNext {
                    message.set("")
                }
                .subscribeOn(Schedulers.io())
                .retryWhen(ApiConfig.RetryWithDelay())
                .map { parseSendResult(Jsoup.parse(it.string())) }
                .flatMap {
                    (code, msg) ->
                    if (code == 0) {
                        Observable.just(Object())
                    } else {
                        Observable.error(Error(msg))
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    //
                }, {
                    error ->
                    error.printStackTrace()
                    error.localizedMessage.toast()
                })
    }

    fun parseSendResult(doc: Document): Pair<Int, String> {
        val content = doc.body().text()
        return if ("成功" in content) {
            Pair(0, "")
        } else {
            Pair(1, content)
        }
    }
}