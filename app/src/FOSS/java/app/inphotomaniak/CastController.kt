package app.inphotomaniak

import android.app.Application
import coil.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** FOSS implementation: no SDK initialization, discovery, sockets, or playback. */
@Suppress("UNUSED_PARAMETER")
internal class CastController(app: Application,scope: CoroutineScope) {
    val state=MutableStateFlow(CastingState()).asStateFlow()
    fun show(entry: RemoteEntry,client: DriveClient,images: ImageLoader) = Unit
    fun clearError() = Unit
    fun stop() = Unit
    fun close() = Unit
}
