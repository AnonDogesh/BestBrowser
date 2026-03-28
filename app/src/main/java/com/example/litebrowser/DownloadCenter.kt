package com.example.litebrowser

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DownloadCenter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads

    private val calls = ConcurrentHashMap<String, okhttp3.Call>()
    private val jobs = ConcurrentHashMap<String, Job>()

    fun start(context: Context, url: String): String {
        val id = UUID.randomUUID().toString()
        val filename = url.substringAfterLast('/').substringBefore('?').ifBlank { "file_${System.currentTimeMillis()}" }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "LiteBrowser")
        dir.mkdirs()
        val file = File(dir, filename)

        _downloads.update {
            listOf(
                DownloadItem(id, url, filename, DownloadStatus.QUEUED, 0, 0L, -1L, file.absolutePath)
            ) + it
        }

        downloadFrom(context, id, file, 0L)
        return id
    }

    fun pause(id: String) {
        calls[id]?.cancel()
        jobs[id]?.cancel()
        _downloads.update { list -> list.map { if (it.id == id && it.status == DownloadStatus.DOWNLOADING) it.copy(status = DownloadStatus.PAUSED) else it } }
    }

    fun resume(context: Context, id: String) {
        val item = _downloads.value.firstOrNull { it.id == id } ?: return
        val file = item.filePath?.let(::File) ?: return
        val downloaded = if (file.exists()) file.length() else 0L
        downloadFrom(context, id, file, downloaded)
    }

    fun cancel(id: String) {
        calls[id]?.cancel()
        jobs[id]?.cancel()
        val file = _downloads.value.firstOrNull { it.id == id }?.filePath?.let(::File)
        if (file?.exists() == true) file.delete()
        _downloads.update { list -> list.map { if (it.id == id) it.copy(status = DownloadStatus.CANCELED, progress = 0) else it } }
    }

    private fun downloadFrom(context: Context, id: String, file: File, offset: Long) {
        val item = _downloads.value.firstOrNull { it.id == id } ?: return
        jobs[id]?.cancel()
        jobs[id] = scope.launch {
            try {
                _downloads.update { list -> list.map { if (it.id == id) it.copy(status = DownloadStatus.DOWNLOADING) else it } }

                val reqBuilder = Request.Builder().url(item.url)
                if (offset > 0) reqBuilder.addHeader("Range", "bytes=$offset-")
                val call = client.newCall(reqBuilder.build())
                calls[id] = call

                val res = call.execute()
                val body = res.body ?: throw IllegalStateException("Empty response")
                val total = if (body.contentLength() > 0) body.contentLength() + offset else -1L

                FileOutputStream(file, offset > 0).use { fos ->
                    val input = body.byteStream()
                    val buf = ByteArray(16 * 1024)
                    var read: Int
                    var downloaded = offset
                    while (input.read(buf).also { read = it } != -1) {
                        fos.write(buf, 0, read)
                        downloaded += read
                        val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        _downloads.update { list ->
                            list.map {
                                if (it.id == id) it.copy(
                                    status = DownloadStatus.DOWNLOADING,
                                    progress = progress.coerceIn(0, 100),
                                    downloadedBytes = downloaded,
                                    totalBytes = total
                                ) else it
                            }
                        }
                    }
                }

                _downloads.update { list -> list.map { if (it.id == id) it.copy(status = DownloadStatus.COMPLETED, progress = 100) else it } }
            } catch (ce: Exception) {
                val state = _downloads.value.firstOrNull { it.id == id }?.status
                if (state != DownloadStatus.PAUSED && state != DownloadStatus.CANCELED) {
                    _downloads.update { list -> list.map { if (it.id == id) it.copy(status = DownloadStatus.FAILED) else it } }
                }
            }
        }
    }
}
