package com.example.litebrowser

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request

class FilterUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val lists = listOf(
            "https://easylist.to/easylist/easylist.txt",
            "https://easylist.to/easylist/easyprivacy.txt"
        )
        val client = OkHttpClient()
        val combined = lists.mapNotNull { url ->
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() }
            }.getOrNull()
        }.joinToString("\n")

        if (combined.isNotBlank()) {
            applicationContext.filesDir.resolve("easylists_cache.txt").writeText(combined)
            AdBlocker.init(applicationContext)
        }
        return Result.success()
    }
}
