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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DownloadCenter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads

    private val calls = ConcurrentHashMap<String, okhttp3.Call>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, null).orEmpty()
        if (raw.isBlank()) return

        runCatching {
            val arr = JSONArray(raw)
            val restored = mutableListOf<DownloadItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                restored.add(
                    DownloadItem(
                        id = o.getString("id"),
                        url = o.getString("url"),
                        fileName = o.getString("fileName"),
                        status = DownloadStatus.valueOf(o.getString("status")),
                        progress = o.getInt("progress"),
                        downloadedBytes = o.getLong("downloadedBytes"),
                        totalBytes = o.getLong("totalBytes"),
                        filePath = o.optString("filePath").ifBlank { null }
                    )
                )
            }
            _downloads.value = restored
        }
    }

    fun start(context: Context, url: String): String {
        init(context)
        val id = UUID.randomUUID().toString()
        val filename = initialFileName(url)
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "LiteBrowser")
        dir.mkdirs()
        val file = File(dir, filename)

        _downloads.update {
            listOf(DownloadItem(id, url, filename, DownloadStatus.QUEUED, 0, 0L, -1L, file.absolutePath)) + it
        }
        persist(context)

        downloadFrom(context, id, file, 0L)
        return id
    }

    fun pause(context: Context, id: String) {
        calls[id]?.cancel()
        jobs[id]?.cancel()
        _downloads.update { list -> list.map { if (it.id == id && it.status == DownloadStatus.DOWNLOADING) it.copy(status = DownloadStatus.PAUSED) else it } }
        persist(context)
    }

    fun resume(context: Context, id: String) {
        val item = _downloads.value.firstOrNull { it.id == id } ?: return
        val file = item.filePath?.let(::File) ?: return
        val downloaded = if (file.exists()) file.length() else 0L
        downloadFrom(context, id, file, downloaded)
    }

    fun cancel(context: Context, id: String) {
        calls[id]?.cancel()
        jobs[id]?.cancel()
        _downloads.update { list -> list.map { if (it.id == id) it.copy(status = DownloadStatus.CANCELED) else it } }
        persist(context)
    }

    fun delete(context: Context, id: String) {
        calls[id]?.cancel()
        jobs[id]?.cancel()
        val file = _downloads.value.firstOrNull { it.id == id }?.filePath?.let(::File)
        if (file?.exists() == true) file.delete()
        _downloads.update { list -> list.filterNot { it.id == id } }
        persist(context)
    }

    private fun downloadFrom(context: Context, id: String, file: File, offset: Long) {
        val item = _downloads.value.firstOrNull { it.id == id } ?: return
        jobs[id]?.cancel()
        jobs[id] = scope.launch {
            try {
                _downloads.update { list -> list.map { if (it.id == id) it.copy(status = DownloadStatus.DOWNLOADING) else it } }
                persist(context)

                val reqBuilder = Request.Builder().url(item.url)
                if (offset > 0) reqBuilder.addHeader("Range", "bytes=$offset-")
                val call = client.newCall(reqBuilder.build())
                calls[id] = call

                val res = call.execute()
                val body = res.body ?: throw IllegalStateException("Empty response")
                val total = if (body.contentLength() > 0) body.contentLength() + offset else -1L
                val contentType = body.contentType()?.toString().orEmpty()
                if (offset == 0L) {
                    val updatedName = betterFileName(item.url, res.header("Content-Disposition"), contentType)
                    if (updatedName != file.name) {
                        val renamed = File(file.parentFile, updatedName)
                        if (!renamed.exists()) {
                            _downloads.update { list ->
                                list.map {
                                    if (it.id == id) it.copy(fileName = updatedName, filePath = renamed.absolutePath) else it
                                }
                            }
                            file.delete()
                            renamed.createNewFile()
                            persist(context)
                        }
                    }
                }
                val targetPath = _downloads.value.firstOrNull { it.id == id }?.filePath ?: file.absolutePath
                val targetFile = File(targetPath)

                FileOutputStream(targetFile, offset > 0).use { fos ->
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
                persist(context)
            } catch (_: Exception) {
                val state = _downloads.value.firstOrNull { it.id == id }?.status
                if (state != DownloadStatus.PAUSED && state != DownloadStatus.CANCELED) {
                    _downloads.update { list -> list.map { if (it.id == id) it.copy(status = DownloadStatus.FAILED) else it } }
                    persist(context)
                }
            }
        }
    }

    private fun persist(context: Context) {
        val arr = JSONArray()
        _downloads.value.forEach { d ->
            arr.put(
                JSONObject().apply {
                    put("id", d.id)
                    put("url", d.url)
                    put("fileName", d.fileName)
                    put("status", d.status.name)
                    put("progress", d.progress)
                    put("downloadedBytes", d.downloadedBytes)
                    put("totalBytes", d.totalBytes)
                    put("filePath", d.filePath ?: "")
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private const val PREFS = "download_center"
    private const val KEY_LIST = "download_list"

    private fun initialFileName(url: String): String {
        val base = URLDecoder.decode(url.substringAfterLast('/').substringBefore('?'), Charsets.UTF_8.name())
            .ifBlank { "image_${System.currentTimeMillis()}" }
            .take(80)
        return if (base.contains('.')) base else "$base.jpg"
    }

    private fun betterFileName(url: String, contentDisposition: String?, contentType: String): String {
        val fromHeader = contentDisposition
            ?.substringAfter("filename=", "")
            ?.trim('"', '\'', ' ')
            ?.takeIf { it.isNotBlank() }
        if (fromHeader != null) return fromHeader

        val base = URLDecoder.decode(url.substringAfterLast('/').substringBefore('?'), Charsets.UTF_8.name())
            .ifBlank { "image_${System.currentTimeMillis()}" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)

        if (base.contains('.')) return base
        val ext = when {
            contentType.contains("png", ignoreCase = true) -> ".png"
            contentType.contains("webp", ignoreCase = true) -> ".webp"
            contentType.contains("gif", ignoreCase = true) -> ".gif"
            contentType.contains("avif", ignoreCase = true) -> ".avif"
            contentType.contains("svg", ignoreCase = true) -> ".svg"
            else -> ".jpg"
        }
        return "$base$ext"
    }
}
