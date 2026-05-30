package space.kalloware.hysera.config

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import space.kalloware.hysera.logging.EventLogger

sealed interface SaveConfigResult {
    data class Success(val config: SavedConfig) : SaveConfigResult
    data class Error(val message: String) : SaveConfigResult
}

class ConfigRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableConfigs = MutableStateFlow(loadConfigs())

    val configs = mutableConfigs.asStateFlow()

    fun save(name: String, rawConfig: String, preferredCore: CoreType): SaveConfigResult {
        val normalizedConfig = rawConfig.trim()
        val detection = ConfigDetector.detect(normalizedConfig)
        if (!detection.isSupported) {
            EventLogger.error("Config rejected: ${detection.explanation}")
            return SaveConfigResult.Error(detection.explanation)
        }

        val config = SavedConfig(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Hysera config ${mutableConfigs.value.size + 1}" },
            rawConfig = normalizedConfig,
            preferredCore = preferredCore,
            createdAtMillis = System.currentTimeMillis(),
        )
        mutableConfigs.value = mutableConfigs.value + config
        persist()
        EventLogger.info("Saved config '${config.name}' as ${detection.format.displayName}.")
        return SaveConfigResult.Success(config)
    }

    fun findById(id: String): SavedConfig? = mutableConfigs.value.firstOrNull { it.id == id }

    fun delete(id: String) {
        val removedConfig = mutableConfigs.value.firstOrNull { it.id == id } ?: return
        mutableConfigs.value = mutableConfigs.value.filterNot { it.id == id }
        persist()
        EventLogger.info("Deleted config '${removedConfig.name}'.")
    }

    private fun loadConfigs(): List<SavedConfig> {
        val serialized = preferences.getString(KEY_CONFIGS, null) ?: return emptyList()
        return try {
            val array = JSONArray(serialized)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        SavedConfig(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            rawConfig = item.getString("rawConfig"),
                            preferredCore = runCatching {
                                CoreType.valueOf(item.getString("preferredCore"))
                            }.getOrDefault(CoreType.AUTO),
                            createdAtMillis = item.optLong("createdAtMillis"),
                        ),
                    )
                }
            }
        } catch (exception: Exception) {
            EventLogger.error("Stored configs could not be read: ${exception.message}")
            emptyList()
        }
    }

    private fun persist() {
        val array = JSONArray()
        mutableConfigs.value.forEach { config ->
            array.put(
                JSONObject()
                    .put("id", config.id)
                    .put("name", config.name)
                    .put("rawConfig", config.rawConfig)
                    .put("preferredCore", config.preferredCore.name)
                    .put("createdAtMillis", config.createdAtMillis),
            )
        }
        preferences.edit().putString(KEY_CONFIGS, array.toString()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "hysera_configs"
        const val KEY_CONFIGS = "saved_configs"
    }
}
