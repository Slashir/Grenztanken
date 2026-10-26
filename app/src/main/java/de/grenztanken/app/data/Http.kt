package de.grenztanken.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT = "Grenztanken/0.1 (Android)"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** GET; liefert (HTTP-Status, Body). Wirft IOException bei Netzwerkfehlern. */
    suspend fun get(url: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            response.code to (response.body?.string() ?: "")
        }
    }
}

fun errorText(e: Throwable): String = when (e) {
    is UnknownHostException -> "keine Verbindung zum Server"
    is SocketTimeoutException -> "Zeitüberschreitung"
    else -> e.message ?: e.javaClass.simpleName
}
