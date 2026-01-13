package net.kibotu.ro.zone.service

import net.kibotu.ro.zone.model.CollisionMap
import net.kibotu.ro.zone.model.Position
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A* pathfinding service for server-side pathfinding
 * Based on GAME_SPEC.md section 2.2
 */
class PathfindingService(private val collisionMap: CollisionMap) {
    
    data class Node(
        val position: Position,
        val g: Int = 0, // Cost from start
        val h: Int = 0, // Heuristic cost to goal
        val parent: Node? = null
    ) {
        val f: Int get() = g + h
    }
    
    /**
     * Find path from start to goal using A* algorithm
     */
    fun findPath(start: Position, goal: Position): List<Position> {
        if (!isValidPosition(start)) {
            return emptyList()
        }
        if (!isValidPosition(goal)) {
            return emptyList()
        }
        
        // If start equals goal, return empty path (already there)
        if (start == goal) {
            return emptyList()
        }
        
        val openSet = mutableSetOf<Node>()
        val closedSet = mutableSetOf<Position>()
        
        val startNode = Node(start, g = 0, h = heuristic(start, goal))
        openSet.add(startNode)
        
        val maxPathLength = collisionMap.width * collisionMap.height
        var iterations = 0
        
        while (openSet.isNotEmpty() && iterations < maxPathLength) {
            iterations++
            
            // Get node with lowest f score
            val current = openSet.minByOrNull { it.f } ?: break
            
            // Check if we reached the goal
            if (current.position == goal) {
                return reconstructPath(current)
            }
            
            openSet.remove(current)
            closedSet.add(current.position)
            
            // Check neighbors (4-directional movement)
            val neighbors = getNeighbors(current.position)
            for (neighbor in neighbors) {
                if (neighbor in closedSet) continue
                if (!isValidPosition(neighbor)) continue
                
                val g = current.g + 1 // Cost to move to neighbor
                val h = heuristic(neighbor, goal)
                val neighborNode = Node(neighbor, g, h, current)
                
                val existingNode = openSet.find { it.position == neighbor }
                if (existingNode == null) {
                    openSet.add(neighborNode)
                } else if (g < existingNode.g) {
                    openSet.remove(existingNode)
                    openSet.add(neighborNode)
                }
            }
        }
        
        return emptyList()
    }
    
    /**
     * Get valid neighbors (4-directional)
     */
    private fun getNeighbors(position: Position): List<Position> {
        return listOf(
            Position(position.x + 1, position.y),     // Right
            Position(position.x - 1, position.y),     // Left
            Position(position.x, position.y + 1),     // Down
            Position(position.x, position.y - 1)      // Up
        )
    }
    
    /**
     * Heuristic function (Manhattan distance)
     */
    private fun heuristic(a: Position, b: Position): Int {
        return abs(a.x - b.x) + abs(a.y - b.y)
    }
    
    /**
     * Reconstruct path from goal node back to start
     */
    private fun reconstructPath(node: Node): List<Position> {
        val path = mutableListOf<Position>()
        var current: Node? = node
        
        while (current != null) {
            path.add(0, current.position) // Add to front
            current = current.parent
        }
        
        return path
    }
    
    /**
     * Check if position is valid (within bounds and walkable)
     */
    private fun isValidPosition(position: Position): Boolean {
        if (position.x < 0 || position.y < 0 || 
            position.x >= collisionMap.width || position.y >= collisionMap.height) {
            return false
        }
        return collisionMap.isWalkable(position.x, position.y)
    }
}









