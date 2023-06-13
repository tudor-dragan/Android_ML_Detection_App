package com.example.myapplication3

import android.graphics.drawable.Drawable

class ApplicationModel(packageName: String, appName: String, sourceDir: String, icon: Drawable) {
    val packageName = packageName.split(".").last()
    val appName = appName
    val sourceDir = sourceDir
    val icon = icon
}