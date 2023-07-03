package com.example.myapplication3

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import net.dongliu.apk.parser.ApkFile
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.IOException
import java.nio.FloatBuffer

/*
    * This class is used to scan one or multiple applications and return the result
    * @param ortSession: the session of the machine learning model
    * @param features: the features that the model will use
 */
class AppScanner(private val ortSession: OrtSession, private val features: MutableMap<String, Float>) {

    // scan the app and return the result
    fun scan(app: ApplicationModel) : Long {
        parseManifest(getManifest(app.sourceDir))
        val input = createFloatArray(features)
        // reset the features map
        features.forEach { features[it.key] = 0.0f }
        return getInferenceResult(input)
    }

    // parse the manifest file
    /*
    * This function parses the manifest file of an application and checks for permissions, features and intent filters
    * It sets the value of the features hash map that are found to 1.0.
    * @param xmlString: the manifest file as a string
    *
     */
    private fun parseManifest(xmlString: String){
        //Initialize the parser
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        val input = xmlString.reader()
        parser.setInput(input)
        // parse the xml
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            if(name != null) {
                when (name) {
                    // check for permissions
                    "uses-permission" -> parser.getAttributeValue(null, "android:name")?.let {
                        if(features.containsKey(it)) {
                            features[it] = 1.0f
                        }
                    }
                    // check for features
                    "uses-feature" -> parser.getAttributeValue(null, "android:name")?.let {
                        if(features.containsKey(it)) {
                            features[it] = 1.0f
                        }
                    }
                    // check for intent filters
                    "action" -> parser.getAttributeValue(null, "android:name")?.let {
                        if(features.containsKey(it)) {
                            features[it] = 1.0f
                        }
                    }
                    else -> {}
                }
            }
            eventType = parser.next()
        }
    }

    // create a float array from the hashmap values
    private fun createFloatArray(map: MutableMap<String, Float>) : FloatArray {
        val floatArray = FloatArray(map.size)
        var i = 0
        map.keys.forEach {
            floatArray[i] = map[it]!!
            i++
        }
        return floatArray
    }

    // get the inference result
    private fun getInferenceResult(input: FloatArray) : Long {
        val result = runInference(input, ortSession, OrtEnvironment.getEnvironment())
        return result[0]
    }

    // run inference
    private fun runInference(
        input: FloatArray,
        ortSession: OrtSession,
        ortEnvironment: OrtEnvironment
    ): LongArray {
        val inputName = ortSession.inputNames?.iterator()?.next()
        val floatBufferInput = FloatBuffer.wrap(input)
        // create input tensor
        val inputTensor =
            OnnxTensor.createTensor(ortEnvironment, floatBufferInput, longArrayOf(1, 613))
        val results = ortSession.run(mapOf(inputName to inputTensor))
        return results.get(0).value as LongArray
    }

    // get manifest from apk file
    private fun getManifest(s: String): String {
        try {
            ApkFile(File(s)).use { apkFile -> return apkFile.manifestXml }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return "Error While Fetching Manifest"
    }


}