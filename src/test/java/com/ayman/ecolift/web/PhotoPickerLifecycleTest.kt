package com.ayman.ecolift.web

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import org.junit.After
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.MethodSorters
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNull

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PhotoPickerLifecycleTest {
    @get:Rule
    val bridgeReleasedAfterTheTest = object : TestWatcher() {
        override fun finished(description: Description) {
            val results = mutableListOf<Pair<String, String>?>()
            PhotoPickerBridge.request { results += it }
            assertEquals(listOf<Pair<String, String>?>(null), results)
        }
    }

    private val hosts = mutableListOf<Host>()

    private fun openHost(): Host = Host().also { hosts += it }

    @After
    fun releaseRegisteredHosts() {
        hosts.forEach { host ->
            if (host.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                host.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
        }
        hosts.clear()
    }

    private class Host {
        val activity = mock<ComponentActivity>()
        val lifecycle = LifecycleRegistry.createUnsafe(activity)
        val launcher = mock<ActivityResultLauncher<PickVisualMediaRequest>>()

        init {
            whenever(activity.lifecycle).thenReturn(lifecycle)
            whenever(
                activity.registerForActivityResult(
                    any<ActivityResultContract<PickVisualMediaRequest, Uri?>>(),
                    any<ActivityResultCallback<Uri?>>(),
                ),
            ).thenReturn(launcher)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
    }

    @Test
    fun destroyed_host_cancels_pending_request_and_releases_launcher() {
        val host = openHost()
        PhotoPickerBridge.register(host.activity)
        val results = mutableListOf<Pair<String, String>?>()
        PhotoPickerBridge.request { results += it }
        assertEquals(0, results.size)

        host.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        assertEquals(1, results.size)
        assertNull(results.single())
        verify(host.launcher).unregister()

        PhotoPickerBridge.request { results += it }
        assertEquals(2, results.size)
        assertNull(results.last())
    }

    @Test
    fun older_host_destruction_cannot_cancel_the_replacement_host_request() {
        val old = openHost()
        val current = openHost()
        PhotoPickerBridge.register(old.activity)
        PhotoPickerBridge.register(current.activity)
        val results = mutableListOf<Pair<String, String>?>()
        PhotoPickerBridge.request { results += it }
        old.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        assertEquals(0, results.size)
        current.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        assertEquals(1, results.size)
        assertNull(results.single())
    }

    @Test
    fun zzz_registered_host_does_not_outlive_the_test() {
        val host = openHost()
        PhotoPickerBridge.register(host.activity)
        val results = mutableListOf<Pair<String, String>?>()
        PhotoPickerBridge.request { results += it }
        assertEquals(0, results.size)
    }
}
