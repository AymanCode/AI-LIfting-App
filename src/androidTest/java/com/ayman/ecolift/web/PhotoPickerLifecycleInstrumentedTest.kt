package com.ayman.ecolift.web

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ayman.ecolift.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PhotoPickerLifecycleInstrumentedTest {
    @Test
    fun recreated_activity_can_pick_and_finish_cancels_the_pending_request() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val cancellations = AtomicInteger()
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.recreate()
            scenario.onActivity {
                PhotoPickerBridge.request { result ->
                    if (result == null) cancellations.incrementAndGet()
                }
            }
            assertEquals(0, cancellations.get())
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(1, cancellations.get())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            PhotoPickerBridge.request { result ->
                if (result == null) cancellations.incrementAndGet()
            }
        }
        assertEquals(2, cancellations.get())
    }
}
