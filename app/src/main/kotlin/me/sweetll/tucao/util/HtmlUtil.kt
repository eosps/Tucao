package me.sweetll.tucao.util

import me.sweetll.tucao.model.json.Channel
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.model.raw.Banner
import org.jsoup.nodes.Element

val HID_PATTERN = "/play/h([0-9]+)/".toRegex()
val TID_PATTERN = "/list/([0-9]+)/".toRegex()

fun parseBanner(slides: List<Element>): List<Banner> {
    val banners = slides.map {
        // <div class="slide"><a href="https://www.tucao.cool/play/h4100629/" target="_blank" class="i"><img
        //         src="https://www.tucao.cool/uploadfile/2024/0211/thumb_300_487_20240211125530340.jpg"></a><a
        //         href="https://www.tucao.cool/play/h4100629/" target="_blank" class="t">
        //     <div>【2024日影】神推偶像登上武道馆我就死而无憾 剧场版【幻月】</div>
        //     <p> 绘里飘与自推的第一次相识，是在三年前的七夕祭典上，当时冈山当地的地下偶像团体ChamJam在那里表演。其中一位成员舞菜的表现让绘里飘产生 </p>
        // </a></div>
        val aElement = it.child(0)
        val linkUrl = aElement.attr("href")
        val imgElement = aElement.child(0)
        val imgUrl = imgElement.attr("src")
        val hid: String? = HID_PATTERN.find(linkUrl)?.groupValues?.get(1)
        Banner(imgUrl, linkUrl, hid)
    }
    return banners
}

fun parseChannelList(parent: Element): List<Pair<Channel, List<Video>>> {
    val result = mutableListOf<Pair<Channel, List<Video>>>()
    parent.children().forEachIndexed { index, element ->
        if (index % 2 == 0 && index + 1 < parent.childNodeSize()) {
            val title = element
            val list = parent.child(index + 1)
            if (title.className() == "title" && list.className().startsWith("list")) {
                val channelLinkUrl = title.child(1).attr("href")
                val tid: Int = TID_PATTERN.find(channelLinkUrl)!!.groupValues[1].toInt()
                val channel = Channel.find(tid)!!
                val videos = parseListVideo(list)
                result.add(channel to videos)
            }
        }
    }
    return result
}

fun parseListVideo(list: Element): List<Video> {
    val items = list.getElementsByClass("item")
    val videos = items.mapNotNull {
        try {
            // 新版 HTML: item > b > a.img (含背景图) + a.t (标题) + div.i (播放量)
            val bElement = it.getElementsByClass("b").first() ?: it.child(0)
            val aElement = bElement.child(0)
            val linkUrl = aElement.attr("href")
            val hid: String = HID_PATTERN.find(linkUrl)?.groupValues?.get(1) ?: return@mapNotNull null

            // 缩略图：优先从 style 的 background-image 取，其次从 img 的 src 取
            val style = aElement.attr("style")
            val thumb = if (style.contains("background-image")) {
                style.substringAfter("url(").substringBeforeLast(')')
            } else {
                val imgElement = aElement.getElementsByTag("img").first()
                val imgStyle = imgElement?.attr("style") ?: ""
                if (imgStyle.contains("background-image")) {
                    imgStyle.substringAfter("url(").substringBeforeLast(')')
                } else {
                    imgElement?.attr("src") ?: ""
                }
            }

            val title = bElement.getElementsByClass("t").first()?.text() ?: ""
            val playStr = bElement.getElementsByClass("i").first()?.text()?.replace(",", "") ?: "0"
            val play = playStr.split(" ")?.firstOrNull()?.toIntOrNull() ?: 0

            Video(hid = hid, title = title, play = play, thumb = thumb)
        } catch (e: Exception) {
            null
        }
    }
    return videos
}

/**
 * 从平级的 title + list 元素对中解析频道列表
 * 新版网站中 title 和 list 是兄弟节点，不再嵌套在同一个父容器中
 */
fun parseChannelListFromSiblings(parent: Element): List<Pair<Channel, List<Video>>> {
    val result = mutableListOf<Pair<Channel, List<Video>>>()
    val children = parent.children()
    var i = 0
    while (i < children.size - 1) {
        val title = children[i]
        val list = children[i + 1]
        if (title.className() == "title" && list.className().startsWith("list")) {
            // 从 title 中提取频道链接
            val links = title.getElementsByAttribute("href")
            val channelLink = links.lastOrNull { it.attr("href").contains("/list/") }
            if (channelLink != null) {
                val channelUrl = channelLink.attr("href")
                val tid = TID_PATTERN.find(channelUrl)?.groupValues?.get(1)?.toIntOrNull()
                if (tid != null) {
                    val channel = Channel.find(tid)
                    if (channel != null) {
                        val videos = parseListVideo(list)
                        if (videos.isNotEmpty()) {
                            result.add(channel to videos)
                        }
                    }
                }
            }
            i += 2
        } else {
            i++
        }
    }
    return result
}