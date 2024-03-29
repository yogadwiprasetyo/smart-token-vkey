package id.co.sistema.vkey.smarttoken

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Environment.getExternalStorageDirectory
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.gson.Gson
import com.vkey.android.vguard.LocalBroadcastManager
import com.vkey.android.vguard.VGuard
import com.vkey.android.vtap.VTapFactory
import com.vkey.android.vtap.VTapInterface
import com.vkey.android.vtap.VTapManager
import com.vkey.android.vtap.utility.ResultCode
import com.vkey.android.vtap.utility.VTapUtility
import id.co.sistema.vkey.CustomApplication
import id.co.sistema.vkey.model.TokenAssignModel
import id.co.sistema.vkey.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.properties.Delegates

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel by viewModel<SmartTokenViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initiate vGuard, device id, and view model
        iVGuardManager = CustomApplication.vGuardManager
        iVGuardManager?.let { viewModel.initialize(it.customerId.toString()) }
        deviceId = iVGuardManager!!.troubleshootingId
        pushToken = MyFirebaseMessagingService.getToken(applicationContext).toString()
        binding.pushToken.text = "FCM token:\n$pushToken"

        val pass = iVGuardManager!!.password
        Log.d(TAG_MAIN, "Password: ${VTapUtility.bytesToHex(pass)}")

        initiateSmartToken()
        setLocationDownload()
        setViewModelObservers()
        initiateButtonListener()
    }

    private fun initiateButtonListener() {
        binding.btnCrtUser.setOnClickListener {
            viewModel.createUserOnTMS()
        }
        binding.btnTokenAssign.setOnClickListener {
            viewModel.assignToken()
        }
        binding.btnGetloadToken.setOnClickListener {
            viewModel.getLoadAckTokenFirmware()
        }
        binding.btnLoadToken.setOnClickListener {
            if (downloadFilePath.isEmpty()) {
                setLocationDownload()
            }
            viewModel.loadTokenFirmware(downloadFilePath)
        }
        binding.btnRegisterPin.setOnClickListener {
            if (binding.inputRegisterPin.text.isEmpty()){
                Toast.makeText(this, "Harap isi PIN -> Sample 111111", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.checkAndRegisterPIN(binding.inputRegisterPin.text.toString())
            }
        }
        binding.btnCheckToken.setOnClickListener {
            if (binding.inputCheckToken.text.isEmpty()){
                Toast.makeText(this, "Harap isi PIN -> Sample 111111", Toast.LENGTH_SHORT).show()
            }else {
                viewModel.checkToken(binding.inputCheckToken.text.toString())
            }
        }
        binding.btnRegistPki.setOnClickListener {
            if (binding.inputRegistPki.text.isEmpty()){
                Toast.makeText(this, "Harap isi PIN -> Sample 111111", Toast.LENGTH_SHORT).show()
            }else {
                viewModel.registerPKIFunction(binding.inputRegistPki.text.toString())
            }
        }
        binding.btnRegistPushNotif.setOnClickListener {
            if (pushToken.isEmpty()) {
                pushToken = MyFirebaseMessagingService.getToken(this).toString()
            }

            viewModel.registerPushNotification(deviceId, pushToken)
        }
    }

    private fun initiateSmartToken() {
        CoroutineScope(Dispatchers.IO).launch {
            /**
             *  Set up VTapFactory
             */
            iVTapManager = VTapFactory.getInstance(this@MainActivity)

            /**
             *  Set hostname
             */
            iVTapManager.setThreatIntelligenceServerURL(tiServer)
            iVTapManager.setHostName(provServer, vtapServer)
            iVTapManager.setPKIHostName(pkiServer)

            /**
             *  Set local broadcast
             */
            val localBroadcastManager: LocalBroadcastManager = LocalBroadcastManager.getInstance(this@MainActivity)
            localBroadcastManager.registerReceiver(mVtapRcvr, IntentFilter(VTAP_SETUP_ACTION))

            /**
             * Renewal PKI Certificate Broadcast
             * */
            val pushBroadcastManager: LocalBroadcastManager = LocalBroadcastManager.getInstance(this@MainActivity)
            pushBroadcastManager.registerReceiver(mPushReceiver, IntentFilter(
                PUSH_NOTIFICATION_BROADCAST
            ))

            /**
             *  Setup V-OS Smart Token
             */
            iVTapManager.setupVTap()

            /**
             * Send Troubleshooting Logs to Dashboard (Optional)
             * */
            sendTroubleshootingLogs()
        }
    }

    private suspend fun sendTroubleshootingLogs() {
        withContext(Dispatchers.IO) {
            val statusSendTroubleshootingLogs = iVTapManager.sendTroubleshootingLogs()
            when (statusSendTroubleshootingLogs) {
                40502 -> {
                    Log.i(TAG_MAIN, "VTAP_SEND_TROUBLESHOOTING_LOGS_SUCCESS")
                }
                40503 -> {
                    Log.i(TAG_MAIN, "VTAP_SEND_TROUBLESHOOTING_LOGS_FAILED")
                }
                41012 -> {
                    Log.i(TAG_MAIN, "VTAP_ERROR_CONNECTION_FAILED")
                }
                else -> {
                    Log.i(TAG_MAIN, "VTAP status sendTroubleshootingLogs: $statusSendTroubleshootingLogs")
                }
            }
        }
    }

    private val mVtapRcvr: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            if (VTAP_SETUP_ACTION.equals(action, ignoreCase = true)) {
                val vtapSetupStatus = intent.getIntExtra(VTAP_SETUP_STATUS, 0)
                if (vtapSetupStatus == ResultCode.VTAP_SETUP_SUCCESS) {
                    // TODO: Setup succeed, your action here
                    Log.d(TAG_MAIN, "VTap reciver sukses")

                    CoroutineScope(Dispatchers.IO).launch {
                        /**
                         *  Check Device Compatibility
                         */
                        when (iVTapManager.checkDeviceCompatibility()) {
                            ResultCode.VTAP_WHITE_LISTED_DEVICE -> {
                                /**
                                 *  TODO: Actions for whitelisted device, e.g. token provisioning,
                                 *  registration, transaction, authentication related actions, etc.
                                 *  See the API guide for the APIs available.
                                 */
                                Log.d(TAG_MAIN, "Test SmartToken : 1")
                            }
                            ResultCode.VTAP_BLACK_LISTED_DEVICE -> {
                                /**
                                 *  TODO: Action for blacklisted device, i.e. quit app
                                 */
                                Log.d(TAG_MAIN, "Test SmartToken : 2")
                            }
                            ResultCode.VTAP_GREY_LISTED_DEVICE -> {
                                /**
                                 *  TODO: Actions for the greylisted device, send device info and
                                 *  other similar activities as the whitelisted device if permitted.
                                 */
                                Log.d(TAG_MAIN, "Test SmartToken : 3")
                            }
                        }
                    }
                } else {
                    // TODO: Setup failed, your action here
                    Log.d(TAG_MAIN, "VTap reciver gagal")
                }
            }
        }
    }

    private val mPushReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {

            // Get the broadcast intent action
            val action = intent.action
            Log.i(TAG_MAIN, "action: $action")
            // When the broadcast received is a push notification
            if (PUSH_NOTIFICATION_BROADCAST.equals(action, true)) {

                // Capture the message ID and message type
                val messageId = intent.getStringExtra(PUSH_NOTIFICATION_MESSAGE_ID_KEY)
                val mesageType = intent.getStringExtra(PUSH_NOTIFICATION_MESSAGE_TYPE_KEY)

                if (messageId != null) {
                    // TODO: implement actions for different message types
                    Log.d(TAG_MAIN,"implement actions for different message types")
                }

                when {
                    mesageType.equals(MESSAGE_TYPE_ASP_CERT_RENEW, true) -> {
                        Log.d(TAG_MAIN, "ASP renewal")

                        // Check if the ASP certificate is already generated and PKI function
                        // is already registered. This is applicable to ASP certificate
                        if (iVTapManager.isPKIFunctionRegistered(VTapInterface.PKI_FUNC_ID_AUTH)) {
                            // Remove the PKI function and revoke the certificate if already exists
                            iVTapManager.removePKIFunction(VTapInterface.PKI_FUNC_ID_AUTH)
                            Log.d(TAG_MAIN, "Remove PKI Function - Auth")
                        }
                    }
                    mesageType.equals(MESSAGE_TYPE_SMP_CERT_RENEW, true) -> {
                        Log.d(TAG_MAIN, "ASP and SMP renewal")

                        // Check if the SMP certificate is already generated and PKI function
                        // is already registered. This is applicable to SMP certificate renewal.
                        if (iVTapManager.isPKIFunctionRegistered(VTapInterface.PKI_FUNC_ID_V_MESSAGE)) {
                            // Remove the PKI function and revoke the ASP certificate if already exists
                            iVTapManager.removePKIFunction(VTapInterface.PKI_FUNC_ID_AUTH)
                            // Remove the PKI function and revoke the SMP certificate if already exists
                            iVTapManager.removePKIFunction(VTapInterface.PKI_FUNC_ID_V_MESSAGE)

                            Log.d(TAG_MAIN, "Remove PKI Function - Auth & Message")
                        }
                    }
                    else -> {
                        Log.d(TAG_MAIN, "New notification: $mesageType | New msg id: $messageId")
                    }
                }
            }
        }
    }

    private fun setLocationDownload() {
        downloadFilePath = getExternalStorageDirectory().absolutePath
    }

    private fun setViewModelObservers() {
        val owner = this@MainActivity
        with(viewModel) {
            error.observe(owner, ::onError)
            isLoading.observe(owner, ::onLoading)
            id.observe(owner, ::showUserId)
            token.observe(owner, ::showTokenAssign)

            loadAckTokenFirmwareStatus.observe(owner ) {
                binding.responseGetloadToken.text = it
            }
            loadTokenFirmwareStatus.observe(owner) {
                binding.responseLoadToken.text = it
            }
            registerPINStatus.observe(owner) {
                binding.responseRegisterPin.text = it
            }
            checkTokenStatus.observe(owner) {
                binding.responseCheckToken.text = it
            }
            registerPushNotificationStatus.observe(owner) {
                binding.responseRegistPushNotif.text = it
            }
            registerPKIFunctionStatus.observe(owner) {
                binding.responseRegistPki.text = it
            }
        }
    }

    private fun onError(message: String) {
        Log.d(TAG_MAIN, message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun onLoading(isLoading: Boolean) {
        when (isLoading) {
            true -> binding.loadingBar.isVisible = true
            false -> binding.loadingBar.isVisible = false
        }
    }

    private fun showUserId(id: String) {
        if (id.isEmpty()) {
            Log.i(TAG_MAIN, "ID is empty!")
            return
        }

        binding.responseCrtUser.text = id
    }

    private fun showTokenAssign(data: TokenAssignModel) {
        if (data.token.isNullOrEmpty() || data.apin.isNullOrEmpty()) {
            Log.i(TAG_MAIN, "Token or APIN is null or empty")
            return
        }

        binding.responseTokenAssign.text = Gson().toJson(data)
    }

    companion object {
        var iVTapManager: VTapManager by Delegates.notNull()
        var iVGuardManager: VGuard? = null

        private var downloadFilePath = ""
        private var deviceId = ""
        private var pushToken = ""

        /**
         *  Set up server
         */
        const val urlProv = "https://sistemadev.com"
        const val urlVTap = "https://sistemadev.com"

        const val tiServer = urlProv
        const val provServer = "$urlProv/provision"
        const val vtapServer = "$urlVTap/vtap"
        const val pkiServer = urlVTap

        const val VTAP_SETUP_ACTION = "vkey.android.vtap.VTAP_SETUP"
        const val VTAP_SETUP_STATUS = "vkey.android.vtap.VTAP_SETUP_STATUS"

        private const val TAG_MAIN = "MainActivity"

        const val PUSH_NOTIFICATION_BROADCAST = "push_notification"
        const val PUSH_NOTIFICATION_MESSAGE_ID_KEY = "messageId"
        const val PUSH_NOTIFICATION_MESSAGE_TYPE_KEY = "messageType"
        const val MESSAGE_TYPE_ASP_CERT_RENEW = "ASP_CERT_RENEW"
        const val MESSAGE_TYPE_SMP_CERT_RENEW = "SMP_CERT_RENEW"
    }
}