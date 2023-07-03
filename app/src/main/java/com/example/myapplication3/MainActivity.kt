package com.example.myapplication3

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Bundle
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


val SERVER_URL = "http://192.168.0.104:5000"

class MainActivity : AppCompatActivity() {
    private var featuresMap = LinkedHashMap<String, Float>()
    private val apps = ArrayList<ApplicationModel>()
    private var selectedApps = BooleanArray(0)
    private var modelDownloader: ModelDownloader? = null
    private var receiver: ModelDownloadCompleteReceiver? = null
    private var textBox: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // creates the empty hash map of features that the model will use
        apps.addAll(getApps()) // add user installed apps to the list
        featuresMap = readFeatures() // read the features from the permissions, intents, and hardwdare files located in /res/raw
        selectedApps = BooleanArray(apps.size) // create a boolean array to keep track of which apps are selected in the UI
        modelDownloader = ModelDownloader(this)
        textBox = findViewById(R.id.app_scan_result) // the textbox output for the scan results

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
    // this should only happen when the application is installed for the first time
    private fun setDefaultSharedPreferences() {
        val sharedPreferences = this.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE)
        if(sharedPreferences.contains("date")) {
            return
        }
        with (sharedPreferences.edit()) {
            putString("date", "01/06/2020") // to check for a model update request
            putString("modelUsed", "base") // to know where to load the model from
            putBoolean("modelReady", true) // to know if the model is ready to be used after a download
            commit()
        }
    }

    // set up the components of the recycler view
    private fun setUpRecyclerView(apps : ArrayList<ApplicationModel>) {
        val recyclerView = findViewById<RecyclerView>(R.id.app_list)
        recyclerView.adapter = ListItemAdapter(this, apps, selectedApps)
        recyclerView.setHasFixedSize(true)
    }

    // update the model if the date of the model is older than the date of the last update
    private fun updateButtonListener() {
        val updateButton = findViewById<Button>(R.id.update_btn)
        updateButton.setOnClickListener {
            GlobalScope.launch(Dispatchers.IO) {
                if(isUpdateNeeded()){
                    runOnUiThread {
                        textBox?.setText("Updating model. Check download notification for progress.")
                    }
                    updateModel()
                } else {
                    runOnUiThread {
                        textBox?.setText("Model is up to date")
                    }
                }
            }
        }
    }

    // scan all applications selected in the UI
    private fun scanButtonListener() {
        val scanButton = findViewById<Button>(R.id.scan_btn)
        var string: String
        scanButton.setOnClickListener {
            if(this.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE).getBoolean("modelReady", false)) {
                GlobalScope.launch(Dispatchers.Default) {
                    runOnUiThread() {
                        textBox?.setText("Scanning...")
                    }
                    string = scanApplications()
                    runOnUiThread {
                        textBox?.setText(string)
                    }
                }
            } else {
                textBox?.setText("Model not ready")
            }
        }
    }

    // get the user-installed apps on the device
    private fun getApps(): Collection<ApplicationModel> {
        val appList = ArrayList<ApplicationModel>()
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
            var result: Long
            if(selectedApps[apps.indexOf(app)]) {
                result = scanner.scan(app)
                //write a line in the text view to show the result of the scan
                scanText.append("${app.appName} : ${if(result == 1L){
                    "Malicious"
                } else {
                    "Benign"
                }}\n")
            }
        }
        return scanText.toString()
    }

    // fetch the model from the server and update the model
    private fun updateModel() {
        // unregister the receiver if it is already registered
        if(receiver != null) {
            unregisterReceiver(receiver)
        }
        // set a download id and register the receiver
        val downloadId = modelDownloader!!.downloadModel("$SERVER_URL/static/sk_rf_model.onnx")
        val sharedPreferences = this.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("modelReady", false).apply()
        receiver = ModelDownloadCompleteReceiver(downloadId, sharedPreferences, SERVER_URL)
        registerReceiver(receiver, receiver!!.filter)
    }


    // function that will go to the server and
    // compare the date returned as json by the url with the date in shared preferences
    private fun isUpdateNeeded() : Boolean{
        val sharedPref = this.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE)
        val date = sharedPref.getString("date", "01/06/2023") // local model date
        val url = URL("$SERVER_URL/api/date")
        try {
            val connection = url.openConnection()
            var data = connection.getInputStream().bufferedReader().readText()
            //get the Json value for date in data object
            data = data.substring(data.indexOf(":") + 2, data.indexOf("}") - 1) // remote model date
            val dateFormat = SimpleDateFormat("dd/mm/yyyy")
            val localDate = date?.let { dateFormat.parse(it) }
            val remoteDate = dateFormat.parse(data)
            if (remoteDate != null) {
                return remoteDate.after(localDate)
            }
        } catch (e: Exception) {
            // send a notification that the connection failed
            runOnUiThread() {
                textBox?.setText("Connection to server failed")
            }
            return false
        }
        return false
    }

    /*
    * This function will create an ort session from the model in the resources folder
    * or from the model in the external files directory if it exists
     */
    private fun createORTSession(ortEnvironment: OrtEnvironment): OrtSession {
        // if using base model in shared preferences then load the model in resources
        val sharedPreferences = this.getSharedPreferences("com.example.myapplication3", Context.MODE_PRIVATE)
        val fileName = sharedPreferences.getString("modelUsed", "base")
        val model = R.raw.sk_svc_model


        // if using the base model or if the external files directory does not exist
        if ( fileName == "base") {
            val modelBytes = resources.openRawResource(model).readBytes()
            return ortEnvironment.createSession(modelBytes)
        } else if (this.getExternalFilesDir(null) != null){
            val latestModel = getMostRecentFile(this.getExternalFilesDir(null))
            try {
                val modelBytes = latestModel.readBytes()
                return ortEnvironment.createSession(modelBytes)
            } catch (e: Exception) {
                val modelBytes = resources.openRawResource(model).readBytes()
                return ortEnvironment.createSession(modelBytes)
            }
        } else {
            val modelBytes = resources.openRawResource(model).readBytes()
            return ortEnvironment.createSession(modelBytes)
        }
    }

    /*
    * This function will return the most recent file in the directory with the .onnx extension
    * This is meant to retrieve the most recent model downloaded from the server
     */
    private fun getMostRecentFile(directory: File?) : File {
        if(directory == null) {
            this
            return File("")
        }
        val files = directory.listFiles()
        var mostRecentFile = files[0]
        for (file in files) {
            if(file.lastModified() > mostRecentFile.lastModified() && file.name.endsWith(".onnx")) {
                mostRecentFile = file
            }
        }
        return mostRecentFile
    }

    /*
    * This function will read the features from the txt files in the raw folder
    * and return a linked hash map of the features
    * The features are the permissions, intents and hardware features
    * A linked hash map is used to preserve the order of the features
     */
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