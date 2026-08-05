package com.shottimer.app.drills

data class Drill(
    val id: String,
    val name: String,
    val summary: String,
    val instructions: String,
    val roundCount: Int
)

/**
 * Static reference data, not persisted - a run just stores the drill's [Drill.name] as a plain
 * string tag (see RunEntity.drillName), so this list can be edited freely later without needing
 * a migration.
 */
object DrillLibrary {
    val ALL: List<Drill> = listOf(
        Drill(
            id = "bill_drill",
            name = "Bill Drill",
            summary = "6 rounds, single target, 7 yards",
            instructions = "From 7 yards, draw and fire 6 rounds into a single target as fast " +
                "as you can while keeping all hits in the A-zone. Tests draw speed, recoil " +
                "management, and visual tracking.",
            roundCount = 6
        ),
        Drill(
            id = "el_presidente",
            name = "El Presidente",
            summary = "Turn, draw, 2+2+2 across 3 targets with a reload, 10 yards",
            instructions = "Start at 10 yards with your back to three targets. On the beep, " +
                "turn, draw, and fire 2 rounds into each of the three targets, perform a " +
                "mandatory reload, then fire 2 more rounds into each target. 12 rounds total.",
            roundCount = 12
        ),
        Drill(
            id = "mozambique",
            name = "Mozambique Drill (Failure to Stop)",
            summary = "2 to the chest, 1 to the head, 7-10 yards",
            instructions = "From 7 to 10 yards, draw and fire 2 rounds to the chest followed " +
                "by 1 precise round to the head. Simulates a failure-to-stop response.",
            roundCount = 3
        ),
        Drill(
            id = "controlled_pairs",
            name = "Controlled Pairs (Doubles)",
            summary = "Draw, fire a pair of shots on command",
            instructions = "Draw and fire a pair of shots on the beep. Focus on a consistent " +
                "split time between the two shots and a tight group, not just raw speed. " +
                "Repeat for reps.",
            roundCount = 2
        )
    )
}
