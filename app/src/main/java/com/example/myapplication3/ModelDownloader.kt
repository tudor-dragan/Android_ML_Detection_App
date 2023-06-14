package com.example.myapplication3

import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri

var fileName = "sk_rf_model.onnx"

class ModelDownloader(private val context: Context) {
    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    var downloadId: Long = -1L

    /*
    * Download a model file from a given url. If the file already exists, delete it first.
    * @param url: the url to download the model from (the server)
     */
    fun downloadModel(url: String): Long {
        if(modelExists()) {
            deleteModel()
        }
        val request = DownloadManager.Request(url.toUri())
        request.setDestinationInExternalFilesDir(context, null, fileName)
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            .setTitle("Downloading model")
            .setDescription("Downloading model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        downloadId = downloadManager.enqueue(request)
        return downloadId
    }

    // check if a model file already exists
    private fun modelExists(): Boolean {
        val file = context.getExternalFilesDir(null)?.listFiles()?.find { it.name == fileName }
        return file != null
    }

    // delete a model file
    private fun deleteModel() {
        val file = context.getExternalFilesDir(null)?.listFiles()?.find { it.name == fileName }
        file?.delete()
    }
}