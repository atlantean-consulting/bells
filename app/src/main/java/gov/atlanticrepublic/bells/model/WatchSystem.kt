package gov.atlanticrepublic.bells.model

enum class WatchSystem {
    TRADITIONAL,
    ATLANTIC_REPUBLIC;

    val displayName: String
        get() = when (this) {
            TRADITIONAL -> "Traditional (Royal Navy)"
            ATLANTIC_REPUBLIC -> "Atlantic Republic Navy"
        }

    val defaultTeamNames: List<String>
        get() = when (this) {
            TRADITIONAL -> listOf("Team 1", "Team 2", "Team 3")
            ATLANTIC_REPUBLIC -> listOf("Green", "Gold", "Blue")
        }

    val teamCount: Int get() = 3

    val watchesPerDay: Int
        get() = when (this) {
            TRADITIONAL -> 7
            ATLANTIC_REPUBLIC -> 4
        }
}
