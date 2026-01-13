package net.kibotu.ro.client.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import net.kibotu.ro.client.model.Position
import net.kibotu.ro.client.util.ClientLogger
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Terrain system for Payon map according to GAME_SPEC.md section 4.
 * Features:
 * - Heightmap-based terrain generation
 * - Splatmap texturing (4 texture layers)
 * - Navigation grid generation
 * - Terrain collision
 */
data class TerrainData(
    val width: Int,
    val height: Int,
    val heightMap: FloatArray, // Height values (0-15 units)
    val splatMap: SplatMapData? = null // Texture blending data
) {
    /**
     * Get height at position
     */
    fun getHeight(x: Int, y: Int): Float {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0f
        val index = y * width + x
        return heightMap[index]
    }
    
    /**
     * Get height at world position (interpolated)
     */
    fun getHeightAt(worldX: Float, worldY: Float): Float {
        val x = worldX.toInt()
        val y = worldY.toInt()
        val fx = worldX - x
        val fy = worldY - y
        
        // Bilinear interpolation
        val h00 = getHeight(x, y)
        val h10 = getHeight(min(x + 1, width - 1), y)
        val h01 = getHeight(x, min(y + 1, height - 1))
        val h11 = getHeight(min(x + 1, width - 1), min(y + 1, height - 1))
        
        val h0 = h00 * (1 - fx) + h10 * fx
        val h1 = h01 * (1 - fx) + h11 * fx
        
        return h0 * (1 - fy) + h1 * fy
    }
}

/**
 * Splatmap data for texture blending (from GAME_SPEC.md section 4.3)
 * RGBA channels represent weights for 4 texture layers:
 * - R: Grass weight
 * - G: Dirt weight
 * - B: Stone weight
 * - A: Forest floor weight
 */
data class SplatMapData(
    val width: Int,
    val height: Int,
    val data: ByteArray // RGBA values
) {
    /**
     * Get texture weights at position
     */
    fun getWeights(x: Int, y: Int): TextureWeights {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return TextureWeights(1f, 0f, 0f, 0f) // Default to grass
        }
        
        val index = (y * width + x) * 4
        val r = (data[index].toInt() and 0xFF) / 255f
        val g = (data[index + 1].toInt() and 0xFF) / 255f
        val b = (data[index + 2].toInt() and 0xFF) / 255f
        val a = (data[index + 3].toInt() and 0xFF) / 255f
        
        return TextureWeights(r, g, b, a)
    }
}

data class TextureWeights(
    val grass: Float,
    val dirt: Float,
    val stone: Float,
    val forestFloor: Float
)

/**
 * Terrain generator for Payon map
 */
class TerrainGenerator {
    /**
     * Generate Payon terrain according to GAME_SPEC.md section 4
     * Map dimensions: 200 × 200 units
     * Height range: 0-15 units
     */
    fun generatePayonTerrain(): TerrainData {
        val width = 200
        val height = 200
        val heightMap = FloatArray(width * height)
        
        // Generate terrain features
        // 1. Central village area (flat, 0-2 units variance)
        val villageCenterX = width / 2
        val villageCenterY = height / 2
        val villageSize = 30
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Central village area
                val distToVillage = kotlin.math.sqrt(
                    ((x - villageCenterX) * (x - villageCenterX) + 
                     (y - villageCenterY) * (y - villageCenterY)).toDouble()
                ).toFloat()
                
                var h: Float = if (distToVillage < villageSize) {
                    // Flat village area with slight variance
                    Random.nextFloat() * 2f
                } else {
                    // Forest areas (elevated, 2-8 units)
                    val distToEdge = minOf(
                        x.toFloat(),
                        y.toFloat(),
                        (width - x).toFloat(),
                        (height - y).toFloat()
                    )
                    
                    if (distToEdge < 20) {
                        // Edge areas: higher elevation
                        8f + (Random.nextFloat() * 7f) // 8-15 units
                    } else {
                        // Forest floor: 2-8 units
                        2f + (Random.nextFloat() * 6f)
                    }
                }
                
                // Add some hills (2-3 gentle hills, 8-15 units)
                val hill1X = width * 0.3f
                val hill1Y = height * 0.3f
                val hill1Dist = kotlin.math.sqrt(
                    ((x - hill1X) * (x - hill1X) + (y - hill1Y) * (y - hill1Y)).toDouble()
                ).toFloat()
                if (hill1Dist < 15) {
                    h = max(h, 8f + (15 - hill1Dist) * 0.5f)
                }
                
                val hill2X = width * 0.7f
                val hill2Y = height * 0.7f
                val hill2Dist = kotlin.math.sqrt(
                    ((x - hill2X) * (x - hill2X) + (y - hill2Y) * (y - hill2Y)).toDouble()
                ).toFloat()
                if (hill2Dist < 15) {
                    h = max(h, 8f + (15 - hill2Dist) * 0.5f)
                }
                
                heightMap[y * width + x] = h.coerceIn(0f, 15f)
            }
        }
        
        return TerrainData(width, height, heightMap)
    }
    
    /**
     * Generate splatmap for Payon terrain
     */
    fun generatePayonSplatmap(terrainData: TerrainData): SplatMapData {
        val width = terrainData.width
        val height = terrainData.height
        val data = ByteArray(width * height * 4)
        
        val villageCenterX = width / 2
        val villageCenterY = height / 2
        val villageSize = 30
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = (y * width + x) * 4
                
                // Calculate distance to village center
                val distToVillage = kotlin.math.sqrt(
                    ((x - villageCenterX) * (x - villageCenterX) + 
                     (y - villageCenterY) * (y - villageCenterY)).toDouble()
                ).toFloat()
                
                if (distToVillage < villageSize) {
                    // Village area: Stone texture
                    data[index] = 0 // R: grass = 0
                    data[index + 1] = 0 // G: dirt = 0
                    data[index + 2] = 255.toByte() // B: stone = 255
                    data[index + 3] = 0 // A: forest floor = 0
                } else if (distToVillage < villageSize + 5) {
                    // Pathways: Dirt texture
                    data[index] = 0 // R: grass = 0
                    data[index + 1] = 255.toByte() // G: dirt = 255
                    data[index + 2] = 0 // B: stone = 0
                    data[index + 3] = 0 // A: forest floor = 0
                } else {
                    // Forest areas: Forest floor texture
                    data[index] = 0 // R: grass = 0
                    data[index + 1] = 0 // G: dirt = 0
                    data[index + 2] = 0 // B: stone = 0
                    data[index + 3] = 255.toByte() // A: forest floor = 255
                }
            }
        }
        
        return SplatMapData(width, height, data)
    }
    
    /**
     * Generate navigation grid from terrain data
     * According to GAME_SPEC.md section 4.5
     */
    fun generateNavigationGrid(terrainData: TerrainData): CollisionMap {
        val width = terrainData.width
        val height = terrainData.height
        val collisionMap = CollisionMap(width, height)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val h = terrainData.getHeight(x, y)
                
                // Check slope (max 45° angle)
                var maxSlope = 0f
                if (x > 0) {
                    val slope = kotlin.math.abs(h - terrainData.getHeight(x - 1, y))
                    maxSlope = max(maxSlope, slope)
                }
                if (x < width - 1) {
                    val slope = kotlin.math.abs(h - terrainData.getHeight(x + 1, y))
                    maxSlope = max(maxSlope, slope)
                }
                if (y > 0) {
                    val slope = kotlin.math.abs(h - terrainData.getHeight(x, y - 1))
                    maxSlope = max(maxSlope, slope)
                }
                if (y < height - 1) {
                    val slope = kotlin.math.abs(h - terrainData.getHeight(x, y + 1))
                    maxSlope = max(maxSlope, slope)
                }
                
                // Slope < 45° means walkable (tan(45°) = 1, so slope < 1 unit per cell)
                val walkable = maxSlope < 1f
                collisionMap.setWalkable(x, y, walkable)
            }
        }
        
        return collisionMap
    }
}

/**
 * Terrain renderer - renders 3D terrain mesh from heightmap
 */
class TerrainRenderer {
    /**
     * Render terrain as 3D quads with heightmap-based elevation
     * Each terrain cell is rendered as a quad with vertices at the correct height
     */
    fun renderTerrain(
        terrainData: TerrainData,
        @Suppress("UNUSED_PARAMETER") camera: com.badlogic.gdx.graphics.Camera,
        renderer: com.badlogic.gdx.graphics.glutils.ShapeRenderer
    ) {
        // Calculate visible terrain bounds based on camera frustum
        // For now, render all terrain (can be optimized with frustum culling later)
        val cellSize = 1f
        
        // Render terrain as boxes with height-based elevation
        renderer.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled)
        
        // Render each terrain cell as a box with height
        for (y in 0 until terrainData.height) {
            for (x in 0 until terrainData.width) {
                val h = terrainData.getHeight(x, y)
                
                // Calculate world position (center of cell)
                val worldX = x * cellSize + cellSize / 2f
                val worldZ = y * cellSize + cellSize / 2f
                
                // Determine color based on height and terrain type
                val colorIntensity = (h / 15f).coerceIn(0f, 1f)
                
                // Terrain color: darker green for lower areas, lighter brown for higher areas
                val color = when {
                    h < 2f -> {
                        // Village/stone area - grayish
                        Color(0.6f, 0.6f, 0.65f, 1f)
                    }
                    h < 5f -> {
                        // Forest floor - dark green/brown
                        Color(0.3f + colorIntensity * 0.2f, 0.5f + colorIntensity * 0.1f, 0.2f + colorIntensity * 0.1f, 1f)
                    }
                    else -> {
                        // Hills - brown/gray
                        Color(0.5f + colorIntensity * 0.2f, 0.4f + colorIntensity * 0.2f, 0.3f + colorIntensity * 0.2f, 1f)
                    }
                }
                
                renderer.color = color
                
                // Render box: position at center, height extends from 0 to h
                // Box dimensions: cellSize x h x cellSize
                renderer.box(
                    worldX - cellSize / 2f, 0f, worldZ - cellSize / 2f,
                    cellSize, h, cellSize
                )
            }
        }
        
        renderer.end()
        
        // Note: Grid lines removed - LibGDX ShapeRenderer doesn't support 3D lines directly
        // For future: Could use ModelBuilder/Mesh for more advanced terrain rendering
    }
}

