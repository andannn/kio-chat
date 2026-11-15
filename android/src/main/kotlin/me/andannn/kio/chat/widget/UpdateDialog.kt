package me.andannn.kio.chat.widget

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import java.io.File
import kio.chat.domain.AppVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.andannn.kio.chat.BuildConfig
import me.andannn.kio.chat.ChatService
import androidx.core.net.toUri

@Composable
fun UpdateDialog(version: AppVersion, service: ChatService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var apkPath by rememberSaveable(version.versionCode) { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun install() {
        try {
            val apk = apkPath?.let(::File)?.takeIf { it.isFile }
            if (apk == null) {
                apkPath = null
                error = "安装包已失效，请重新下载。"
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            error = "无法打开安装界面，请重试。"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) install()
        else error = "请允许安装此来源的应用后重试。"
    }

    fun requestInstall() {
        if (context.packageManager.canRequestPackageInstalls()) {
            install()
        } else {
            try {
                permissionLauncher.launch(Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri(),
                ))
            } catch (_: Exception) {
                error = "无法打开安装授权设置，请在系统设置中允许安装此来源的应用。"
            }
        }
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("请更新后继续使用") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("当前版本：${BuildConfig.VERSION_NAME}\n最新版本：${version.versionName}")
                if (downloading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("正在下载，请稍候…")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = {
                    error = null
                    if (apkPath != null) {
                        requestInstall()
                    } else {
                        downloading = true
                        scope.launch {
                            var apk: File? = null
                            try {
                                apk = withContext(Dispatchers.IO) {
                                    val directory = File(context.cacheDir, "updates")
                                    check(directory.isDirectory || directory.mkdirs())
                                    File(directory, "kio-chat-${version.versionCode}.apk")
                                }
                                val (_, downloadError) = service.downloadApk(apk)
                                if (downloadError != null) {
                                    error = "下载失败，请检查网络后重试。"
                                    return@launch
                                }
                                val valid = withContext(Dispatchers.IO) {
                                    context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.let {
                                        val currentPkgName = if (BuildConfig.DEBUG) {
                                            context.packageName.removeSuffix(".debug")
                                        } else {
                                            context.packageName
                                        }
                                        it.packageName == currentPkgName && it.longVersionCode >= version.versionCode
                                    } == true
                                }
                                if (!valid) {
                                    apk.delete()
                                    error = "安装包版本或应用不匹配，请重新下载。"
                                    return@launch
                                }
                                apkPath = apk.absolutePath
                                requestInstall()
                            } catch (cancelled: CancellationException) {
                                if (apkPath == null) apk?.delete()
                                throw cancelled
                            } catch (_: Exception) {
                                apk?.delete()
                                error = "下载失败，请稍后重试。"
                            } finally {
                                downloading = false
                            }
                        }
                    }
                },
            ) { Text(if (downloading) "下载中…" else if (apkPath != null) "安装更新" else "下载并安装") }
        },
    )
}
