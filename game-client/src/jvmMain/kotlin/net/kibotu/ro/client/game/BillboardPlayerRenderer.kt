package net.kibotu.ro.client.game

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.kibotu.ro.client.util.ClientLogger

/**
 * Renders player as a billboard (always faces camera) in 3D space
 */
class BillboardPlayerRenderer {
    private var playerModel: Model? = null
    private var playerInstance: ModelInstance? = null
    private val playerScale = 1.5f // Player height in world units
    
    /**
     * Check if a number is a power of 2
     */
    private fun isPowerOfTwo(n: Int): Boolean {
        return n > 0 && (n and (n - 1)) == 0
    }
    
    /**
     * Create billboard model from texture
     */
    fun createBillboardModel(textureRegion: TextureRegion?): ModelInstance? {
        if (textureRegion == null) {
            ClientLogger.warn("No texture region provided for billboard", "BillboardPlayerRenderer")
            return null
        }
        
        try {
            // Extract texture from TextureRegion
            val texture = textureRegion.texture
            if (texture == null) {
                ClientLogger.error("TextureRegion has no texture", "BillboardPlayerRenderer")
                return null
            }
            
            val modelBuilder = ModelBuilder()
            modelBuilder.begin()
            
            // Create material with texture attribute for billboard rendering
            // Add both Diffuse and Ambient texture attributes for unlit rendering
            // Also add a white diffuse color to ensure full brightness
            val material = Material(
                TextureAttribute.createDiffuse(texture),
                ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.WHITE),
                ColorAttribute.createAmbient(com.badlogic.gdx.graphics.Color.WHITE)
            )
            
            // Add TextureCoordinates to vertex attributes for UV mapping
            val meshBuilder = modelBuilder.part(
                "billboard",
                GL20.GL_TRIANGLES,
                (VertexAttributes.Usage.Position or 
                 VertexAttributes.Usage.Normal or 
                 VertexAttributes.Usage.TextureCoordinates).toLong(),
                material
            )
            
            // Calculate billboard dimensions based on sprite aspect ratio
            val spriteWidth = textureRegion.regionWidth.toFloat()
            val spriteHeight = textureRegion.regionHeight.toFloat()
            val aspectRatio = spriteWidth / spriteHeight
            
            // Scale height to playerScale, width maintains aspect ratio
            val height = 2f * playerScale // 2 units tall
            val width = height * aspectRatio
            
            // Create a quad facing forward (billboard will be rotated to face camera)
            val halfWidth = width / 2f
            val halfHeight = height / 2f
            
            // Get UV coordinates from TextureRegion
            val u = textureRegion.u
            val v = textureRegion.v
            val u2 = textureRegion.u2
            val v2 = textureRegion.v2
            
            // Set UV range for the quad (this sets the UV coordinates for subsequent rect() call)
            // Note: LibGDX TextureRegion uses bottom-left origin, but UV coordinates might need adjustment
            meshBuilder.setUVRange(u, v2, u2, v)
            
            // Create rectangle facing +Z direction (towards camera when camera is at positive Z)
            // rect() will use the UV range set above
            // The quad is created in XY plane at Z=0, facing +Z
            val normal = Vector3(0f, 0f, 1f)
            meshBuilder.rect(
                -halfWidth, -halfHeight, 0f,  // Bottom-left
                halfWidth, -halfHeight, 0f,    // Bottom-right
                halfWidth, halfHeight, 0f,     // Top-right
                -halfWidth, halfHeight, 0f,    // Top-left
                normal.x, normal.y, normal.z   // Normal pointing +Z (towards camera)
            )
            
            playerModel = modelBuilder.end()
            playerInstance = ModelInstance(playerModel)
            
            // Log detailed information about the billboard
            ClientLogger.info("Billboard model created successfully", "BillboardPlayerRenderer")
            ClientLogger.info("  World dimensions: ${width}x${height} units (aspect ratio: $aspectRatio)", "BillboardPlayerRenderer")
            ClientLogger.info("  Sprite size: ${spriteWidth}x${spriteHeight} pixels", "BillboardPlayerRenderer")
            ClientLogger.info("  Texture size: ${texture.width}x${texture.height} pixels (power of 2: ${isPowerOfTwo(texture.width)}x${isPowerOfTwo(texture.height)})", "BillboardPlayerRenderer")
            ClientLogger.info("  UV coordinates: ($u,$v) to ($u2,$v2)", "BillboardPlayerRenderer")
            ClientLogger.info("  Material has texture: ${material.has(TextureAttribute.Diffuse)}", "BillboardPlayerRenderer")
            ClientLogger.info("  Material has diffuse color: ${material.has(ColorAttribute.Diffuse)}", "BillboardPlayerRenderer")
            
            // Verify texture is loaded correctly
            if (texture.width <= 0 || texture.height <= 0) {
                ClientLogger.error("Invalid texture dimensions: ${texture.width}x${texture.height}", "BillboardPlayerRenderer")
            }
            
            return playerInstance
        } catch (e: Exception) {
            ClientLogger.error("Failed to create billboard model: ${e.message}", "BillboardPlayerRenderer")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Update billboard position and rotation to face camera
     */
    fun updateBillboard(
        instance: ModelInstance,
        position: Vector3,
        cameraPosition: Vector3,
        cameraUp: Vector3
    ) {
        // Reset transform to identity
        instance.transform.idt()
        
        // Calculate direction from billboard to camera (billboard faces camera)
        val direction = Vector3(cameraPosition).sub(position)
        val distance = direction.len()
        
        // Avoid division by zero
        if (distance < 0.001f) {
            // If camera is too close, just set position without rotation
            instance.transform.setToTranslation(position)
            return
        }
        
        direction.nor()
        
        // Calculate right vector: cross product of direction and camera up
        // This gives us the right vector perpendicular to both
        val right = Vector3().set(direction).crs(cameraUp)
        if (right.len2() < 0.001f) {
            // If camera up and direction are parallel, use a default right vector
            // Try using world up (Y-axis) instead
            val worldUp = Vector3(0f, 1f, 0f)
            right.set(direction).crs(worldUp)
            if (right.len2() < 0.001f) {
                // Still parallel, use X-axis as right
                right.set(1f, 0f, 0f)
            }
        }
        right.nor()
        
        // Recalculate up vector to ensure orthogonality: up = right × direction
        val up = Vector3().set(right).crs(direction)
        up.nor()
        
        // Build rotation matrix (billboard's local -Z should point towards camera)
        // The billboard quad is created facing +Z, so we need to rotate it to face camera
        val rotation = Matrix4()
        // Column-major order for LibGDX Matrix4
        // First column: right vector (X-axis)
        rotation.`val`[Matrix4.M00] = right.x
        rotation.`val`[Matrix4.M10] = right.y
        rotation.`val`[Matrix4.M20] = right.z
        rotation.`val`[Matrix4.M30] = 0f
        
        // Second column: up vector (Y-axis)
        rotation.`val`[Matrix4.M01] = up.x
        rotation.`val`[Matrix4.M11] = up.y
        rotation.`val`[Matrix4.M21] = up.z
        rotation.`val`[Matrix4.M31] = 0f
        
        // Third column: -direction (Z-axis points away from camera, towards billboard)
        rotation.`val`[Matrix4.M02] = -direction.x
        rotation.`val`[Matrix4.M12] = -direction.y
        rotation.`val`[Matrix4.M22] = -direction.z
        rotation.`val`[Matrix4.M32] = 0f
        
        // Fourth column: translation
        rotation.`val`[Matrix4.M03] = position.x
        rotation.`val`[Matrix4.M13] = position.y
        rotation.`val`[Matrix4.M23] = position.z
        rotation.`val`[Matrix4.M33] = 1f
        
        // Set the complete transform (rotation + translation in one matrix)
        instance.transform.set(rotation.`val`)
    }
    
    fun dispose() {
        playerModel?.dispose()
        playerModel = null
        playerInstance = null
    }
}

