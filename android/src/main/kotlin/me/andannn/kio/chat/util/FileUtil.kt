package me.andannn.kio.chat.util

import android.app.DownloadManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.CookieManager
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kio.chat.domain.MessageExtra
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.andannn.kio.chat.BuildConfig
import java.io.IOException

internal fun Uri.toChannelProvider(resolver: ContentResolver, size: Long?): ChannelProvider =
    ChannelProvider(size = size) {
        CoroutineScope(Dispatchers.IO).writer {
            val stream = resolver.openInputStream(this@toChannelProvider) ?: throw IOException("无法读取文件")
            stream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    channel.writeFully(buffer, 0, count)
                }
            }
        }.channel
    }

internal fun ContentResolver.queryFileMetadata(uri: Uri): Triple<String, Long?, String?> {
    var name = "file"
    var size: Long? = null
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0) {
                cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
            }
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                size = cursor.getLong(sizeIdx)
            }
        }
    }
    return Triple(name, size, getType(uri))
}

internal fun enqueueOrOpenFileDownload(
    context: Context,
    file: MessageExtra.File,
    onNotice: (String) -> Unit,
) {
    val safeName = file.name
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .filterNot { it == '\r' || it == '\n' || it == '\u0000' }
        .trim()
        .ifEmpty { "file" }
    val fullUrl = "${BuildConfig.BASE_URL}/${file.url.trimStart('/')}"
    val downloadManager = context.getSystemService(DownloadManager::class.java)
    if (downloadManager == null) {
        onNotice("当前设备不支持系统下载管理器。")
        return
    }

    findExistingDownloadedUri(context, downloadManager, fullUrl)?.let { existingUri ->
        openDownloadedUri(context, existingUri, file.mimeType, onNotice)
        return
    }

    val cookie = CookieManager.getInstance().getCookie(fullUrl)
    try {
        val request = DownloadManager.Request(Uri.parse(fullUrl)).apply {
            setTitle(safeName)
            setDescription("正在下载文件")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
            file.mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
            if (!cookie.isNullOrBlank()) {
                addRequestHeader("Cookie", cookie)
            }
        }
        downloadManager.enqueue(request)
        onNotice("已开始下载：$safeName")
    } catch (e: Exception) {
        onNotice(e.message ?: "下载启动失败。")
    }
}

private fun findExistingDownloadedUri(
    context: Context,
    downloadManager: DownloadManager,
    fullUrl: String,
): Uri? {
    val query = DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL)
    return try {
        downloadManager.query(query)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
            val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
            if (idIdx < 0 || uriIdx < 0) return null
            while (cursor.moveToNext()) {
                if (cursor.getString(uriIdx) == fullUrl) {
                    val downloadId = cursor.getLong(idIdx)
                    val localUri = downloadManager.getUriForDownloadedFile(downloadId) ?: continue
                    val readable = runCatching {
                        context.contentResolver.openInputStream(localUri)?.use { true } ?: false
                    }.getOrDefault(false)
                    if (readable) return localUri
                }
            }
            null
        }
    } catch (_: Exception) {
        null
    }
}

private fun openDownloadedUri(
    context: Context,
    uri: Uri,
    mimeType: String?,
    onNotice: (String) -> Unit,
) {
    try {
        val resolvedType = mimeType?.takeIf { it.isNotBlank() }
            ?: context.contentResolver.getType(uri)
            ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "打开文件"))
    } catch (_: Exception) {
        onNotice("未找到可打开此文件的应用。")
    }
}
