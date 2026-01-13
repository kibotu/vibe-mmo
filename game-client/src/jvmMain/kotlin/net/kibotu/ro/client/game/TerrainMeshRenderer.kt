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
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import net.kibotu.ro.client.util.ClientLogger

/**
 * Renders terrain as a 3D mesh from height map data
 */
class TerrainMeshRenderer {
    private var terrainModel: Model? = null
    private var terrainInstance: ModelInstance? = null
    
    /**
     * Build terrain mesh from height map
     */
    fun buildTerrainMesh(terrainData: TerrainData): ModelInstance? {
        try {
            val modelBuilder = ModelBuilder()
            modelBuilder.begin()
            
            val meshBuilder = modelBuilder.part(
                "terrain",
                GL20.GL_TRIANGLES,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.ColorPacked).toLong(),
                Material(
                    ColorAttribute.createDiffuse(Color(0.3f, 0.6f, 0.3f, 1f)),
                    ColorAttribute.createSpecular(Color(0.2f, 0.2f, 0.2f, 1f))
                )
            )
            
            val cellSize = 1f
            val width = terrainData.width
            val height = terrainData.height
            
            // Build mesh vertices and triangles
            for (y in 0 until height - 1) {
                for (x in 0 until width - 1) {
                    val x0 = x * cellSize
                    val z0 = y * cellSize
                    val x1 = (x + 1) * cellSize
                    val z1 = (y + 1) * cellSize
                    
                    val h00 = terrainData.getHeight(x, y)
                    val h10 = terrainData.getHeight(x + 1, y)
                    val h01 = terrainData.getHeight(x, y + 1)
                    val h11 = terrainData.getHeight(x + 1, y + 1)
                    
                    // Calculate color based on height
                    val color = getTerrainColor((h00 + h10 + h01 + h11) / 4f)
                    
                    // First triangle: (x0,z0) -> (x1,z0) -> (x0,z1)
                    addTriangle(
                        meshBuilder,
                        Vector3(x0, h00, z0),
                        Vector3(x1, h10, z0),
                        Vector3(x0, h01, z1),
                        color
                    )
                    
                    // Second triangle: (x1,z0) -> (x1,z1) -> (x0,z1)
                    addTriangle(
                        meshBuilder,
                        Vector3(x1, h10, z0),
                        Vector3(x1, h11, z1),
                        Vector3(x0, h01, z1),
                        color
                    )
                }
            }
            
            terrainModel = modelBuilder.end()
            terrainInstance = ModelInstance(terrainModel)
            
            ClientLogger.info("Terrain mesh built: ${width}x${height} cells", "TerrainMeshRenderer")
            return terrainInstance
        } catch (e: Exception) {
            ClientLogger.error("Failed to build terrain mesh: ${e.message}", "TerrainMeshRenderer")
            e.printStackTrace()
            return null
        }
    }
    
    private fun addTriangle(
        meshBuilder: MeshPartBuilder,
        v1: Vector3,
        v2: Vector3,
        v3: Vector3,
        color: Color
    ) {
        // Calculate normal
        val edge1 = Vector3(v2).sub(v1)
        val edge2 = Vector3(v3).sub(v1)
        val normal = Vector3(edge1).crs(edge2).nor()
        
        // Use triangle() with Vector3 and Color parameters
        meshBuilder.triangle(
            v1, color,
            v2, color,
            v3, color
        )
    }
    
    private fun getTerrainColor(height: Float): Color {
        // Subtle color variation based on height
        val intensity = (height / 2f).coerceIn(0f, 1f)
        return Color(
            0.2f + intensity * 0.2f,
            0.5f + intensity * 0.1f,
            0.2f + intensity * 0.1f,
            1f
        )
    }
    
    /**
     * Render terrain using ShapeRenderer (fallback if ModelBuilder fails)
     */
    fun renderTerrainSimple(terrainData: TerrainData, renderer: ShapeRenderer) {
        val cellSize = 1f
        
        renderer.begin(ShapeRenderer.ShapeType.Filled)
        
        for (y in 0 until terrainData.height) {
            for (x in 0 until terrainData.width) {
                val h = terrainData.getHeight(x, y)
                val worldX = x * cellSize
                val worldZ = y * cellSize
                
                val color = getTerrainColor(h)
                renderer.color = color
                
                // Render as a box
                renderer.box(
                    worldX,
                    0f,
                    worldZ,
                    cellSize,
                    h,
                    cellSize
                )
            }
        }
        
        renderer.end()
    }
    
    fun dispose() {
        terrainModel?.dispose()
        terrainModel = null
        terrainInstance = null
    }
}

