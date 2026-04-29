package me.sweetll.tucao.business.login

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import android.view.View
import androidx.transition.TransitionManager
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.Observables

import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseActivity
import me.sweetll.tucao.business.login.viewmodel.LoginViewModel
import me.sweetll.tucao.databinding.ActivityLoginBinding
import me.sweetll.tucao.transition.FabTransform

class LoginActivity : BaseActivity() {
    lateinit var binding: ActivityLoginBinding
    lateinit var viewModel: LoginViewModel

    companion object {
        const val ARG_FAB_COLOR = "fab_color"
        const val ARG_FAB_RES_ID = "fab_res_id"

        fun intentTo(context: Context, requestCode: Int = 1, options: Bundle?) {
            val intent = Intent(context, LoginActivity::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                FabTransform.addExtras(intent, options!!.getInt(ARG_FAB_COLOR), options.getInt(ARG_FAB_RES_ID))
            }
            ActivityCompat.startActivityForResult(context as Activity, intent, requestCode, options)
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_login)
        viewModel = LoginViewModel(this)
        binding.viewModel = viewModel

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            FabTransform.setup(this, binding.container)
        }

        // 登录按钮启用条件：用户名和密码都不为空（不再需要验证码）
        val validUsername = RxTextView.textChanges(binding.usernameEdit)
                .map { text -> text.isNotEmpty() }
        val validPassword = RxTextView.textChanges(binding.passwordEdit)
                .map { text -> text.isNotEmpty() }
        Observables.combineLatest(validUsername, validPassword) { a, b -> a && b }
                .distinctUntilChanged()
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    enable ->
                    binding.signInBtn.isEnabled = enable
                }
    }

    fun showLoading() {
        TransitionManager.beginDelayedTransition(binding.container)
        viewModel.container.set(View.GONE)
        viewModel.progress.set(View.VISIBLE)
    }

    fun showLogin() {
        TransitionManager.beginDelayedTransition(binding.container)
        viewModel.container.set(View.VISIBLE)
        viewModel.progress.set(View.GONE)
    }
}
