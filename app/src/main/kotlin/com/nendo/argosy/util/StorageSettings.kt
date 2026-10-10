package com.nendo.argosy.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

fun Context.openStorageAccessSettings() {
    val packageUri = Uri.fromParts("package", packageName, null)
    val intents = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        listOf(
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            StorageAccess.settingsIntent(this),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        )
    } else {
        listOf(StorageAccess.settingsIntent(this))
    }

    for (intent in intents) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}
