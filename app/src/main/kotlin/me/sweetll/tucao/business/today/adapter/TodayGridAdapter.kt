package me.sweetll.tucao.business.today.adapter

import android.widget.ImageView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import me.sweetll.tucao.R
import me.sweetll.tucao.extension.load
import me.sweetll.tucao.model.json.Video

/**
 * 今天更新网格列表适配器
 */
class TodayGridAdapter(data: MutableList<Video>?) :
        BaseQuickAdapter<Video, BaseViewHolder>(R.layout.item_today_grid_video, data) {

    override fun convert(helper: BaseViewHolder, item: Video) {
        helper.setText(R.id.text_title, item.title)
        val thumbImg = helper.getView<ImageView>(R.id.img_thumb)
        thumbImg.load(mContext, item.thumb)
    }
}
