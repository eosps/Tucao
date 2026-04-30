package me.sweetll.tucao.extension

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Environment.DIRECTORY_DOWNLOADS
import android.provider.Settings
import com.chad.library.adapter.base.entity.MultiItemEntity
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import me.sweetll.tucao.AppApplication
import me.sweetll.tucao.business.download.event.RefreshDownloadingVideoEvent
import me.sweetll.tucao.model.json.Part
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.di.service.XmlApiService
import me.sweetll.tucao.di.service.RawApiService
import me.sweetll.tucao.model.xml.Durl
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject
import me.sweetll.tucao.business.download.event.RefreshDownloadedVideoEvent
import java.io.File
import android.preference.PreferenceManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import me.sweetll.tucao.di.service.ApiConfig
import me.sweetll.tucao.rxdownload.RxDownload
import me.sweetll.tucao.rxdownload.entity.DownloadBean
import me.sweetll.tucao.rxdownload.entity.DownloadMission
import me.sweetll.tucao.rxdownload.entity.DownloadStatus
import okhttp3.ResponseBody
import java.io.FileOutputStream

object DownloadHelpers {
    private val DOWNLOAD_FILE_NAME = "download"

    private val KEY_S_DOWNLOAD_VIDEO = "download_video"

    // metadata.json 文件名，保存在下载目录根目录
    private val METADATA_FILE_NAME = "metadata.json"

    // 分享临时文件生存周期：5 天（毫秒）
    private val SHARE_FILE_TTL_MS = 5L * 24 * 60 * 60 * 1000

    private val defaultPath = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS).path + "/me.sweetll.tucao"
    // 延迟初始化：只有真正执行下载/暂停/取消操作时才启动 DownloadService
    private val rxDownload: RxDownload by lazy { RxDownload.getInstance(AppApplication.get()) }

    val serviceInstance = ServiceInstance()

    /**
     * 获取分享临时文件目录
     */
    fun getShareTempDir(): File {
        return File(AppApplication.get().cacheDir, "share")
    }

    /**
     * 清理过期的分享临时文件（超过 TTL 的自动删除）
     * 在 App 启动时调用
     * @return 清理的文件数量
     */
    fun cleanExpiredShareFiles(): Int {
        val shareDir = getShareTempDir()
        if (!shareDir.exists()) return 0
        val deadline = System.currentTimeMillis() - SHARE_FILE_TTL_MS
        var count = 0
        shareDir.listFiles()?.forEach { file ->
            if (file.lastModified() < deadline) {
                if (file.delete()) count++
            }
        }
        return count
    }

    /**
     * 清理所有分享临时文件（手动清理时调用）
     * @return 清理的文件数量
     */
    fun cleanAllShareTempFiles(): Int {
        val shareDir = getShareTempDir()
        if (!shareDir.exists()) return 0
        var count = 0
        shareDir.listFiles()?.forEach { file ->
            if (file.delete()) count++
        }
        return count
    }

    /**
     * 获取分享临时文件占用空间信息
     * @return (文件数量, 总大小字节)
     */
    fun getShareTempFilesInfo(): Pair<Int, Long> {
        val shareDir = getShareTempDir()
        if (!shareDir.exists()) return Pair(0, 0L)
        val files = shareDir.listFiles() ?: return Pair(0, 0L)
        return Pair(files.size, files.sumOf { it.length() })
    }

    private val adapter by lazy {
        val moshi = Moshi.Builder()
                .build()
        val type = Types.newParameterizedType(MutableList::class.java, Video::class.java)
        moshi.adapter<MutableList<Video>>(type)
    }

    fun getDownloadFolder(): File {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(AppApplication.get())
        val downloadPath = sharedPref.getString("download_path", defaultPath)
        val downloadFolder = File(downloadPath)
        if (!downloadFolder.exists()) {
            downloadFolder.mkdirs()
        }
        return downloadFolder
    }

    /**
     * 检查是否有存储访问权限（兼容 Android 10/11+）
     */
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Android 10 通过 requestLegacyExternalStorage 处理
        }
    }

    /**
     * 引导用户授予存储权限（Android 11+ 需要"所有文件访问权限"）
     * @return true 表示已引导到设置页，false 表示不需要引导
     */
    fun requestStoragePermissionIfNeeded(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:me.sweetll.tucao"))
            activity.startActivity(intent)
            return true
        }
        return false
    }

    fun loadDownloadVideos(): MutableList<Video> {
        val sp = DOWNLOAD_FILE_NAME.getSharedPreference()
        val jsonString = sp.getString(KEY_S_DOWNLOAD_VIDEO, "[]")
        return adapter.fromJson(jsonString)!!
    }

    fun loadDownloadingVideos(): MutableList<MultiItemEntity> = loadDownloadVideos()
            .filter {
                video ->
                video.subItems.any {
                    it.flag != DownloadStatus.COMPLETED
                }
            }
            .map {
                video ->
                video.subItems.removeAll { it.flag == DownloadStatus.COMPLETED }
                video
            }
            .toMutableList()

    fun loadDownloadedVideos(): MutableList<MultiItemEntity> = loadDownloadVideos()
            .filter {
                video ->
                video.subItems.any {
                    it.flag == DownloadStatus.COMPLETED
                }
            }
            .map {
                video ->
                video.subItems.removeAll { it.flag != DownloadStatus.COMPLETED }
                video.totalSize = video.subItems.sumByLong(Part::totalSize)
                video.downloadSize = video.totalSize
                video
            }
            .toMutableList()

    fun saveDownloadVideo(video: Video) {
        val videos = loadDownloadVideos()

        val existVideo = videos.find { it.hid == video.hid }
        if (existVideo != null) {
            existVideo.subItems.addAll(video.parts)
            existVideo.subItems.sortBy(Part::order)
        } else {
            videos.add(0, video)
        }

        val jsonString = adapter.toJson(videos)
        val sp = DOWNLOAD_FILE_NAME.getSharedPreference()
        sp.edit {
            putString(KEY_S_DOWNLOAD_VIDEO, jsonString)
        }

        // 同步保存 metadata.json 到下载目录
        saveMetadataToFile(videos)

        EventBus.getDefault().post(RefreshDownloadingVideoEvent())
    }

    // 保存已下载的视频
    fun saveDownloadPart(part: Part) {
        val videos = loadDownloadVideos()
        videos.flatMap {
            it.parts
        }.find { it.vid == part.vid}?.let {
            it.durls = part.durls
            it.flag = part.flag
            it.downloadSize = part.downloadSize
            it.totalSize = part.totalSize
        }

        val jsonString = adapter.toJson(videos)
        val sp = DOWNLOAD_FILE_NAME.getSharedPreference()
        sp.edit {
            putString(KEY_S_DOWNLOAD_VIDEO, jsonString)
        }

        // 同步保存 metadata.json 到下载目录
        saveMetadataToFile(videos)

        EventBus.getDefault().post(RefreshDownloadingVideoEvent())
        EventBus.getDefault().post(RefreshDownloadedVideoEvent())
    }

    /**
     * 将完整元数据保存到下载目录根目录的 metadata.json
     * 确保即使 app 重装也能从本地恢复所有缓存信息
     */
    private fun saveMetadataToFile(videos: MutableList<Video>) {
        try {
            val downloadDir = getDownloadFolder()
            val metadataFile = File(downloadDir, METADATA_FILE_NAME)
            metadataFile.writeText(adapter.toJson(videos))
        } catch (e: Exception) {
            // 保存失败不影响主流程
            e.printStackTrace()
        }
    }

    // 开始下载
    fun startDownload(activity: Activity, video: Video) {
        // Android 11+ 检查所有文件访问权限
        if (requestStoragePermissionIfNeeded(activity)) {
            "请授予文件访问权限后重试".toast()
            return
        }

        RxPermissions(activity)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .doOnNext {
                    granted ->
                    if (!granted) {
                        throw RuntimeException("请给予写存储卡权限以供离线缓存使用")
                    }
                    "已开始下载".toast()
                }
                .observeOn(Schedulers.io())
                .doOnNext {
                    // 下载预览图到本地
                    downloadThumb(video)
                }
                .observeOn(Schedulers.computation())
                .flatMap {
                    Observable.fromIterable(video.subItems)
                }
                .doOnComplete {
                    saveDownloadVideo(video)
                }
                .subscribe({
                    part ->
                    download(video, part)
                }, {
                    error ->
                    error.printStackTrace()
                    error.message?.toast()
                })
    }

    /**
     * 下载视频预览图并保存为本地文件
     * 保存路径：{download_dir}/{hid}/thumb.jpg
     * 保存成功后将 video.thumb 更新为本地路径
     */
    private fun downloadThumb(video: Video) {
        if (video.thumb.isEmpty()) return
        try {
            val thumbFile = File(getDownloadFolder(), "${video.hid}/thumb.jpg")
            if (thumbFile.exists()) {
                // 已有本地预览图，直接更新路径
                video.thumb = thumbFile.absolutePath
                return
            }
            // 下载预览图
            val response = serviceInstance.rawApiService.download(video.thumb).blockingFirst()
            if (response.isSuccessful) {
                thumbFile.parentFile?.mkdirs()
                response.body()?.byteStream()?.use { input ->
                    thumbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (thumbFile.exists() && thumbFile.length() > 0) {
                    video.thumb = thumbFile.absolutePath
                }
            }
        } catch (e: Exception) {
            // 预览图下载失败不影响视频下载
            e.printStackTrace()
        }
    }

    // 继续下载
    fun resumeDownload(video: Video, part: Part) {
        val mission = DownloadMission(hid = video.hid, order = part.order, title = video.title, type = part.type, vid = part.vid)
        rxDownload.download(mission, part)
    }

    // 下载新视频
    private fun download(video: Video, part: Part) {
        // 先下载弹幕
        val playerId = ApiConfig.generatePlayerId(video.hid, part.order)
        val saveName = "danmu.xml"
        val savePath = "${getDownloadFolder().absolutePath}/${video.hid}/p${part.order}"
        rxDownload.downloadDanmu("${ApiConfig.DANMU_API_URL}&playerID=$playerId&r=${System.currentTimeMillis() / 1000}", saveName, savePath)

        // 再处理视频
        val mission = DownloadMission(hid = video.hid, order = part.order, title = video.title, type = part.type, vid = part.vid) // 没有beans
        if (part.file.isNotEmpty()) {
            // 处理直传的情况
                mission.beans.add(DownloadBean(part.file, saveName = "0", savePath = "${DownloadHelpers.getDownloadFolder().absolutePath}/${mission.hid}/p${mission.order}"))
        }

        // 加入到队列里去
        rxDownload.download(mission, part)

        // 标记一下
        part.flag = DownloadStatus.STARTED
    }

    fun pauseDownload(part: Part) {
        rxDownload.pause(part.vid)
    }

    fun updateDanmu(parts: List<Part>) {
        val videos = loadDownloadVideos()

        val requests = videos.fold(mutableListOf<Observable<ResponseBody>>()) {
            total, video ->
            video.subItems.filter {
                part ->
                parts.any { it.vid == part.vid }
            }.forEach {
                val playerId = ApiConfig.generatePlayerId(video.hid, it.order)
                val saveName = "danmu.xml"
                val savePath = "${getDownloadFolder().absolutePath}/${video.hid}/p${it.order}"
                val ob = serviceInstance.rawApiService.danmu(playerId, System.currentTimeMillis() / 1000)
                        .doOnNext {
                            responseBody ->
                            val outputFile = File(savePath, saveName)
                            val outputStream = FileOutputStream(outputFile)

                            outputStream.write(responseBody.bytes())
                            outputStream.flush()
                            outputStream.close()
                        }
                total.add(ob)
            }
            total
        }

        "更新弹幕中...".toast()
        Observable.fromIterable(requests)
                .subscribeOn(Schedulers.io())
                .flatMap { it }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    "更新弹幕成功".toast()
                }
                .subscribe ({
                    // Do nothing
                }, {
                    error ->
                    error.printStackTrace()
                    "更新弹幕失败".toast()
                })
    }

    fun cancelDownload(parts: List<Part>) {
        val videos = loadDownloadVideos()
        videos.forEach {
            video ->
            video.parts.removeAll {
                part ->
                parts.any { it.vid == part.vid }
            }
        }
        videos.removeAll {
            it.parts.isEmpty()
        }

        val jsonString = adapter.toJson(videos)
        val sp = DOWNLOAD_FILE_NAME.getSharedPreference()
        sp.edit {
            putString(KEY_S_DOWNLOAD_VIDEO, jsonString)
        }

        // 同步更新 metadata.json
        saveMetadataToFile(videos)

        parts.forEach {
            part ->
            rxDownload.cancel(part.vid, true)
        }
        EventBus.getDefault().post(RefreshDownloadingVideoEvent())
        EventBus.getDefault().post(RefreshDownloadedVideoEvent())
    }

    fun cancelDownload(vid: String) {
        val videos = loadDownloadVideos()

        cancelDownload(videos.flatMap { it.parts }.filter { it.vid == vid })
    }

    /**
     * 将一个 Part 的所有已完成片段按 order 排序后拼接成单个 .mp4 文件
     * @param part 视频分集
     * @param videoTitle 视频标题，用于命名输出文件
     * @param outputDir 输出目录
     * @return 合并后的文件，失败返回 null
     */
    fun mergePartFiles(part: Part, videoTitle: String, outputDir: File): File? {
        // 收集已完成的片段文件，按 order 排序
        val durls = part.durls
                .filter { it.flag == DownloadStatus.COMPLETED }
                .sortedBy { it.order }
        if (durls.isEmpty()) return null

        // 构建输出文件名：标题_集名.mp4（集名为空时用 P+序号代替）
        val partName = if (part.title.isNotEmpty()) part.title else "P${part.order}"
        val safeName = (videoTitle + "_" + partName)
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
        val outputFile = File(outputDir, "$safeName.mp4")

        // 如果只有一个片段，直接复制
        if (durls.size == 1) {
            val srcFile = File(durls[0].getCacheAbsolutePath())
            if (!srcFile.exists()) return null
            srcFile.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return outputFile
        }

        // 多个片段按顺序拼接
        outputFile.outputStream().buffered().use { output ->
            for (durl in durls) {
                val srcFile = File(durl.getCacheAbsolutePath())
                if (!srcFile.exists()) continue
                srcFile.inputStream().buffered().use { input ->
                    input.copyTo(output)
                }
            }
        }

        // 如果合并后文件为空（所有源文件都不存在），删除并返回 null
        if (outputFile.length() == 0L) {
            outputFile.delete()
            return null
        }
        return outputFile
    }

    /**
     * 合并 Part 到应用缓存目录（用于分享等临时场景）
     */
    fun mergePartToTempFile(part: Part, videoTitle: String): File? {
        val tempDir = File(AppApplication.get().cacheDir, "share")
        if (!tempDir.exists()) tempDir.mkdirs()
        return mergePartFiles(part, videoTitle, tempDir)
    }

    /**
     * 将多个文件打包为 ZIP
     * 使用 java.util.zip.ZipOutputStream，无需额外依赖
     * @param files 要打包的文件列表
     * @param outputDir ZIP 输出目录
     * @return ZIP 文件，失败返回 null
     */
    fun zipFiles(files: List<File>, outputDir: File, onProgress: ((index: Int, total: Int, fileName: String) -> Unit)? = null): File? {
        if (files.isEmpty()) return null
        val zipFile = File(outputDir, "videos_${System.currentTimeMillis()}.zip")
        try {
            java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(zipFile.outputStream())).use { zipOut ->
                files.forEachIndexed { index, file ->
                    if (!file.exists()) return@forEachIndexed
                    onProgress?.invoke(index, files.size, file.name)
                    java.io.BufferedInputStream(file.inputStream()).use { input ->
                        zipOut.putNextEntry(java.util.zip.ZipEntry(file.name))
                        input.copyTo(zipOut)
                        zipOut.closeEntry()
                    }
                }
            }
            if (zipFile.length() == 0L) {
                zipFile.delete()
                return null
            }
            return zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            zipFile.delete()
            return null
        }
    }

    /**
     * 合并 Part 并导出到系统 Downloads 目录
     * @return 导出后的文件，失败返回 null
     */
    fun exportPartToDownloads(part: Part, videoTitle: String): File? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        // 为每个视频创建以标题命名的子文件夹
        val safeTitle = videoTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val videoDir = File(downloadsDir, safeTitle)
        if (!videoDir.exists()) videoDir.mkdirs()
        return mergePartFiles(part, videoTitle, videoDir)
    }

    private val PREF_BACKUP_PATH = "backup_path"
    private val defaultBackupPath = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS).path + "/tucao_backup"

    fun getBackupFolder(): File {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(AppApplication.get())
        val backupPath = sharedPref.getString(PREF_BACKUP_PATH, defaultBackupPath)
        val backupFolder = File(backupPath)
        if (!backupFolder.exists()) backupFolder.mkdirs()
        return backupFolder
    }

    fun setBackupFolder(folder: File) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(AppApplication.get())
        sharedPref.edit { putString(PREF_BACKUP_PATH, folder.absolutePath) }
    }

    /**
     * 备份所有已下载的视频文件和元数据到指定目录
     */
    fun backupToFolder(backupDir: File): Int {
        val downloadDir = getDownloadFolder()
        if (!downloadDir.exists()) return 0

        val videos = loadDownloadVideos()
        val metadataFile = File(backupDir, METADATA_FILE_NAME)
        metadataFile.writeText(adapter.toJson(videos))

        var count = 0
        downloadDir.listFiles()?.filter { it.isDirectory }?.forEach { hidDir ->
            val targetHidDir = File(backupDir, hidDir.name)
            copyDir(hidDir, targetHidDir)
            count++
        }
        return count
    }

    /**
     * 从备份目录恢复视频文件和元数据
     */
    fun restoreFromFolder(backupDir: File): Int {
        if (!backupDir.exists()) return 0

        val metadataFile = File(backupDir, METADATA_FILE_NAME)
        val downloadDir = getDownloadFolder()

        if (metadataFile.exists()) {
            val backupVideos = adapter.fromJson(metadataFile.readText()) ?: mutableListOf()
            val existingVideos = loadDownloadVideos()
            val existingHids = existingVideos.map { it.hid }.toSet()
            backupVideos.filter { it.hid !in existingHids }.forEach {
                existingVideos.add(0, it)
            }
            val sp = DOWNLOAD_FILE_NAME.getSharedPreference()
            sp.edit { putString(KEY_S_DOWNLOAD_VIDEO, adapter.toJson(existingVideos)) }
        }

        var count = 0
        backupDir.listFiles()?.filter { it.isDirectory }?.forEach { hidDir ->
            val targetHidDir = File(downloadDir, hidDir.name)
            if (!targetHidDir.exists()) {
                copyDir(hidDir, targetHidDir)
                count++
            }
        }

        EventBus.getDefault().post(RefreshDownloadedVideoEvent())
        EventBus.getDefault().post(RefreshDownloadingVideoEvent())
        return count
    }

    private fun copyDir(src: File, dest: File) {
        if (!dest.exists()) dest.mkdirs()
        src.listFiles()?.forEach { file ->
            val target = File(dest, file.name)
            if (file.isDirectory) {
                copyDir(file, target)
            } else {
                file.inputStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    interface Callback {
        fun startDownload()

        fun pauseDownload()
    }


    /**
     * 扫描下载目录，恢复丢失的缓存元数据（完全离线，不调用任何 API）
     *
     * 恢复策略：
     * 1. 优先读取下载目录中的 metadata.json（包含完整的视频信息）
     * 2. 如果 metadata.json 不存在，回退到文件扫描模式（用 hid 作为标题）
     *
     * @param onComplete 完成回调：(恢复的视频数量)
     * @param onError 错误回调
     */
    fun recoverCachedVideos(
            onComplete: (Int) -> Unit = { _ -> },
            onError: (Throwable) -> Unit = { _ -> }
    ) {
        io.reactivex.Single.fromCallable {
            val downloadDir = getDownloadFolder()
            if (!downloadDir.exists() || !downloadDir.canRead()) {
                return@fromCallable 0
            }

            // 第一步：尝试从 metadata.json 恢复
            val metadataFile = File(downloadDir, METADATA_FILE_NAME)
            if (metadataFile.exists()) {
                val recoveredCount = recoverFromMetadata(metadataFile)
                if (recoveredCount > 0) return@fromCallable recoveredCount
            }

            // 第二步：回退到文件扫描模式
            recoverFromFileScan(downloadDir)
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ count ->
                    onComplete(count)
                }, { error ->
                    onError(error)
                })
    }

    /**
     * 从 metadata.json 恢复缓存记录
     * 合并到现有数据中（跳过已存在的 hid）
     * @return 恢复的视频数量
     */
    private fun recoverFromMetadata(metadataFile: File): Int {
        val backupVideos = adapter.fromJson(metadataFile.readText()) ?: return 0
        val existingVideos = loadDownloadVideos()
        val existingHids = existingVideos.map { it.hid }.toSet()

        var count = 0
        for (video in backupVideos) {
            if (video.hid in existingHids) continue

            // 只恢复有已完成 part 的视频
            val completedParts = video.parts.filter { it.flag == DownloadStatus.COMPLETED }
            if (completedParts.isEmpty()) continue

            video.parts.clear()
            video.parts.addAll(completedParts)
            video.flag = DownloadStatus.COMPLETED
            video.totalSize = completedParts.sumByLong(Part::totalSize)
            video.downloadSize = video.totalSize

            // 检查本地预览图是否存在
            val thumbFile = File(getDownloadFolder(), "${video.hid}/thumb.jpg")
            if (thumbFile.exists()) {
                video.thumb = thumbFile.absolutePath
            }

            existingVideos.add(0, video)
            count++
        }

        if (count > 0) {
            val sp = DOWNLOAD_FILE_NAME.getSharedPreference()
            sp.edit { putString(KEY_S_DOWNLOAD_VIDEO, adapter.toJson(existingVideos)) }

            EventBus.getDefault().post(RefreshDownloadedVideoEvent())
            EventBus.getDefault().post(RefreshDownloadingVideoEvent())
        }
        return count
    }

    /**
     * 通过扫描文件结构恢复缓存记录（不依赖 metadata.json 和 API）
     * 目录结构：{hid}/p{order}/{segment_files}
     * @return 恢复的视频数量
     */
    private fun recoverFromFileScan(downloadDir: File): Int {
        val existingHids = loadDownloadVideos().map { it.hid }.toSet()
        val downloadDirPath = downloadDir.absolutePath

        val recoveredVideos = mutableListOf<Video>()
        downloadDir.listFiles()?.filter { it.isDirectory }?.forEach { hidDir ->
            val hid = hidDir.name
            // 跳过非数字目录名和已存在的 hid
            if (!hid.matches(Regex("\\d+")) || hid in existingHids) return@forEach

            val parts = mutableListOf<Part>()
            // 扫描 p0, p1, p2... 子目录
            hidDir.listFiles()?.filter { it.isDirectory }?.sortedBy {
                it.name.removePrefix("p").toIntOrNull() ?: 0
            }?.forEach { partDir ->
                val order = partDir.name.removePrefix("p").toIntOrNull() ?: return@forEach
                // 收集视频片段文件（排除 danmu.xml 和 thumb.jpg）
                val segmentFiles = partDir.listFiles()?.filter {
                    it.isFile && it.name != "danmu.xml" && it.name != "thumb.jpg"
                }?.sortedBy { it.name.toIntOrNull() ?: 0 } ?: emptyList()

                // 只恢复有实际视频文件的 part
                if (segmentFiles.isNotEmpty() && segmentFiles.any { it.length() > 0 }) {
                    val durls = segmentFiles.mapIndexed { index, file ->
                        Durl(
                                order = index,
                                length = file.length(),
                                url = "",
                                cacheFolderPath = file.parent,
                                cacheFileName = file.name,
                                flag = DownloadStatus.COMPLETED,
                                downloadSize = file.length(),
                                totalSize = file.length()
                        )
                    }.toMutableList()

                    val partTotalSize = segmentFiles.sumByLong { it.length() }
                    parts.add(Part(
                            title = if (parts.isNotEmpty()) "P$order" else "",
                            order = order,
                            vid = "${hid}_$order",
                            flag = DownloadStatus.COMPLETED,
                            downloadSize = partTotalSize,
                            totalSize = partTotalSize,
                            durls = durls
                    ))
                }
            }

            if (parts.isEmpty()) return@forEach

            val totalSize = parts.sumByLong { it.totalSize }

            // 检查本地预览图
            val thumbFile = File(hidDir, "thumb.jpg")
            val thumb = if (thumbFile.exists()) thumbFile.absolutePath else ""

            val video = Video(
                    hid = hid,
                    title = "视频 $hid",
                    flag = DownloadStatus.COMPLETED,
                    downloadSize = totalSize,
                    totalSize = totalSize,
                    thumb = thumb
            )
            video.parts.addAll(parts)
            recoveredVideos.add(video)
        }

        if (recoveredVideos.isEmpty()) return 0

        // 合并到现有数据
        val existingVideos = loadDownloadVideos()
        existingVideos.addAll(0, recoveredVideos)

        val sp = DOWNLOAD_FILE_NAME.getSharedPreference()
        sp.edit { putString(KEY_S_DOWNLOAD_VIDEO, adapter.toJson(existingVideos)) }

        // 同步保存 metadata.json（这次恢复后下次就不用再扫描了）
        saveMetadataToFile(existingVideos)

        EventBus.getDefault().post(RefreshDownloadedVideoEvent())
        EventBus.getDefault().post(RefreshDownloadingVideoEvent())

        return recoveredVideos.size
    }

    class ServiceInstance {
        @Inject
        lateinit var xmlApiService: XmlApiService

        @Inject
        lateinit var rawApiService: RawApiService

        init {
            AppApplication.get()
                    .getApiComponent()
                    .inject(this)
        }
    }

}
