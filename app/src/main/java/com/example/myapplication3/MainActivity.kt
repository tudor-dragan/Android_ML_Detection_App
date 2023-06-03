package com.example.myapplication3

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.ValueInfo
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import java.nio.FloatBuffer
import java.util.Random


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        getPermissions()
        printResults()
    }


    private fun getPermissions() {
        Log.d("test", "getPermissions")
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
        // get intent filters of installed application
        val intent = pm.getLaunchIntentForPackage("com.example.myapplication3")
        val intents = intent?.let { pm.queryIntentActivities(it, PackageManager.GET_INTENT_FILTERS) }

        // log all permissions of installed application to the console
        for (app in apps) {
            if(app.sourceDir.startsWith("/data/app/")) {
                try {
                    val packageInfo =
                        pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                    //Get Permissions
                    val requestedPermissions = packageInfo.requestedPermissions
                    if (requestedPermissions != null) {
                        for (i in requestedPermissions.indices) {
                            Log.d("test", requestedPermissions[i])
                        }
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                }
            }
        }
        createORTSession(OrtEnvironment.getEnvironment())

    }
    // create ort session
    private fun createORTSession(ortEnvironment: OrtEnvironment) : OrtSession {
        val modelBytes = resources.openRawResource(R.raw.sk_rf_model).readBytes()
        val session = ortEnvironment.createSession(modelBytes)
        session.inputNames?.forEach { Log.d("ML-Input", it) }
        session.inputInfo?.forEach { Log.d("ML-Input", it.toString()) }
        return session
    }
    // read the hardware, permissions and intents txt files and convert them to an hashmap of strings
    private fun readFeatures() : HashMap<String, Float> {
        val hashMap:HashMap<String, Float> = HashMap()
        val permissions = resources.openRawResource(R.raw.permissions).bufferedReader().useLines { it.toList() }
        val intents = resources.openRawResource(R.raw.intent).bufferedReader().useLines { it.toList() }
        val hardware = resources.openRawResource(R.raw.hardware).bufferedReader().useLines { it.toList() }
        permissions.forEach { hashMap[it] = 0.0f }
        intents.forEach { hashMap[it] = 0.0f }
        hardware.forEach { hashMap[it] = 0.0f }
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
        hashMap["android.permission.ACCESS_ALL_DOWNLOADS"] = 1.0f
        hashMap["android.permission.ACCESS_CHECKIN_PROPERTIES"] = 1.0f
        hashMap["android.permission.CREATE_USERS"] = 1.0f
        hashMap["android.permission.DELETE_PACKAGES"] = 1.0f
        hashMap["android.permission.SEND_SMS_NO_CONFIRMATION"] = 1.0f
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
        Log.d("Ml-output-info", out.toString())
        Log.d("Ml-output-info", out[0].toString())
        return out
    }

    // print results from inference
    private fun printResults() {
        val random = createFloatArray(randomizeFeatures(readFeatures()))
        Log.d("random size", random.size.toString())
        val result = runInference(random, createORTSession(OrtEnvironment.getEnvironment()), OrtEnvironment.getEnvironment())
        // log result
        Log.d("result of inference", result.toString())
    }
}