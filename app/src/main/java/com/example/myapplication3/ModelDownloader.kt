package com.example.myapplication3

import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri

class ModelDownloader(private val context: Context) {
    private val downloadManager = context.getSystemService(DownloadManager::class.java)

    fun downloadModel(url: String, fileName: String): Long {
        val request = DownloadManager.Request(url.toUri())
        request.setDestinationInExternalFilesDir(context, null, fileName)
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            .setTitle("Downloading model")
            .setDescription("Downloading model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        return downloadManager.enqueue(request)
    }
}