package space.kalloware.hysera.subscription

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import space.kalloware.hysera.config.ConfigFormat
import space.kalloware.hysera.config.CoreType

class SubscriptionParserTest {
    private val parser = SubscriptionParser()

    @Test
    fun parsesMetadataAndMultipleNodes() {
        val result = parser.parse(
            """
            #profile-update-interval: 1
            #profile-title: Dedus VPN
            #subscription-userinfo: upload=0; download=12; total=0; expire=2235340800
            #support-url: https://t.me/example_support
            #profile-web-page-url: https://example.com/profile
            #unknown-header: ignored

            vless://00000000-0000-0000-0000-000000000000@example.com:443#Primary
            hy2://password@example.com:443#Fast
            trojan://password@example.com:443#Backup
            ss://YWVzLTEyOC1nY206cGFzc3dvcmQ@example.com:443#Legacy
            broken-node
            """.trimIndent(),
        )

        assertEquals("Dedus VPN", result.metadata.profileTitle)
        assertEquals(1, result.metadata.profileUpdateIntervalHours)
        assertEquals("https://t.me/example_support", result.metadata.supportUrl)
        assertEquals("https://example.com/profile", result.metadata.profileWebPageUrl)
        assertEquals(12, result.metadata.userInfo?.download)
        assertEquals(0, result.metadata.userInfo?.total)
        assertEquals(2235340800, result.metadata.userInfo?.expire)
        assertEquals(5, result.nodes.size)
        assertEquals(4, result.validNodes.size)
        assertEquals(1, result.errors.size)
        assertEquals("Primary", result.validNodes.first().name)
        assertEquals(ConfigFormat.HYSTERIA2_URI, result.validNodes[1].format)
        assertEquals(CoreType.SING_BOX, result.validNodes[1].suggestedCore)
    }

    @Test
    fun decodesBase64Announcement() {
        val announcement = "Hysera subscription update"
        val payload = Base64.getEncoder().encodeToString(
            announcement.toByteArray(StandardCharsets.UTF_8),
        )

        val result = parser.parse(
            """
            #announce: base64:$payload
            vmess://ZXhhbXBsZQ==
            """.trimIndent(),
        )

        assertEquals(announcement, result.metadata.announce)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun keepsFallbackForBrokenBase64Announcement() {
        val result = parser.parse(
            """
            #announce: base64:not-valid-***
            vless://00000000-0000-0000-0000-000000000000@example.com:443
            """.trimIndent(),
        )

        assertEquals("Failed to decode announcement", result.metadata.announce)
        assertTrue(result.errors.contains("Failed to decode announcement"))
    }

    @Test
    fun ignoresMissingAndNonNumericUserInfoFields() {
        val result = parser.parse(
            """
            #subscription-userinfo: upload=bad; download=7
            trojan://password@example.com:443
            """.trimIndent(),
        )

        assertNull(result.metadata.userInfo?.upload)
        assertEquals(7, result.metadata.userInfo?.download)
        assertNull(result.metadata.userInfo?.expire)
    }
}
