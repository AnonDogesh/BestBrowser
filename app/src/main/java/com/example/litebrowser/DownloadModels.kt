package com.example.litebrowser

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELED }

data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val status: DownloadStatus,
    val progress: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val filePath: String?
)
