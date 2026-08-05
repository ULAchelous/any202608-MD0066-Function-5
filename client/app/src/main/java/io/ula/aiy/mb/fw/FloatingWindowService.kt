package io.ula.aiy.mb.fw

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

class FloatingWindowService : Service(), LifecycleOwner, ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        startForegroundService()
        showFloatingWindow()
    }

    private fun startForegroundService() {
        val channelId = "floating_window_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Floating Window Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Floating Window")
            .setContentText("Running...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else
                    0
            )
        } else {
            startForeground(1, notification)
        }
    }

    private fun showFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 100
            y = 500 // Start a bit higher from the bottom
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)

            setContent {
                FloatingContent(
                    onDrag = { dx, dy ->
                        params.x += dx.roundToInt()
                        params.y -= dy.roundToInt()
                        windowManager.updateViewLayout(this, params)
                    },
                    onFocusChange = { isFocused ->
                        if (isFocused) {
                            params.flags =
                                params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                        } else {
                            params.flags =
                                params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        }
                        windowManager.updateViewLayout(this, params)
                    },
                    onExpandChange = { expanded ->
                        if (expanded) {
                            params.flags =
                                params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                        } else {
                            params.flags =
                                params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        }
                        windowManager.updateViewLayout(this, params)
                    }
                )
            }
        }


        floatingView = composeView
        windowManager.addView(floatingView, params)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (floatingView != null) {
            windowManager.removeView(floatingView)
        }
    }
}
