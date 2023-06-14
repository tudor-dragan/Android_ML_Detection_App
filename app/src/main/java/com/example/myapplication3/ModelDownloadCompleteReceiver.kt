package com.example.myapplication3

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDateTime

class ModelDownloadCompleteReceiver(private val downloadId: Long, private val sharedPreferences: SharedPreferences, private val SERVER_URL: String) : BroadcastReceiver() {
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
                            Log.d("ModelDownloadCompleteReceiver", "${modelFile.name} downloaded")
                            Log.d("ModelDownloaderReceiver", "path: ${modelFile.absolutePath}")
                            GlobalScope.launch(Dispatchers.IO) {
                                val remoteDate = getDate()
                                sharedPreferences.edit().putString("date", remoteDate).apply()
                                Log.d("After editing shared pref", "date: ${sharedPreferences.getString("date", "01/01/1999")}")
                            }

                            with (sharedPreferences.edit()) {
                                putString("modelUsed", modelFile.name)
                                putBoolean("modelReady", true)
                                commit()
                            }
                            Log.d("ModelDownloadCompleteReceiver", "date: ${sharedPreferences.getString("date", "01/01/2020")}")
                            Log.d("ModelDownloadCompleteReceiver", "modelUsed: ${sharedPreferences.getString("modelUsed", "base")}")
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

    private fun getDate() : String {
        val url = URL("$SERVER_URL/api/date")
        return try {
            val connection = url.openConnection()
            var data = connection.getInputStream().bufferedReader().readText()
            //get the Json value for date in data object
            data = data.substring(data.indexOf(":") + 2, data.indexOf("}") - 1)
            Log.d("getDate form url", "date: $data")
            data
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("getDate form url", "date: fell into exception")
            "01/01/2020"
        }
    }

    // old on receive function
//    override fun onReceive(context: Context?, intent: Intent?) {
//        if(intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
//            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
//            if(id != -1L) {
//                val downloadManager = context?.getSystemService(DownloadManager::class.java)
//                val query = DownloadManager.Query().setFilterById(downloadId)
//                val cursor = downloadManager?.query(query)
//                if(cursor?.moveToFirst() == true) {
//                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
//                    if(status == DownloadManager.STATUS_SUCCESSFUL) {
//                        val uri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
//                        if(uri != null) {
//                            val modelFile = File(uri)
//                            Log.d("ModelDownloadCompleteReceiver", "${modelFile.name} downloaded")
//                            Log.d("ModelDownloaderReceiver", "path: ${modelFile.absolutePath}")
//                            GlobalScope.launch(Dispatchers.IO) {
//                                val remoteDate = getDate()
//                                sharedPreferences.edit().putString("date", remoteDate).apply()
//                                Log.d("After editing shared pref", "date: ${sharedPreferences.getString("date", "01/01/1999")}")
//                            }
//
//                            with (sharedPreferences.edit()) {
//                                putString("modelUsed", modelFile.name)
//                                putBoolean("modelReady", true)
//                                commit()
//                            }
//                            Log.d("ModelDownloadCompleteReceiver", "date: ${sharedPreferences.getString("date", "01/01/2020")}")
//                            Log.d("ModelDownloadCompleteReceiver", "modelUsed: ${sharedPreferences.getString("modelUsed", "base")}")
////                            if(modelFile.exists()) {
////                                val sharedPreferences = context.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE)
////                                sharedPreferences.edit().putBoolean("modelReady", true).apply()
////                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
}