package app.inphotomaniak

import android.view.View
import android.view.ViewGroup
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class CastIntegrationTest {
    @Test fun officialDevicePickerOpensInRealFragmentActivity() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        Assume.assumeTrue(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)==ConnectionResult.SUCCESS)
        fun await(timeout: Long=5000,condition: ()->Boolean) {
            val deadline=SystemClock.elapsedRealtime()+timeout
            while(!condition()) { if(SystemClock.elapsedRealtime()>=deadline) fail("Timed out waiting for the Cast UI");SystemClock.sleep(50) }
        }
        fun find(view: View): MediaRouteButton? {
            if(view is MediaRouteButton) return view
            if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: GalleryModel
            scenario.onActivity { activity ->
                model=ViewModelProvider(activity)[GalleryModel::class.java]
                activity.setContent { PhotoManiakTheme(ThemeMode.LIGHT) { androidx.compose.foundation.layout.Column { CastMenuItem(model.casting) {} } } }
            }
            await(30000) { model.casting.state.value.ready || model.casting.state.value.unavailable!=null }
            assertTrue(model.casting.state.value.unavailable.orEmpty(),model.casting.state.value.ready)
            var button: MediaRouteButton?=null
            await { scenario.onActivity { button=find(it.window.decorView) };button!=null }
            scenario.onActivity { assertTrue(requireNotNull(button).performClick()) }
            var dialog=false
            await { scenario.onActivity { activity -> dialog=activity.supportFragmentManager.fragments.any { it is DialogFragment && it.dialog?.isShowing==true } };dialog }
            instrumentation.uiAutomation.takeScreenshot()?.let { image ->
                context.openFileOutput("cast-picker.png",0).use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
        }
    }
}
