package me.sweetll.tucao.business.login.viewmodel

import androidx.databinding.ObservableField
import android.os.Handler
import android.os.Message
import android.view.View
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.Observables
import io.reactivex.schedulers.Schedulers
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.business.home.event.RefreshPersonalEvent
import me.sweetll.tucao.business.login.RegisterActivity
import me.sweetll.tucao.di.service.ApiConfig
import me.sweetll.tucao.extension.NonNullObservableField
import me.sweetll.tucao.extension.Variable
import me.sweetll.tucao.extension.toast
import org.greenrobot.eventbus.EventBus
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.lang.ref.WeakReference

class RegisterViewModel(val activity: RegisterActivity): BaseViewModel() {

    val account = NonNullObservableField("")
    val newPassword = NonNullObservableField("")

    val accountEnabled = NonNullObservableField(true)
    val newPasswordEnabled = NonNullObservableField(true)

    val accountError = NonNullObservableField("")
    val newError = NonNullObservableField("")

    var hasError: Boolean = false

    val finishRequest = Variable(false) // 标记请求是否完成
    val finishDelay = Variable(false)   // 标记延时是否完成
    var success = false
    var failMsg = ""

    companion object {

        const val MESSAGE_TRANSITION = 1
        const val TRANSITION_DELAY = 1000L

        class TransitionHandler(vm: RegisterViewModel): Handler() {

            private val vmRef = WeakReference<RegisterViewModel>(vm)

            override fun handleMessage(msg: Message) {
                if (msg.what == MESSAGE_TRANSITION) {
                    vmRef.get()?.let {
                        it.finishDelay.value = true
                    }
                } else {
                    super.handleMessage(msg)
                }
            }
        }
    }

    val handler = TransitionHandler(this)

    /**
     * 判断字符串是否包含中文字符
     */
    private fun hasChinese(s: String): Boolean {
        return s.any { it.code > 0x4E00 && it.code < 0x9FFF }
    }

    /**
     * 生成随机字母+数字组合（用于中文用户名的邮箱前缀）
     */
    private fun randomAlphaNum(len: Int = 8): String {
        val chars = ('a'..'z') + ('0'..'9')
        return (1..len).map { chars.random() }.joinToString("")
    }

    fun onClickCreate(view: View) {
        hasError = false
        accountError.set("")
        newError.set("")

        // 验证帐号（2-20位）
        if (account.get().length < 2 || account.get().length > 20) {
            hasError = true
            accountError.set("帐号应在2-20位之间")
        }

        // 验证密码（6-20位）
        if (newPassword.get().length < 6 || newPassword.get().length > 20) {
            hasError = true
            newError.set("密码应在6-20位之间")
        }

        if (hasError) return

        // 自动填充：与 web 注册页逻辑一致
        val nickname = account.get()
        val pwdconfirm = newPassword.get()
        val email = if (hasChinese(account.get())) {
            randomAlphaNum() + "@tucao.fun"
        } else {
            account.get() + "@tucao.fun"
        }

        accountEnabled.set(false)
        newPasswordEnabled.set(false)
        activity.startRegister()

        Observables.combineLatest(finishRequest.stream, finishDelay.stream) {
            a, b -> a && b
        }.distinctUntilChanged()
                .subscribe {
                    if (success) {
                        registerSuccess()
                    } else {
                        registerFailed(failMsg)
                    }
                }

        handler.sendMessageDelayed(handler.obtainMessage(MESSAGE_TRANSITION), TRANSITION_DELAY)

        rawApiService.checkUsername(account.get())
                .bindToLifecycle(activity)
                .subscribeOn(Schedulers.io())
                .retryWhen(ApiConfig.RetryWithDelay())
                .map {
                    response ->
                    parseCheckResult(Jsoup.parse(response.string()))
                }
                .flatMap {
                    (c, _) ->
                    if (c == 0) {
                        rawApiService.checkNickname(nickname)
                                .retryWhen(ApiConfig.RetryWithDelay())
                    } else {
                        throw Error("帐号已存在")
                    }
                }
                .map {
                    response ->
                    parseCheckResult(Jsoup.parse(response.string()))
                }
                .flatMap {
                    (c, _) ->
                    if (c == 0) {
                        rawApiService.checkEmail(email)
                                .retryWhen(ApiConfig.RetryWithDelay())
                    } else {
                        throw Error("昵称已存在")
                    }
                }
                .map {
                    response ->
                    parseCheckResult(Jsoup.parse(response.string()))
                }
                .flatMap {
                    (c, _) ->
                    if (c == 0) {
                        rawApiService.register(account.get(), nickname, email, newPassword.get(), pwdconfirm)
                                .retryWhen(ApiConfig.RetryWithDelay())
                    } else {
                        throw Error("邮箱已存在")
                    }
                }
                .map {
                    response ->
                    parseCreateResult(Jsoup.parse(response.string()))
                }
                .map {
                    (code, msg) ->
                    if (code == 0) {
                        Object()
                    } else {
                        throw Error(msg)
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    user.username = email
                    user.name = nickname
                    user.avatar = ""
                    user.level = 1
                    user.signature = ""
                    EventBus.getDefault().post(RefreshPersonalEvent())

                    success = true
                    finishRequest.value = true
                }, {
                    error ->
                    error.printStackTrace()

                    success = false
                    failMsg = error.message ?: "注册失败"
                    finishRequest.value = false
                })

    }


    private fun registerSuccess() {
        activity.registerSuccess()
    }

    private fun registerFailed(msg: String) {
        accountEnabled.set(true)
        newPasswordEnabled.set(true)
        activity.registerFailed(msg)
    }

    private fun parseCheckResult(doc: Document): Pair<Int, String> {
        val result = doc.body().text()
        return if ("1" == result) {
            Pair(0, "")
        } else {
            Pair(1, result)
        }
    }

    private fun parseCreateResult(doc: Document):Pair<Int, String>  {
        val result = doc.body().text()
        return if ("成功" in result) {
            Pair(0, "")
        } else {
            Pair(1, result)
        }
    }

}
