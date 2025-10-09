package com.example.group_32_mdp

/**
 * Canonical catalog of valid obstacle targets and their Target IDs (C.9 spec).
 */
object ObstacleCatalog {
	/** Label shown for the target on the obstacle (e.g. "1", "A", "↑"). */
	data class Target(val label: String, val id: Int)

	// Full list as per spec images provided
	val targets: List<Target> = listOf(
		// Numbers 1..9 → 11..19
		Target("1", 11),
		Target("2", 12),
		Target("3", 13),
		Target("4", 14),
		Target("5", 15),
		Target("6", 16),
		Target("7", 17),
		Target("8", 18),
		Target("9", 19),

		// Alphabets A..H → 20..27
		Target("A", 20),
		Target("B", 21),
		Target("C", 22),
		Target("D", 23),
		Target("E", 24),
		Target("F", 25),
		Target("G", 26),
		Target("H", 27),

		// Alphabets S..Z → 28..35
		Target("S", 28),
		Target("T", 29),
		Target("U", 30),
		Target("V", 31),
		Target("W", 32),
		Target("X", 33),
		Target("Y", 34),
		Target("Z", 35),

		// Arrows and Stop → 36..40
		Target("UP", 36),
		Target("DOWN", 37),
		Target("RIGHT", 38),
		Target("LEFT", 39),
		Target("STOP", 40)
	)

	// Special target ID for NULL targets (blue color)
	val NULL_TARGET_ID = -1

	// Convenience maps
	val idToLabel: Map<Int, String> = targets.associate { it.id to it.label }
	val labelToId: Map<String, Int> = targets.associate { it.label to it.id }
	
	// Check if a target ID represents a NULL target (should be blue)
	fun isNullTarget(targetId: Int?): Boolean = targetId == NULL_TARGET_ID
}


