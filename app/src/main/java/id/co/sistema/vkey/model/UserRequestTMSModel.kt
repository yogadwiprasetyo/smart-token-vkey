package id.co.sistema.vkey.model

data class UserRequestTMSModel(
    val userId: String?,
    val createdUser: String?,
    val customerId: String?,
    val nric: String?, // The national ID of the user
    val firstName: String?,
    val lastName: String?,
    val country: String?,
    val deviceId: String?
)
