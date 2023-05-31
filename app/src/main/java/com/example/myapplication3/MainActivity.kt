package com.example.myapplication3

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import java.nio.FloatBuffer

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        getPermissions()
    }


    fun getPermissions() {
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

    private fun createORTSession(ortEnvironment: OrtEnvironment) : OrtSession {
        val modelBytes = resources.openRawResource(R.raw.sk_rf_model).readBytes()
        val session = ortEnvironment.createSession(modelBytes)
        session.inputNames?.forEach { Log.d("ML-Input", it) }
        return session
    }

    private fun runInference( input: FloatArray, ortSession: OrtSession, ortEnvironment: OrtEnvironment) : FloatArray {
        val inputName = ortSession.inputNames?.iterator()?.next()
        val FloatBufferInput = FloatBuffer.wrap(floatArrayOf(input))

        // create input tensor
        val inputTensor = OnnxTensor.createTensor(FloatBufferInput, longArrayOf(1, 4))

        val inputName = ortSession.inputNames[0]
        val outputName = ortSession.outputNames[0]
        val inputTensor = ortSession.createTensor(input, longArrayOf(1, 4))
        val outputMap = ortSession.run(mapOf(inputName to inputTensor), listOf(outputName))
        val outputTensor = outputMap[outputName]
        val output = outputTensor?.floatArray
        return output!!
    }
}