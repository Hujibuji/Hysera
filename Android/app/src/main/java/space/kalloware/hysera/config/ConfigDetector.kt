package space.kalloware.hysera.config

import org.json.JSONObject

enum class ConfigFormat(val displayName: String) {
    SING_BOX_JSON("sing-box JSON"),
    XRAY_JSON("Xray JSON"),
    VLESS_URI("VLESS URI"),
    VMESS_URI("VMess URI"),
    TROJAN_URI("Trojan URI"),
    SHADOWSOCKS_URI("Shadowsocks URI"),
    HYSTERIA2_URI("Hysteria2 URI"),
    UNKNOWN("Unknown"),
}

data class ConfigDetection(
    val format: ConfigFormat,
    val suggestedCore: CoreType?,
    val explanation: String,
) {
    val isSupported: Boolean
        get() = format != ConfigFormat.UNKNOWN && suggestedCore != null
}

object ConfigDetector {
    fun detect(rawConfig: String): ConfigDetection {
        val value = rawConfig.trim()
        if (value.isBlank()) {
            return unsupported("Config is empty.")
        }

        if (value.startsWith("{")) {
            return detectJson(value)
        }

        return when (value.substringBefore("://").lowercase()) {
            "vless" -> uriDetection(ConfigFormat.VLESS_URI, value)
            "vmess" -> uriDetection(ConfigFormat.VMESS_URI, value)
            "trojan" -> uriDetection(ConfigFormat.TROJAN_URI, value)
            "ss" -> uriDetection(ConfigFormat.SHADOWSOCKS_URI, value)
            "hysteria2", "hy2" -> uriDetection(ConfigFormat.HYSTERIA2_URI, value)
            else -> unsupported("Unknown config format. Paste JSON or a supported URI.")
        }
    }

    fun resolveCore(config: SavedConfig): CoreType {
        if (config.preferredCore != CoreType.AUTO) {
            return config.preferredCore
        }

        return detect(config.rawConfig).suggestedCore
            ?: error("Cannot select a core for an unknown config format.")
    }

    private fun detectJson(rawConfig: String): ConfigDetection {
        val json = try {
            JSONObject(rawConfig)
        } catch (_: Exception) {
            return unsupported("JSON config is malformed.")
        }

        val outbounds = json.optJSONArray("outbounds")
        val inbounds = json.optJSONArray("inbounds")
        val hasXrayOutbound = (0 until (outbounds?.length() ?: 0)).any { index ->
            outbounds?.optJSONObject(index)?.has("protocol") == true
        }
        val hasSingBoxOutbound = (0 until (outbounds?.length() ?: 0)).any { index ->
            outbounds?.optJSONObject(index)?.has("type") == true
        }
        val hasXrayInbound = (0 until (inbounds?.length() ?: 0)).any { index ->
            inbounds?.optJSONObject(index)?.has("protocol") == true
        }
        val hasSingBoxInbound = (0 until (inbounds?.length() ?: 0)).any { index ->
            inbounds?.optJSONObject(index)?.has("type") == true
        }

        return when {
            json.has("routing") ||
                json.has("policy") ||
                json.has("stats") ||
                json.has("api") ||
                hasXrayOutbound ||
                hasXrayInbound -> ConfigDetection(
                format = ConfigFormat.XRAY_JSON,
                suggestedCore = CoreType.XRAY,
                explanation = "Xray-style JSON markers detected.",
            )

            json.has("route") ||
                json.has("experimental") ||
                hasSingBoxOutbound ||
                hasSingBoxInbound -> ConfigDetection(
                format = ConfigFormat.SING_BOX_JSON,
                suggestedCore = CoreType.SING_BOX,
                explanation = "sing-box-style JSON markers detected.",
            )

            else -> unsupported("JSON is valid but does not contain recognized sing-box or Xray markers.")
        }
    }

    private fun uriDetection(format: ConfigFormat, rawConfig: String): ConfigDetection {
        val needsXray = requiresXrayFallback(rawConfig)
        return ConfigDetection(
            format = format,
            suggestedCore = if (needsXray) CoreType.XRAY else CoreType.SING_BOX,
            explanation = if (needsXray) {
                "URI contains an Xray fallback transport marker."
            } else {
                "URI is supported by the primary sing-box adapter."
            },
        )
    }

    private fun requiresXrayFallback(rawConfig: String): Boolean {
        val normalized = rawConfig.lowercase()
        return XRAY_ONLY_URI_MARKERS.any(normalized::contains)
    }

    private fun unsupported(explanation: String) = ConfigDetection(
        format = ConfigFormat.UNKNOWN,
        suggestedCore = null,
        explanation = explanation,
    )

    private val XRAY_ONLY_URI_MARKERS = listOf(
        "type=xhttp",
        "type=splithttp",
        "type=kcp",
        "type=quic",
        "transport=xhttp",
        "transport=splithttp",
        "transport=kcp",
        "transport=quic",
    )
}
