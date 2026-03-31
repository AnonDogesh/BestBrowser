package com.example.litebrowser

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class ImageJsBridge(private val callback: (imageUrl: String) -> Unit) {
    @JavascriptInterface
    fun onImageLongPress(imageUrl: String) {
        Handler(Looper.getMainLooper()).post { callback(imageUrl) }
    }
}
