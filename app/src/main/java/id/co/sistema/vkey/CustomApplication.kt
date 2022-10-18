package id.co.sistema.vkey

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.google.firebase.FirebaseApp
import com.vkey.android.vguard.*
import org.greenrobot.eventbus.EventBus
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import vkey.android.vos.Vos
import vkey.android.vos.VosWrapper
import kotlin.properties.Delegates


class CustomApplication : Application(), VGExceptionHandler,
    Application.ActivityLifecycleCallbacks, VosWrapper.Callback {

    private lateinit var hook: VGuardLifecycleHook
    private lateinit var broadcastReceiver: VGuardBroadcastReceiver

    // Configuration for V-OS Processor
    private lateinit var mVos: Vos
    private lateinit var mStartVosThread: Thread

    companion object {
        const val PROFILE_LOADED = "vkey.android.vguard.PROFILE_LOADED"
        const val FIRMWARE_RETURN_CODE_KEY = "vkey.android.vguard.FIRMWARE_RETURN_CODE"
        const val TAG_CA = "CustomApp"

        var vGuardManager: VGuard? = null
        var vosWrapper: VosWrapper by Delegates.notNull()
    }

    private fun setupVGuard() {
        receiveVGuardBroadcast()
        registerVGuardBroadcast()
        setupAppProtection()
    }

    private fun receiveVGuardBroadcast() {
        broadcastReceiver = object : VGuardBroadcastReceiver(null) {
            override fun onReceive(context: Context?, intent: Intent?) {
                super.onReceive(context, intent)

                when {
                    PROFILE_LOADED == intent?.action -> {
                        Log.d(TAG_CA, "Profile loaded...")
                    }

                    VOS_READY == intent?.action -> {
                        Log.d(TAG_CA, "VOS_READY calling...")
                        instanceVGuardManager(intent)
                    }

                    ACTION_SCAN_COMPLETE == intent?.action -> {
                        Log.d(TAG_CA, "ACTION_SCAN_COMPLETE calling...")
                        // Send publish event about V-OS status
                        EventBus.getDefault().post(vGuardManager?.isVosStarted)
                    }
                }
            }
        }
    }

    private fun registerVGuardBroadcast() {
        LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(broadcastReceiver, IntentFilter(VGuardBroadcastReceiver.ACTION_FINISH))
            registerReceiver(
                broadcastReceiver,
                IntentFilter(VGuardBroadcastReceiver.ACTION_SCAN_COMPLETE)
            )
            registerReceiver(broadcastReceiver, IntentFilter(PROFILE_LOADED))
            registerReceiver(broadcastReceiver, IntentFilter(VGuardBroadcastReceiver.VOS_READY))
        }
    }

    private fun setupAppProtection() {
        try {
            val config = VGuardFactory.Builder()
                .setDebugable(true)
                .setAllowsArbitraryNetworking(true)
                .setMemoryConfiguration(MemoryConfiguration.DEFAULT)
                .setVGExceptionHandler(this as VGExceptionHandler)
            VGuardFactory().getVGuard(this, config)
        } catch (e: Exception) {
            Log.e(TAG_CA, e.message.toString())
        }
    }

    private fun instanceVGuardManager(intent: Intent) {
        val firmwareCode = intent.getLongExtra(FIRMWARE_RETURN_CODE_KEY, 0)
        if (firmwareCode >= 0) {
            if (vGuardManager == null) {
                vGuardManager = VGuardFactory.getInstance()
                hook = ActivityLifecycleHook(vGuardManager)

                // Instantiate a `Vos` instance
                mVos = Vos(this)
                mVos.registerVosWrapperCallback(this)

                // Starting V-OS
                startVos(this)
            }
        } else {
            Log.e(TAG_CA, "firmwareCode: $firmwareCode, failed instance VGuardManager")
        }
    }

    /**
     * Method will starting [Vos] with method [Vos.start].
     * we need 'firmware' assets for passing the argument to [Vos.start] method.
     *
     * if vosReturnCode 'positive', V-OS starting successfully.
     * Then we can do publish event using [EventBus.getDefault] to sending V-OS status [VGuard.getIsVosStarted],
     * also instantiate a [VosWrapper.getInstance] instance for calling V-OS Processor APIs.
     * */
    private fun startVos(context: Context) {
        mStartVosThread = Thread {
            try {
                val inputStream = context.assets.open("4.9/firmware")
                val firmware = inputStream.readBytes()
                inputStream.read(firmware)
                inputStream.close()

                // Start V-OS
                val vosReturnCode = mVos.start(firmware, null, null, null, null)
                Log.d(TAG_CA, "vosCode: $vosReturnCode")

                if (vosReturnCode > 0) {
                    // Successfully started V-OS
                    // Instantiate a `VosWrapper` instance for calling V-OS Processor APIs
                    vosWrapper = VosWrapper.getInstance(this)
                    // Send publish event about V-OS status
                    EventBus.getDefault().post(vGuardManager?.isVosStarted)
                } else {
                    // Failed to start V-OS
                }

            } catch (e: VGException) {
                Log.e(TAG_CA, e.message.toString())
            }
        }
        mStartVosThread.start()
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)

        // Initiate Koin for dependency injection
        startKoin {
            androidContext(this@CustomApplication)
            modules(
                coreModule,
                viewModelModule
            )
        }
    }

    override fun onActivityCreated(activity: Activity, p1: Bundle?) {
        if (vGuardManager == null && activity is SplashScreenActivity) {
            setupVGuard()
//            VosWrapper.getInstance(this).setLoggerBaseUrl("https://sistemadev.my.id/")
        }
    }

    override fun onActivityResumed(activity: Activity) {
        vGuardManager?.onResume(hook)
    }

    override fun onActivityPaused(activity: Activity) {
        vGuardManager?.onPause(hook)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is SplashScreenActivity) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        }
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, p1: Bundle) {}

    override fun handleException(e: Exception?) {
        Log.e(TAG_CA, e?.message.toString())
    }

    /**
     * This method mandatory to override from [VosWrapper.Callback]
     * and using in [Vos.registerVosWrapperCallback]
     * */
    override fun onNotified(p0: Int, p1: Int): Boolean {
        return false
    }
}