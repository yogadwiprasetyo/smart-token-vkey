package id.co.sistema.vkey.smarttoken

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.gson.Gson
import com.vkey.android.vguard.LocalBroadcastManager
import com.vkey.android.vguard.VGuard
import com.vkey.android.vtap.PNSType
import com.vkey.android.vtap.VTapFactory
import com.vkey.android.vtap.VTapInterface
import com.vkey.android.vtap.VTapManager
import com.vkey.android.vtap.pki.DistinguishedName
import com.vkey.android.vtap.utility.ResultCode
import id.co.sistema.vkey.CustomApplication
import id.co.sistema.vkey.MainActivity
import id.co.sistema.vkey.SmartTokenViewModel
import id.co.sistema.vkey.TokenAssignModel
import id.co.sistema.vkey.databinding.ActivitySmartTokenBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.and
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.properties.Delegates

class SmartTokenActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySmartTokenBinding
    private val viewModel by viewModel<SmartTokenViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySmartTokenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCreate.setOnClickListener {
            viewModel.createUserOnTMS()
        }

        binding.btnToken.setOnClickListener {
            viewModel.assignToken()
        }

        collectVGuardData()
        logPasswordVGuard()
        setLocationDownload()
        setViewModelObservers()
        initiateSmartToken()
    }

    private fun collectVGuardData() {
        CustomApplication.vGuardManager?.let {
            iVGuardManager = it
            deviceId = it.troubleshootingId
            viewModel.initialize(it.customerId.toString())
        }
    }

    private fun logPasswordVGuard() {
        val pass = iVGuardManager!!.password
        Log.d("SmartToken", "Pass-1: $pass")
        Log.d("SmartToken", "Pass-2: ${String(pass)}")
        Log.d("SmartToken", "Pass-3: ${Base64.decode(pass, Base64.DEFAULT)}")
        Log.d("SmartToken", "Pass-4: ${bytesToHex(pass)}")
        Log.d("SmartToken", "Pass-5: ${bytesToHex(Base64.decode(pass, Base64.DEFAULT))}")
    }

    /**
     * Convert byteArray into Hex
     */
    fun bytesToHex(data: ByteArray?): String {
        if (data == null) {
            return ""
        }
        val len = data.size
        var str = ""
        for (i in 0 until len) {
            str = if (data[i] and 0xFF < 16) {
                str + "0" + Integer.toHexString(data[i] and 0xFF)
            } else { str + Integer.toHexString(data[i] and 0xFF) }
        }
        return str
    }

    private fun setLocationDownload() {
        downloadFilePath = Environment.getExternalStorageDirectory().absolutePath
    }

    private fun setViewModelObservers() {
        viewModel.error.observe(this, ::onError)
        viewModel.isLoading.observe(this, ::onLoading)
        viewModel.id.observe(this, ::setUserId)
        viewModel.token.observe(this, ::doProvisioning)
    }

    private fun onError(message: String) {
        Log.e("SmartToken", message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun onLoading(isLoading: Boolean) {
        when (isLoading) {
            true -> binding.loading.isVisible = true
            false -> binding.loading.isVisible = false
        }
    }

    private fun setUserId(id: String) {
        if (id.isEmpty()) {
            Log.i("SmartToken", "ID is empty!")
            return
        }

        binding.tvUserId.text = id
        userId = id
    }

    private fun doProvisioning(data: TokenAssignModel) {
        if (data.token.isNullOrEmpty() || data.apin.isNullOrEmpty()) {
            Log.i("SmartToken", "Token or APIN is null or empty")
            return
        }

        binding.tvToken.text = Gson().toJson(data)
        CoroutineScope(Dispatchers.Main).launch {
            provisioningToken(data)
        }
    }

    private suspend fun provisioningToken(data: TokenAssignModel) {
        val tokenSerial = data.token.toString()
        // Decode using Base64
        val apin = Base64.decode(data.apin.toString(), Base64.DEFAULT)
        // Convert bytes to string Hexadecimal
        val apinStringHex = bytesToHex(apin)

        withContext(Dispatchers.IO) {
            val provisioningInfo = ArrayList(listOf(tokenSerial, apinStringHex))
            Log.d("SmartToken", "Token = $tokenSerial and APIN = $apin")

            val loadTokenFirmwareStatus = iVTapManager.getLoadAckTokenFirmware(provisioningInfo)
            when (loadTokenFirmwareStatus) {
                40600 -> {
                    /**
                     *  VTAP_TOKEN_DOWNLOAD_SUCCESS, token is loaded successfully
                     *  Proceed to create token PIN
                     */
                    Log.d("SmartToken", "Test SmartToken : Provision 1")
                    loadTokenFirmware(provisioningInfo)
                }
                40608 -> {
                    /**
                     *  VTAP_LOAD_FIRMWARE_SUCCESS, token is loaded successfully
                     *  Proceed to create token PIN
                     */
                    Log.d("SmartToken", "Test SmartToken : Provision 2")
                }
                else -> {
                    /**
                     *  Other possible result codes
                     */
                    Log.d("SmartToken", "Test SmartToken : Provision 3 -> $loadTokenFirmwareStatus")
                }
            }
        }
    }

    private suspend fun loadTokenFirmware(provisioningInfo: ArrayList<String>) {
        withContext(Dispatchers.IO) {
            val loadTokenFirmwareStatus = iVTapManager.loadTokenFirmware(
                provisioningInfo[0],
                provisioningInfo[1],
                downloadFilePath
            )

            if (loadTokenFirmwareStatus == 40608) {
                // VTAP_LOAD_FIRMWARE_SUCCESS, token is loaded successfully
                // Proceed to create token PIN
                Log.d("SmartToken", "LoadTokenFirmware Success")

                checkAndRegisterPIN(provisioningInfo[0])
            } else {
                // Other possible result codes
                Log.d("SmartToken", "LoadTokenFirmware Failed")
            }
        }
    }

    private suspend fun checkAndRegisterPIN(tokenSerial: String) {
        withContext(Dispatchers.IO) {
            /**
             *  Check if token is registered
             */
            val isTokenRegisteredStatus = iVTapManager.isTokenRegistered(tokenSerial)

            /**
             *  If token is not registered, set token PIN
             */
            if (!isTokenRegisteredStatus) {
                /**
                 *  Create token PIN
                 */
                val createTokenPinStatus = iVTapManager.createTokenPIN(pin, tokenSerial)
                when (createTokenPinStatus) {
                    40700 -> {
                        /**
                         *  VTAP_CREATE_PIN_SUCCESS, create PIN successful
                         *  Proceed to check token PIN
                         */
                        Log.d("SmartToken", "Test SmartToken : Provision 4")
                        checkToken(tokenSerial)
                    }
                    40701 -> {
                        /**
                         *  VTAP_CREATE_PIN_FAILED, create PIN failed
                         */
                        Log.d("SmartToken", "Test SmartToken : Provision 5")
                    }
                    else -> {
                        /**
                         *  Other possible result codes
                         */
                        Log.d("SmartToken", "Test SmartToken : Provision 6 -> $createTokenPinStatus")
                    }
                }
            }
        }
    }

    private suspend fun checkToken(tokenSerial: String) {
        withContext(Dispatchers.IO) {
            // Check token PIN
            val checkTokenPinStatus = iVTapManager.checkTokenPIN(pin, false, tokenSerial)
            Log.d("SmartToken", "Return : $checkTokenPinStatus")
            when (checkTokenPinStatus) {
                40800 -> {
                    // VTAP_CHECK_PIN_SUCCESS, check PIN successful
                    // Proceed to OTP/PKI/FIDO2 functions
                    Log.d("SmartToken", "Test SmartToken : Check PIN Success")
                    registerPushNotification()
                }
                40801 -> {
                    // VTAP_CHECK_PIN_FAILED, check PIN failed
                    Log.d("SmartToken", "Test SmartToken : Check PIN Failed")
                }
                else -> {
                    // Other possible result codes
                    Log.d("SmartToken", "Test SmartToken : Check PIN Failed by $checkTokenPinStatus")
                }
            }
        }
    }

    private suspend fun registerPushNotification(){
        withContext(Dispatchers.Main){
            // Based on your desired push notification service type,
            // i.e., PNSType.FCM, PNSType.HMS
            val pnsType = PNSType.FCM

            // Checking for ASP authentication function
            if (iVTapManager.isPKIFunctionRegistered(VTapInterface.PKI_FUNC_ID_AUTH)) {
                iVTapManager.removePKIFunction(VTapInterface.PKI_FUNC_ID_AUTH);
            }

            // Checking for SMP secure messaging function
            if (iVTapManager.isPKIFunctionRegistered(VTapInterface.PKI_FUNC_ID_V_MESSAGE)) {
                iVTapManager.removePKIFunction(VTapInterface.PKI_FUNC_ID_V_MESSAGE);
            }

            Log.d("SmartToken","CrossCheck : user -> $userId , deviceId -> $deviceId , pushTOken -> $pushToken , pnsType -> $pnsType")
            val pushRegResult = iVTapManager.pushNotificationRegister(
                userId,
                deviceId,
                pushToken,
                pnsType
            )
            when (pushRegResult) {
                41014 -> {
                    // VTAP_PUSH_NOTIFICATION_REGISTRATION_SUCCESS
                    // Proceed to OTP/PKI/FIDO2 functions
                    registeringPKIFunction()
                }

                else -> {
                    // Other possible result codes
                    Log.d("SmartToken","Register push notification failed by $pushRegResult")
                }
            }
        }
    }

    private suspend fun registeringPKIFunction(){
        // Preparing Distinguished Name
        val distinguishedName = DistinguishedName().apply {
            country = "ID"
            stateName = "JKT"
            localityName = "IDN"
            organizationName = "Sistema"
            organizationUnit = "IT"
            givenName = "Test"
            surname = "TestUser"
            serialNumber = "ABC123"
            emailAddress = "test@test.id"
        }

        withContext(Dispatchers.Main){
            // For ASP authentication function
            val aspGenerateCsrAndSendResult = iVTapManager.generateCsrAndSendSync(
                VTapInterface.PKI_FUNC_ID_AUTH,
                distinguishedName,
                pin,
                false
            )

            // For SMP secure messaging function
            val smpGenerateCsrAndSendResult = iVTapManager.generateCsrAndSendSync(
                VTapInterface.PKI_FUNC_ID_V_MESSAGE,
                distinguishedName,
                "0",
                false
            )

            Log.d("SmartToken","status ASP: $aspGenerateCsrAndSendResult")
            if (aspGenerateCsrAndSendResult == 41100) {
                // ASP certificate and authentication function is registered successfully
                Log.d("SmartToken","Test SmartToken : ASP certificate and secure messaging function is registered successfully")
            } else {
                // When generation failed
                Log.d("SmartToken","Test SmartToken : ASP generation failed")
            }

            Log.d("SmartToken","status SMP: $smpGenerateCsrAndSendResult")
            if (smpGenerateCsrAndSendResult == 41100) {
                // SMP certificate and secure messaging function is registered successfully
                Log.d("SmartToken","Test SmartToken : SMP certificate and secure messaging function is registered successfully")
            } else {
                // When generation failed
                Log.d("SmartToken","Test SmartToken : SMP generation failed")
            }
        }
    }

    private fun initiateSmartToken() {
        /**
         *  Set up VTapFactory
         */
        iVTapManager = VTapFactory.getInstance(this)

        /**
         *  Set hostname
         */
        iVTapManager.setThreatIntelligenceServerURL(tiServer)
        iVTapManager.setHostName(provServer, vtapServer)
        iVTapManager.setPKIHostName(pkiServer)

        /**
         *  Set local broadcast
         */
        val localBroadcastManager: LocalBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.registerReceiver(mVtapRcvr, IntentFilter(VTAP_SETUP_ACTION))

        /**
         *  Setup V-OS Smart Token
         */
        iVTapManager.setupVTap()
    }

    private val mVtapRcvr: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            if (VTAP_SETUP_ACTION.equals(action, ignoreCase = true)) {
                val vtapSetupStatus = intent.getIntExtra(VTAP_SETUP_STATUS, 0)
                if (vtapSetupStatus == ResultCode.VTAP_SETUP_SUCCESS) {
                    // TODO: Setup succeed, your action here
                    Log.d("SmartToken", "VTap receiver success")

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
                            Log.d("SmartToken", "Test SmartToken : 1")
                        }
                        ResultCode.VTAP_BLACK_LISTED_DEVICE -> {
                            /**
                             *  TODO: Action for blacklisted device, i.e. quit app
                             */
                            Log.d("SmartToken", "Test SmartToken : 2")
                        }
                        ResultCode.VTAP_GREY_LISTED_DEVICE -> {
                            /**
                             *  TODO: Actions for the greylisted device, send device info and
                             *  other similar activities as the whitelisted device if permitted.
                             */
                            Log.d("SmartToken", "Test SmartToken : 3")
                        }
                    }
                } else {
                    // TODO: Setup failed, your action here
                    Log.d("SmartToken", "VTap receiver failed")
                }
            }
        }
    }

    companion object {
        var iVTapManager: VTapManager by Delegates.notNull()
        var iVGuardManager: VGuard? = null

        private var downloadFilePath = ""
        private var userId = ""
        private var deviceId = ""
        private var pushToken = ""

        /*
         * PIN from user
         * */
        private var pin = "111111"


        /*
         * Set the host URLs for servers
         */
        const val urlProv = "https://sistemadev.com"
        const val urlVTap = "https://sistemadev.com"
        const val tiServer = urlProv
        const val provServer = "$urlProv/provision"
        const val vtapServer = "$urlVTap/vtap"
        const val pkiServer = urlVTap

        /*
         * Custom broadcast receiver to listen V-OS Smart Token setup
         * broadcasts from VTapManager
         * */
        const val VTAP_SETUP_ACTION = "vkey.android.vtap.VTAP_SETUP"
        const val VTAP_SETUP_STATUS = "vkey.android.vtap.VTAP_SETUP_STATUS"
    }
}