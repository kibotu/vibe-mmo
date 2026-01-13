package net.kibotu.ro.client.game

import com.badlogic.gdx.math.Vector3
import kotlin.math.*

/**
 * Click-to-move pathfinding system (Diablo-style)
 * Simple A* pathfinding on terrain grid
 */
class ClickToMove(private val terrainData: TerrainData) {
    data class PathNode(val x: Int, val y: Int, val g: Float = 0f, val h: Float = 0f, val parent: PathNode? = null) {
        val f: Float get() = g + h
    }
    
    /**
     * Find path from start to target position
     * Returns list of waypoints (world positions)
     */
    fun findPath(startX: Float, startZ: Float, targetX: Float, targetZ: Float): List<Vector3> {
        val startNodeX = startX.toInt()
        val startNodeY = startZ.toInt()
        val targetNodeX = targetX.toInt()
        val targetNodeZ = targetZ.toInt()
        
        // Bounds check
        if (startNodeX < 0 || startNodeX >= terrainData.width ||
            startNodeY < 0 || startNodeY >= terrainData.height ||
            targetNodeX < 0 || targetNodeX >= terrainData.width ||
            targetNodeZ < 0 || targetNodeZ >= terrainData.height) {
            return emptyList()
        }
        
        // If start equals target, return empty list - caller should handle exact positioning
        // This allows GameScreen to add the exact click position as the final waypoint
        if (startNodeX == targetNodeX && startNodeY == targetNodeZ) {
            // Return empty list so GameScreen can add exact click position
            // If we return grid center here, it might prevent exact positioning
            return emptyList()
        }
        
        val openSet = mutableSetOf<PathNode>()
        val closedSet = mutableSetOf<Pair<Int, Int>>()
        
        val startNode = PathNode(startNodeX, startNodeY, 0f, heuristic(startNodeX, startNodeY, targetNodeX, targetNodeZ))
        openSet.add(startNode)
        
        while (openSet.isNotEmpty()) {
            // Get node with lowest f score
            val current = openSet.minByOrNull { it.f } ?: break
            
            if (current.x == targetNodeX && current.y == targetNodeZ) {
                // Reconstruct path
                val pathNodes = reconstructPath(current)
                // Skip the first node (start position) since we're already there
                val waypoints = if (pathNodes.size > 1) {
                    pathNodes.drop(1).map { node ->
                        val height = terrainData.getHeight(node.x, node.y)
                        // Center the waypoint in the grid cell
                        Vector3(node.x + 0.5f, height, node.y + 0.5f)
                    }
                } else {
                    // If path only has one node (start = target), create a single waypoint at target
                    listOf(Vector3(targetNodeX + 0.5f, terrainData.getHeight(targetNodeX, targetNodeZ), targetNodeZ + 0.5f))
                }
                return waypoints
            }
            
            openSet.remove(current)
            closedSet.add(Pair(current.x, current.y))
            
            // Check neighbors (8-directional)
            for (dx in -1..1) {
                for (dy in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    
                    val nx = current.x + dx
                    val ny = current.y + dy
                    
                    if (nx < 0 || nx >= terrainData.width || ny < 0 || ny >= terrainData.height) continue
                    if (closedSet.contains(Pair(nx, ny))) continue
                    
                    // Check if walkable (slope check)
                    val currentHeight = terrainData.getHeight(current.x, current.y)
                    val neighborHeight = terrainData.getHeight(nx, ny)
                    val heightDiff = abs(neighborHeight - currentHeight)
                    
                    // Max slope: 2.0 units per cell (very lenient for subtle terrain variations)
                    // Since terrain has max height of 2 units, this allows walking anywhere
                    if (heightDiff > 2.0f) continue
                    
                    val moveCost = if (abs(dx) + abs(dy) == 2) 1.414f else 1f // Diagonal costs more
                    val g = current.g + moveCost
                    val h = heuristic(nx, ny, targetNodeX, targetNodeZ)
                    
                    val neighbor = PathNode(nx, ny, g, h, current)
                    
                    // Check if already in open set with better path
                    val existing = openSet.find { it.x == nx && it.y == ny }
                    if (existing == null || neighbor.g < existing.g) {
                        openSet.remove(existing)
                        openSet.add(neighbor)
                    }
                }
            }
        }
        
        return emptyList() // No path found
    }
    
    private fun heuristic(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        val dx = (x2 - x1).toFloat()
        val dy = (y2 - y1).toFloat()
        return sqrt(dx * dx + dy * dy)
    }
    
    private fun reconstructPath(node: PathNode): List<PathNode> {
        val path = mutableListOf<PathNode>()
        var current: PathNode? = node
        while (current != null) {
            path.add(0, current)
            current = current.parent
        }
        return path
    }
}

