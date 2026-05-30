package space.kalloware.hysera.subscription

import space.kalloware.hysera.config.ConfigFormat
import space.kalloware.hysera.config.CoreType

data class SubscriptionMetadata(
    val profileTitle: String? = null,
    val profileUpdateIntervalHours: Int? = null,
    val supportUrl: String? = null,
    val profileWebPageUrl: String? = null,
    val announce: String? = null,
    val userInfo: SubscriptionUserInfo? = null,
)

data class SubscriptionUserInfo(
    val upload: Long? = null,
    val download: Long? = null,
    val total: Long? = null,
    val expire: Long? = null,
)

data class SubscriptionNode(
    val id: String,
    val name: String,
    val rawConfig: String,
    val format: ConfigFormat,
    val suggestedCore: CoreType?,
    val error: String? = null,
) {
    val isValid: Boolean
        get() = suggestedCore != null && error == null
}

data class SubscriptionProfile(
    val id: String,
    val name: String,
    val sourceUrl: String?,
    val rawSubscriptionText: String,
    val metadata: SubscriptionMetadata,
    val nodes: List<SubscriptionNode>,
    val lastUpdatedAtMillis: Long?,
    val lastUpdateError: String?,
    val createdAtMillis: Long,
) {
    val updateIntervalHours: Int
        get() = metadata.profileUpdateIntervalHours ?: DEFAULT_UPDATE_INTERVAL_HOURS

    private companion object {
        const val DEFAULT_UPDATE_INTERVAL_HOURS = 24
    }
}

data class SubscriptionParseResult(
    val metadata: SubscriptionMetadata,
    val nodes: List<SubscriptionNode>,
    val errors: List<String>,
) {
    val validNodes: List<SubscriptionNode>
        get() = nodes.filter(SubscriptionNode::isValid)
}
