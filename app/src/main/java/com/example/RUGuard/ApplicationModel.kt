package com.example.RUGuard

import android.graphics.drawable.Drawable

/*
    * This class is used to store the information about an application
    * that is displayed in the list of applications
    * @param packageName: the package name of the application
    * @param appName: the name of the application
    * @param sourceDir: the directory of the application
    * @param icon: the icon of the application
 */
class ApplicationModel(packageName: String, appName: String, sourceDir: String, icon: Drawable) {
    val packageName = packageName.split(".").last()
    val appName = appName
    val sourceDir = sourceDir
    val icon = icon
}