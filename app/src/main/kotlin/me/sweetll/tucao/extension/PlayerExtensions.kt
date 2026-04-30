package me.sweetll.tucao.extension

import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import me.sweetll.tucao.AppApplication
import me.sweetll.tucao.model.xml.Durl
import java.io.File
import java.io.FileOutputStream

/**
 * 多段视频拼接扩展函数
 * 使用 ffconcat 协议拼接多段视频
 */
fun StandardGSYVideoPlayer.setUp(durls: MutableList<Durl>, cache: Boolean, vararg objects: Any) {
    try {
        val outputFile = File.createTempFile("tucao", ".concat", AppApplication.get().cacheDir)
        val outputStream = FileOutputStream(outputFile)

        val concatContent = buildString {
            appendln("ffconcat version 1.0")
            for (durl in durls) {
                appendln("file '${if (cache) durl.getCacheAbsolutePath() else durl.url}'")
                appendln("duration ${durl.length / 1000f}")
            }
        }

        outputStream.write(concatContent.toByteArray())

        outputStream.flush()
        outputStream.close()

        // v11 的 setUp 签名: setUp(url, cacheWithPlay, title)
        this.setUp(outputFile.absolutePath, true, "")
    } catch (e: Exception) {
        e.message?.toast()
        e.printStackTrace()
    }
}
