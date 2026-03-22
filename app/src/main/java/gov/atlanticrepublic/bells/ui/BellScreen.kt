package gov.atlanticrepublic.bells.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gov.atlanticrepublic.bells.R
import gov.atlanticrepublic.bells.config.AppPreferences
import gov.atlanticrepublic.bells.config.MotdProvider
import gov.atlanticrepublic.bells.model.BellSchedule
import gov.atlanticrepublic.bells.model.WatchSystem
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun BellScreen(
    onNavigateToSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }

    val watchSystem by prefs.watchSystem.collectAsState(initial = WatchSystem.TRADITIONAL)
    val teamNames by prefs.teamNames.collectAsState(initial = WatchSystem.TRADITIONAL.defaultTeamNames)
    val anchorDate by prefs.anchorDate.collectAsState(initial = LocalDate.now())
    val anchorWatchIndex by prefs.anchorWatchIndex.collectAsState(initial = 0)
    val anchorTeamIndex by prefs.anchorTeamIndex.collectAsState(initial = 0)
    val motdList by prefs.motdList.collectAsState(initial = emptyList())

    var now by remember { mutableStateOf(LocalDateTime.now()) }

    // Update clock every second
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    val watchInfo = remember(now.minute, now.hour, watchSystem, teamNames, anchorDate, anchorWatchIndex, anchorTeamIndex) {
        BellSchedule.getWatchInfo(
            dateTime = now,
            watchSystem = watchSystem,
            teamNames = teamNames,
            anchorDate = anchorDate,
            anchorWatchIndex = anchorWatchIndex,
            anchorTeamIndex = anchorTeamIndex,
        )
    }

    val nextBellTime = remember(now.minute, now.hour) {
        BellSchedule.nextBellTime(now)
    }

    val nextBellCount = remember(nextBellTime, watchSystem) {
        BellSchedule.nextBellCount(now, watchSystem)
    }

    val motd = remember(motdList, now.toLocalDate()) {
        MotdProvider.getMotd(motdList, now.toLocalDate())
    }

    val zone = ZoneId.systemDefault()
    val offset = zone.rules.getOffset(now)
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    val nextBellFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    // MediaPlayer for test sound
    var testPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            testPlayer?.release()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top: Title and settings
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SHIPS BELL",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Button(
                onClick = onNavigateToSettings,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
            ) {
                Text("Settings")
            }

            Spacer(modifier = Modifier.weight(1f))

            // Middle: Clock and watch info
            Text(
                text = dateFormatter.format(now),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                text = "${timeFormatter.format(now)} UTC${offset}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = watchInfo.watchName,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = watchInfo.teamName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            val bellWord = if (nextBellCount == 1) "bell" else "bells"
            Text(
                text = "Next: $nextBellCount $bellWord at ${nextBellFormatter.format(nextBellTime)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    testPlayer?.release()
                    testPlayer = MediaPlayer.create(context, R.raw.bells_4).apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setOnCompletionListener { mp -> mp.release() }
                        start()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Text("Test Sound (4 Bells)")
            }

            Spacer(modifier = Modifier.weight(3f))

            // MOTD — positioned at roughly 3/4 down the screen
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "\u201C$motd\u201D",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
