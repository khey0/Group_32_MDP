package com.example.group_32_mdp

data class Obstacle(
    val id: Int,
    val x: Int,
    val y: Int,
    val direction: Direction = Direction.NORTH
)

enum class Direction {
    NORTH, SOUTH, EAST, WEST
}

