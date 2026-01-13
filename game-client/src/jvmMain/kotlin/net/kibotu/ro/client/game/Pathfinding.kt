package net.kibotu.ro.client.game

import net.kibotu.ro.client.model.Position
import net.kibotu.ro.client.util.ClientLogger
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A* pathfinding algorithm implementation according to GAME_SPEC.md section 2.2.
 * Features:
 * - A* pathfinding on navigation grid
 * - Grid cell size: 1x1 units
 * - Maximum path length: 100 cells
 * - Path recalculation support
 */
class Pathfinding(
    private val collisionMap: CollisionMap,
    private val mapWidth: Int,
    private val mapHeight: Int
) {
    private val maxPathLength: Int = 100 // from GAME_SPEC.md section 2.2
    
    /**
     * Find path from start to goal using A* algorithm
     * @return List of positions representing the path, or empty list if no path found
     */
    fun findPath(start: Position, goal: Position): List<Position> {
        if (!collisionMap.isValidPosition(start)) {
            ClientLogger.warn("Pathfinding: Start position is not valid: $start", "Pathfinding")
            return emptyList()
        }
        
        if (!collisionMap.isValidPosition(goal)) {
            ClientLogger.warn("Pathfinding: Goal position is not valid: $goal", "Pathfinding")
            return emptyList()
        }
        
        if (start == goal) {
            return listOf(goal)
        }
        
        // A* algorithm
        val openSet = PriorityQueue<Node> { a, b -> 
            (a.fCost - b.fCost).toInt()
        }
        val closedSet = mutableSetOf<Position>()
        val cameFrom = mutableMapOf<Position, Position>()
        
        val startNode = Node(start, 0f, heuristic(start, goal))
        openSet.add(startNode)
        
        val gScore = mutableMapOf<Position, Float>()
        gScore[start] = 0f
        
        val fScore = mutableMapOf<Position, Float>()
        fScore[start] = heuristic(start, goal)
        
        while (openSet.isNotEmpty()) {
            val current = openSet.poll()
            
            // Check path length limit
            if (closedSet.size >= maxPathLength) {
                ClientLogger.warn("Pathfinding: Path length exceeded maximum ($maxPathLength)", "Pathfinding")
                break
            }
            
            if (current.position == goal) {
                // Reconstruct path
                return reconstructPath(cameFrom, current.position)
            }
            
            closedSet.add(current.position)
            
            // Check neighbors
            val neighbors = getNeighbors(current.position)
            for (neighbor in neighbors) {
                if (neighbor in closedSet) continue
                
                val tentativeGScore = gScore[current.position]!! + 1f // Cost to move to neighbor
                
                if (!gScore.containsKey(neighbor) || tentativeGScore < gScore[neighbor]!!) {
                    cameFrom[neighbor] = current.position
                    gScore[neighbor] = tentativeGScore
                    val h = heuristic(neighbor, goal)
                    fScore[neighbor] = tentativeGScore + h
                    
                    // Check if neighbor is already in open set
                    val existingNode = openSet.find { it.position == neighbor }
                    if (existingNode == null) {
                        openSet.add(Node(neighbor, tentativeGScore, h))
                    }
                }
            }
        }
        
        // No path found
        ClientLogger.warn("Pathfinding: No path found from $start to $goal", "Pathfinding")
        return emptyList()
    }
    
    /**
     * Get valid neighbors of a position (8-directional movement)
     */
    private fun getNeighbors(position: Position): List<Position> {
        val neighbors = mutableListOf<Position>()
        
        // 8-directional movement
        val directions = listOf(
            Position(-1, -1), Position(0, -1), Position(1, -1),
            Position(-1, 0),                 Position(1, 0),
            Position(-1, 1),  Position(0, 1),  Position(1, 1)
        )
        
        for (dir in directions) {
            val neighbor = Position(position.x + dir.x, position.y + dir.y)
            if (collisionMap.isValidPosition(neighbor)) {
                neighbors.add(neighbor)
            }
        }
        
        return neighbors
    }
    
    /**
     * Heuristic function (Euclidean distance)
     */
    private fun heuristic(a: Position, b: Position): Float {
        val dx = (a.x - b.x).toFloat()
        val dy = (a.y - b.y).toFloat()
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Reconstruct path from cameFrom map
     */
    private fun reconstructPath(cameFrom: Map<Position, Position>, current: Position): List<Position> {
        val path = mutableListOf<Position>()
        var currentPos = current
        
        path.add(currentPos)
        
        while (cameFrom.containsKey(currentPos)) {
            currentPos = cameFrom[currentPos]!!
            path.add(0, currentPos) // Add to beginning
        }
        
        return path
    }
    
    /**
     * Check if a path needs recalculation (from GAME_SPEC.md section 2.2)
     * Recalculate if:
     * - Path length exceeded
     * - Collision detected
     * - Every 2 seconds if moving
     */
    fun shouldRecalculatePath(path: List<Position>, currentPosition: Position): Boolean {
        if (path.isEmpty()) return true
        
        // Check if current position is still on path
        val currentIndex = path.indexOf(currentPosition)
        if (currentIndex == -1) {
            return true // Off path
        }
        
        // Check if path is too long
        if (path.size > maxPathLength) {
            return true
        }
        
        // Check if next waypoint is still valid
        if (currentIndex < path.size - 1) {
            val nextWaypoint = path[currentIndex + 1]
            if (!collisionMap.isValidPosition(nextWaypoint)) {
                return true // Next waypoint is blocked
            }
        }
        
        return false
    }
    
    /**
     * Node for A* algorithm
     */
    private data class Node(
        val position: Position,
        val gCost: Float, // Cost from start
        val hCost: Float  // Heuristic cost to goal
    ) {
        val fCost: Float get() = gCost + hCost
    }
}

/**
 * Path following system according to GAME_SPEC.md section 2.2
 */
class PathFollower(
    private val pathfinding: Pathfinding
) {
    private var currentPath: List<Position> = emptyList()
    private var currentPathIndex: Int = 0
    private var lastRecalculationTime: Long = 0
    private val recalculationInterval: Long = 2000 // 2 seconds
    
    /**
     * Set a new path to follow
     */
    fun setPath(path: List<Position>) {
        currentPath = path
        currentPathIndex = 0
        lastRecalculationTime = System.currentTimeMillis()
    }
    
    /**
     * Get next waypoint in path
     * @return Next position to move toward, or null if path is complete
     */
    fun getNextWaypoint(currentPosition: Position): Position? {
        if (currentPath.isEmpty()) return null
        
        // Check if we need to recalculate
        val now = System.currentTimeMillis()
        val shouldRecalculate = pathfinding.shouldRecalculatePath(currentPath, currentPosition) ||
                (now - lastRecalculationTime > recalculationInterval)
        
        if (shouldRecalculate && currentPathIndex < currentPath.size - 1) {
            // Recalculate path from current position to goal
            val goal = currentPath.last()
            val newPath = pathfinding.findPath(currentPosition, goal)
            if (newPath.isNotEmpty()) {
                setPath(newPath)
            } else {
                // No path found, stop
                currentPath = emptyList()
                return null
            }
        }
        
        // Find current position in path
        val currentIndex = currentPath.indexOf(currentPosition)
        if (currentIndex != -1) {
            currentPathIndex = currentIndex
        }
        
        // Get next waypoint
        if (currentPathIndex < currentPath.size - 1) {
            val nextWaypoint = currentPath[currentPathIndex + 1]
            
            // Check if waypoint reached (threshold: 0.5 units from GAME_SPEC.md)
            val distance = distance(currentPosition, nextWaypoint)
            if (distance <= 0.5f) {
                currentPathIndex++
                // Get next waypoint after reaching current one
                if (currentPathIndex < currentPath.size - 1) {
                    return currentPath[currentPathIndex + 1]
                } else {
                    // Reached goal
                    currentPath = emptyList()
                    return null
                }
            }
            
            return nextWaypoint
        }
        
        // Path complete
        currentPath = emptyList()
        return null
    }
    
    /**
     * Check if currently following a path
     */
    fun isFollowingPath(): Boolean = currentPath.isNotEmpty()
    
    /**
     * Clear current path
     */
    fun clearPath() {
        currentPath = emptyList()
        currentPathIndex = 0
    }
    
    /**
     * Calculate distance between two positions
     */
    private fun distance(a: Position, b: Position): Float {
        val dx = (a.x - b.x).toFloat()
        val dy = (a.y - b.y).toFloat()
        return sqrt(dx * dx + dy * dy)
    }
}









