package app.inphotomaniak

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class AvailableUpdate(val version: String, val edition: String) {
    val downloadUrl: String get() = "https://github.com/cocciasecca/PhotoManiak/releases/download/v$version/PhotoManiak-$version-$edition.apk"
}

internal object ReleaseUpdates {
    private val stableVersion=Regex("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)")
    fun newer(candidate: String, current: String): Boolean {
        if(!stableVersion.matches(candidate) || !stableVersion.matches(current)) return false
        val a=candidate.split('.').map { it.toLongOrNull() ?: return false }
        val b=current.split('.').map { it.toLongOrNull() ?: return false }
        for(i in a.indices) if(a[i]!=b[i]) return a[i]>b[i]
        return false
    }
    fun parse(json: String, current: String, edition: String): AvailableUpdate? {
        require(edition in setOf("FOSS","nonFOSS"))
        val release=JSONObject(json)
        if(release.optBoolean("draft") || release.optBoolean("prerelease")) return null
        val tag=release.getString("tag_name")
        if(!tag.startsWith("v")) return null
        val version=tag.removePrefix("v")
        if(!newer(version,current)) return null
        val update=AvailableUpdate(version,edition)
        val assets=release.getJSONArray("assets")
        // Only offer an actual APK for this edition, from this repository and release.
        return update.takeIf {
            (0 until assets.length()).any { i ->
                val asset=assets.getJSONObject(i)
                asset.optString("name")=="PhotoManiak-$version-$edition.apk" &&
                    asset.optString("browser_download_url")==update.downloadUrl && asset.optLong("size")>0
            }
        }
    }
    fun checkDue(lastAttempt: Long, now: Long): Boolean = lastAttempt==0L || now-lastAttempt !in 0 until 24*60*60*1000L
}

internal data class UpdateState(
    val automatic: Boolean = true, val checking: Boolean = false,
    val available: AvailableUpdate? = null, val showNotice: Boolean = false,
    val message: Int? = null
)

/** Separate transport and preferences: no kDrive token or account data reaches GitHub. */
internal class AppUpdates(context: Context, private val scope: CoroutineScope) {
    private val prefs=context.getSharedPreferences("app_updates",Context.MODE_PRIVATE)
    private val edition=context.getString(R.string.edition_name)
    private val http=OkHttpClient.Builder().callTimeout(20,TimeUnit.SECONDS)
        .connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
        .followRedirects(false).build()
    private val cached=prefs.getString("available_version",null)?.takeIf { prefs.getString("available_edition",null)==edition && ReleaseUpdates.newer(it,BuildConfig.VERSION_NAME) }
        ?.let { AvailableUpdate(it,edition) }
    private val mutable=MutableStateFlow(UpdateState(
        automatic=prefs.getBoolean("automatic",true),available=cached,
        showNotice=cached!=null && prefs.getString("dismissed_version",null)!=cached.version
    ))
    val state=mutable.asStateFlow()
    fun automatic(enabled: Boolean) {
        prefs.edit().putBoolean("automatic",enabled).apply()
        mutable.update { it.copy(automatic=enabled) }
        if(enabled) checkAutomatically()
    }
    fun checkAutomatically() {
        if(state.value.automatic && (prefs.getString("last_attempt_edition",null)!=edition || ReleaseUpdates.checkDue(prefs.getLong("last_attempt",0),System.currentTimeMillis()))) check(false)
    }
    fun check(manual: Boolean = true) {
        if(state.value.checking) return
        mutable.update { it.copy(checking=true,message=null) }
        // Rate-limit attempts, including failures; manual checks remain available offline/after errors.
        prefs.edit().putLong("last_attempt",System.currentTimeMillis()).putString("last_attempt_edition",edition).apply()
        scope.launch {
            try {
                val update=withContext(Dispatchers.IO) {
                    val request=Request.Builder().url("https://api.github.com/repos/cocciasecca/PhotoManiak/releases/latest")
                        .header("Accept","application/vnd.github+json")
                        .header("User-Agent","PhotoManiak/${BuildConfig.VERSION_NAME}").build()
                    http.newCall(request).execute().use { response ->
                        if(!response.isSuccessful) throw IOException("Update check failed")
                        val body=response.body ?: throw IOException("Empty release")
                        val json=body.byteStream().use { LimitedStream(it,2L*1024*1024).bufferedReader().readText() }
                        ReleaseUpdates.parse(json,BuildConfig.VERSION_NAME,edition)
                    }
                }
                prefs.edit().putString("available_version",update?.version).putString("available_edition",edition).apply()
                mutable.update { it.copy(available=update,showNotice=update!=null && (manual || prefs.getString("dismissed_version",null)!=update.version),
                    message=if(manual && update==null) R.string.updates_none else null) }
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { if(manual) mutable.update { it.copy(message=R.string.updates_failed) } }
            finally { mutable.update { it.copy(checking=false) } }
        }
    }
    fun dismiss() {
        state.value.available?.let { prefs.edit().putString("dismissed_version",it.version).apply() }
        mutable.update { it.copy(showNotice=false) }
    }
    fun downloadFailed() { mutable.update { it.copy(message=R.string.update_browser_failed) } }
    fun close() { http.dispatcher.cancelAll() }
}
