package space.kalloware.hysera.config

import java.net.URI

enum class ImportSourceType {
    EMPTY,
    SINGLE_CONFIG,
    SUBSCRIPTION_URL,
    RAW_SUBSCRIPTION,
    UNKNOWN,
}

object ImportSourceDetector {
    fun detect(rawInput: String): ImportSourceType {
        val input = rawInput.trim()
        if (input.isBlank()) {
            return ImportSourceType.EMPTY
        }

        if (isHttpUrl(input)) {
            return ImportSourceType.SUBSCRIPTION_URL
        }

        if (ConfigDetector.detect(input).isSupported) {
            return ImportSourceType.SINGLE_CONFIG
        }

        val meaningfulLines = input.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()
        val hasMetadata = meaningfulLines.any { it.startsWith("#") }
        val supportedNodeCount = meaningfulLines.count { line ->
            !line.startsWith("#") && ConfigDetector.detect(line).isSupported
        }
        if (hasMetadata || supportedNodeCount > 1) {
            return ImportSourceType.RAW_SUBSCRIPTION
        }

        return ImportSourceType.UNKNOWN
    }

    private fun isHttpUrl(input: String): Boolean {
        return runCatching {
            val uri = URI(input)
            (uri.scheme.equals("https", ignoreCase = true) ||
                uri.scheme.equals("http", ignoreCase = true)) &&
                !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}
