package com.example.cameraxapp

import android.Manifest

const val TAG = "cameraX"
const val FILE_NAME_FORMAT = "yy-MM-dd-HH-mm-ss-SSS"
const val REQUEST_CODE_PERMISSIONS = 123
val REQUIRED_PERMISSION = arrayOf(Manifest.permission.CAMERA)
object Constants {
    const val HSV_CONFIG = "HSV_CONFIG_PREFS"
    const val KEY_LOWER_H = "lowerH"
    const val KEY_LOWER_S = "lowerS"
    const val KEY_LOWER_V = "lowerV"
    const val KEY_UPPER_H = "upperH"
    const val KEY_UPPER_S = "upperS"
    const val KEY_UPPER_V = "upperV"
}
