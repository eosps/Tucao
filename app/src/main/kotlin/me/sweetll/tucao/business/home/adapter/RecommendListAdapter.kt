package me.sweetll.tucao.business.home.adapter

import android.widget.ImageView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import me.sweetll.tucao.R
import me.sweetll.tucao.extension.formatByWan
import me.sweetll.tucao.extension.load
import me.sweetll.tucao.model.json.Video

class RecommendListAdapter(data: MutableList<Video>?) :
        BaseQuickAdapter<Video, BaseViewHolder>(R.layout.item_video, data) {

    override fun convert(helper: BaseViewHolder, video: Video) {
        helper.setText(R.id.text_title, video.title)
        helper.setText(R.id.text_user, "up：${video.user}")
        helper.setText(R.id.text_play, "播放：${video.play.formatByWan()}")
        helper.setGone(R.id.text_mukio, false)

        val thumbImg: ImageView = helper.getView(R.id.img_thumb)
        thumbImg.load(mContext, video.thumb)
    }
}
