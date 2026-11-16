package com.dfmob.updater

data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkFileName: String,
    val apkSize: Long
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class UpdateAvailable(val releaseInfo: ReleaseInfo) : UpdateState
    data class Downloading(val progressPercent: Int) : UpdateState
    data class ReadyToInstall(val apkPath: String) : UpdateState
    data class Error(val message: String) : UpdateState
}
