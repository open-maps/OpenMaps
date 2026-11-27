package nz.eloque.openmaps

import kotlinx.coroutines.suspendCancellableCoroutine
import nz.eloque.openmaps.map.FetchedFile
import nz.eloque.openmaps.map.MapFileFetcher
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.toKotlinInstant

/** Loads map files for the map renderer, allowing as many parallel requests per server as the renderer itself would. */
class OkHttpMapFiles(
    private val userAgent: String,
) : MapFileFetcher {
    private val client =
        OkHttpClient
            .Builder()
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
            .build()

    override suspend fun fetch(
        url: String,
        etag: String?,
    ): FetchedFile {
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .apply { etag?.let { header("If-None-Match", it) } }
                .build()
        return client.newCall(request).await().use { response ->
            FetchedFile(
                bytes = response.body.bytes().takeIf { response.isSuccessful && response.code != NO_CONTENT },
                status = response.code,
                etag = response.header("ETag"),
                expires = response.headers.getInstant("Expires")?.toKotlinInstant(),
                modified = response.headers.getInstant("Last-Modified")?.toKotlinInstant(),
            )
        }
    }
}

private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) = continuation.resume(response)

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) = continuation.resumeWithException(e)
            },
        )
    }

private const val MAX_REQUESTS_PER_HOST = 20
private const val NO_CONTENT = 204
