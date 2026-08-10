package no.vardir.skald.core.gesh

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The one seam between the protocol client and the network, so tests can drive
 * a whole sync loop against an in-memory relay without a socket.
 */
interface HttpTransport {

    data class Call(
        val method: String,
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val body: ByteArray? = null,
    )

    data class Reply(
        val status: Int,
        val body: ByteArray = ByteArray(0),
        val headers: Map<String, String> = emptyMap(),
    ) {
        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    /** Throws [IOException] when the relay could not be reached at all. */
    fun execute(call: Call): Reply
}

/** The real transport. One client is shared, as OkHttp intends. */
class OkHttpTransport(
    private val client: OkHttpClient = defaultClient(),
) : HttpTransport {

    override fun execute(call: HttpTransport.Call): HttpTransport.Reply {
        val builder = Request.Builder().url(call.url)
        for ((name, value) in call.headers) builder.header(name, value)

        val contentType = call.headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value?.toMediaType()
        val body = call.body?.toRequestBody(contentType)

        // OkHttp requires a body on PUT/POST even when there is nothing to send.
        val needsBody = call.method == "POST" || call.method == "PUT" || call.method == "PATCH"
        builder.method(call.method, body ?: if (needsBody) ByteArray(0).toRequestBody(null) else null)

        client.newCall(builder.build()).execute().use { response ->
            return HttpTransport.Reply(
                status = response.code,
                body = response.body?.bytes() ?: ByteArray(0),
                headers = response.headers.toMultimap().mapValues { it.value.firstOrNull().orEmpty() },
            )
        }
    }

    companion object {
        fun defaultClient(timeoutSeconds: Long = 30): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
