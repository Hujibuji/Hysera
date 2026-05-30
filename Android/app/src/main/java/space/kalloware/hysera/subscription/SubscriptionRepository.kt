package space.kalloware.hysera.subscription

import android.content.Context
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import space.kalloware.hysera.config.ConfigFormat
import space.kalloware.hysera.config.CoreType
import space.kalloware.hysera.logging.EventLogger

sealed interface SubscriptionOperationResult {
    data class Success(
        val profile: SubscriptionProfile,
        val parseResult: SubscriptionParseResult,
    ) : SubscriptionOperationResult

    data class Error(val message: String) : SubscriptionOperationResult
}

class SubscriptionRepository(
    context: Context,
    private val parser: SubscriptionParser = SubscriptionParser(),
    private val fetcher: SubscriptionFetcher = SubscriptionFetcher(),
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableProfiles = MutableStateFlow(loadProfiles())

    val profiles = mutableProfiles.asStateFlow()

    fun parse(rawText: String): SubscriptionParseResult = parser.parse(rawText)

    suspend fun fetchText(url: String): Result<String> = fetcher.fetch(url)

    fun importText(sourceUrl: String?, rawText: String): SubscriptionOperationResult {
        val parseResult = parser.parse(rawText)
        if (parseResult.validNodes.isEmpty()) {
            val message = "Subscription does not contain any valid VPN nodes."
            EventLogger.error(message)
            return SubscriptionOperationResult.Error(message)
        }

        val now = System.currentTimeMillis()
        val normalizedUrl = sourceUrl?.trim()?.takeIf(String::isNotBlank)
        val profile = SubscriptionProfile(
            id = UUID.randomUUID().toString(),
            name = resolveName(parseResult.metadata.profileTitle, normalizedUrl),
            sourceUrl = normalizedUrl,
            rawSubscriptionText = rawText,
            metadata = parseResult.metadata,
            nodes = parseResult.validNodes,
            lastUpdatedAtMillis = now,
            lastUpdateError = warningSummary(parseResult),
            createdAtMillis = now,
        )
        mutableProfiles.value = mutableProfiles.value + profile
        persist()
        logImport(profile, parseResult)
        scheduleAutomaticRefresh(profile)
        return SubscriptionOperationResult.Success(profile, parseResult)
    }

    suspend fun fetchAndImport(url: String): SubscriptionOperationResult {
        return fetcher.fetch(url).fold(
            onSuccess = { rawText -> importText(url, rawText) },
            onFailure = { exception ->
                val message = exception.message ?: "Could not download subscription."
                EventLogger.error("Subscription import failed: $message")
                SubscriptionOperationResult.Error(message)
            },
        )
    }

    suspend fun refresh(id: String): SubscriptionOperationResult {
        val profile = mutableProfiles.value.firstOrNull { it.id == id }
            ?: return SubscriptionOperationResult.Error("Subscription no longer exists.")
        val sourceUrl = profile.sourceUrl
            ?: return updateError(profile, "This subscription has no URL for manual refresh.")

        return fetcher.fetch(sourceUrl).fold(
            onSuccess = { rawText ->
                val parseResult = parser.parse(rawText)
                if (parseResult.validNodes.isEmpty()) {
                    updateError(profile, "Updated subscription does not contain valid VPN nodes.")
                } else {
                    val updatedProfile = profile.copy(
                        name = resolveName(parseResult.metadata.profileTitle, sourceUrl),
                        rawSubscriptionText = rawText,
                        metadata = parseResult.metadata,
                        nodes = parseResult.validNodes,
                        lastUpdatedAtMillis = System.currentTimeMillis(),
                        lastUpdateError = warningSummary(parseResult),
                    )
                    replace(updatedProfile)
                    logImport(updatedProfile, parseResult)
                    scheduleAutomaticRefresh(updatedProfile)
                    SubscriptionOperationResult.Success(updatedProfile, parseResult)
                }
            },
            onFailure = { exception ->
                updateError(profile, exception.message ?: "Could not download subscription.")
            },
        )
    }

    fun delete(id: String) {
        val profile = mutableProfiles.value.firstOrNull { it.id == id } ?: return
        mutableProfiles.value = mutableProfiles.value.filterNot { it.id == id }
        persist()
        EventLogger.info("Deleted subscription '${profile.name}'.")
    }

    private fun updateError(
        profile: SubscriptionProfile,
        message: String,
    ): SubscriptionOperationResult.Error {
        replace(profile.copy(lastUpdateError = message))
        EventLogger.error("Subscription '${profile.name}' refresh failed: $message")
        return SubscriptionOperationResult.Error(message)
    }

    private fun replace(profile: SubscriptionProfile) {
        mutableProfiles.value = mutableProfiles.value.map { existing ->
            if (existing.id == profile.id) profile else existing
        }
        persist()
    }

    private fun logImport(profile: SubscriptionProfile, parseResult: SubscriptionParseResult) {
        EventLogger.info(
            "Saved subscription '${profile.name}' with ${profile.nodes.size} valid node(s).",
        )
        if (parseResult.errors.isNotEmpty()) {
            EventLogger.warning(
                "Subscription '${profile.name}' completed with ${parseResult.errors.size} warning(s).",
            )
        }
    }

    private fun warningSummary(parseResult: SubscriptionParseResult): String? {
        return parseResult.errors.takeIf { it.isNotEmpty() }?.joinToString(
            separator = " ",
            limit = 3,
            truncated = "...",
        )
    }

    private fun resolveName(profileTitle: String?, sourceUrl: String?): String {
        if (!profileTitle.isNullOrBlank()) {
            return profileTitle
        }

        if (!sourceUrl.isNullOrBlank()) {
            val derivedName = runCatching {
                val uri = URI(sourceUrl)
                val pathName = uri.path.substringAfterLast('/').takeIf(String::isNotBlank)
                URLDecoder.decode(pathName ?: uri.host, StandardCharsets.UTF_8.name())
            }.getOrNull()
            if (!derivedName.isNullOrBlank()) {
                return derivedName
            }
        }

        return UNTITLED_SUBSCRIPTION
    }

    private fun scheduleAutomaticRefresh(profile: SubscriptionProfile) {
        // TODO: Schedule WorkManager periodic refresh with profile.updateIntervalHours.
        EventLogger.info(
            "Subscription '${profile.name}' refresh interval is ${profile.updateIntervalHours} hour(s).",
        )
    }

    private fun loadProfiles(): List<SubscriptionProfile> {
        val serialized = preferences.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val array = JSONArray(serialized)
            buildList {
                repeat(array.length()) { index ->
                    add(array.getJSONObject(index).toProfile())
                }
            }
        } catch (exception: Exception) {
            EventLogger.error("Stored subscriptions could not be read: ${exception.message}")
            emptyList()
        }
    }

    private fun persist() {
        val array = JSONArray()
        mutableProfiles.value.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun SubscriptionProfile.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .putNullable("sourceUrl", sourceUrl)
        .put("rawSubscriptionText", rawSubscriptionText)
        .put("metadata", metadata.toJson())
        .put("nodes", JSONArray().apply { nodes.forEach { put(it.toJson()) } })
        .putNullable("lastUpdatedAtMillis", lastUpdatedAtMillis)
        .putNullable("lastUpdateError", lastUpdateError)
        .put("createdAtMillis", createdAtMillis)

    private fun SubscriptionMetadata.toJson() = JSONObject()
        .putNullable("profileTitle", profileTitle)
        .putNullable("profileUpdateIntervalHours", profileUpdateIntervalHours)
        .putNullable("supportUrl", supportUrl)
        .putNullable("profileWebPageUrl", profileWebPageUrl)
        .putNullable("announce", announce)
        .putNullable("userInfo", userInfo?.toJson())

    private fun SubscriptionUserInfo.toJson() = JSONObject()
        .putNullable("upload", upload)
        .putNullable("download", download)
        .putNullable("total", total)
        .putNullable("expire", expire)

    private fun SubscriptionNode.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("rawConfig", rawConfig)
        .put("format", format.name)
        .putNullable("suggestedCore", suggestedCore?.name)
        .putNullable("error", error)

    private fun JSONObject.toProfile() = SubscriptionProfile(
        id = getString("id"),
        name = getString("name"),
        sourceUrl = nullableString("sourceUrl"),
        rawSubscriptionText = getString("rawSubscriptionText"),
        metadata = getJSONObject("metadata").toMetadata(),
        nodes = getJSONArray("nodes").toNodes(),
        lastUpdatedAtMillis = nullableLong("lastUpdatedAtMillis"),
        lastUpdateError = nullableString("lastUpdateError"),
        createdAtMillis = getLong("createdAtMillis"),
    )

    private fun JSONObject.toMetadata() = SubscriptionMetadata(
        profileTitle = nullableString("profileTitle"),
        profileUpdateIntervalHours = nullableInt("profileUpdateIntervalHours"),
        supportUrl = nullableString("supportUrl"),
        profileWebPageUrl = nullableString("profileWebPageUrl"),
        announce = nullableString("announce"),
        userInfo = nullableObject("userInfo")?.toUserInfo(),
    )

    private fun JSONObject.toUserInfo() = SubscriptionUserInfo(
        upload = nullableLong("upload"),
        download = nullableLong("download"),
        total = nullableLong("total"),
        expire = nullableLong("expire"),
    )

    private fun JSONArray.toNodes() = buildList {
        repeat(length()) { index ->
            val json = getJSONObject(index)
            add(
                SubscriptionNode(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    rawConfig = json.getString("rawConfig"),
                    format = ConfigFormat.valueOf(json.getString("format")),
                    suggestedCore = json.nullableString("suggestedCore")?.let(CoreType::valueOf),
                    error = json.nullableString("error"),
                ),
            )
        }
    }

    private fun JSONObject.putNullable(key: String, value: Any?) = put(key, value ?: JSONObject.NULL)

    private fun JSONObject.nullableString(key: String) =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.nullableLong(key: String) =
        if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.nullableInt(key: String) =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.nullableObject(key: String) =
        if (has(key) && !isNull(key)) getJSONObject(key) else null

    private companion object {
        const val PREFERENCES_NAME = "hysera_subscriptions"
        const val KEY_PROFILES = "subscription_profiles"
        const val UNTITLED_SUBSCRIPTION = "Untitled subscription"
    }
}
