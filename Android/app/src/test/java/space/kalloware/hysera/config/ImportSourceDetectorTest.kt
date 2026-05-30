package space.kalloware.hysera.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportSourceDetectorTest {
    @Test
    fun recognizesGithubRawUrlAsSubscription() {
        assertEquals(
            ImportSourceType.SUBSCRIPTION_URL,
            ImportSourceDetector.detect(
                "https://raw.githubusercontent.com/Hujibuji/whitelizt/refs/heads/main/Lichno",
            ),
        )
    }

    @Test
    fun recognizesSingleUriAndJsonConfigs() {
        assertEquals(
            ImportSourceType.SINGLE_CONFIG,
            ImportSourceDetector.detect("vless://id@example.com:443"),
        )
        assertEquals(
            ImportSourceType.SINGLE_CONFIG,
            ImportSourceDetector.detect("""{"outbounds":[{"type":"direct"}]}"""),
        )
    }

    @Test
    fun recognizesRawSubscriptionText() {
        assertEquals(
            ImportSourceType.RAW_SUBSCRIPTION,
            ImportSourceDetector.detect(
                """
                #profile-title: Test
                vless://id@example.com:443
                hy2://password@example.com:443
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun rejectsUnsupportedSingleLine() {
        assertEquals(ImportSourceType.UNKNOWN, ImportSourceDetector.detect("not-a-config"))
    }
}
