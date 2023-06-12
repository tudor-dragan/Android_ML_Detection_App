package com.example.myapplication3

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import java.io.File

class ModelDownloadCompleteReceiver(downloadId: Long) : BroadcastReceiver() {
    private val downloadId = downloadId
    val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
    // when the model file is finished downloading, set the shared preferences to indicate that the model is ready to use
    @SuppressLint("Range")
    override fun onReceive(context: Context?, intent: Intent?) {
        if(intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if(id != -1L) {
                val downloadManager = context?.getSystemService(DownloadManager::class.java)
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager?.query(query)
                if(cursor?.moveToFirst() == true) {
                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    if(status == DownloadManager.STATUS_SUCCESSFUL) {
                        val uri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                        if(uri != null) {
                            val modelFile = File(uri)
                            //Toast.makeText(, "${modelFile.name} downloaded", Toast.LENGTH_SHORT).show()
                            Log.d("ModelDownloadCompleteReceiver", "${modelFile.name} downloaded")
//                            if(modelFile.exists()) {
//                                val sharedPreferences = context.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE)
//                                sharedPreferences.edit().putBoolean("modelReady", true).apply()
//                            }
                        }
                    }
                }
            }
        }
    }
}