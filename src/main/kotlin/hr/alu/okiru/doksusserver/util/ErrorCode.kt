package hr.alu.okiru.doksusserver.util

/** Machine-readable error codes returned to the frontend; mirrored in the client's `ErrorCode.ts`. */
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