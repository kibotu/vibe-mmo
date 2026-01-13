package net.kibotu.ro.client.game

import kotlin.math.*

/**
 * Simple noise generator for terrain height maps
 * Uses Perlin-like noise for smooth, natural-looking terrain
 */
class NoiseGenerator(private val seed: Long = 0L) {
    private val random = kotlin.random.Random(seed)
    
    /**
     * Generate a simple noise value at given coordinates
     * Uses a simplified Perlin noise approach
     */
    fun noise(x: Float, y: Float, scale: Float = 1f): Float {
        val nx = x * scale
        val ny = y * scale
        
        // Get integer and fractional parts
        val ix = floor(nx).toInt()
        val iy = floor(ny).toInt()
        val fx = nx - ix
        val fy = ny - iy
        
        // Smooth interpolation function
        fun smooth(t: Float): Float = t * t * (3f - 2f * t)
        val u = smooth(fx)
        val v = smooth(fy)
        
        // Get random gradients at corners
        val n00 = dotGridGradient(ix, iy, fx, fy)
        val n10 = dotGridGradient(ix + 1, iy, fx - 1f, fy)
        val n01 = dotGridGradient(ix, iy + 1, fx, fy - 1f)
        val n11 = dotGridGradient(ix + 1, iy + 1, fx - 1f, fy - 1f)
        
        // Bilinear interpolation
        val nx0 = lerp(n00, n10, u)
        val nx1 = lerp(n01, n11, u)
        return lerp(nx0, nx1, v)
    }
    
    /**
     * Generate octave noise (multiple frequencies combined)
     */
    fun octaveNoise(x: Float, y: Float, octaves: Int = 4, persistence: Float = 0.5f, scale: Float = 0.1f): Float {
        var value = 0f
        var amplitude = 1f
        var frequency = scale
        var maxValue = 0f
        
        for (i in 0 until octaves) {
            value += noise(x, y, frequency) * amplitude
            maxValue += amplitude
            amplitude *= persistence
            frequency *= 2f
        }
        
        return value / maxValue
    }
    
    private fun dotGridGradient(ix: Int, iy: Int, x: Float, y: Float): Float {
        // Generate pseudo-random gradient vector
        val angle = hash(ix, iy) * 2f * PI.toFloat()
        val gx = cos(angle)
        val gy = sin(angle)
        
        // Compute dot product
        val dx = x
        val dy = y
        return dx * gx + dy * gy
    }
    
    private fun hash(x: Int, y: Int): Float {
        // Simple hash function
        val n = ((x * 73856093) xor (y * 19349663) xor (seed.toInt() * 83492791)) and 0x7FFFFFFF
        return (n % 10000) / 10000f
    }
    
    private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)
}

/**
 * Generate a noise-based height map with subtle height differences
 */
fun generateNoiseHeightMap(width: Int, height: Int, maxHeight: Float = 2f, seed: Long = 0L): FloatArray {
    val noiseGen = NoiseGenerator(seed)
    val heightMap = FloatArray(width * height)
    
    for (y in 0 until height) {
        for (x in 0 until width) {
            // Use octave noise for natural-looking terrain
            // Scale down the noise to keep it subtle
            val noiseValue = noiseGen.octaveNoise(
                x.toFloat(),
                y.toFloat(),
                octaves = 3,
                persistence = 0.5f,
                scale = 0.05f // Small scale for subtle variations
            )
            
            // Map noise from [-1, 1] to [0, maxHeight]
            val height = (noiseValue + 1f) * 0.5f * maxHeight
            heightMap[y * width + x] = height
        }
    }
    
    return heightMap
}

