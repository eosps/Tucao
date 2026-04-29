package me.sweetll.tucao.model.json

data class Channel(var id: Int, var name: String, var parentId: Int? = null) {
    companion object {
        private val CHANNELS = listOf(
                // 综合首页，页面内包含动画(28)、音乐(6)、游戏(29)子频道
                Channel(19, "综合"),
                Channel(28, "动画", 19),
                Channel(6, "音乐", 19),
                Channel(29, "游戏", 19),

                // 影剧
                Channel(23, "影剧"),
                Channel(39, "电视剧", 23),
                Channel(38, "电影", 23),
                Channel(16, "综艺娱乐", 23),

                // 新番
                Channel(24, "新番"),
                Channel(11, "连载新番", 24),
                Channel(43, "天朝出品", 24),
                Channel(26, "OAD·OVA·剧场版", 24),
                Channel(10, "完结番组", 24)
        )

        fun find(tid: Int): Channel? = CHANNELS.find { it.id == tid }

        fun findSiblingChannels(tid: Int) = CHANNELS.filter { tid == it.id || tid == it.parentId }

        fun findAllParentChannels() = CHANNELS.filter { it.parentId == null }
    }

    fun getValidParentId(): Int = if (parentId != null) parentId!! else id
}
