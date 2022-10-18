package id.co.sistema.vkey

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import com.vkey.android.internal.vguard.engine.BasicThreatInfo
import com.vkey.android.vguard.*
import id.co.sistema.vkey.CustomApplication.Companion.TAG_CA
import org.greenrobot.eventbus.EventBus
import vkey.android.vos.Vos
import vkey.android.vos.VosWrapper
import java.lang.Exception
import kotlin.properties.Delegates

class MyApplication : Application(), VGExceptionHandler,
    Application.ActivityLifecycleCallbacks, VosWrapper.Callback {

    // LifecycleHook to notify VGuard of activity's lifecycle
    private lateinit var hook: VGuardLifecycleHook

    // For VGuard to notify host app of events
    private lateinit var broadcastReceiver: VGuardBroadcastReceiver

    // Instance for Starting V-OS
    private lateinit var mVos: Vos
    private lateinit var mStartVosThread: Thread

    companion object {
        // VGuard object that is used for scanning
        var vGuardManager: VGuard? = null
        // Instance for VosWrapper to access V-OS APIs
        var vosWrapper: VosWrapper by Delegates.notNull()

        const val PROFILE_LOADED = "vkey.android.vguard.PROFILE_LOADED"
        const val FIRMWARE_RETURN_CODE_KEY = "vkey.android.vguard.FIRMWARE_RETURN_CODE"
    }

    private fun setupVGuard() {
        receiveVGuardBroadcast()
        registerVGuardBroadcast()
        setupAppProtection()
    }

    /**
     * Handling receiving notifications from V-OS App Protection
     * */
    private fun receiveVGuardBroadcast() {
        broadcastReceiver = object : VGuardBroadcastReceiver(null) {
            override fun onReceive(context: Context?, intent: Intent?) {
                super.onReceive(context, intent)

                when {
                    PROFILE_LOADED == intent?.action -> {
                        Log.d("CustomApp", "Profile is loaded...")
                    }

                    VOS_READY == intent?.action -> configureVOS(intent)

                    ACTION_SCAN_COMPLETE == intent?.action -> {
                        Log.d("CustomApp", "Scan complete...")
                        scanningThreats(intent)
                    }

                    VGUARD_STATUS == intent?.action -> {}
                }
            }
        }
    }

    private fun configureVOS(intent: Intent) {
        val firmwareCode = intent.getLongExtra(FIRMWARE_RETURN_CODE_KEY, 0)
        if (firmwareCode >= 0) {
            // if the `VGuardManager` is not available,
            // create a `VGuardManager` instance from `VGuardFactory`
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
            // Error handling
            val message = "firmwareCode: $firmwareCode, failed instance VGuardManager"
            Log.e("CustomApp", message)
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
                Log.d("CustomApp", "vosCode: $vosReturnCode")

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
                Log.e("CustomApp", e.message.toString())
            }
        }
        mStartVosThread.start()
    }

    /**
     * Scan all threats in devices
     * */
    private fun scanningThreats(intent: Intent) {
        val detectedThreats = intent
            .getParcelableArrayListExtra<Parcelable>(VGuardBroadcastReceiver.SCAN_COMPLETE_RESULT) as ArrayList<Parcelable>

        val threats: ArrayList<BasicThreatInfo> = arrayListOf()
        for (item in detectedThreats) {
            threats.add(item as BasicThreatInfo)
            Log.d("CustomApplication", "Threat: $threats")
        }

        /**
         * EventBus is used to send the threat into Activity/Fragment.
         * It need to be delayed for 5 sec to make sure Activity
         * is already rendered on the screen.
         */
        Handler(Looper.getMainLooper()).postDelayed({
            EventBus.getDefault().post(threats)
        }, 5000)
    }

    /**
     * Register using LocalBroadcastManager only for keeping data within your app
     * */
    private fun registerVGuardBroadcast() {
        LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(broadcastReceiver, IntentFilter(VGuardBroadcastReceiver.ACTION_FINISH))
            registerReceiver(broadcastReceiver, IntentFilter(VGuardBroadcastReceiver.ACTION_SCAN_COMPLETE))
            registerReceiver(broadcastReceiver, IntentFilter(PROFILE_LOADED))
            registerReceiver(broadcastReceiver, IntentFilter(VGuardBroadcastReceiver.VOS_READY))
        }
    }

    /**
     * Setting up V-OS App Protection here
     * */
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

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    // Override method from VGExceptionHandler
    override fun handleException(e: Exception?) {
        TODO("Not yet implemented")
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (vGuardManager == null && activity is SplashScreenActivity) {
            setupVGuard()
        }
    }

    override fun onActivityStarted(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivityResumed(activity: Activity) {
        vGuardManager?.onResume(hook)
    }

    override fun onActivityPaused(activity: Activity) {
        vGuardManager?.onPause(hook)
    }

    override fun onActivityStopped(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        TODO("Not yet implemented")
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is SplashScreenActivity) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        }
    }

    // Override method from VosWrapper.Callback
    override fun onNotified(p0: Int, p1: Int): Boolean {
        TODO("Not yet implemented")
    }
}