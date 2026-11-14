package app.inphotomaniak

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors

internal class CastController(private val app: Application,private val scope: CoroutineScope) {
    private val mutable=MutableStateFlow(CastingState())
    val state=mutable.asStateFlow()
    private var context: CastContext?=null
    private var media: RemoteMediaClient?=null
    private var relay: CastRelay?=null
    private var address: InetAddress?=null
    private var preparing: Job?=null
    private var generation=0
    private var disposed=false
    private val executor=Executors.newSingleThreadExecutor()
    init {
        if(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app)!=ConnectionResult.SUCCESS) {
            mutable.value=CastingState(unavailable=app.getString(R.string.cast_requires_play))
            executor.shutdown()
        } else try {
            CastContext.getSharedInstance(app,executor).addOnSuccessListener { cast ->
                if(!disposed) {
                    context=cast
                    cast.sessionManager.addSessionManagerListener(sessions,CastSession::class.java)
                    mutable.update { it.copy(ready=true) }
                    attach(cast.sessionManager.currentCastSession)
                }
                executor.shutdown()
            }.addOnFailureListener {
                mutable.update { it.copy(unavailable=app.getString(R.string.cast_init_failed)) };executor.shutdown()
            }
        } catch(_: Exception) { mutable.value=CastingState(unavailable=app.getString(R.string.cast_init_failed));executor.shutdown() }
    }
    private val progress=RemoteMediaClient.ProgressListener { position,duration -> mutable.update { it.copy(position=position,duration=duration) } }
    private val callback=object: RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val remote=media ?: return
            val status=remote.mediaStatus
            mutable.update { it.copy(playing=remote.isPlaying,loading=remote.isBuffering,
                position=remote.approximateStreamPosition,duration=remote.streamDuration,
                error=if(status?.idleReason==MediaStatus.IDLE_REASON_ERROR) app.getString(R.string.cast_play_failed) else it.error) }
        }
    }
    private fun attach(session: CastSession?) {
        media?.unregisterCallback(callback);media?.removeProgressListener(progress)
        media=session?.takeIf { it.isConnected }?.remoteMediaClient
        media?.registerCallback(callback);media?.addProgressListener(progress,1000)
        mutable.update { it.copy(connected=session?.isConnected==true,device=session?.castDevice?.friendlyName.orEmpty()) }
    }
    private val sessions=object: SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession,id: String) { attach(session) }
        override fun onSessionResumed(session: CastSession,wasSuspended: Boolean) { attach(session) }
        override fun onSessionEnded(session: CastSession,error: Int) { clearMedia();attach(null) }
        override fun onSessionSuspended(session: CastSession,reason: Int) { clearMedia();attach(null) }
        override fun onSessionStartFailed(session: CastSession,error: Int) { clearMedia();attach(null);fail(app.getString(R.string.cast_connect_failed)) }
        override fun onSessionResumeFailed(session: CastSession,error: Int) { clearMedia();attach(null);fail(app.getString(R.string.cast_connect_failed)) }
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession,id: String) = Unit
    }
    private fun lanAddress(): InetAddress {
        val manager=app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network=manager.activeNetwork ?: error(app.getString(R.string.cast_wifi_required))
        val capabilities=manager.getNetworkCapabilities(network)
        require(capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true || capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)==true) { app.getString(R.string.cast_wifi_required) }
        return manager.getLinkProperties(network)?.linkAddresses?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?: error(app.getString(R.string.cast_wifi_required))
    }
    fun show(entry: RemoteEntry,client: DriveClient,images: ImageLoader) {
        val session=context?.sessionManager?.currentCastSession?.takeIf { it.isConnected } ?: return
        preparing?.cancel();val request=++generation
        mutable.update { it.copy(loading=true,error=null,title=entry.name,video=entry.video,position=0,duration=0) }
        preparing=scope.launch {
            try {
                val lan=lanAddress()
                val content=withContext(Dispatchers.IO) {
                    if(entry.video) CastVideo(client,entry,castVideoMime(entry)) else {
                        val result=images.execute(ImageRequest.Builder(app).data(client.url(entry.path)).size(1280,720).precision(Precision.EXACT).allowHardware(false).build())
                        val drawable=(result as? SuccessResult)?.drawable ?: error(app.getString(R.string.cast_image_failed))
                        val factor=minOf(1f,1280f/drawable.intrinsicWidth.coerceAtLeast(1),720f/drawable.intrinsicHeight.coerceAtLeast(1))
                        val bitmap=drawable.toBitmap((drawable.intrinsicWidth*factor).toInt().coerceAtLeast(1),(drawable.intrinsicHeight*factor).toInt().coerceAtLeast(1))
                        val bytes=ByteArrayOutputStream().use { output ->
                            check(bitmap.compress(Bitmap.CompressFormat.JPEG,90,output));output.toByteArray()
                        }
                        CastImage(bytes)
                    }
                }
                if(request!=generation || session!==context?.sessionManager?.currentCastSession || !session.isConnected) { content.close();return@launch }
                if(relay==null || address!=lan) { relay?.close();relay=CastRelay(lan);address=lan }
                val url=requireNotNull(relay).publish(content)
                val metadata=MediaMetadata(if(entry.video) MediaMetadata.MEDIA_TYPE_MOVIE else MediaMetadata.MEDIA_TYPE_PHOTO).apply {
                    putString(MediaMetadata.KEY_TITLE,entry.name)
                }
                val info=MediaInfo.Builder(url).setContentType(if(entry.video) castVideoMime(entry) else "image/jpeg")
                    .setStreamType(if(entry.video) MediaInfo.STREAM_TYPE_BUFFERED else MediaInfo.STREAM_TYPE_NONE).setMetadata(metadata).build()
                session.remoteMediaClient?.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build())?.setResultCallback { result ->
                    if(request==generation) mutable.update { it.copy(loading=false,error=if(result.status.isSuccess) null else app.getString(R.string.cast_play_failed)) }
                } ?: error(app.getString(R.string.cast_connect_failed))
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { if(request==generation) { relay?.close();relay=null;fail(app.getString(R.string.cast_play_failed)) } }
        }
    }
    fun togglePlayback() { media?.let { if(it.isPlaying) it.pause() else it.play() } }
    fun seek(position: Long) { media?.seek(MediaSeekOptions.Builder().setPosition(position.coerceAtLeast(0)).build()) }
    fun clearError() { mutable.update { it.copy(error=null) } }
    fun fail(message: String) { mutable.update { it.copy(error=message,loading=false) } }
    private fun clearMedia() {
        generation++;preparing?.cancel();relay?.close();relay=null;address=null
        mutable.update { it.copy(title="",loading=false,video=false,playing=false,position=0,duration=0) }
    }
    fun stop() {
        clearMedia();context?.sessionManager?.endCurrentSession(true);attach(null)
    }
    fun close() {
        disposed=true;stop();context?.sessionManager?.removeSessionManagerListener(sessions,CastSession::class.java);executor.shutdownNow()
    }
}
internal fun castVideoMime(entry: RemoteEntry): String = entry.mime.takeIf { it.startsWith("video/") && it!="video/*" }
    ?: when(entry.name.substringAfterLast('.').lowercase()) {
        "webm" -> "video/webm";"mkv" -> "video/x-matroska";"mov" -> "video/quicktime";"3gp" -> "video/3gpp";else -> "video/mp4"
    }
