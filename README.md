# Tucao

[![Travis](https://img.shields.io/travis/blackbbc/Tucao.svg)](https://travis-ci.org/blackbbc/Tucao)
[![GitHub release](https://img.shields.io/github/release/blackbbc/Tucao.svg)](https://github.com/blackbbc/Tucao/releases)
[![license](https://img.shields.io/github/license/blackbbc/Tucao.svg)](https://github.com/blackbbc/Tucao/blob/master/LICENSE)

![](http://www.tucao.tv/skin2013/banner.jpg)

## 特色
- 首页六大模块，推荐、新番、影剧、游戏、动画、频道
- 全站排行榜，支持每日/每周排序
- 放映时间表，可以查看周一到周日新番的更新情况
- 频道列表，支持按照发布时间/播放量/弹幕排序
- 视频搜索，支持分频道搜索
- 视频查看，使用 ExoPlayer (Media3) 播放视频，DanmakuFlameMaster 播放弹幕，自动拼接多段视频
- 视频离线缓存，支持缓存弹幕
- 进度条拖动预览（本地缓存视频支持帧预览，在线视频支持已播放画面预览）

## 截图
<a href="art/1.gif"><img src="art/1.gif" width="30%"/></a>
<a href="art/2.gif"><img src="art/2.gif" width="30%"/></a>
<a href="art/3.gif"><img src="art/3.gif" width="30%"/></a>

<a href="art/4.gif"><img src="art/4.gif" width="50%"/></a>

<a href="art/5.png"><img src="art/5.png" width="30%"/></a>
<a href="art/6.png"><img src="art/6.png" width="30%"/></a>
<a href="art/7.png"><img src="art/7.png" width="30%"/></a>

<a href="art/8.png"><img src="art/8.png" width="30%"/></a>
<a href="art/9.png"><img src="art/9.png" width="30%"/></a>
<a href="art/10.png"><img src="art/10.png" width="30%"/></a>

<a href="art/11.png"><img src="art/11.png" width="30%"/></a>
<a href="art/12.png"><img src="art/12.png" width="30%"/></a>
<a href="art/13.png"><img src="art/13.png" width="30%"/></a>

<a href="art/14.png"><img src="art/14.png" width="30%"/></a>
<a href="art/15.png"><img src="art/15.png" width="30%"/></a>
<a href="art/16.png"><img src="art/16.png" width="30%"/></a>

<a href="art/17.png"><img src="art/17.png" width="30%"/></a>
<a href="art/18.png"><img src="art/18.png" width="30%"/></a>
<a href="art/19.png"><img src="art/19.png" width="30%"/></a>

## 待做列表
- [ ] 同步收藏（接口不全）
- [ ] 发私信（接口不全）
- [ ] 查看私信（接口不全）
- [ ] 准备播放时会弹回主界面（无法重现）
- [ ] 修复播放时跳回开头的问题（无法100%重现，原因不明）

## 常见问题
- [常见问题](https://github.com/blackbbc/Tucao/blob/master/FAQ.md)

## 更新历史
- [更新历史](https://github.com/blackbbc/Tucao/blob/master/changelog.md)

## 近期修复记录

### 播放器与缓存
- **离线缓存播放崩溃修复**：GSY 的 CacheDataSource 使用 DefaultHttpDataSource，无法处理 `file://` 协议的本地文件 URL，导致 ClassCastException。对本地文件强制关闭缓存层绕过此问题。
- **缓存视频播放源修复**：确保已缓存的视频播放本地文件而非重新从网络加载。
- **视频详情页缓存标记修复**：修复了通过文件扫描恢复的缓存（vid 格式不匹配）在视频详情页不显示对勾的问题，增加了按 `order` 字段的回退匹配逻辑。

### 进度条预览
- **拖动进度条预览功能**：
  - 修复 GSY 的 `setOnSeekBarChangeListener()` 覆盖 PreviewSeekBarLayout 回调的问题。
  - 修复 PreviewDelegate 的 frameView 遮挡预览图的问题。
  - 本地缓存视频：使用 MediaMetadataRetriever 按 10 秒间隔提取帧画面。
  - 在线视频：使用 TextureView 截图，每 3 秒捕获一帧。
  - 两种帧来源合并，避免互相覆盖。

### 评论系统
- **评论加载适配新版网站**：网站评论页 HTML 结构从 `table.comment_list>tbody>tr` 变更为 `div.clist>div.item`，重写了评论解析逻辑和分页计算。
- 自动跳过评论列表中的广告项。

### 其他
- **播放器容器自适应**：视频加载前默认 16:9 比例，加载后按实际宽高比自适应（上限屏幕 45%、下限 25%）。
- **SharePoint URL 截断恢复**：API 返回的 `share=` token 被截断时，从视频页面 HTML 中恢复完整播放 URL。

## 开发指南
Android Studio 版本: 最新稳定版

由于项目中使用了[子模块](https://git-scm.com/book/zh/v1/Git-工具-子模块)，请务必使用以下命令克隆项目
```
git clone --recursive -j8 https://github.com/blackbbc/Tucao.git
```

- 架构基于 MVVM 模式，使用 `DataBinding` + `RxJava2` + `Dagger2` + `Retrofit` 实现
- 视频播放基于 GSYVideoPlayer 11.3.0 + ExoPlayer (Media3) 后端
- minSdkVersion: 23

## 免责声明
该项目仅供交流学习使用，如果该项目有侵犯Tucao版权问题，本人会及时删除此页面与整个项目。

## 感谢以下开源项目
- [Kotlin](https://github.com/JetBrains/kotlin)
- [RxJava](https://github.com/ReactiveX/RxJava)
- [RxLifecycle](https://github.com/trello/RxLifecycle)
- [RxDownload](https://github.com/ssseasonnn/RxDownload)
- [Retrofit](https://github.com/square/retrofit)
- [Dagger2](https://github.com/google/dagger)
- [EventBus](https://github.com/greenrobot/EventBus)
- [GSYVideoPlayer](https://github.com/CarGuo/GSYVideoPlayer)
- [ExoPlayer / Media3](https://github.com/androidx/media)
- [DanmakuFlameMaster](https://github.com/Bilibili/DanmakuFlameMaster)
- [Glide](https://github.com/bumptech/glide)
- [BaseRecyclerViewAdapterHelper](https://github.com/CymChad/BaseRecyclerViewAdapterHelper)
- [CrashWoodpecker](https://github.com/drakeet/CrashWoodpecker)
- [Leakcanary](https://github.com/square/leakcanary)
- [Convenientbanner](https://github.com/saiwu-bigkoo/Android-ConvenientBanner)
