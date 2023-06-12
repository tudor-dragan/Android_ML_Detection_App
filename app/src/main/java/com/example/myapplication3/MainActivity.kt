package com.example.myapplication3

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dongliu.apk.parser.ApkFile
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.text.SimpleDateFormat





class MainActivity : AppCompatActivity() {
    private val SERVER_URL = "http://192.168.0.103:5000"
    private var features = HashMap<String, Float>()
    private val apps = ArrayList<ApplicationListModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        features = readFeatures()
        apps.addAll(getApps())
        setDefaultSharedPreferences()
        setUpRecyclerView(apps)
        updateButtonListener()
//        GlobalScope.launch(Dispatchers.IO) {
//            isUpdateNeeded()
//        }

        //getPermissions()
        //getInferenceResult()
    }

    private fun setUpRecyclerView(apps : ArrayList<ApplicationListModel>) {
        val recyclerView = findViewById<RecyclerView>(R.id.app_list)
        recyclerView.adapter = ListItemAdapter(this, apps)
        recyclerView.setHasFixedSize(true)
    }

    private fun getApps(): Collection<ApplicationListModel> {
        val appList = ArrayList<ApplicationListModel>()
        // get package manager
        val pm = packageManager
        // get a list of installed apps.
        val apps = pm.getInstalledApplications(0)
        for(app in apps) {
            if(app.sourceDir.startsWith("/data/app/")) {
                appList.add(ApplicationListModel(app.packageName, app.sourceDir))
            }
        }
        return appList
    }

    private fun updateButtonListener() {
        val updateButton = findViewById<Button>(R.id.update_btn)
        updateButton.setOnClickListener {
            GlobalScope.launch(Dispatchers.IO) {
                updateModel()
            }
        }
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


    private fun getPermissions() {
        // get package manager
        val pm = packageManager
        // get a list of installed apps.
        val apps = pm.getInstalledApplications(0)
        // log all installed applications to the console
        for (app in apps) {
            Log.d("installed", "Installed package :" + app.packageName)
            Log.d("installed", "Source dir : " + app.sourceDir)
            Log.d("installed", "Launch Activity :" + pm.getLaunchIntentForPackage(app.packageName))
        }

        var featureMap = HashMap<String, Float>()
        for (app in apps) {
            if(app.sourceDir.startsWith("/data/app/")) {
                featureMap = parseManifest(getManifest(app.sourceDir))
                for (feature in featureMap) {
                    Log.d("feature for ${app.packageName}", feature.key + ": " + feature.value)
                }
            }
            if(app.sourceDir.startsWith("/data/app/")) {
                for (feature in featureMap.keys) {
                    if (feature in features.keys) {
                        features[feature] = 1.0f
                    }
                }
                var res = getInferenceResult()
                Log.d("Final Inference result for ${app.packageName}", res.toString())
                for (key in features.keys) {
                    features[key] = 0.0f
                }
            }
            //reset features

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
        return false
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


    private fun parseManifest(xmlString: String): HashMap<String, Float> {
        val hashMap:HashMap<String, Float> = HashMap()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        val input = xmlString.reader()
        parser.setInput(input)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            var name = parser.name
            if(name != null) {
                when (name) {
                    "uses-permission" ->
                        hashMap[parser.getAttributeValue(null, "android:name")] = 1.0f
                    "uses-feature" -> hashMap[parser.getAttributeValue(null, "android:name")] = 1.0f
                    "action" -> hashMap[parser.getAttributeValue(null, "android:name")] = 1.0f
                    else -> {}
                }
            }
            eventType = parser.next()
        }
        return hashMap
    }

    // create ort session
    private fun createORTSession(ortEnvironment: OrtEnvironment) : OrtSession {
        val modelBytes = resources.openRawResource(R.raw.sk_rf_model).readBytes()
        val session = ortEnvironment.createSession(modelBytes)
        return session
    }

    // read the hardware, permissions and intents txt files and convert them to an hashmap of strings
    private fun readFeatures() : HashMap<String, Float> {
        val hashMap:HashMap<String, Float> = HashMap()
        val permissions = resources.openRawResource(R.raw.permissions).bufferedReader().useLines { it.toList() }
        val intents = resources.openRawResource(R.raw.intent).bufferedReader().useLines { it.toList() }
        val hardware = resources.openRawResource(R.raw.hardware).bufferedReader().useLines { it.toList() }
        permissions.forEach { hashMap[it] = 0.0f}
        intents.forEach { hashMap[it] = 0.0f}
        hardware.forEach { hashMap[it] = 0.0f}
        return hashMap
    }

    // create a float array from the hashmap values
    private fun createFloatArray(hashMap: HashMap<String, Float>) : FloatArray {
        val floatArray = FloatArray(hashMap.size)
        var i = 0
        hashMap.values.forEach { floatArray[i++] = it}
        return floatArray
    }

    // run inference
    private fun runInference( input: FloatArray, ortSession: OrtSession, ortEnvironment: OrtEnvironment) : LongArray {
        val inputName = ortSession.inputNames?.iterator()?.next()

        val floatBufferInput = FloatBuffer.wrap(input)
        // create input tensor
        val inputTensor = OnnxTensor.createTensor(ortEnvironment, floatBufferInput, longArrayOf(1, 613))
        val results = ortSession.run( mapOf( inputName to inputTensor ) )
        val out = results.get(0).value as LongArray
        Log.d("Ml-output-info", out[0].toString())
        for (i in out.indices) {
            Log.d("Ml-output-index", out[i].toString())
        }
        Log.d("Ml-output-size", out.size.toString())
        return out
    }


    // print results from inference
    private fun getInferenceResult() : Long {
        val random = createFloatArray(readFeatures())
        Log.d("random size", random.size.toString())
        val result = runInference(random, createORTSession(OrtEnvironment.getEnvironment()), OrtEnvironment.getEnvironment())
        // log result
        //Log.d("result of inference", result.toString())
        return result[0]
    }

    // get manifest from apk file
    fun getManifest(s: String): String {
        try {
            ApkFile(File(s)).use { apkFile -> return apkFile.manifestXml }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return "Error While Fetching Manifest"
    }

    // create a float array with values 0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,1.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0

}