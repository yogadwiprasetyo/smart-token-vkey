package id.co.sistema.vkey

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.vkey.android.secure.net.Response
import com.vkey.android.secure.net.SecureHttpUrlConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class SmartTokenRepository (private val context: Context) {

    suspend fun createUserTMS(userRequest: UserRequestTMSModel): Flow<String> =
        flow {
            val response: Response?
            try {
                // Convert request to JSON
                val data = Gson().toJson(userRequest)
                // Send post request to server
                response = SecureHttpUrlConnection.post_urlconnection(
                    CREATE_USER_TMS_PATH, NULL, JSON_TYPE, data, context, NULL
                )

                // Return empty string if failed
                if (response?.response == NULL && response.responseCode != 200) {
                    Log.d(TAG_REPO, "Response failed, code: ${response?.responseCode}")
                    emit("")
                    return@flow
                }

                emit(String(response.response))
                Log.d(TAG_REPO, "Response: ${String(response.response)}")
            } catch (e: Exception) {
                emit("")
                Log.e(TAG_REPO, "Error: ${e.message.toString()}")
            }
        }

    suspend fun assignTokenTMS(body: TokenRequestTMSModel): Flow<TokenAssignModel> =
        flow {
            val response: Response?
            try {
                // Convert request to JSON
                val data = Gson().toJson(body)
                // Send post request to server
                response = SecureHttpUrlConnection.post_urlconnection(
                    TOKEN_ASSIGN_PATH, NULL, JSON_TYPE, data, context, NULL
                )

                // Return data class with null value if failed
                if (response?.response == NULL && response.responseCode != 200) {
                    Log.d(TAG_REPO, "Response failed, code: ${response?.responseCode}")
                    emit(TokenAssignModel(null, null))
                    return@flow
                }

                val responseInString = String(response.response)
                val result = Gson().fromJson(responseInString, TokenAssignModel::class.java)
                emit(result)
                Log.d(TAG_REPO, "Response: ${String(response.response)}")
            } catch (e: Exception) {
                emit(TokenAssignModel(null, null))
                Log.e(TAG_REPO, "Error: ${e.message.toString()}")
            }
        }

    companion object {
        private const val CREATE_USER_TMS_PATH = "https://sistemadev.com/tms/user/create"
        private const val TOKEN_ASSIGN_PATH = "https://sistemadev.com/tms/token/assign"
        private const val JSON_TYPE = "application/json"
        private const val TAG_REPO = "Repository"
        private val NULL = null
    }
}