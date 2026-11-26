package app.inphotomaniak

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class UpdateApk(private val context: Context, client: OkHttpClient) {
    private val http = client.newBuilder()
        .dispatcher(okhttp3.Dispatcher())
        .callTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            if (!url.isHttps || url.host !in DOWNLOAD_HOSTS) throw IOException("Unexpected update host")
            chain.proceed(chain.request())
        }
        .build()

    fun fetch(update: AvailableUpdate): File {
        val directory = File(context.cacheDir, "updates")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create update cache")
        val target = File(directory, "PhotoManiak.apk")
        val partial = File.createTempFile("download-", ".apk", directory)
        try {
            http.newCall(Request.Builder().url(update.downloadUrl).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null || body.contentLength() > MAX_APK_BYTES) {
                    throw IOException("Update download failed")
                }
                partial.outputStream().use { output ->
                    LimitedStream(body.byteStream(), MAX_APK_BYTES).use { it.copyTo(output) }
                }
            }
            verify(partial, update)
            if (!partial.renameTo(target)) throw IOException("Cannot save update")
            return target
        } finally {
            partial.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun verify(file: File, update: AvailableUpdate) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
        if (archive == null || archive.packageName != installed.packageName ||
            archive.versionName != update.version ||
            PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed) ||
            signers(installed).isEmpty() || signers(archive) != signers(installed)) {
            throw IOException("APK does not match the installed application")
        }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo) =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
            .orEmpty().toSet()

    fun close() = http.dispatcher.cancelAll()

    private companion object {
        const val MAX_APK_BYTES = 128L * 1024 * 1024
        val DOWNLOAD_HOSTS = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }
}

internal fun updateInstaller(context: Context, apk: File): Intent =
    Intent(Intent.ACTION_VIEW).apply {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
