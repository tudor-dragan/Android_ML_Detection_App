package com.example.myapplication3

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import net.dongliu.apk.parser.ApkFile
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.nio.FloatBuffer





class MainActivity : AppCompatActivity() {

    private var features = HashMap<String, Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        features = readFeatures()
        getPermissions()
        getInferenceResult()
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
//        session.inputNames?.forEach { Log.d("ML-Input", it) }
//        session.inputInfo?.forEach { Log.d("ML-Input", it.toString()) }
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

    // put 1's in random places in the hashmap\
    private fun randomizeFeatures(hashMap: HashMap<String, Float>) : HashMap<String, Float> {
//        val random = Random()
//        val keys = hashMap.keys.toList()
//        for (i in 0..10) {
//            val randomKey = keys[random.nextInt(keys.size)]
//            hashMap[randomKey] = 1.0f
//        }
//
//        hashMap["android.permission.ACCESS_ALL_DOWNLOADS"] = 1.0f
//        hashMap["android.permission.ACCESS_CHECKIN_PROPERTIES"] = 1.0f
//        hashMap["android.permission.CREATE_USERS"] = 1.0f
//        hashMap["android.permission.DELETE_PACKAGES"] = 1.0f
//        hashMap["android.permission.SEND_SMS_NO_CONFIRMATION"] = 1.0f
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
        val random = createFloatArray(randomizeFeatures(readFeatures()))
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