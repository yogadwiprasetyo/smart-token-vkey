package id.co.sistema.vkey

import android.util.Base64
import android.util.Log
import androidx.lifecycle.*
import com.vkey.android.vtap.PNSType
import com.vkey.android.vtap.VTapInterface
import com.vkey.android.vtap.pki.DistinguishedName
import com.vkey.android.vtap.utility.VTapUtility.bytesToHex
import id.co.sistema.vkey.smarttoken.MyFirebaseMessagingService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmartTokenViewModel(private val repository: SmartTokenRepository) : ViewModel() {

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _id = MutableLiveData<String>()
    val id: LiveData<String> = _id

    private val _customerId = MutableLiveData<String>()
    val customerId: LiveData<String> = _customerId

    private val _token = MutableLiveData<TokenAssignModel>()
    val token: LiveData<TokenAssignModel> = _token
    
    private val _provisioningInfo = MutableLiveData<ArrayList<String>>()
    val provisioningInfo: LiveData<ArrayList<String>> = _provisioningInfo
    
    private val _loadAckTokenFirmwareStatus = MutableLiveData<String>()
    val loadAckTokenFirmwareStatus: LiveData<String> = _loadAckTokenFirmwareStatus

    private val _loadTokenFirmwareStatus = MutableLiveData<String>()
    val loadTokenFirmwareStatus: LiveData<String> = _loadTokenFirmwareStatus

    private val _registerPINStatus = MutableLiveData<String>()
    val registerPINStatus: LiveData<String> = _registerPINStatus

    private val _checkTokenStatus = MutableLiveData<String>()
    val checkTokenStatus: LiveData<String> = _checkTokenStatus

    private val _registerPushNotificationStatus = MutableLiveData<String>()
    val registerPushNotificationStatus: LiveData<String> = _registerPushNotificationStatus

    private val _registerPKIFunctionStatus = MutableLiveData<String>()
    val registerPKIFunctionStatus: LiveData<String> = _registerPKIFunctionStatus

    private val errorHandler = CoroutineExceptionHandler { _, e ->
        Log.d("ViewModel", e.message.toString())
        _error.value = e.message
        _isLoading.value = false
    }
    
    fun initialize(customerId: String) {
        _customerId.postValue(customerId)
    }

    fun createUserOnTMS() {
        val userRequestTMS = UserRequestTMSModel(
            userId = "test@test.id",
            createdUser = "sistema_server",
            customerId = customerId.value,
            nric = "ABC123",
            firstName = "Test",
            lastName = "Sistema",
            country = "ID",
            deviceId = "test",
        )

        viewModelScope.launch(errorHandler) {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                repository.createUserTMS(userRequestTMS).collectLatest {
                    _id.postValue(it)
                }
            }
            _isLoading.value = false
        }
    }

    fun assignToken() {
        val body = TokenRequestTMSModel(id = id.value, customerId = "7824")
        viewModelScope.launch(errorHandler) {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                repository.assignTokenTMS(body).collect {
                    _token.postValue(it)
                }
            }
            _isLoading.value = false
        }
    }
    
    fun getLoadAckTokenFirmware() {
        val tokenSerial = token.value?.token as String
        // Decode using Base64
        val apin = Base64.decode(token.value?.apin.toString(), Base64.DEFAULT)
        // Convert bytes to string Hexadecimal
        val apinStringHex = bytesToHex(apin)

        viewModelScope.launch(errorHandler) {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                val provInfo = ArrayList(listOf(tokenSerial, apinStringHex))
                _provisioningInfo.postValue(provInfo)
                Log.d("ViewModel", "Token = $tokenSerial and APIN = $apin")

                val loadTokenFirmwareStatus = MainActivity.iVTapManager.getLoadAckTokenFirmware(provInfo)
                when (loadTokenFirmwareStatus) {
                    40600 -> {
                        /**
                         *  VTAP_TOKEN_DOWNLOAD_SUCCESS, token is loaded successfully
                         *  Proceed to create token PIN
                         */
                        Log.d("ViewModel", "Test SmartToken : Provision 1")
                        _loadAckTokenFirmwareStatus.postValue("Code: $loadTokenFirmwareStatus, Message: Token Download Success")
                    }
                    40608 -> {
                        /**
                         *  VTAP_LOAD_FIRMWARE_SUCCESS, token is loaded successfully
                         *  Proceed to create token PIN
                         */
                        Log.d("ViewModel", "Test SmartToken : Provision 2")
                        _loadAckTokenFirmwareStatus.postValue("Code: $loadTokenFirmwareStatus, Message: Load Firmware Success")
                    }
                    else -> {
                        /**
                         *  Other possible result codes
                         */
                        Log.d("ViewModel", "Test SmartToken : Provision 3 -> $loadTokenFirmwareStatus")
                        _loadAckTokenFirmwareStatus.postValue("Code: $loadTokenFirmwareStatus, Message: Failed")
                    }
                }
            }
            _isLoading.value = false
        }
    }

    fun loadTokenFirmware(downloadFilePath: String) {
        viewModelScope.launch(errorHandler) {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                val loadTokenFirmwareStatus = MainActivity.iVTapManager.loadTokenFirmware(
                    provisioningInfo.value?.get(0),
                    provisioningInfo.value?.get(1),
                    downloadFilePath
                )

                if (loadTokenFirmwareStatus == 40608) {
                    // VTAP_LOAD_FIRMWARE_SUCCESS, token is loaded successfully
                    // Proceed to create token PIN
                    Log.d("ViewModel", "Test SmartToken LoadTokenFirmware Success")
                    _loadTokenFirmwareStatus.postValue("Code: $loadTokenFirmwareStatus, Message: Load Token Firmware Success")
                } else {
                    // Other possible result codes
                    Log.d("ViewModel", "Test SmartToken LoadTokenFirmware Failed")
                    _loadTokenFirmwareStatus.postValue("Code: $loadTokenFirmwareStatus, Message: Load Token Firmware Failed")
                }
            }
            _isLoading.value = false
        }
    }
    
    fun checkAndRegisterPIN(pin: String? = "111111") {
        viewModelScope.launch(errorHandler) {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                /**
                 *  Check if token is registered
                 */
                val isTokenRegisteredStatus = MainActivity.iVTapManager.isTokenRegistered(provisioningInfo.value?.get(0))

                /**
                 *  If token is not registered, set token PIN
                 */
                if (!isTokenRegisteredStatus) {
                    /**
                     *  Create token PIN
                     */
                    val createTokenPinStatus = MainActivity.iVTapManager.createTokenPIN(pin, provisioningInfo.value?.get(0))
                    when (createTokenPinStatus) {
                        40700 -> {
                            /**
                             *  VTAP_CREATE_PIN_SUCCESS, create PIN successful
                             *  Proceed to check token PIN
                             */
                            Log.d("ViewModel", "Test SmartToken : Provision 4")
                            _registerPINStatus.postValue("Code: $createTokenPinStatus, Message: Create PIN Successful")
                        }
                        40701 -> {
                            /**
                             *  VTAP_CREATE_PIN_FAILED, create PIN failed
                             */
                            Log.d("ViewModel", "Test SmartToken : Provision 5")
                            _registerPINStatus.postValue("Code: $createTokenPinStatus, Message: Create PIN Failed")
                        }
                        else -> {
                            /**
                             *  Other possible result codes
                             */
                            Log.d("ViewModel", "Test SmartToken : Provision 6 -> $createTokenPinStatus")
                            _registerPINStatus.postValue("Code: $createTokenPinStatus, Message: Unknown Status")
                        }
                    }
                }
            }
            _isLoading.value = false
        }
    }
    
    fun checkToken(pin: String? = "111111") {
        viewModelScope.launch(errorHandler) {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                // Check token PIN
                val checkTokenPinStatus = MainActivity.iVTapManager.checkTokenPIN(pin, false, provisioningInfo.value?.get(0))
                Log.d("ViewModel", "Return : $checkTokenPinStatus")
                when (checkTokenPinStatus) {
                    40800 -> {
                        // VTAP_CHECK_PIN_SUCCESS, check PIN successful
                        // Proceed to OTP/PKI/FIDO2 functions
                        Log.d("ViewModel", "Test SmartToken : Check PIN Success")
                        _checkTokenStatus.postValue("Code: $checkTokenPinStatus, Message: Check PIN Success")
                    }
                    40801 -> {
                        // VTAP_CHECK_PIN_FAILED, check PIN failed
                        Log.d("ViewModel", "Test SmartToken : Check PIN Failed")
                        _checkTokenStatus.postValue("Code: $checkTokenPinStatus, Message: Check PIN Failed")
                    }
                    else -> {
                        // Other possible result codes
                        Log.d("ViewModel", "Test SmartToken : Check PIN Failed by $checkTokenPinStatus")
                        _checkTokenStatus.postValue("Code: $checkTokenPinStatus, Message: Unknown Message")
                    }
                }
            }
            _isLoading.value = false
        }
    }

    fun registerPushNotification(deviceId: String, pushToken: String) {
        viewModelScope.launch(errorHandler) {
            _isLoading.value = true
            withContext(Dispatchers.IO){
                // Based on your desired push notification service type,
                // i.e., PNSType.FCM, PNSType.HMS
                val pnsType = PNSType.FCM

                // Checking for ASP authentication function
                if (MainActivity.iVTapManager.isPKIFunctionRegistered(VTapInterface.PKI_FUNC_ID_AUTH)) {
                    MainActivity.iVTapManager.removePKIFunction(VTapInterface.PKI_FUNC_ID_AUTH);
                }

                // Checking for SMP secure messaging function
                if (MainActivity.iVTapManager.isPKIFunctionRegistered(VTapInterface.PKI_FUNC_ID_V_MESSAGE)) {
                    MainActivity.iVTapManager.removePKIFunction(VTapInterface.PKI_FUNC_ID_V_MESSAGE);
                }

                Log.d("ViewModel","CrossCheck : user -> ${id.value} , deviceId -> $deviceId , pushToken -> $pushToken , pnsType -> $pnsType")
                val pushRegResult = MainActivity.iVTapManager.pushNotificationRegister(
                    null,
                    null,
                    pushToken,
                    pnsType
                )

                when (pushRegResult) {
                    41014 -> {
                        // VTAP_CHECK_PIN_SUCCESS, check PIN successful
                        // Proceed to OTP/PKI/FIDO2 functions
                        Log.d("ViewModel","Test SmartToken : Register push notif Success")
                        _registerPushNotificationStatus.postValue("Code: $pushRegResult, Message: Register Push Notification Success")
                    }
                    else -> {
                        // Other possible result codes
                        Log.d("ViewModel","Test SmartToken : Register push notif failed by $pushRegResult")
                        _registerPushNotificationStatus.postValue("Code: $pushRegResult, Message: Register Push Notification Failed")
                    }
                }
            }
            _isLoading.value = false
        }
    }

    fun registerPKIFunction(pin: String? = "111111") {
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

        viewModelScope.launch(errorHandler){
            _isLoading.value = true
            withContext(Dispatchers.IO){
                // For ASP authentication function
                val aspGenerateCsrAndSendResult = MainActivity.iVTapManager.generateCsrAndSendSync(
                    VTapInterface.PKI_FUNC_ID_AUTH,
                    distinguishedName,
                    pin,
                    false
                )

                // For SMP secure messaging function
                val smpGenerateCsrAndSendResult = MainActivity.iVTapManager.generateCsrAndSendSync(
                    VTapInterface.PKI_FUNC_ID_V_MESSAGE,
                    distinguishedName,
                    "0", // Set pin to zero to avoid INVALID INPUT
                    false
                )

                Log.i("ViewModel", "ASP status: $aspGenerateCsrAndSendResult")
                val aspRegisterStatus = if (aspGenerateCsrAndSendResult == 41100) {
                    // ASP certificate and authentication function is registered successfully
                    Log.i("ViewModel","Test SmartToken : ASP certificate and secure messaging function is registered successfully")
                    "Code: $aspGenerateCsrAndSendResult, Message: ASP is registered successfully"
                } else {
                    // When generation failed
                    Log.i("ViewModel","Test SmartToken : ASP generation failed")
                    "Code: $aspGenerateCsrAndSendResult, Message: ASP is registered failed"
                }

                Log.i("ViewModel", "SMP status: $smpGenerateCsrAndSendResult")
                val smpRegisterStatus = if (smpGenerateCsrAndSendResult == 41100) {
                    // SMP certificate and secure messaging function is registered successfully
                    Log.i("ViewModel","Test SmartToken : SMP certificate and secure messaging function is registered successfully")
                    "Code: $smpGenerateCsrAndSendResult, Message: SMP is registered successfully"
                } else {
                    // When generation failed
                    Log.i("ViewModel","Test SmartToken : SMP generation failed")
                    "Code: $smpGenerateCsrAndSendResult, Message: SMP is registered failed"
                }
                
                _registerPKIFunctionStatus.postValue("$aspRegisterStatus\n$smpRegisterStatus")
            }
            _isLoading.value = false
        }
    }
}