package hr.alu.okiru.doksusserver.util

enum class ErrorCode {
    NOT_WHITELISTED,
    ALREADY_REGISTERED,
    INTERNAL_ERROR,
    UNAUTHORIZED,
    BAD_REQUEST,
    SUCCESS // Optional: can be used for generic 200 OK responses if needed
}

data class ApiError(
    val errorCode: ErrorCode,
    val message: String? = null
)