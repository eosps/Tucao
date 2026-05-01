package me.sweetll.tucao.business.video.viewmodel

import android.annotation.SuppressLint
import androidx.databinding.ObservableField
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.sweetll.tucao.AppApplication
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.model.json.Part
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.business.video.VideoActivity
import me.sweetll.tucao.di.service.ApiConfig
import me.sweetll.tucao.extension.*
import me.sweetll.tucao.model.xml.Durl
import me.sweetll.tucao.rxdownload.entity.DownloadStatus
import java.io.File
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class VideoViewModel(val activity: VideoActivity): BaseViewModel() {
    val video = ObservableField<Video>()

    var playUrlDisposable: Disposable? = null
    var danmuDisposable: Disposable? = null

    var currentPlayerId: String? = null

    /**
     * 用于解析视频 URL 重定向的 HTTP 客户端
     * SharePoint 等服务的下载链接通过 HTTP 重定向到 CDN 直链
     * ExoPlayer 不如 FFmpeg 容错，需要预先解析重定向获取最终 URL
     */
    private val urlResolveClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 解析视频 URL 的 HTTP 重定向链，获取最终直链
     *
     * SharePoint 链接特殊处理：跳过预解析，只修复旧版 URL 格式
     * 原因：OkHttp 预解析会跟随重定向到 Microsoft OAuth2 登录页，
     * 导致 ExoPlayer 收到 HTML 页面而非视频流。
     * 直接传原始链接给 ExoPlayer + 完整浏览器 UA 可能避免触发 OAuth2。
     *
     * 其他 URL：正常解析重定向，但检测到认证页面时回退到原始 URL。
     */
    private fun resolveVideoUrl(url: String): String {
        // 本地文件路径不需要解析
        if (url.startsWith("/") || url.startsWith("file://")) {
            return url
        }

        // SharePoint 链接：修复 URL 格式
        if (url.contains("sharepoint.com")) {
            val fixedUrl = url.replace("_layouts/52/", "_layouts/15/")
            // 有 share= token 的链接是公开分享链接，可以预解析获取直链
            // 缺少 share= 的链接会被截断，由 recoverSharePointUrl() 处理，这里跳过
            if (!fixedUrl.contains("share=")) {
                android.util.Log.w("VideoDebug", "SharePoint URL 缺少 share= token，跳过预解析: $fixedUrl")
                return fixedUrl
            }
            // 预解析：OkHttpClient 正确处理 SharePoint 的 HTTPS 响应，
            // 避免 ExoPlayer 的 DefaultHttpDataSource 出现兼容问题
            return try {
                val request = Request.Builder()
                    .url(fixedUrl)
                    .header("User-Agent", ApiConfig.CHROME_USER_AGENT)
                    .build()
                val response = urlResolveClient.newCall(request).execute()
                val resolvedUrl = response.request.url.toString()
                val contentType = response.header("Content-Type", "")
                val contentLength = response.header("Content-Length", "?")
                response.body?.close()
                response.close()
                android.util.Log.w("VideoDebug", "SharePoint 预解析结果:\n  原始: $fixedUrl\n  解析: $resolvedUrl\n  Content-Type: $contentType\n  Content-Length: $contentLength")
                // 如果解析到了登录页或非视频内容，回退到原始 URL
                if (resolvedUrl.contains("login.") || resolvedUrl.contains("oauth2")
                    || (contentType != null && contentType.isNotEmpty() && !contentType.contains("video/") && !contentType.contains("octet-stream"))) {
                    android.util.Log.w("VideoDebug", "SharePoint 预解析检测到非视频内容，回退原始 URL")
                    fixedUrl
                } else {
                    resolvedUrl
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoDebug", "SharePoint 预解析失败", e)
                fixedUrl
            }
        }

        // 其他 URL：解析 HTTP 重定向获取最终直链
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", ApiConfig.CHROME_USER_AGENT)
                .build()
            val response = urlResolveClient.newCall(request).execute()
            val resolvedUrl = response.request.url.toString()
            val contentType = response.header("Content-Type", "unknown")
            response.body?.close()
            response.close()

            // 如果重定向到了登录/认证页面，回退使用原始 URL
            if (resolvedUrl.contains("login.") || resolvedUrl.contains("oauth2")) {
                url
            } else {
                resolvedUrl
            }
        } catch (e: Exception) {
            url
        }
    }

    constructor(activity: VideoActivity, video: Video) : this(activity) {
        this.video.set(video)
    }

    @SuppressLint("CheckResult")
    fun queryVideo(hid: String) {
        jsonApiService.view(hid)
                .bindToLifecycle(activity)
                .sanitizeJson()
                .subscribe({
                    video ->
                    this.video.set(video)
                    activity.loadVideo(video)
                }, {
                    error ->
                    error.printStackTrace()
                    activity.binding.player.loadText?.let {
                        it.text = it.text.replace("获取视频信息...".toRegex(), "获取视频信息...[失败]")
                    }
                })
    }

    fun queryPlayUrls(hid: String, part: Part) {
        if (playUrlDisposable != null && !playUrlDisposable!!.isDisposed) {
            playUrlDisposable!!.dispose()
        }
        if (danmuDisposable != null && !danmuDisposable!!.isDisposed) {
            danmuDisposable!!.dispose()
        }

        if (part.flag == DownloadStatus.COMPLETED) {
            activity.loadDurls(part.durls)
        } else if (part.file.isNotEmpty()) {
            if ("clicli" !in part.file) {
                if (part.file.contains("sharepoint.com")) {
                    // SharePoint 链接：API 可能把 share= 后面的 token 截断了
                    val fixedUrl = part.file.replace("_layouts/52/", "_layouts/15/")
                    if (!fixedUrl.contains("share=")) {
                        // URL 被截断（缺少 share=TOKEN），从网页 HTML 中恢复完整 URL
                        recoverSharePointUrl(hid, fixedUrl, part.order)
                    } else {
                        activity.loadDurls(mutableListOf(Durl(url = fixedUrl)))
                    }
                } else {
                    // 非 SharePoint 链接，正常解析重定向
                    playUrlDisposable = Observable.fromCallable { resolveVideoUrl(part.file) }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ resolvedUrl ->
                            activity.loadDurls(mutableListOf(Durl(url = resolvedUrl)))
                        }, { error ->
                            error.printStackTrace()
                            activity.loadDurls(mutableListOf(Durl(url = part.file)))
                        })
                }
            } else {
                // 这个视频来自clicli
                playUrlDisposable = jsonApiService.clicli(part.file)
                        .bindToLifecycle(activity)
                        .subscribeOn(Schedulers.io())
                        .flatMap {
                            clicli ->
                            if (clicli.code == 0) {
                                Observable.just(clicli.url)
                            } else {
                                Observable.error(Throwable("请求视频接口出错"))
                            }
                        }
                        .map { url -> resolveVideoUrl(url) }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            url ->
                            activity.loadDurls(mutableListOf(Durl(url=url)))
                        }, {
                            error ->
                            error.printStackTrace()
                            activity.binding.player.loadText?.let {
                                it.text = it.text.replace("解析视频地址...".toRegex(), "解析视频地址...[失败]")
                            }
                        })

            }
        } else {
            playUrlDisposable = xmlApiService.playUrl(part.type, part.vid, System.currentTimeMillis() / 1000)
                    .bindToLifecycle(activity)
                    .subscribeOn(Schedulers.io())
                    .flatMap {
                        response ->
                        if (response.durls.isNotEmpty()) {
                            // 解析每个视频 URL 的 HTTP 重定向
                            response.durls.forEach { durl ->
                                durl.url = resolveVideoUrl(durl.url)
                            }
                            Observable.just(response.durls)
                        } else {
                            Observable.error(Throwable("请求视频接口出错"))
                        }
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        duals ->
                        activity.loadDurls(duals)
                    }, {
                        error ->
                        error.printStackTrace()
                        activity.binding.player.loadText?.let {
                            it.text = it.text.replace("解析视频地址...".toRegex(), "解析视频地址...[失败]")
                        }
                    })
        }

        currentPlayerId = ApiConfig.generatePlayerId(hid, part.order)
        danmuDisposable = rawApiService.danmu(currentPlayerId!!, System.currentTimeMillis() / 1000)
            .bindToLifecycle(activity)
            .subscribeOn(Schedulers.io())
            .map {
                responseBody ->
                val outputFile = File.createTempFile("tucao", ".xml", AppApplication.get().cacheDir)
                val outputStream = FileOutputStream(outputFile)

                outputStream.write(responseBody.bytes())
                outputStream.flush()
                outputStream.close()
                outputFile.absolutePath
            }
            .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    uri ->
                    activity.loadDanmuUri(uri)
                }, {
                    error ->
                    error.printStackTrace()
                    activity.binding.player.loadText?.let {
                        it.text = it.text.replace("全舰弹幕装填...".toRegex(), "全舰弹幕装填...[失败]")
                    }
                })
    }

    fun sendDanmu(stime: Float, message: String) {
        currentPlayerId?.let {
            rawApiService.sendDanmu(it, it, stime, message)
                    .bindToLifecycle(activity)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        // 发送成功
                    }, Throwable::printStackTrace)
        }
    }

    /**
     * 从网页 HTML 中恢复被 API 截断的 SharePoint 分享链接
     * API 返回: ...?share（缺少 =TOKEN）
     * 网页源码格式: <li>type=video&file=URL1|**type=video&file=URL2|**...</li>
     * 按 partOrder 选择对应集数的 URL
     */
    private fun recoverSharePointUrl(hid: String, truncatedUrl: String, partOrder: Int) {
        val baseUrl = ApiConfig.getBaseUrl()
        val playPageUrl = "https://$baseUrl/play/h$hid/"

        playUrlDisposable = rawApiService.playPage(playPageUrl)
                .subscribeOn(Schedulers.io())
                .map { responseBody ->
                    val html = responseBody.string()

                    // 从 <ul id="player_code"> 的第一个 <li> 中提取视频列表
                    val liRegex = Regex("""<ul[^>]*id="player_code"[^>]*>\s*<li>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
                    val liMatch = liRegex.find(html)
                    if (liMatch == null) {
                        return@map truncatedUrl
                    }

                    val liContent = liMatch.groupValues[1]

                    // 按 |** 分隔符拆分各集视频信息
                    val entries = liContent.split("|**")

                    if (partOrder >= entries.size || partOrder < 0) {
                        return@map truncatedUrl
                    }

                    // 从选中的条目中提取 file= 参数值（即视频URL）
                    // 正则排除 | 分隔符，避免 URL 末尾带上多余的 |
                    val entry = entries[partOrder]
                    val fileRegex = Regex("""file=(https?://[^|\s]+)""")
                    val fileMatch = fileRegex.find(entry)
                    if (fileMatch != null) {
                        fileMatch.groupValues[1].replace("_layouts/52/", "_layouts/15/")
                    } else {
                        truncatedUrl
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ url ->
                    activity.loadDurls(mutableListOf(Durl(url = url)))
                }, { error ->
                    error.printStackTrace()
                    activity.loadDurls(mutableListOf(Durl(url = truncatedUrl)))
                })
    }
}
