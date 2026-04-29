package me.sweetll.tucao.business.download

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.schedulers.Schedulers
import me.sweetll.tucao.AppApplication
import me.sweetll.tucao.R
import me.sweetll.tucao.business.explorer.FileExplorerActivity
import android.app.AlertDialog
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import me.sweetll.tucao.extension.DownloadHelpers
import me.sweetll.tucao.extension.edit
import me.sweetll.tucao.extension.toast
import java.io.File

class DownloadSettingFragment: PreferenceFragment() {
    val REQUEST_DOWNLOAD_PATH = 1
    val REQUEST_BACKUP_PATH = 2
    val REQUEST_RESTORE_PATH = 3

    lateinit var downloadPathPref: Preference
    lateinit var backupPathPref: Preference
    lateinit var backupNowPref: Preference
    lateinit var restoreNowPref: Preference
    lateinit var recoverCachePref: Preference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.settings_download)

        // 下载位置
        downloadPathPref = findPreference("download_path")
        downloadPathPref.summary = DownloadHelpers.getDownloadFolder().absolutePath
        downloadPathPref.setOnPreferenceClickListener {
            requestStoragePermission {
                val intent = Intent(activity, FileExplorerActivity::class.java)
                startActivityForResult(intent, REQUEST_DOWNLOAD_PATH)
            }
            true
        }

        // 备份路径
        backupPathPref = findPreference("backup_path")
        backupPathPref.summary = DownloadHelpers.getBackupFolder().absolutePath
        backupPathPref.setOnPreferenceClickListener {
            requestStoragePermission {
                val intent = Intent(activity, FileExplorerActivity::class.java)
                startActivityForResult(intent, REQUEST_BACKUP_PATH)
            }
            true
        }

        // 立即备份
        backupNowPref = findPreference("backup_now")
        backupNowPref.setOnPreferenceClickListener {
            requestStoragePermission {
                performBackup()
            }
            true
        }

        // 从备份恢复
        restoreNowPref = findPreference("restore_now")
        restoreNowPref.setOnPreferenceClickListener {
            requestStoragePermission {
                val intent = Intent(activity, FileExplorerActivity::class.java)
                startActivityForResult(intent, REQUEST_RESTORE_PATH)
            }
            true
        }

        // 扫描恢复缓存
        recoverCachePref = findPreference("recover_cache")
        recoverCachePref.setOnPreferenceClickListener {
            requestStoragePermission {
                performRecoverCache()
            }
            true
        }
    }

    private fun requestStoragePermission(action: () -> Unit) {
        RxPermissions(activity)
                .request(Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe { granted ->
                    if (granted) {
                        action()
                    } else {
                        "请授予读写存储卡权限".toast()
                    }
                }
    }

    private fun performBackup() {
        val backupDir = DownloadHelpers.getBackupFolder()
        "正在备份到 ${backupDir.absolutePath}...".toast()
        io.reactivex.Single.fromCallable {
            DownloadHelpers.backupToFolder(backupDir)
        }.subscribeOn(Schedulers.io())
                .subscribe({ count ->
                    "备份完成，共 ${count} 个视频目录".toast()
                }, { error ->
                    error.printStackTrace()
                    "备份失败".toast()
                })
    }

    private fun performRestore(restoreDir: File) {
        "正在从 ${restoreDir.absolutePath} 恢复...".toast()
        io.reactivex.Single.fromCallable {
            DownloadHelpers.restoreFromFolder(restoreDir)
        }.subscribeOn(Schedulers.io())
                .subscribe({ count ->
                    "恢复完成，共恢复 ${count} 个视频目录".toast()
                }, { error ->
                    error.printStackTrace()
                    "恢复失败".toast()
                })
    }

    private fun performRecoverCache() {
        // Android 11+ 需要所有文件访问权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            AlertDialog.Builder(activity)
                    .setTitle("需要文件访问权限")
                    .setMessage("扫描恢复缓存需要\"所有文件访问权限\"才能读取下载目录。\n\n请在设置页面中开启该权限。")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:me.sweetll.tucao"))
                        startActivity(intent)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            return
        }

        AlertDialog.Builder(activity)
                .setTitle("扫描恢复缓存")
                .setMessage("将扫描下载目录恢复丢失的离线缓存记录。\n\n优先从本地元数据恢复，无需联网。\n此操作不会删除或覆盖已有数据。")
                .setPositiveButton("开始扫描") { _, _ ->
                    "正在扫描下载目录...".toast()
                    DownloadHelpers.recoverCachedVideos(
                            onComplete = { count ->
                                if (count > 0) {
                                    "恢复完成，共恢复 ${count} 个视频".toast()
                                } else {
                                    "未找到可恢复的缓存文件".toast()
                                }
                            },
                            onError = { error ->
                                error.printStackTrace()
                                "恢复失败: ${error.message}".toast()
                            }
                    )
                }
                .setNegativeButton("取消", null)
                .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return

        val folder = data.getSerializableExtra("folder") as File
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(AppApplication.get())

        when (requestCode) {
            REQUEST_DOWNLOAD_PATH -> {
                sharedPref.edit { putString("download_path", folder.absolutePath) }
                downloadPathPref.summary = folder.absolutePath
            }
            REQUEST_BACKUP_PATH -> {
                DownloadHelpers.setBackupFolder(folder)
                backupPathPref.summary = folder.absolutePath
            }
            REQUEST_RESTORE_PATH -> {
                performRestore(folder)
            }
        }
    }
}
