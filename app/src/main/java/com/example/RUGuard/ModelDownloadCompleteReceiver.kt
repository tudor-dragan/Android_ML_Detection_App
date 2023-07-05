package com.example.RUGuard

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

class ModelDownloadCompleteReceiver(private val downloadId: Long, private val sharedPreferences: SharedPreferences, private val SERVER_URL: String) : BroadcastReceiver() {
    val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)

    /*
    * When the download is complete, get the uri of the downloaded file and save it to shared preferences.
    * Also save the date of the model to shared preferences.
    * This uses a downloadId to identify the download that was completed was the one that was started by this app.
     */
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
                            GlobalScope.launch(Dispatchers.IO) {
                                val remoteDate = getDate()
                                sharedPreferences.edit().putString("date", remoteDate).apply()
                            }

                            with (sharedPreferences.edit()) {
                                putString("modelUsed", modelFile.name)
                                putBoolean("modelReady", true)
                                commit()
                            }
                        }
                    }
                }
            }
        }
    }

    // get the date of the model from the server
    private fun getDate() : String {
        val url = URL("$SERVER_URL/api/date")
        return try {
            val connection = url.openConnection()
            var data = connection.getInputStream().bufferedReader().readText()
            //get the Json value for date in data object
            data = data.substring(data.indexOf(":") + 2, data.indexOf("}") - 1)
            data
        } catch (e: Exception) {
            e.printStackTrace()
            "01/01/2020"
        }
    }
}