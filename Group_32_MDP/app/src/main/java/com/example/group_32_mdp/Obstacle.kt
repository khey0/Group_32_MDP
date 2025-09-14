package com.example.group_32_mdp

data class Obstacle(
    val id: Int,
    val x: Int,
    val y: Int,
    val direction: Direction = Direction.NORTH
)

enum class Direction {
    NORTH, SOUTH, EAST, WEST;
    companion object {
        fun fromLetter(letter: String): Direction {
            return when (letter.uppercase()) {
                "N" -> NORTH
                "S" -> SOUTH
                "E" -> EAST
                "W" -> WEST
                else -> throw IllegalArgumentException("Invalid direction letter: $letter")
            }
        }
    }
}

