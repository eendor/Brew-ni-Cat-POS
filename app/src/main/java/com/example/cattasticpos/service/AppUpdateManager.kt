package com.example.cattasticpos.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.cattasticpos.BuildConfig
import com.example.cattasticpos.CattasticPosApp
import com.example.cattasticpos.domain.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Self-update channel. Reads the latest published build from the cloud `app_release`
 * table, and — when newer than the running build — downloads its APK and hands it to
 * the system package installer. This lets fixes/features reach the tablets without a
 * manual reinstall. The very first build carrying this manager must still be installed
 * by hand; every release after that can update in place.
 */
class AppUpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "AppUpdateManager"
    }

    /**
     * Returns an [UpdateInfo] when the cloud advertises a build newer than this one.
     * Fail-soft: returns null when up to date, unconfigured, or on any error.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as CattasticPosApp
            val config = app.container.database.appConfigDao().getAppConfigOnce()
            val supabaseUrl = config?.supabaseUrl?.trim()?.ifEmpty { null }
                ?: BuildConfig.SUPABASE_URL.trim().ifEmpty { return@withContext null }
            val supabaseKey = config?.supabaseAnonKey?.trim()?.ifEmpty { null }
                ?: BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim().ifEmpty { return@withContext null }

            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/app_release?select=*&order=version_code.desc&limit=1")
                .get()
                .header("apikey", supabaseKey)
                .header("Authorization", "Bearer $supabaseKey")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string()
                if (body.isNullOrEmpty()) return@withContext null
                val arr = JSONArray(body)
                if (arr.length() == 0) return@withContext null
                val obj = arr.getJSONObject(0)
                val versionCode = obj.getLong("version_code")
                val apkUrl = obj.optString("apk_url", "")
                if (versionCode <= BuildConfig.VERSION_CODE.toLong() || apkUrl.isBlank()) {
                    return@withContext null
                }
                val notes = if (obj.isNull("notes")) null else obj.optString("notes").ifBlank { null }
                UpdateInfo(
                    sha256 = ApkChecksum.normalize(if (obj.isNull("sha256")) null else obj.optString("sha256")),
                    versionCode = versionCode,
                    versionName = obj.optString("version_name", ""),
                    apkUrl = apkUrl,
                    notes = notes,
                    mandatory = obj.optBoolean("mandatory", false)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            null
        }
    }

    /**
     * Streams the APK to cache/updates/update.apk, reporting 0f..1f progress, and
     * keeps it only when its SHA-256 matches the published one (FR-118). An update
     * published without a hash is refused too.
     */
    suspend fun downloadApk(update: UpdateInfo, onProgress: (Float) -> Unit): ApkDownload =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(dir, "update.apk")
            if (outFile.exists()) outFile.delete()
            if (update.sha256 == null) {
                Log.w(TAG, "Update ${update.versionCode} has no SHA-256; not installing it")
                return@withContext ApkDownload.Rejected
            }
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val request = Request.Builder().url(update.apkUrl).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ApkDownload.Failed
                    val stream = response.body?.byteStream() ?: return@withContext ApkDownload.Failed
                    val total = response.body?.contentLength() ?: -1L
                    outFile.outputStream().use { out ->
                        val buffer = ByteArray(8 * 1024)
                        var downloaded = 0L
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                        out.flush()
                    }
                }
                if (ApkChecksum.matches(update.sha256, ApkChecksum.hex(digest.digest()))) {
                    ApkDownload.Ok(outFile)
                } else {
                    Log.w(TAG, "Update ${update.versionCode} failed the SHA-256 check; deleted")
                    outFile.delete()
                    ApkDownload.Rejected
                }
            } catch (e: Exception) {
                Log.e(TAG, "APK download failed", e)
                outFile.delete()
                ApkDownload.Failed
            }
        }

    /**
     * Launches the system installer for [file]. If this app isn't yet allowed to install
     * unknown apps, routes the user to that setting first (they then re-tap Update).
     * Returns true if the install intent was launched, false if redirected to settings.
     */
    fun installApk(file: File): Boolean {
        return try {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settings)
                return false
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "APK install launch failed", e)
            false
        }
    }
}
