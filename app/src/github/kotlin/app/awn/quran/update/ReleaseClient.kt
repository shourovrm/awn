package app.awn.quran.update

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val RELEASES_URL = "https://api.github.com/repos/shourovrm/awn/releases?per_page=30"

/** GitHub answered with an error status, such as 403 when the hourly request limit is used up. */
internal class HttpStatusException(url: String, val statusCode: Int) :
    IOException("$url answered $statusCode")

/** Reads the release list and small text files from GitHub. */
internal class ReleaseClient(private val httpClient: OkHttpClient) {

    /** Throws [IOException] when GitHub cannot be reached or does not answer with the list. */
    suspend fun listReleases(): List<AppRelease> {
        val request =
            Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
        return parseReleases(fetchText(request))
    }

    suspend fun fetchText(url: String): String = fetchText(Request.Builder().url(url).build())

    private suspend fun fetchText(request: Request): String =
        httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful)
                throw HttpStatusException(request.url.toString(), response.code)
            response.body.string()
        }
}

/** Runs the call without blocking a thread, and cancels it when the coroutine is cancelled. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        },
    )
}
