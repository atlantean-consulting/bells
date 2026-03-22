package gov.atlanticrepublic.bells.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import gov.atlanticrepublic.bells.config.AppPreferences
import gov.atlanticrepublic.bells.model.BellSchedule
import gov.atlanticrepublic.bells.model.WatchSystem
import gov.atlanticrepublic.bells.scheduling.BellAlarmManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()

    val watchSystem by prefs.watchSystem.collectAsState(initial = WatchSystem.TRADITIONAL)
    val teamNames by prefs.teamNames.collectAsState(initial = WatchSystem.TRADITIONAL.defaultTeamNames)
    val anchorDate by prefs.anchorDate.collectAsState(initial = LocalDate.now())
    val anchorWatchIndex by prefs.anchorWatchIndex.collectAsState(initial = 0)
    val anchorTeamIndex by prefs.anchorTeamIndex.collectAsState(initial = 0)
    val quietStart by prefs.quietStart.collectAsState(initial = LocalTime.of(22, 0))
    val quietEnd by prefs.quietEnd.collectAsState(initial = LocalTime.of(6, 0))
    val quietEnabled by prefs.quietEnabled.collectAsState(initial = true)
    val bellsEnabled by prefs.bellsEnabled.collectAsState(initial = true)
    val motdList by prefs.motdList.collectAsState(initial = emptyList())

    val onColor = MaterialTheme.colorScheme.onBackground
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = onColor,
        unfocusedTextColor = onColor,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = onColor.copy(alpha = 0.5f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = onColor.copy(alpha = 0.7f),
        cursorColor = MaterialTheme.colorScheme.primary,
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = onColor,
                    )
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = onColor,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Bells enabled ──
            SectionHeader("Master Control")
            SettingRow("Bells enabled") {
                Switch(
                    checked = bellsEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            prefs.setBellsEnabled(enabled)
                            if (enabled) {
                                BellAlarmManager.scheduleNext(context)
                            } else {
                                BellAlarmManager.cancel(context)
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    ),
                )
            }

            SettingsDivider()

            // ── Watch system ──
            SectionHeader("Watch System")
            WatchSystem.entries.forEach { system ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(
                        selected = watchSystem == system,
                        onClick = {
                            scope.launch {
                                prefs.setWatchSystem(system)
                                prefs.setTeamNames(system.defaultTeamNames)
                                prefs.setAnchorWatchIndex(0)
                                prefs.setAnchorTeamIndex(0)
                                BellAlarmManager.scheduleNext(context)
                            }
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = onColor.copy(alpha = 0.6f),
                        ),
                    )
                    Text(
                        text = system.displayName,
                        color = onColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            SettingsDivider()

            // ── Team names ──
            SectionHeader("Teams")
            val teamNamesStr = teamNames.joinToString(", ")
            OutlinedTextField(
                value = teamNamesStr,
                onValueChange = { value ->
                    val names = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (names.isNotEmpty()) {
                        scope.launch { prefs.setTeamNames(names) }
                    }
                },
                label = { Text("Team names (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                singleLine = true,
            )

            SettingsDivider()

            // ── Rotation anchor ──
            SectionHeader("Watch Rotation Anchor")
            Text(
                text = "Set the reference point: which team started which watch on which date.",
                style = MaterialTheme.typography.bodyMedium,
                color = onColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp),
            )

            OutlinedTextField(
                value = anchorDate.toString(),
                onValueChange = { value ->
                    try {
                        val date = LocalDate.parse(value)
                        scope.launch { prefs.setAnchorDate(date) }
                    } catch (_: Exception) { }
                },
                label = { Text("Anchor date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Watch selector
            val watches = BellSchedule.watchNames(watchSystem)
            Text(
                text = "Anchor watch:",
                style = MaterialTheme.typography.bodyLarge,
                color = onColor,
                modifier = Modifier.padding(top = 8.dp),
            )
            watches.forEachIndexed { index, name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    RadioButton(
                        selected = anchorWatchIndex == index,
                        onClick = { scope.launch { prefs.setAnchorWatchIndex(index) } },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = onColor.copy(alpha = 0.6f),
                        ),
                    )
                    Text(text = name, color = onColor, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Team selector for anchor
            Text(
                text = "Team on watch at anchor:",
                style = MaterialTheme.typography.bodyLarge,
                color = onColor,
                modifier = Modifier.padding(top = 8.dp),
            )
            teamNames.forEachIndexed { index, name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    RadioButton(
                        selected = anchorTeamIndex == index,
                        onClick = { scope.launch { prefs.setAnchorTeamIndex(index) } },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = onColor.copy(alpha = 0.6f),
                        ),
                    )
                    Text(text = name, color = onColor, style = MaterialTheme.typography.bodyMedium)
                }
            }

            SettingsDivider()

            // ── Quiet hours ──
            SectionHeader("Quiet Hours")
            SettingRow("Quiet hours enabled") {
                Switch(
                    checked = quietEnabled,
                    onCheckedChange = { scope.launch { prefs.setQuietEnabled(it) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    ),
                )
            }

            if (quietEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    OutlinedTextField(
                        value = quietStart.format(DateTimeFormatter.ofPattern("HH:mm")),
                        onValueChange = { value ->
                            try {
                                val time = LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"))
                                scope.launch { prefs.setQuietStart(time) }
                            } catch (_: Exception) { }
                        },
                        label = { Text("Start") },
                        modifier = Modifier.weight(1f),
                        colors = fieldColors,
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedTextField(
                        value = quietEnd.format(DateTimeFormatter.ofPattern("HH:mm")),
                        onValueChange = { value ->
                            try {
                                val time = LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"))
                                scope.launch { prefs.setQuietEnd(time) }
                            } catch (_: Exception) { }
                        },
                        label = { Text("End") },
                        modifier = Modifier.weight(1f),
                        colors = fieldColors,
                        singleLine = true,
                    )
                }
            }

            SettingsDivider()

            // ── Messages of the day ──
            SectionHeader("Messages of the Day")
            OutlinedTextField(
                value = motdList.joinToString("\n"),
                onValueChange = { value ->
                    val messages = value.split("\n").filter { it.isNotBlank() }
                    scope.launch { prefs.setMotdList(messages) }
                },
                label = { Text("One message per line") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = fieldColors,
                maxLines = 10,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SettingRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        content()
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
    Spacer(modifier = Modifier.height(8.dp))
}
