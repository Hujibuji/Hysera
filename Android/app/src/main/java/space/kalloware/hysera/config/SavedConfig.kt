package space.kalloware.hysera.config

data class SavedConfig(
    val id: String,
    val name: String,
    val rawConfig: String,
    val preferredCore: CoreType,
    val createdAtMillis: Long,
)
