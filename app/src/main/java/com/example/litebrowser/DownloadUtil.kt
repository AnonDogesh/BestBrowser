package com.example.litebrowser

import android.content.Context
import android.widget.Toast

object DownloadUtil {
    fun downloadImage(context: Context, url: String) {
        DownloadCenter.start(context, url)
        Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
    }
}
