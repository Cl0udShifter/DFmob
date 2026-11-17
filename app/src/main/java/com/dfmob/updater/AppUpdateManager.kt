package com.dfmob.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateManager(private val context: Context) {

    companion object {
        private const val GITHUB_REPO = "Cl0udShifter/DFmob"
        private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private const val FILE_PROVIDER_AUTHORITY = "com.dfmob.fileprovider"
        private const val CURRENT_VERSION_NAME = "1.1.4"
        private const val CURRENT_VERSION_CODE = 15
    }

    fun getCurrentVersion(): String = CURRENT_VERSION_NAME

    suspend fun checkForUpdates(): UpdateState = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(RELEASES_API_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "DFmob-Android")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext UpdateState.UpToDate(CURRENT_VERSION_NAME)
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateState.Error("Servidor GitHub retornou codigo $responseCode")
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)

            val rawTagName = json.optString("tag_name", "").trim()
            val tagName = rawTagName.removePrefix("v").removePrefix("V")
            val title = json.optString("name", "Versao $tagName")
            val body = json.optString("body", "")

            val assets = json.optJSONArray("assets")
            var downloadUrl: String? = null
            var apkFileName = "DFmob-v$tagName.apk"
            var apkSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url")
                        apkFileName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (isNewerVersion(tagName, CURRENT_VERSION_NAME)) {
                if (downloadUrl.isNullOrBlank()) {
                    return@withContext UpdateState.Error("Nova versao encontrada ($tagName), mas o arquivo APK ainda nao foi anexado a release.")
                }
                val releaseInfo = ReleaseInfo(
                    tagName = tagName,
                    title = title,
                    releaseNotes = body,
                    downloadUrl = downloadUrl,
                    apkFileName = apkFileName,
                    apkSize = apkSize
                )
                UpdateState.UpdateAvailable(releaseInfo)
            } else {
                UpdateState.UpToDate(CURRENT_VERSION_NAME)
            }
        } catch (e: Exception) {
            UpdateState.Error(e.localizedMessage ?: "Falha ao verificar atualizacoes")
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        targetFileName: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            val destinationFile = File(updatesDir, targetFileName)
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val url = URL(downloadUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "DFmob-Android")
                instanceFollowRedirects = true
            }

            // Segue redirecionamentos do GitHub CDN se necessario
            var redirectedConnection = connection
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                val newUrl = connection.getHeaderField("Location")
                redirectedConnection = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "DFmob-Android")
                }
            }

            val totalLength = redirectedConnection.contentLengthLong
            redirectedConnection.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalLength > 0) {
                            val progress = ((totalRead * 100) / totalLength).toInt()
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            destinationFile
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun installApk(apkFile: File): Boolean {
        return try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                FILE_PROVIDER_AUTHORITY,
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        try {
            val remoteParts = remote.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
            val localParts = local.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }

            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        } catch (_: Exception) {
        }
        return false
    }
}
