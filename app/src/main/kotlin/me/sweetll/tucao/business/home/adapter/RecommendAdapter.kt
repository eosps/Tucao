package me.sweetll.tucao.business.home.adapter

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import me.sweetll.tucao.R
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.extension.formatByWan
import me.sweetll.tucao.extension.load
import me.sweetll.tucao.model.json.Channel

class RecommendAdapter(data: MutableList<Pair<Channel, List<Video>>>?): BaseQuickAdapter<Pair<Channel, List<Video>>, BaseViewHolder>(R.layout.item_recommend_video, data) {
    override fun convert(helper: BaseViewHolder, item: Pair<Channel, List<Video>>) {
        val channel = item.first
        helper.setText(R.id.text_channel, channel.name)

        // 4 张卡片的 view id
        val cardIds = intArrayOf(R.id.card1, R.id.card2, R.id.card3, R.id.card4)
        val thumbIds = intArrayOf(R.id.img_thumb1, R.id.img_thumb2, R.id.img_thumb3, R.id.img_thumb4)
        val playIds = intArrayOf(R.id.text_play1, R.id.text_play2, R.id.text_play3, R.id.text_play4)
        val titleIds = intArrayOf(R.id.text_title1, R.id.text_title2, R.id.text_title3, R.id.text_title4)

        val maxCards = minOf(4, cardIds.size)
        val videoCount = minOf(item.second.size, maxCards)

        // 填充有数据的卡片
        item.second.take(maxCards).forEachIndexed { index, result ->
            val thumbImg: ImageView = helper.getView(thumbIds[index])
            val playText: TextView = helper.getView(playIds[index])
            val titleText: TextView = helper.getView(titleIds[index])

            helper.setTag(cardIds[index], result.hid)
            helper.addOnClickListener(cardIds[index])

            titleText.tag = result.thumb
            thumbImg.load(mContext, result.thumb)
            playText.text = result.play.formatByWan()
            titleText.text = result.title
            helper.getView<View>(cardIds[index]).visibility = View.VISIBLE
        }

        // 隐藏没有数据的卡片
        for (index in videoCount until maxCards) {
            helper.getView<View>(cardIds[index]).visibility = View.INVISIBLE
        }

        if (channel.id == -1) {
            // "今天推荐": 显示排行榜入口和"更多"按钮（点击打开浏览器）
            helper.setGone(R.id.img_rank, true)
            helper.addOnClickListener(R.id.img_rank)
            helper.setText(R.id.text_more, "更多${channel.name}内容")
            // tag 设为 URL 字符串，用于在 fragment 中区分并打开浏览器
            helper.setTag(R.id.card_more, "https://www.tucao.my/html/pos.html")
            helper.setGone(R.id.card_more, true)
            helper.addOnClickListener(R.id.card_more)
        } else if (channel.id != 0) {
            // 其他频道: 显示更多按钮，隐藏排行榜
            helper.setText(R.id.text_more, "更多${channel.name}内容")
            helper.setTag(R.id.card_more, channel.id)
            helper.setGone(R.id.card_more, true)
            helper.setGone(R.id.img_rank, false)
            helper.addOnClickListener(R.id.card_more)
        } else {
            helper.setGone(R.id.card_more, false)
            helper.setGone(R.id.img_rank, true)
            helper.addOnClickListener(R.id.img_rank)
        }

    }
}