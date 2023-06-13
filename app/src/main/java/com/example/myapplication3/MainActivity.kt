package com.example.myapplication3

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import kotlin.math.log


class MainActivity : AppCompatActivity() {
    private val SERVER_URL = "http://192.168.0.103:5000"
    private var featuresMap = LinkedHashMap<String, Float>()
    private var featuresArray = ArrayList<String>()
    private val apps = ArrayList<ApplicationModel>()
    private var selectedApps = BooleanArray(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // creates the empty hash map of features that the model will use
        apps.addAll(getApps())
        featuresMap = readFeatures()
        featuresArray = readFeaturesArray()
        selectedApps = BooleanArray(apps.size)

        setDefaultSharedPreferences()
        setUpRecyclerView(apps)
        updateButtonListener()
        scanButtonListener()
    }

    private fun setUpRecyclerView(apps : ArrayList<ApplicationModel>) {
        val recyclerView = findViewById<RecyclerView>(R.id.app_list)
        recyclerView.adapter = ListItemAdapter(this, apps, selectedApps)
        recyclerView.setHasFixedSize(true)
    }

    private fun updateButtonListener() {
        val updateButton = findViewById<Button>(R.id.update_btn)
        updateButton.setOnClickListener {
            GlobalScope.launch(Dispatchers.IO) {
                updateModel()
            }
        }
    }

    private fun scanButtonListener() {
        val scanButton = findViewById<Button>(R.id.scan_btn)
        val scanResultEditText = findViewById<EditText>(R.id.app_scan_result)
        var string = ""
        scanButton.setOnClickListener {
            GlobalScope.launch(Dispatchers.Default) {
                runOnUiThread() {
                    scanResultEditText.setText("Scanning...")
                }
                string = scanApplications()
                runOnUiThread {
                    scanResultEditText.setText(string)
                }
            }
        }

    }

    private fun getApps(): Collection<ApplicationModel> {
        val appList = ArrayList<ApplicationModel>()
        // get package manager
        val pm = packageManager
        // get a list of installed apps.
        val apps = pm.getInstalledApplications(0)
        for(app in apps) {
            // user installed apps go in /data/app/
            // this checks so we do not scan system apps
            if(app.sourceDir.startsWith("/data/app/")) {
                appList.add(ApplicationModel(app.packageName, pm.getApplicationLabel(app).toString(), app.sourceDir, pm.getApplicationIcon(app.packageName)))
            }
        }
        return appList
    }

    // scan all checked applications in the recycler view
    private fun scanApplications() : String {
        val scanText = StringBuilder()
        val scanner = AppScanner(createORTSession(OrtEnvironment.getEnvironment()), featuresMap)

        // for testing purposes
        // scanner.runTest()

        for (app in apps) {
            var result: Long = 0
            if(selectedApps[apps.indexOf(app)]) {
                result = scanner.scan(app)
                //write a line in the text view to show the result of the scan
                scanText.append("${app.appName} : $result\n")
            }
        }
        return scanText.toString()
    }

    private fun updateModel() {
        if(isUpdateNeeded()) {
            val modelDownloader = ModelDownloader(this)
            val downloadId = modelDownloader.downloadModel("$SERVER_URL/static/sk_rf_model.onnx", "model.onnx")
            val sharedPreferences = this.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE)
            sharedPreferences.edit().putBoolean("modelReady", false).apply()
            val modelDownloadCompleteReceiver = ModelDownloadCompleteReceiver(downloadId)
            registerReceiver(modelDownloadCompleteReceiver, modelDownloadCompleteReceiver.filter)
            Log.d("updateModel", "Model is updating")
        } else {
            Log.d("updateModel", "Model is up to date")
        }
    }


    // set date and base model shared preferences if there are none
    private fun setDefaultSharedPreferences() {
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
        if(sharedPref.contains("date")) {
            Log.d("shared preferences", "date already exists")
            return
        }
        with (sharedPref.edit()) {
            Log.d("shared preferences", "date does not exist")
            putString("date", "01/06/2020")
            putBoolean("usingBaseModel", true)
            putBoolean("modelReady", true)
            commit()
        }
    }

    // function that will go to a url (http://127.0.0.1:5000/api/date) and
    // compare the date returned as json by the url with the date in shared preferences
    private fun isUpdateNeeded() : Boolean{
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return false
        val date = sharedPref.getString("date", "01/06/2023")
        val url = URL("$SERVER_URL/api/date")
        var data = ""
        try {
            val connection = url.openConnection()
            var data = connection.getInputStream().bufferedReader().readText()
            //get the Json value for date in data object
            data = data.substring(data.indexOf(":") + 2, data.indexOf("}") - 1)


            val dateFormat = SimpleDateFormat("dd/mm/yyyy")
            val localDate = date?.let { dateFormat.parse(it) }
            val remoteDate = dateFormat.parse(data)
            Log.d("local date", localDate.toString())
            Log.d("remote date", remoteDate.toString())

            if (remoteDate != null) {
                Log.d("is update needed", remoteDate.after(localDate).toString())
                return remoteDate.after(localDate)
            }
        } catch (e: Exception) {
            // send a notification that the connection failed
            Log.d("isUpdateNeeded", "Connection failed")
        } finally {
            return false
        }
    }

    // download a file into internal storage from a url
    private fun downloadFile(url: String, fileName: String): Long {
        val request = DownloadManager.Request(Uri.parse(url))
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setTitle("Download")
        request.setDescription("Downloading file...")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    // use the downloaded file to load a new model into the ort session
    private fun loadModel(fileName: String) {
        val ortEnvironment = OrtEnvironment.getEnvironment()
        val modelBytes = File(this.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName).readBytes()
        val session = ortEnvironment.createSession(modelBytes)
    }




    // create ort session
    private fun createORTSession(ortEnvironment: OrtEnvironment): OrtSession {
        val modelBytes = resources.openRawResource(R.raw.sk_rf_model).readBytes()
        return ortEnvironment.createSession(modelBytes)
    }

    // read the hardware, permissions and intents txt files and convert them to an hashmap of strings
    private fun readFeatures() : LinkedHashMap<String, Float> {
        val hashMap:LinkedHashMap<String, Float> = LinkedHashMap()
        val permissions = resources.openRawResource(R.raw.permissions).bufferedReader().useLines { it.toList() }
        val intents = resources.openRawResource(R.raw.intent).bufferedReader().useLines { it.toList() }
        val hardware = resources.openRawResource(R.raw.hardware).bufferedReader().useLines { it.toList() }
        permissions.forEach { hashMap[it] = 0.0f}
        intents.forEach { hashMap[it] = 0.0f}
        hardware.forEach { hashMap[it] = 0.0f}
        return hashMap
    }

    // read the hardware, permissions and intents txt files and convert them to an array of strings
    private fun readFeaturesArray() : ArrayList<String> {
        val permissions = resources.openRawResource(R.raw.permissions).bufferedReader().useLines { it.toList() }
        val intents = resources.openRawResource(R.raw.intent).bufferedReader().useLines { it.toList() }
        val hardware = resources.openRawResource(R.raw.hardware).bufferedReader().useLines { it.toList() }
        return ArrayList(permissions + intents + hardware)
    }

}