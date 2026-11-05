package com.routinepulse.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.routinepulse.app.data.model.ErrorBody
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/** Categories of failure the UI knows how to explain to the user. */
enum class ErrorKind { NETWORK, UNAUTHORIZED, NOT_FOUND, CONFLICT, VALIDATION, SERVER, UNKNOWN }

/** [message] is the server's own explanation (e.g. "Invalid email or password") when it sent one. */
data class ApiError(val kind: ErrorKind, val message: String? = null)

/** Success/failure wrapper so ViewModels never deal with exceptions directly. */
sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Failure(val error: ApiError) : ApiResult<Nothing>()
}

/** Pure helper (unit tested): turns an HTTP status + JSON error body into an [ApiError]. */
object ApiErrorParser {
    private val gson = Gson()

    fun kindForStatus(code: Int): ErrorKind = when (code) {
        401 -> ErrorKind.UNAUTHORIZED
        404 -> ErrorKind.NOT_FOUND
        409 -> ErrorKind.CONFLICT
        400, 422 -> ErrorKind.VALIDATION
        in 500..599 -> ErrorKind.SERVER
        else -> ErrorKind.UNKNOWN
    }

    fun fromHttp(code: Int, body: String?): ApiError {
        val message = try {
            gson.fromJson(body, ErrorBody::class.java)?.error
        } catch (e: Exception) {
            null // body was not JSON (e.g. an HTML error page from the host)
        }
        return ApiError(kindForStatus(code), message?.takeIf { it.isNotBlank() })
    }
}

private const val TAG = "ApiCall"

/** Runs a network call and converts every possible failure into an [ApiResult.Failure]. */
suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e // never swallow coroutine cancellation
} catch (e: HttpException) {
    // Reading the body can itself fail, so guard it - the user should still get a sensible error.
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    val error = ApiErrorParser.fromHttp(e.code(), body)
    Log.w(TAG, "HTTP ${e.code()} -> ${error.kind}: ${error.message}")
    ApiResult.Failure(error)
} catch (e: IOException) {
    Log.w(TAG, "Network failure: ${e.javaClass.simpleName}: ${e.message}")
    ApiResult.Failure(ApiError(ErrorKind.NETWORK))
} catch (e: Exception) {
    Log.e(TAG, "Unexpected error", e)
    ApiResult.Failure(ApiError(ErrorKind.UNKNOWN))
}
