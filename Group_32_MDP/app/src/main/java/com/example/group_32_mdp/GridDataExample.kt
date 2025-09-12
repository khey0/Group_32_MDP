package com.example.group_32_mdp

/**
 * Example class demonstrating how to access the 2D grid data from other files
 */
class GridDataExample {
    
    /**
     * Example method showing how to access grid data
     */
    fun demonstrateGridAccess() {
        // Access the 2D grid array directly
        val gridArray = GridData.gridArray
        
        // Check if a specific position has an obstacle
        val hasObstacle = GridData.hasObstacleAt(5, 10)
        println("Position (5,10) has obstacle: $hasObstacle")
        
        // Get obstacle details at a specific position
        if (hasObstacle) {
            val obstacleId = GridData.getObstacleIdAt(5, 10)
            val direction = GridData.getObstacleDirectionAt(5, 10)
            println("Obstacle ID: $obstacleId, Direction: $direction")
        }
        
        // Get a specific grid cell
        val cell = GridData.getCell(3, 7)
        cell?.let {
            println("Cell (3,7): hasObstacle=${it.hasObstacle}, id=${it.obstacleId}, direction=${it.direction}")
        }
        
        // Get all obstacles in the grid
        val allObstacles = GridData.getAllObstacles()
        println("Total obstacles: ${allObstacles.size}")
        
        // Print grid state for debugging
        GridData.printGridState()
    }
    
    /**
     * Example method showing how to modify grid data
     */
    fun demonstrateGridModification() {
        // Add an obstacle at position (2, 3) facing North
        GridData.setObstacle(2, 3, 1, Direction.NORTH)
        
        // Update obstacle direction
        GridData.updateObstacleDirection(2, 3, Direction.EAST)
        
        // Move obstacle from (2, 3) to (4, 5)
        val moved = GridData.moveObstacle(2, 3, 4, 5)
        println("Obstacle moved successfully: $moved")
        
        // Remove obstacle at position (4, 5)
        GridData.removeObstacle(4, 5)
    }
    
    /**
     * Example method showing how to iterate through the entire grid
     */
    fun iterateThroughGrid() {
        println("=== Grid Iteration ===")
        for (y in 0..19) {
            for (x in 0..19) {
                val cell = GridData.getCell(x, y)
                if (cell?.hasObstacle == true) {
                    println("Position ($x,$y): Obstacle ${cell.obstacleId} facing ${cell.direction}")
                }
            }
        }
    }
    
    /**
     * Example method showing how to find obstacles by direction
     */
    fun findObstaclesByDirection(targetDirection: Direction): List<Pair<Int, Int>> {
        val obstacles = mutableListOf<Pair<Int, Int>>()
        
        for (y in 0..19) {
            for (x in 0..19) {
                val cell = GridData.getCell(x, y)
                if (cell?.hasObstacle == true && cell.direction == targetDirection) {
                    obstacles.add(Pair(x, y))
                }
            }
        }
        
        return obstacles
    }
    
    /**
     * Example method showing how to count obstacles
     */
    fun countObstacles(): Int {
        var count = 0
        for (y in 0..19) {
            for (x in 0..19) {
                if (GridData.hasObstacleAt(x, y)) {
                    count++
                }
            }
        }
        return count
    }
}

