package me.sweetll.tucao.business.login.viewmodel

import android.app.Activity
import androidx.databinding.ObservableField
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.business.login.ForgotPasswordActivity
import me.sweetll.tucao.business.login.LoginActivity
import me.sweetll.tucao.business.login.RegisterActivity
import me.sweetll.tucao.di.service.ApiConfig
import me.sweetll.tucao.extension.NonNullObservableField
import me.sweetll.tucao.extension.toast
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class LoginViewModel(val activity: LoginActivity): BaseViewModel() {

    // 用户名（原来是邮箱，现在网站改为用户名登录）
    val username = NonNullObservableField("")
    val password = NonNullObservableField("")

    val container = NonNullObservableField(View.VISIBLE)
    val progress = NonNullObservableField(View.GONE)

    fun dismiss(view: View) {
        activity.setResult(Activity.RESULT_CANCELED)
        activity.supportFinishAfterTransition()
    }

    fun onClickSignUp(view: View) {
        RegisterActivity.intentTo(activity)
        activity.finish()
    }

    fun onClickSignIn(view: View) {
        activity.showLoading()
        // 网站已取消验证码，code 传空字符串
        rawApiService.login_post(username.get(), password.get(), "")
                .bindToLifecycle(activity)
                .subscribeOn(Schedulers.io())
                .retryWhen(ApiConfig.RetryWithDelay())
                .map { parseLoginResult(Jsoup.parse(it.string())) }
                .flatMap {
                    (code, msg) ->
                    if (code == 0) {
                        rawApiService.personal()
                    } else {
                        Observable.error(Error(msg))
                    }
                }
                .map { parsePersonal(Jsoup.parse(it.string())) }
                .observeOn(AndroidSchedulers.mainThread())
                .doAfterTerminate {
                    activity.showLogin()
                }
                .subscribe({
                    user.username = username.get()
                    activity.setResult(Activity.RESULT_OK)
                    activity.supportFinishAfterTransition()
                }, {
                    error ->
                    error.printStackTrace()
                    Snackbar.make(activity.binding.container, error.message ?: "登陆失败", Snackbar.LENGTH_SHORT).show()
                })
    }

    fun onClickForgotPassword(view: View) {
        ForgotPasswordActivity.intentTo(activity)
    }

    fun parseLoginResult(doc: Document): Pair<Int, String>{
        val content = doc.body().text()
        return if ("成功" in content) {
            Pair(0, "")
        } else {
            Pair(1, content)
        }
    }

    /**
     * 解析个人信息页面 HTML，提取用户数据
     * 网站结构已更新，使用 null 安全的选择器避免崩溃
     */
    private fun parsePersonal(doc: Document): Any {
        // 获取 UID：从 /play/u391975/ 格式的链接中提取
        val nameEl = doc.select("a.name").firstOrNull()
        val href = nameEl?.attr("href") ?: ""
        val uidMatch = "/play/u(\\d+)/".toRegex().find(href)
        if (uidMatch != null) {
            user.uid = uidMatch.groupValues[1]
        }

        // 获取用户名：a.name 内文本（跳过 <b> 标签内容，取最后一个文本节点）
        if (nameEl != null) {
            // a.name 结构: <b>在线天数...</b><b>经验63</b>eosps<span>一元八次</span>
            // 用户名是直接的文本节点，在所有 <b> 和 <span> 之外
            val textNodes = nameEl.textNodes()
            val usernameText = textNodes.lastOrNull()?.text()?.trim() ?: ""
            if (usernameText.isNotEmpty()) {
                user.name = usernameText
            }
        }

        // 获取头像地址：a.avatar img 或 header 中的 .user_1 .n img
        val avatarImg = doc.select("a.avatar img").firstOrNull()
            ?: doc.select(".user_1 .n img").firstOrNull()
        if (avatarImg != null) {
            user.avatar = avatarImg.attr("src")
        }

        // 获取签名：a.name span 内文本
        val signatureEl = nameEl?.select("span")?.firstOrNull()
        if (signatureEl != null) {
            user.signature = signatureEl.text()
        }

        // 获取经验值（替代原来的等级）
        try {
            val nameEl2 = doc.select("a.name").firstOrNull()
            if (nameEl2 != null) {
                val bTags = nameEl2.select("b")
                for (b in bTags) {
                    val text = b.text()
                    if (text.startsWith("经验")) {
                        user.level = text.removePrefix("经验").toIntOrNull() ?: 0
                        break
                    }
                }
            }
        } catch (e: Exception) {
            user.level = 0
        }

        // 短消息数量：从 div.t_all 提取未读数
        val tAllEl = doc.selectFirst("div.t_all")
        user.message = tAllEl?.text()?.trim()?.toIntOrNull() ?: 0

        return Object()
    }


}
