package com.example.group_32_mdp

import android.graphics.Bitmap


/**
 * Data class to represent a single grid cell
 */
data class GridCell(
    var hasObstacle: Boolean = false,
    var obstacleId: Int = -1,
    var direction: Direction = Direction.NORTH,
    var targetId: Int? = null
)

/**
 * Singleton class to manage 2D grid data
 * Accessible by other files and maintains grid state
 */
object GridData {
    
    // 2D array to store grid cell information
    private val grid = Array(20) { Array(20) { GridCell() } }

    private var car: Car? = null // car object, initially null

    // Public getter for the grid array
    val gridArray: Array<Array<GridCell>>
        get() = grid
    
    /**
     * Get grid cell at specific coordinates
     */
    fun getCell(x: Int, y: Int): GridCell? {
        return if (isValidCoordinate(x, y)) {
            grid[y][x]
        } else {
            null
        }
    }
    // Getter for car
    fun getCar(): Car? = car

    // Setter for car
    fun setCar(car: Car) {
        this.car = car
    }
    // helper function to check if a certain area has obstacles
    fun isAreaClear(xRange: IntRange, yRange: IntRange): Boolean {
        return xRange.all { checkX ->
            yRange.all { checkY ->
                // Flip Y to match grid array indexing (top-left origin)
                val flippedY = 19 - checkY
                val cell = getCell(checkX, flippedY)
                cell != null && !cell.hasObstacle
            }
        }
    }



    /**
     * Set obstacle at specific coordinates
     */
    fun setObstacle(x: Int, y: Int, obstacleId: Int, direction: Direction = Direction.NORTH, targetId: Int? = null) {
        if (isValidCoordinate(x, y)) {
            grid[y][x] = GridCell(true, obstacleId, direction, targetId)
        }
    }
    
    /**
     * Remove obstacle at specific coordinates
     */
    fun removeObstacle(x: Int, y: Int) {
        if (isValidCoordinate(x, y)) {
            grid[y][x] = GridCell()
        }
    }
    
    /**
     * Update obstacle direction at specific coordinates
     */
    fun updateObstacleDirection(x: Int, y: Int, direction: Direction) {
        if (isValidCoordinate(x, y) && grid[y][x].hasObstacle) {
            grid[y][x] = grid[y][x].copy(direction = direction)
        }
    }
    
    /**
     * Move obstacle from one position to another
     */
    fun moveObstacle(fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        if (!isValidCoordinate(fromX, fromY) || !isValidCoordinate(toX, toY)) {
            return false
        }
        
        val fromCell = grid[fromY][fromX]
        if (!fromCell.hasObstacle) {
            return false
        }
        
        // Check if destination is empty
        if (grid[toY][toX].hasObstacle) {
            return false
        }
        
        // Move obstacle
        grid[toY][toX] = fromCell
        grid[fromY][fromX] = GridCell()
        
        return true
    }
    
    /**
     * Update obstacle position (for coordinate editing)
     */
    fun updateObstaclePosition(oldX: Int, oldY: Int, newX: Int, newY: Int): Boolean {
        if (!isValidCoordinate(oldX, oldY) || !isValidCoordinate(newX, newY)) {
            return false
        }
        
        val oldCell = grid[oldY][oldX]
        if (!oldCell.hasObstacle) {
            return false
        }
        
        // Check if new position is empty
        if (grid[newY][newX].hasObstacle) {
            return false
        }
        
        // Move obstacle to new position
        grid[newY][newX] = oldCell
        grid[oldY][oldX] = GridCell()
        
        return true
    }
    
    /**
     * Check if coordinate is valid (within grid bounds)
     */
    private fun isValidCoordinate(x: Int, y: Int): Boolean {
        return x in 0..19 && y in 0..19
    }
    
    /**
     * Clear all obstacles from the grid
     */
    fun clearAllObstacles() {
        for (y in 0..19) {
            for (x in 0..19) {
                grid[y][x] = GridCell()
            }
        }
    }
    
    /**
     * Get all obstacles in the grid
     */
    fun getAllObstacles(): List<Obstacle> {
        val obstacles = mutableListOf<Obstacle>()
        for (y in 0..19) {
            for (x in 0..19) {
                val cell = grid[y][x]
                if (cell.hasObstacle) {
                    obstacles.add(Obstacle(cell.obstacleId, x, y, cell.direction))
                }
            }
        }
        return obstacles
    }

    /**
     * Find coordinates of an obstacle by id.
     */
    fun findObstacleCoordinates(obstacleId: Int): Pair<Int, Int>? {
        for (y in 0..19) {
            for (x in 0..19) {
                val cell = grid[y][x]
                if (cell.hasObstacle && cell.obstacleId == obstacleId) return Pair(x, y)
            }
        }
        return null
    }

    /**
     * Update or move an obstacle by id: if coordinates change and destination empty, move it.
     * Otherwise update direction on its current cell.
     */
    fun upsertObstacleById(obstacleId: Int, newX: Int, newY: Int, direction: Direction) {
        val current = findObstacleCoordinates(obstacleId)
        if (current == null) {
            // Not found; create if empty and valid
            if (isValidCoordinate(newX, newY) && !grid[newY][newX].hasObstacle) {
                setObstacle(newX, newY, obstacleId, direction)
            }
            return
        }

        val (oldX, oldY) = current
        if (oldX == newX && oldY == newY) {
            updateObstacleDirection(oldX, oldY, direction)
            return
        }

        if (!isValidCoordinate(newX, newY)) {
            // Invalid target; keep at old and just update direction
            updateObstacleDirection(oldX, oldY, direction)
            return
        }

        if (grid[newY][newX].hasObstacle) {
            // Occupied; keep at old and just update direction
            updateObstacleDirection(oldX, oldY, direction)
            return
        }

        // Move and set direction
        val existing = grid[oldY][oldX]
        grid[newY][newX] = existing.copy(direction = direction)
        grid[oldY][oldX] = GridCell()
    }

    /**
     * Check if a position has an obstacle
     */
    fun hasObstacleAt(x: Int, y: Int): Boolean {
        return if (isValidCoordinate(x, y)) {
            grid[y][x].hasObstacle
        } else {
            false
        }
    }
    
    /**
     * Get obstacle ID at specific coordinates
     */
    fun getObstacleIdAt(x: Int, y: Int): Int {
        return if (isValidCoordinate(x, y) && grid[y][x].hasObstacle) {
            grid[y][x].obstacleId
        } else {
            -1
        }
    }
    
    /**
     * Get obstacle direction at specific coordinates
     */
    fun getObstacleDirectionAt(x: Int, y: Int): Direction? {
        return if (isValidCoordinate(x, y) && grid[y][x].hasObstacle) {
            grid[y][x].direction
        } else {
            null
        }
    }
    
    /**
     * Set target ID for an obstacle by obstacle ID
     */
    fun setTargetIdForObstacle(obstacleId: Int, targetId: Int) {
        for (y in 0..19) {
            for (x in 0..19) {
                val cell = grid[y][x]
                if (cell.hasObstacle && cell.obstacleId == obstacleId) {
                    grid[y][x] = cell.copy(targetId = targetId)
                    return
                }
            }
        }
    }
    
    /**
     * Get target ID for an obstacle at specific coordinates
     */
    fun getTargetIdAt(x: Int, y: Int): Int? {
        return if (isValidCoordinate(x, y) && grid[y][x].hasObstacle) {
            grid[y][x].targetId
        } else {
            null
        }
    }

    /**
     * Print grid state for debugging
     */
    fun printGridState() {
        println("=== Grid State ===")
        for (y in 0..19) {
            for (x in 0..19) {
                val cell = grid[y][x]
                if (cell.hasObstacle) {
                    print("${cell.obstacleId}${cell.direction.name[0]} ")
                } else {
                    print("-- ")
                }
            }
            println()
        }
        println("==================")
    }
}
