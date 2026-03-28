package com.example.litebrowser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

object DownloadUtil {
    fun downloadImage(context: Context, url: String) {
        val filename = url.substringAfterLast('/').substringBefore('?')
            .ifBlank { "image_${System.currentTimeMillis()}.jpg" }
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(filename)
            setDescription("Downloading…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "LiteBrowser/$filename")
        }
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(context, "Saved to Pictures/LiteBrowser", Toast.LENGTH_SHORT).show()
    }
}
