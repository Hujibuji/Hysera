package space.kalloware.hysera.subscription

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.json.JSONObject
import space.kalloware.hysera.config.ConfigDetector
import space.kalloware.hysera.logging.EventLogger

class SubscriptionParser {
    fun parse(rawText: String): SubscriptionParseResult {
        val metadata = MetadataBuilder()
        val nodes = mutableListOf<SubscriptionNode>()
        val errors = mutableListOf<String>()
        var jsonBuffer: StringBuilder? = null
        var jsonBraceBalance = 0

        fun addNode(rawConfig: String) {
            val normalizedConfig = rawConfig.trim()
            if (normalizedConfig.isBlank()) {
                return
            }

            val detection = ConfigDetector.detect(normalizedConfig)
            val nodeNumber = nodes.size + 1
            val error = detection.explanation.takeUnless { detection.isSupported }
            if (error != null) {
                val message = "Subscription node $nodeNumber skipped: $error"
                errors += message
                EventLogger.warning(message)
            }
            nodes += SubscriptionNode(
                id = "node-$nodeNumber",
                name = extractNodeName(normalizedConfig, nodeNumber),
                rawConfig = normalizedConfig,
                format = detection.format,
                suggestedCore = detection.suggestedCore,
                error = error,
            )
        }

        rawText.lineSequence().forEach { rawLine ->
            val activeJsonBuffer = jsonBuffer
            if (activeJsonBuffer != null) {
                activeJsonBuffer.append('\n').append(rawLine)
                jsonBraceBalance += braceDelta(rawLine)
                if (jsonBraceBalance <= 0) {
                    addNode(activeJsonBuffer.toString())
                    jsonBuffer = null
                }
                return@forEach
            }

            val line = rawLine.trim()
            when {
                line.isBlank() -> Unit
                line.startsWith("#") -> parseMetadataLine(line, metadata, errors)
                line.startsWith("{") -> {
                    jsonBuffer = StringBuilder(rawLine)
                    jsonBraceBalance = braceDelta(rawLine)
                    if (jsonBraceBalance <= 0) {
                        addNode(jsonBuffer.toString())
                        jsonBuffer = null
                    }
                }

                else -> addNode(line)
            }
        }

        jsonBuffer?.let { addNode(it.toString()) }

        return SubscriptionParseResult(
            metadata = metadata.build(),
            nodes = nodes,
            errors = errors,
        )
    }

    private fun parseMetadataLine(
        line: String,
        metadata: MetadataBuilder,
        errors: MutableList<String>,
    ) {
        val header = line.removePrefix("#")
        val separatorIndex = header.indexOf(':')
        if (separatorIndex < 0) {
            return
        }

        val name = header.substring(0, separatorIndex).trim().lowercase()
        val value = header.substring(separatorIndex + 1).trim()
        when (name) {
            "profile-update-interval" -> {
                metadata.profileUpdateIntervalHours = value.toIntOrNull()?.takeIf { it > 0 }
                if (metadata.profileUpdateIntervalHours == null && value.isNotBlank()) {
                    recordWarning(errors, "Ignored invalid profile update interval '$value'.")
                }
            }

            "profile-title" -> metadata.profileTitle = value.takeIf(String::isNotBlank)
            "subscription-userinfo" -> metadata.userInfo = parseUserInfo(value)
            "support-url" -> metadata.supportUrl = value.takeIf(String::isNotBlank)
            "profile-web-page-url" -> metadata.profileWebPageUrl = value.takeIf(String::isNotBlank)
            "announce" -> metadata.announce = decodeAnnouncement(value, errors)
        }
    }

    private fun parseUserInfo(value: String): SubscriptionUserInfo {
        val fields = value
            .split(';')
            .mapNotNull { field ->
                val parts = field.split('=', limit = 2)
                if (parts.size != 2) {
                    null
                } else {
                    parts[0].trim().lowercase() to parts[1].trim().toLongOrNull()
                }
            }
            .toMap()

        return SubscriptionUserInfo(
            upload = fields["upload"],
            download = fields["download"],
            total = fields["total"],
            expire = fields["expire"],
        )
    }

    private fun decodeAnnouncement(value: String, errors: MutableList<String>): String? {
        if (!value.startsWith(BASE64_PREFIX, ignoreCase = true)) {
            return value.takeIf(String::isNotBlank)
        }

        return try {
            val payload = value.substring(BASE64_PREFIX.length)
            String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            val message = "Failed to decode announcement"
            errors += message
            EventLogger.error(message)
            message
        }
    }

    private fun extractNodeName(rawConfig: String, nodeNumber: Int): String {
        val uriFragment = rawConfig.substringAfterLast('#', missingDelimiterValue = "")
            .takeIf(String::isNotBlank)
        if (uriFragment != null) {
            return runCatching {
                URLDecoder.decode(uriFragment, StandardCharsets.UTF_8.name())
            }.getOrDefault(uriFragment)
        }

        if (rawConfig.startsWith("{")) {
            return runCatching {
                val json = JSONObject(rawConfig)
                json.optString("tag")
                    .ifBlank { json.optString("name") }
                    .ifBlank { null }
            }.getOrNull() ?: "JSON node $nodeNumber"
        }

        return "Node $nodeNumber"
    }

    private fun braceDelta(line: String): Int {
        var balance = 0
        var insideString = false
        var escaped = false
        line.forEach { character ->
            when {
                escaped -> escaped = false
                character == '\\' && insideString -> escaped = true
                character == '"' -> insideString = !insideString
                !insideString && character == '{' -> balance++
                !insideString && character == '}' -> balance--
            }
        }
        return balance
    }

    private fun recordWarning(errors: MutableList<String>, message: String) {
        errors += message
        EventLogger.warning(message)
    }

    private class MetadataBuilder {
        var profileTitle: String? = null
        var profileUpdateIntervalHours: Int? = null
        var supportUrl: String? = null
        var profileWebPageUrl: String? = null
        var announce: String? = null
        var userInfo: SubscriptionUserInfo? = null

        fun build() = SubscriptionMetadata(
            profileTitle = profileTitle,
            profileUpdateIntervalHours = profileUpdateIntervalHours,
            supportUrl = supportUrl,
            profileWebPageUrl = profileWebPageUrl,
            announce = announce,
            userInfo = userInfo,
        )
    }

    private companion object {
        const val BASE64_PREFIX = "base64:"
    }
}
