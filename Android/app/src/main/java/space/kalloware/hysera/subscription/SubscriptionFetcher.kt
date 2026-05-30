package space.kalloware.hysera.subscription

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SubscriptionFetcher {
    suspend fun fetch(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = url.trim()
            val parsedUrl = URL(normalizedUrl)
            require(parsedUrl.protocol == "https" || parsedUrl.protocol == "http") {
                "Subscription URL must use HTTPS or HTTP."
            }

            val connection = parsedUrl.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "text/plain, */*")
                connection.setRequestProperty("User-Agent", "Hysera/0.1")

                val responseCode = connection.responseCode
                require(responseCode in 200..299) {
                    "Subscription server returned HTTP $responseCode."
                }
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 20_000
    }
}
