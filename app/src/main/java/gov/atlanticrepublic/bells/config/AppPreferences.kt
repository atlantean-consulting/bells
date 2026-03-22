package gov.atlanticrepublic.bells.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import gov.atlanticrepublic.bells.model.WatchSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ships_bell_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val WATCH_SYSTEM = stringPreferencesKey("watch_system")
        private val TEAM_NAMES = stringPreferencesKey("team_names")
        private val ANCHOR_DATE = stringPreferencesKey("anchor_date")
        private val ANCHOR_WATCH_INDEX = intPreferencesKey("anchor_watch_index")
        private val ANCHOR_TEAM_INDEX = intPreferencesKey("anchor_team_index")
        private val QUIET_START = stringPreferencesKey("quiet_start")
        private val QUIET_END = stringPreferencesKey("quiet_end")
        private val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        private val BELLS_ENABLED = booleanPreferencesKey("bells_enabled")
        private val MOTD_LIST = stringPreferencesKey("motd_list")

        private val DEFAULT_MOTD = listOf(
            "Long Live the Atlantic Republic",
            "Liberty, Equality, Solidarity",
            "Turn Up for the Green and Gold",
            "Fair Winds and Following Seas",
            "Stand the Watch",
            "Per Mare, Per Terram",
            "Ready Aye Ready",
            "Steady As She Goes",
        )
    }

    val watchSystem: Flow<WatchSystem> = context.dataStore.data.map { prefs ->
        val name = prefs[WATCH_SYSTEM] ?: WatchSystem.TRADITIONAL.name
        try { WatchSystem.valueOf(name) } catch (_: Exception) { WatchSystem.TRADITIONAL }
    }

    val teamNames: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[TEAM_NAMES]
        if (raw != null) {
            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            WatchSystem.TRADITIONAL.defaultTeamNames
        }
    }

    val anchorDate: Flow<LocalDate> = context.dataStore.data.map { prefs ->
        val raw = prefs[ANCHOR_DATE]
        if (raw != null) {
            try { LocalDate.parse(raw) } catch (_: Exception) { LocalDate.now() }
        } else {
            LocalDate.now()
        }
    }

    val anchorWatchIndex: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[ANCHOR_WATCH_INDEX] ?: 0
    }

    val anchorTeamIndex: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[ANCHOR_TEAM_INDEX] ?: 0
    }

    val quietStart: Flow<LocalTime> = context.dataStore.data.map { prefs ->
        val raw = prefs[QUIET_START] ?: "22:00"
        try { LocalTime.parse(raw) } catch (_: Exception) { LocalTime.of(22, 0) }
    }

    val quietEnd: Flow<LocalTime> = context.dataStore.data.map { prefs ->
        val raw = prefs[QUIET_END] ?: "06:00"
        try { LocalTime.parse(raw) } catch (_: Exception) { LocalTime.of(6, 0) }
    }

    val quietEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[QUIET_ENABLED] ?: true
    }

    val bellsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BELLS_ENABLED] ?: true
    }

    val motdList: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[MOTD_LIST]
        if (raw != null) {
            raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            DEFAULT_MOTD
        }
    }

    suspend fun setWatchSystem(system: WatchSystem) {
        context.dataStore.edit { it[WATCH_SYSTEM] = system.name }
    }

    suspend fun setTeamNames(names: List<String>) {
        context.dataStore.edit { it[TEAM_NAMES] = names.joinToString(",") }
    }

    suspend fun setAnchorDate(date: LocalDate) {
        context.dataStore.edit { it[ANCHOR_DATE] = date.toString() }
    }

    suspend fun setAnchorWatchIndex(index: Int) {
        context.dataStore.edit { it[ANCHOR_WATCH_INDEX] = index }
    }

    suspend fun setAnchorTeamIndex(index: Int) {
        context.dataStore.edit { it[ANCHOR_TEAM_INDEX] = index }
    }

    suspend fun setQuietStart(time: LocalTime) {
        context.dataStore.edit { it[QUIET_START] = time.format(DateTimeFormatter.ofPattern("HH:mm")) }
    }

    suspend fun setQuietEnd(time: LocalTime) {
        context.dataStore.edit { it[QUIET_END] = time.format(DateTimeFormatter.ofPattern("HH:mm")) }
    }

    suspend fun setQuietEnabled(enabled: Boolean) {
        context.dataStore.edit { it[QUIET_ENABLED] = enabled }
    }

    suspend fun setBellsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[BELLS_ENABLED] = enabled }
    }

    suspend fun setMotdList(messages: List<String>) {
        context.dataStore.edit { it[MOTD_LIST] = messages.joinToString("\n") }
    }

    fun isInQuietHours(time: LocalTime, quietStart: LocalTime, quietEnd: LocalTime): Boolean {
        return if (quietStart <= quietEnd) {
            // Same-day range (e.g., 09:00–17:00)
            time in quietStart..quietEnd
        } else {
            // Overnight range (e.g., 22:00–06:00)
            time >= quietStart || time <= quietEnd
        }
    }
}
