package gov.atlanticrepublic.bells.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Core bell scheduling engine. Maps any point in time to a WatchInfo
 * containing the watch name, bell count, and team assignment.
 *
 * Uses explicit lookup tables — no formula tricks, trivially verifiable
 * against the specification.
 */
object BellSchedule {

    // Each entry: (hour, minute) -> (watchName, bellCount)
    // bellCount is the number of bells struck at that time.
    // The audio resource to play = bells_${bellCount}.ogg, except:
    //   - New Year's midnight: bells_16.ogg
    //   - bellCount 0: no bell (between watches or not a bell time)

    // ─── TRADITIONAL ROYAL NAVY ─────────────────────────────────
    //
    // First Watch:     2000–0000  (8 bells)
    // Middle Watch:    0000–0400  (8 bells)
    // Morning Watch:   0400–0800  (8 bells)
    // Forenoon Watch:  0800–1200  (8 bells)
    // Afternoon Watch: 1200–1600  (8 bells)
    // First Dog Watch: 1600–1800  (4 bells)
    // Last Dog Watch:  1800–2000  (special: 1-3, then 8 at 2000)
    //
    // Dog watch pattern per user specification:
    // 1600=8 (end of Afternoon), 1630=1, 1700=2, 1730=3,
    // 1800=4 (end of First Dog), 1830=1, 1900=2, 1930=3,
    // 2000=8 (end of Last Dog / start of First Watch)

    private data class BellEntry(
        val watchName: String,
        val bellCount: Int,
        val watchIndex: Int, // position in the day's watch sequence, for team calc
    )

    // Traditional: 48 half-hour slots (00:00, 00:30, 01:00, ... 23:30)
    // Index = hour * 2 + (minute / 30)
    private val traditionalTable: Array<BellEntry> = arrayOf(
        // 0000 = 8 bells, Middle Watch (end boundary of First Watch, start of Middle)
        BellEntry("Middle Watch", 8, 1),       // 00:00
        BellEntry("Middle Watch", 1, 1),       // 00:30
        BellEntry("Middle Watch", 2, 1),       // 01:00
        BellEntry("Middle Watch", 3, 1),       // 01:30
        BellEntry("Middle Watch", 4, 1),       // 02:00
        BellEntry("Middle Watch", 5, 1),       // 02:30
        BellEntry("Middle Watch", 6, 1),       // 03:00
        BellEntry("Middle Watch", 7, 1),       // 03:30
        // 0400 = 8 bells, Morning Watch
        BellEntry("Morning Watch", 8, 2),      // 04:00
        BellEntry("Morning Watch", 1, 2),      // 04:30
        BellEntry("Morning Watch", 2, 2),      // 05:00
        BellEntry("Morning Watch", 3, 2),      // 05:30
        BellEntry("Morning Watch", 4, 2),      // 06:00
        BellEntry("Morning Watch", 5, 2),      // 06:30
        BellEntry("Morning Watch", 6, 2),      // 07:00
        BellEntry("Morning Watch", 7, 2),      // 07:30
        // 0800 = 8 bells, Forenoon Watch
        BellEntry("Forenoon Watch", 8, 3),     // 08:00
        BellEntry("Forenoon Watch", 1, 3),     // 08:30
        BellEntry("Forenoon Watch", 2, 3),     // 09:00
        BellEntry("Forenoon Watch", 3, 3),     // 09:30
        BellEntry("Forenoon Watch", 4, 3),     // 10:00
        BellEntry("Forenoon Watch", 5, 3),     // 10:30
        BellEntry("Forenoon Watch", 6, 3),     // 11:00
        BellEntry("Forenoon Watch", 7, 3),     // 11:30
        // 1200 = 8 bells, Afternoon Watch
        BellEntry("Afternoon Watch", 8, 4),    // 12:00
        BellEntry("Afternoon Watch", 1, 4),    // 12:30
        BellEntry("Afternoon Watch", 2, 4),    // 13:00
        BellEntry("Afternoon Watch", 3, 4),    // 13:30
        BellEntry("Afternoon Watch", 4, 4),    // 14:00
        BellEntry("Afternoon Watch", 5, 4),    // 14:30
        BellEntry("Afternoon Watch", 6, 4),    // 15:00
        BellEntry("Afternoon Watch", 7, 4),    // 15:30
        // 1600 = 8 bells (end of Afternoon), First Dog Watch begins
        BellEntry("First Dog Watch", 8, 5),    // 16:00
        BellEntry("First Dog Watch", 1, 5),    // 16:30
        BellEntry("First Dog Watch", 2, 5),    // 17:00
        BellEntry("First Dog Watch", 3, 5),    // 17:30
        // 1800 = 4 bells (end of First Dog), Last Dog Watch begins
        BellEntry("Last Dog Watch", 4, 6),     // 18:00
        BellEntry("Last Dog Watch", 1, 6),     // 18:30
        BellEntry("Last Dog Watch", 2, 6),     // 19:00
        BellEntry("Last Dog Watch", 3, 6),     // 19:30
        // 2000 = 8 bells (end of Last Dog), First Watch begins
        BellEntry("First Watch", 8, 0),        // 20:00
        BellEntry("First Watch", 1, 0),        // 20:30
        BellEntry("First Watch", 2, 0),        // 21:00
        BellEntry("First Watch", 3, 0),        // 21:30
        BellEntry("First Watch", 4, 0),        // 22:00
        BellEntry("First Watch", 5, 0),        // 22:30
        BellEntry("First Watch", 6, 0),        // 23:00
        BellEntry("First Watch", 7, 0),        // 23:30
    )

    // Traditional team rotation: 3-day cycle, 7 watches per day
    // Day 1: Team 1,2,3,1,2,3,1  (First,Middle,Morning,Forenoon,Afternoon,FirstDog,LastDog)
    // Day 2: Team 2,3,1,2,3,1,2
    // Day 3: Team 3,1,2,3,1,2,3
    //
    // The "day" starts at 2000 (First Watch). So for team calculation,
    // times 2000-2359 belong to the NEXT calendar day's watch rotation.
    // Actually: the watch day starts at 2000, meaning 2000 on Mar 21
    // starts the same watch-day as 0000-1959 on Mar 22.
    //
    // Watch index 0 = First Watch (starting team offset 0)
    // Watch index 1 = Middle Watch (starting team offset 1)
    // Watch index 2 = Morning Watch (starting team offset 2)
    // Watch index 3 = Forenoon Watch (starting team offset 0)
    // Watch index 4 = Afternoon Watch (starting team offset 1)
    // Watch index 5 = First Dog Watch (starting team offset 2)
    // Watch index 6 = Last Dog Watch (starting team offset 0)
    private val traditionalTeamOffsets = intArrayOf(0, 1, 2, 0, 1, 2, 0)

    // ─── ATLANTIC REPUBLIC NAVY ─────────────────────────────────
    //
    // Dawn Watch:      0100–0700  (12 bells: 1-8 standard, then 8,9,10,11,12)
    // Morning Watch:   0700–1300  (12 bells: same pattern)
    // Afternoon Watch: 1300–1900  (12 bells: same pattern)
    // Evening Watch:   1900–0100  (12 bells: same pattern)
    //
    // Last 2 hours of each watch: 8 bells at +4:00, then 9,10,11 at
    // half-hours, 12 at watch end.

    private val atlanticTable: Array<BellEntry> = arrayOf(
        // 00:00-00:30 = Evening Watch (last hour)
        BellEntry("Evening Watch", 10, 3),     // 00:00
        BellEntry("Evening Watch", 11, 3),     // 00:30
        // 01:00 = 12 bells (end of Evening), Dawn Watch begins
        BellEntry("Dawn Watch", 12, 0),        // 01:00
        BellEntry("Dawn Watch", 1, 0),         // 01:30
        BellEntry("Dawn Watch", 2, 0),         // 02:00
        BellEntry("Dawn Watch", 3, 0),         // 02:30
        BellEntry("Dawn Watch", 4, 0),         // 03:00
        BellEntry("Dawn Watch", 5, 0),         // 03:30
        BellEntry("Dawn Watch", 6, 0),         // 04:00
        BellEntry("Dawn Watch", 7, 0),         // 04:30
        BellEntry("Dawn Watch", 8, 0),         // 05:00
        BellEntry("Dawn Watch", 9, 0),         // 05:30
        BellEntry("Dawn Watch", 10, 0),        // 06:00
        BellEntry("Dawn Watch", 11, 0),        // 06:30
        // 07:00 = 12 bells (end of Dawn), Morning Watch begins
        BellEntry("Morning Watch", 12, 1),     // 07:00
        BellEntry("Morning Watch", 1, 1),      // 07:30
        BellEntry("Morning Watch", 2, 1),      // 08:00
        BellEntry("Morning Watch", 3, 1),      // 08:30
        BellEntry("Morning Watch", 4, 1),      // 09:00
        BellEntry("Morning Watch", 5, 1),      // 09:30
        BellEntry("Morning Watch", 6, 1),      // 10:00
        BellEntry("Morning Watch", 7, 1),      // 10:30
        BellEntry("Morning Watch", 8, 1),      // 11:00
        BellEntry("Morning Watch", 9, 1),      // 11:30
        BellEntry("Morning Watch", 10, 1),     // 12:00
        BellEntry("Morning Watch", 11, 1),     // 12:30
        // 13:00 = 12 bells (end of Morning), Afternoon Watch begins
        BellEntry("Afternoon Watch", 12, 2),   // 13:00
        BellEntry("Afternoon Watch", 1, 2),    // 13:30
        BellEntry("Afternoon Watch", 2, 2),    // 14:00
        BellEntry("Afternoon Watch", 3, 2),    // 14:30
        BellEntry("Afternoon Watch", 4, 2),    // 15:00
        BellEntry("Afternoon Watch", 5, 2),    // 15:30
        BellEntry("Afternoon Watch", 6, 2),    // 16:00
        BellEntry("Afternoon Watch", 7, 2),    // 16:30
        BellEntry("Afternoon Watch", 8, 2),    // 17:00
        BellEntry("Afternoon Watch", 9, 2),    // 17:30
        BellEntry("Afternoon Watch", 10, 2),   // 18:00
        BellEntry("Afternoon Watch", 11, 2),   // 18:30
        // 19:00 = 12 bells (end of Afternoon), Evening Watch begins
        BellEntry("Evening Watch", 12, 3),     // 19:00
        BellEntry("Evening Watch", 1, 3),      // 19:30
        BellEntry("Evening Watch", 2, 3),      // 20:00
        BellEntry("Evening Watch", 3, 3),      // 20:30
        BellEntry("Evening Watch", 4, 3),      // 21:00
        BellEntry("Evening Watch", 5, 3),      // 21:30
        BellEntry("Evening Watch", 6, 3),      // 22:00
        BellEntry("Evening Watch", 7, 3),      // 22:30
        BellEntry("Evening Watch", 8, 3),      // 23:00
        BellEntry("Evening Watch", 9, 3),      // 23:30
    )

    // Atlantic Republic team rotation: 3-day cycle, 4 watches per day
    // Day 1: Green, Gold, Blue, Green  (Dawn, Morning, Afternoon, Evening)
    // Day 2: Gold, Blue, Green, Gold
    // Day 3: Blue, Green, Gold, Blue
    //
    // The "day" starts at 0100 (Dawn Watch). So 0000-0059 belongs to
    // the previous calendar day's Evening Watch.
    private val atlanticTeamOffsets = intArrayOf(0, 1, 2, 0)

    /**
     * Get the watch info for a given time, including team assignment.
     *
     * @param dateTime the current date and time
     * @param watchSystem which watch system to use
     * @param teamNames list of team names (must have 3 entries)
     * @param anchorDate the reference date for team rotation
     * @param anchorWatchIndex which watch was active at the anchor point
     * @param anchorTeamIndex which team was on watch at the anchor point
     */
    fun getWatchInfo(
        dateTime: LocalDateTime,
        watchSystem: WatchSystem,
        teamNames: List<String>,
        anchorDate: LocalDate,
        anchorWatchIndex: Int,
        anchorTeamIndex: Int,
    ): WatchInfo {
        val table = when (watchSystem) {
            WatchSystem.TRADITIONAL -> traditionalTable
            WatchSystem.ATLANTIC_REPUBLIC -> atlanticTable
        }

        val slotIndex = dateTime.hour * 2 + dateTime.minute / 30
        val entry = table[slotIndex]

        val teamName = calculateTeam(
            dateTime = dateTime,
            watchSystem = watchSystem,
            watchIndex = entry.watchIndex,
            teamNames = teamNames,
            anchorDate = anchorDate,
            anchorWatchIndex = anchorWatchIndex,
            anchorTeamIndex = anchorTeamIndex,
        )

        return WatchInfo(
            watchName = entry.watchName,
            bellCount = entry.bellCount,
            teamName = teamName,
            watchSystem = watchSystem,
        )
    }

    /**
     * Get the audio resource name for the bell count at the given time.
     * Returns null if it's not a bell-striking time (between half-hours).
     * Handles the New Year's midnight override.
     */
    fun getAudioResourceName(dateTime: LocalDateTime, watchSystem: WatchSystem): String? {
        // Only ring within the first 2 minutes of a half-hour mark,
        // to allow for alarm delivery delay without being so loose
        // that a late app launch triggers an unexpected bell.
        val minute = dateTime.minute
        if (minute != 0 && minute != 30 && minute != 1 && minute != 31) return null

        // New Year's midnight override
        if (dateTime.monthValue == 1 && dateTime.dayOfMonth == 1
            && dateTime.hour == 0 && dateTime.minute <= 1) {
            return "bells_16"
        }

        val table = when (watchSystem) {
            WatchSystem.TRADITIONAL -> traditionalTable
            WatchSystem.ATLANTIC_REPUBLIC -> atlanticTable
        }

        val slotIndex = dateTime.hour * 2 + dateTime.minute / 30
        val bellCount = table[slotIndex].bellCount
        return "bells_$bellCount"
    }

    /**
     * Find the next bell time from the given dateTime.
     * Returns the next half-hour boundary.
     */
    fun nextBellTime(from: LocalDateTime): LocalDateTime {
        val minute = from.minute
        val nextMinute = if (minute < 30) 30 else 0
        val nextTime = if (nextMinute == 0) {
            from.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        } else {
            from.withMinute(30).withSecond(0).withNano(0)
        }
        // If we're exactly on a half-hour, go to the next one
        return if (!nextTime.isAfter(from)) {
            nextBellTime(nextTime.plusMinutes(1))
        } else {
            nextTime
        }
    }

    /**
     * Get the bell count for the next bell from the given time.
     */
    fun nextBellCount(from: LocalDateTime, watchSystem: WatchSystem): Int {
        val next = nextBellTime(from)
        val table = when (watchSystem) {
            WatchSystem.TRADITIONAL -> traditionalTable
            WatchSystem.ATLANTIC_REPUBLIC -> atlanticTable
        }
        val slotIndex = next.hour * 2 + next.minute / 30
        return table[slotIndex].bellCount
    }

    /**
     * Get the watch name for a specific time without team info.
     */
    fun getWatchName(dateTime: LocalDateTime, watchSystem: WatchSystem): String {
        val table = when (watchSystem) {
            WatchSystem.TRADITIONAL -> traditionalTable
            WatchSystem.ATLANTIC_REPUBLIC -> atlanticTable
        }
        val slotIndex = dateTime.hour * 2 + dateTime.minute / 30
        return table[slotIndex].watchName
    }

    private fun calculateTeam(
        dateTime: LocalDateTime,
        watchSystem: WatchSystem,
        watchIndex: Int,
        teamNames: List<String>,
        anchorDate: LocalDate,
        anchorWatchIndex: Int,
        anchorTeamIndex: Int,
    ): String {
        val teamCount = teamNames.size
        if (teamCount == 0) return ""

        // Determine the "watch day" date
        // Traditional: day starts at 2000, so 2000-2359 = next day's watches
        //   -> if time >= 20:00, watchDate = calendarDate + 1
        //   Actually: First Watch at 2000 on Mar 21 is the start of the
        //   watch-day that includes Middle Watch (0000-0400 Mar 22), etc.
        //   So the watch-day for 2000-2359 is the SAME as the next calendar day.
        //   We define watchDate as the calendar date of the FIRST watch.
        //   For Traditional: First Watch starts at 2000 the PREVIOUS evening.
        //   watchDate = calendarDate if hour < 20, calendarDate + 1 if hour >= 20
        //
        // Atlantic: day starts at 0100, so 0100-2359 = same day
        //   -> if time < 01:00, watchDate = calendarDate - 1 (still previous Evening Watch)

        val watchDate = when (watchSystem) {
            WatchSystem.TRADITIONAL -> {
                if (dateTime.hour >= 20) dateTime.toLocalDate().plusDays(1)
                else dateTime.toLocalDate()
            }
            WatchSystem.ATLANTIC_REPUBLIC -> {
                if (dateTime.hour < 1) dateTime.toLocalDate().minusDays(1)
                else dateTime.toLocalDate()
            }
        }

        // Same for anchor date: convert anchor to watch-date if needed
        // The anchor is given as the calendar date when the anchor watch was active
        val anchorWatchDate = anchorDate

        // Days elapsed between anchor watch-date and current watch-date
        val daysDiff = ChronoUnit.DAYS.between(anchorWatchDate, watchDate)

        // The team offsets within a day
        val teamOffsets = when (watchSystem) {
            WatchSystem.TRADITIONAL -> traditionalTeamOffsets
            WatchSystem.ATLANTIC_REPUBLIC -> atlanticTeamOffsets
        }

        // At the anchor point, the team at anchorWatchIndex was anchorTeamIndex.
        // The base team offset for that watch is teamOffsets[anchorWatchIndex].
        // So the day-rotation offset = anchorTeamIndex - teamOffsets[anchorWatchIndex]
        val dayRotationAtAnchor = ((anchorTeamIndex - teamOffsets[anchorWatchIndex]) % teamCount + teamCount) % teamCount

        // Current day's rotation = dayRotationAtAnchor + daysDiff
        val currentDayRotation = ((dayRotationAtAnchor + daysDiff) % teamCount + teamCount).toInt() % teamCount

        // Team index for the current watch
        val teamIndex = (teamOffsets[watchIndex] + currentDayRotation) % teamCount

        return teamNames[teamIndex]
    }

    /**
     * Get all watch names for a given watch system, in order.
     */
    fun watchNames(watchSystem: WatchSystem): List<String> = when (watchSystem) {
        WatchSystem.TRADITIONAL -> listOf(
            "First Watch", "Middle Watch", "Morning Watch",
            "Forenoon Watch", "Afternoon Watch",
            "First Dog Watch", "Last Dog Watch",
        )
        WatchSystem.ATLANTIC_REPUBLIC -> listOf(
            "Dawn Watch", "Morning Watch", "Afternoon Watch", "Evening Watch",
        )
    }
}
