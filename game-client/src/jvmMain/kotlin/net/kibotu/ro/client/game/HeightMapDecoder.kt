package net.kibotu.ro.client.game

import java.util.Base64

/**
 * Utility for decoding heightmap data from server
 */
object HeightMapDecoder {
    /**
     * Decode heightmap from Base64-encoded FloatArray
     * Format: Base64 string containing little-endian Float32 values
     */
    fun decodeHeightMap(base64: String, width: Int, height: Int): FloatArray {
        val bytes = Base64.getDecoder().decode(base64)
        val heightMap = FloatArray(width * height)
        
        // Read Float32 values (4 bytes each, little-endian)
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        
        for (i in heightMap.indices) {
            if (buffer.remaining() >= 4) {
                heightMap[i] = buffer.float
            } else {
                heightMap[i] = 0f // Default height if data is incomplete
            }
        }
        
        return heightMap
    }
}









