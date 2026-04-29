package me.sweetll.tucao.business.home

import android.accounts.AccountManager
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.databinding.DataBindingUtil
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.appcompat.widget.Toolbar
import android.text.method.ScrollingMovementMethod
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.snackbar.Snackbar
import com.orhanobut.dialogplus.DialogPlus
import com.orhanobut.dialogplus.ViewHolder
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import dagger.android.AndroidInjection
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.schedulers.Schedulers
import org.jsoup.Jsoup
import me.sweetll.tucao.AppApplication
import me.sweetll.tucao.BuildConfig
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseActivity
import me.sweetll.tucao.business.download.DownloadActivity
import me.sweetll.tucao.business.home.adapter.HomePagerAdapter
import me.sweetll.tucao.business.home.event.RefreshPersonalEvent
import me.sweetll.tucao.business.login.LoginActivity
import me.sweetll.tucao.business.personal.PersonalActivity
import me.sweetll.tucao.business.search.SearchActivity
import me.sweetll.tucao.databinding.ActivityMainBinding
import me.sweetll.tucao.di.service.ApiConfig
import me.sweetll.tucao.di.service.JsonApiService
import me.sweetll.tucao.di.service.RawApiService
import me.sweetll.tucao.extension.formatWithUnit
import me.sweetll.tucao.extension.load
import me.sweetll.tucao.extension.sanitizeHtml
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.model.other.User
import me.sweetll.tucao.rxdownload.entity.DownloadEvent
import me.sweetll.tucao.rxdownload.entity.DownloadStatus
import me.sweetll.tucao.AppApplication.Companion.PRIMARY_CHANNEL
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject

class MainActivity : BaseActivity() {

    companion object {
        const val LOGIN_REQUEST = 1

        const val NOTIFICATION_ID = 10
    }

    private val notifyMgr by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    lateinit var binding : ActivityMainBinding
    lateinit var drawerToggle: ActionBarDrawerToggle

    private var lastBackTime = 0L

    @Inject
    lateinit var jsonApiService: JsonApiService

    @Inject
    lateinit var rawApiService: RawApiService

    @Inject
    lateinit var user: User

    lateinit var accountManager: AccountManager

    lateinit var avatarImg: ImageView

    lateinit var usernameText: TextView

    lateinit var messageMenu: MenuItem

    lateinit var messageCounter: TextView

    lateinit var logoutDialog: DialogPlus

    // 消息数轮询
    private var messagePollDisposable: Disposable? = null

    override fun getToolbar(): Toolbar = binding.toolbar

    fun initDialog() {
        val logoutView = LayoutInflater.from(this).inflate(R.layout.dialog_logout, null)
        logoutDialog = DialogPlus.newDialog(this)
                .setContentHolder(ViewHolder(logoutView))
                .setGravity(Gravity.BOTTOM)
                .setContentWidth(ViewGroup.LayoutParams.MATCH_PARENT)
                .setContentHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
                .setContentBackgroundResource(android.R.color.transparent)
                .setOverlayBackgroundResource(R.color.scrim)
                .setOnClickListener {
                    dialog, view ->
                    when (view.id) {
                        R.id.btn_logout -> {
                            rawApiService.logout()
                                    .bindToLifecycle(this)
                                    .sanitizeHtml {
                                        Object()
                                    }
                                    .subscribe({

                                    }, {

                                    })
                            user.invalidate()
                            doRefresh()
                            dialog.dismiss()
                        }
                    }
                }
                .create()
    }

    override fun initView(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)

        EventBus.getDefault().register(this)

        initDialog()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        setupDrawer()

        initCounter()

        accountManager = AccountManager.get(this)

        binding.viewPager.adapter = HomePagerAdapter(supportFragmentManager)
        binding.viewPager.offscreenPageLimit = 6
        binding.tab.setupWithViewPager(binding.viewPager)

        val headerView = binding.navigation.getHeaderView(0)
        avatarImg = headerView.findViewById(R.id.img_avatar)
        usernameText = headerView.findViewById(R.id.text_username)

        doRefresh()

        avatarImg.setOnClickListener {
            if (!user.isValid()) {
                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        this, avatarImg, "transition_login"
                ).toBundle() ?: Bundle()
                options.putInt(LoginActivity.ARG_FAB_COLOR, ContextCompat.getColor(this, R.color.colorPrimary))
                options.putInt(LoginActivity.ARG_FAB_RES_ID, R.drawable.default_avatar)
                LoginActivity.intentTo(this, LOGIN_REQUEST, options)
            } else {
                PersonalActivity.intentTo(this)
//                logoutDialog.show()
            }
        }
    }

    override fun initToolbar() {
        super.initToolbar()
    }

    fun setupDrawer() {
        binding.navigation.setNavigationItemSelectedListener {
            menuItem ->
            when (menuItem.itemId) {
                R.id.nav_star -> {
                    StarActivity.intentTo(this)
                }
                R.id.nav_play_history -> {
                    PlayHistoryActivity.intentTo(this)
                }
                R.id.nav_download -> {
                    DownloadActivity.intentTo(this)
                }
                R.id.nav_message -> {
                    MessageListActivity.intentTo(this)
                }
                R.id.nav_setting -> {
                    me.sweetll.tucao.business.settings.SettingsActivity.intentTo(this)
                }
                R.id.nav_about -> {
                    AboutActivity.intentTo(this)
                }
            }
            binding.drawer.closeDrawers()
            true
        }
        drawerToggle = ActionBarDrawerToggle(this, binding.drawer, binding.toolbar, R.string.drawer_open, R.string.drawer_close)
        binding.drawer.addDrawerListener(drawerToggle)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        } else {
            when (item.itemId) {
                android.R.id.home -> {
                    binding.drawer.openDrawer(GravityCompat.START)
                    return true
                }
                R.id.action_search -> {
                    val searchView = getToolbar().findViewById<View>(R.id.action_search)
                    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, searchView,
                            "transition_search_back").toBundle()
                    SearchActivity.intentTo(this, options = options)
                    return true
                }
                else -> {
                    return super.onOptionsItemSelected(item)
                }
            }
        }
    }

    private fun initCounter() {
        messageMenu = binding.navigation.menu.findItem(R.id.nav_message)
        messageCounter = messageMenu.actionView as TextView
        messageCounter.gravity = Gravity.CENTER_VERTICAL
        messageCounter.setTypeface(null, Typeface.BOLD)
        messageCounter.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
        messageCounter.visibility = View.INVISIBLE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOGIN_REQUEST && resultCode == Activity.RESULT_OK) {
            doRefresh()
        }
    }

    override fun onResume() {
        super.onResume()
        startMessagePolling()
    }

    override fun onPause() {
        super.onPause()
        stopMessagePolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMessagePolling()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshPersonal(event: RefreshPersonalEvent) {
        doRefresh()
    }

    private fun doRefresh() {
        if (user.isValid()) {
            avatarImg.load(this, user.avatar, R.drawable.default_avatar, User.signature())
            usernameText.text = user.name
            if (user.message > 0) {
                messageCounter.text = "${user.message}"
                messageCounter.visibility = View.VISIBLE
            } else {
                messageCounter.visibility = View.INVISIBLE
            }
            messageMenu.isVisible = true
        } else {
            usernameText.text = "点击头像登录"
            messageMenu.isVisible = false
            Glide.with(this)
                    .load(R.drawable.default_avatar)
                    .apply(RequestOptions.circleCropTransform())
                    .into(avatarImg)
        }
    }

    // 每5分钟轮询一次未读消息数
    private fun startMessagePolling() {
        if (!user.isValid()) return
        stopMessagePolling()
        // 立即查一次，然后每5分钟查一次
        messagePollDisposable = Observable.interval(0, 5, TimeUnit.MINUTES)
                .subscribeOn(Schedulers.io())
                .flatMap { refreshMessageCount() }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ count ->
                    user.message = count
                    if (count > 0) {
                        messageCounter.text = "$count"
                        messageCounter.visibility = View.VISIBLE
                    } else {
                        messageCounter.visibility = View.INVISIBLE
                    }
                }, { _ ->
                    // 网络错误静默忽略
                })
    }

    private fun stopMessagePolling() {
        messagePollDisposable?.dispose()
        messagePollDisposable = null
    }

    // 从个人页面 HTML 提取未读消息数（div.t_all）
    private fun refreshMessageCount(): Observable<Int> {
        return rawApiService.personal()
                .map { body ->
                    val doc = Jsoup.parse(body.string())
                    val tAllEl = doc.selectFirst("div.t_all")
                    tAllEl?.text()?.trim()?.toIntOrNull() ?: 0
                }
                .onErrorReturnItem(user.message)
    }

    override fun onBackPressed() {
        val currentBackTime = System.currentTimeMillis()
        if (currentBackTime - lastBackTime < 2000) {
            super.onBackPressed()
        } else {
            lastBackTime = currentBackTime
            "再按一次退出".toast()
        }
    }
}
