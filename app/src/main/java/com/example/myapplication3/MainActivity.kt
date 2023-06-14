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

val SERVER_URL = "http://192.168.0.104:5000"

class MainActivity : AppCompatActivity() {
    private var featuresMap = LinkedHashMap<String, Float>()
    private val apps = ArrayList<ApplicationModel>()
    private var selectedApps = BooleanArray(0)
    private var modelDownloader: ModelDownloader? = null
    private var receiver: ModelDownloadCompleteReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // creates the empty hash map of features that the model will use
        apps.addAll(getApps())
        featuresMap = readFeatures()
        selectedApps = BooleanArray(apps.size)
        modelDownloader = ModelDownloader(this)

        setDefaultSharedPreferences()
        setUpRecyclerView(apps)
        updateButtonListener()
        scanButtonListener()
        this.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE).edit().putString("date", "01/01/1999").apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    // set date and base model shared preferences if there are none
    private fun setDefaultSharedPreferences() {
        val sharedPreferences = this.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE)
        if(sharedPreferences.contains("date")) {
            Log.d("shared preferences", "date already exists ${sharedPreferences.getString("date", "01/06/1990")}")
            return
        }
        with (sharedPreferences.edit()) {
            Log.d("shared preferences", "date does not exist")
            putString("date", "01/06/2020")
            putString("modelUsed", "base")
            putBoolean("modelReady", true)
            commit()
        }
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
            if(this.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE).getBoolean("modelReady", false)) {
                GlobalScope.launch(Dispatchers.Default) {
                    runOnUiThread() {
                        scanResultEditText.setText("Scanning...")
                    }
                    string = scanApplications()
                    runOnUiThread {
                        scanResultEditText.setText(string)
                    }
                }
            } else {
                scanResultEditText.setText("Model not ready")
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
                appList.add(ApplicationModel(app.packageName, pm.getApplicationLabel(app).toString(),
                    app.sourceDir, pm.getApplicationIcon(app.packageName)))
            }
        }
        return appList
    }

    // scan all checked applications in the recycler view
    private fun scanApplications() : String {
        val scanText = StringBuilder()
        val scanner = AppScanner(createORTSession(OrtEnvironment.getEnvironment()), featuresMap)

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
            if(receiver != null) {
                unregisterReceiver(receiver)
            }
            val downloadId = modelDownloader!!.downloadModel("$SERVER_URL/static/sk_rf_model.onnx")
            val sharedPreferences = this.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE)
            sharedPreferences.edit().putBoolean("modelReady", false).apply()
            receiver = ModelDownloadCompleteReceiver(downloadId, sharedPreferences, SERVER_URL)
            registerReceiver(receiver, receiver!!.filter)
            Log.d("updateModel", "Model is updating")
        } else {
            Log.d("updateModel", "Model is up to date")
        }
    }


    // function that will go to a url (http://127.0.0.1:5000/api/date) and
    // compare the date returned as json by the url with the date in shared preferences
    private fun isUpdateNeeded() : Boolean{
        val sharedPref = this.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE)
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
            return false
        }
        return false
    }

    // create ort session
    private fun createORTSession(ortEnvironment: OrtEnvironment): OrtSession {
        // if using base model in shared preferences then load the model in resources
        val sharedPreferences = this.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE)
        val fileName = sharedPreferences.getString("modelUsed", "base")
        Log.d("loading model", "model loaded: $fileName")
        Log.d("loading model", "model loaded: ${sharedPreferences.getString("modelUsed", "notbase")}")
        if ( fileName == "base") {
            val modelBytes = resources.openRawResource(R.raw.sk_rf_model).readBytes()
            return ortEnvironment.createSession(modelBytes)
        } else if (this.getExternalFilesDir(null) != null){

            val latestModel = getMostRecentFile(this.getExternalFilesDir(null))
            try {
                Log.d("loading model", "model loaded: ${latestModel.name}")
                val modelBytes = latestModel.readBytes()
                return ortEnvironment.createSession(modelBytes)
            } catch (e: Exception) {
                val modelBytes = resources.openRawResource(R.raw.sk_rf_model).readBytes()
                return ortEnvironment.createSession(modelBytes)
            }
        } else {
            val modelBytes = resources.openRawResource(R.raw.sk_rf_model).readBytes()
            return ortEnvironment.createSession(modelBytes)
        }
    }

    // get the most recent file in the downloads directory with the .onnx extension
    private fun getMostRecentFile(directory: File?) : File {
        if(directory == null) {
            this
            return File("")
        }
        val files = directory.listFiles()
        Log.d("files", files.toString())
        var mostRecentFile = files[0]
        for (file in files) {
            if(file.lastModified() > mostRecentFile.lastModified() && file.name.endsWith(".onnx")) {
                mostRecentFile = file
            }
        }
        return mostRecentFile
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
}