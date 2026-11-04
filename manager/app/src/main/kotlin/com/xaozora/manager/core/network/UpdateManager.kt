package com.xaozora.manager.core.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.xaozora.manager.core.shell.RootShellHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppUpdateResult(
    @SerializedName("has_update") val hasUpdate: Boolean,
    @SerializedName("new_version") val newVersion: String = "",
    @SerializedName("download_url") val downloadUrl: String = "",
    @SerializedName("release_notes") val releaseNotes: String = "",
    @SerializedName("digest") val digest: String = ""
)

data class UpdateCheckResult(
    @SerializedName("app_update") val appUpdate: AppUpdateResult
)

object UpdateManager {
    init {
        System.loadLibrary("native")
    }

    private val gson = Gson()

    private external fun checkUpdatesJson(currentAppVersion: String): String

    private data class CommandDetail(val code: Int = -1, val output: String = "")

    suspend fun checkUpdates(currentAppVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val jsonStr = checkUpdatesJson(currentAppVersion)
        gson.fromJson(jsonStr, UpdateCheckResult::class.java)
    }

    suspend fun performAppUpdate(
        context: Context,
        downloadUrl: String,
        expectedDigest: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!downloadUrl.startsWith("https://")) return@withContext false

        val apkFile = File(context.cacheDir, "aozora_update.apk")
        if (apkFile.exists()) apkFile.delete()

        if (!download(downloadUrl, apkFile)) {
            apkFile.delete()
            return@withContext false
        }

        if (!verifyDigest(apkFile, expectedDigest)) {
            apkFile.delete()
            return@withContext false
        }

        if (!verifySigner(context, apkFile)) {
            apkFile.delete()
            return@withContext false
        }

        val installed = installApk(apkFile)
        apkFile.delete()
        installed
    }

    private fun download(url: String, target: File): Boolean {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Aozora-Kernel-Manager")
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            connection.disconnect()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun verifyDigest(apkFile: File, expectedDigest: String): Boolean {
        val expected = expectedDigest.substringAfter(":", "").ifBlank { expectedDigest }
        if (expected.isBlank()) return true
        return sha256Hex(apkFile).equals(expected, ignoreCase = true)
    }

    private fun verifySigner(context: Context, apkFile: File): Boolean {
        return try {
            val packageManager = context.packageManager
            val archive = packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES
            ) ?: return false

            if (archive.packageName != context.packageName) return false

            val archiveFingerprint = archive.signingInfo
                ?.apkContentsSigners
                ?.firstOrNull()
                ?.let { sha256Hex(it.toByteArray()) } ?: return false

            val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            }

            val installedFingerprint = installed.signingInfo
                ?.apkContentsSigners
                ?.firstOrNull()
                ?.let { sha256Hex(it.toByteArray()) } ?: return false

            archiveFingerprint.equals(installedFingerprint, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private fun installApk(apkFile: File): Boolean {
        val quoted = shQuote(apkFile.absolutePath)
        val staged = "/data/local/tmp/aozora_update.apk"
        val command = buildString {
            append("cp $quoted $staged && chmod 0644 $staged && pm install -r $staged; rc=\$?; ")
            append("rm -f $staged $quoted; exit \$rc")
        }

        val detail = try {
            gson.fromJson(RootShellHelper.executeCmdDetailed(command), CommandDetail::class.java)
        } catch (e: Exception) {
            null
        } ?: return false

        return detail.code == 0 && detail.output.contains("Success", ignoreCase = true)
    }

    private fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
