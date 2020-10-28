package me.iacn.biliroaming

import com.crossbowffs.remotepreferences.RemotePreferenceProvider

class MyPreferenceProvider : RemotePreferenceProvider(BuildConfig.APPLICATION_ID, arrayOf("${BuildConfig.APPLICATION_ID}_preferences"))
