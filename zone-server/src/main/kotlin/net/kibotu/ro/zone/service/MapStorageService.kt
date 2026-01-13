package net.kibotu.ro.zone.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.kibotu.ro.zone.model.MapData
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Service for loading and saving map data from/to JSON files
 */
class MapStorageService {
    private val logger = LoggerFactory.getLogger(MapStorageService::class.java)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val mapsDirectory: Path = try {
        // Try to use resources directory first (for packaged JAR)
        val resourcePath = javaClass.classLoader.getResource("maps")
        if (resourcePath != null) {
            Paths.get(resourcePath.toURI())
        } else {
            // Fallback to src/main/resources/maps (for development)
            Paths.get("src/main/resources/maps")
        }
    } catch (e: Exception) {
        // Fallback to relative path
        Paths.get("src/main/resources/maps")
    }
    
    init {
        // Ensure maps directory exists
        try {
            Files.createDirectories(mapsDirectory)
        } catch (e: Exception) {
            logger.warn("Could not create maps directory: ${e.message}")
        }
    }
    
    /**
     * Load a map by name
     */
    fun loadMap(mapName: String): MapData? {
        val fileName = "$mapName.json"
        val file = mapsDirectory.resolve(fileName)
        
        if (!Files.exists(file)) {
            logger.debug("Map file not found: $fileName")
            return null
        }
        
        return try {
            val jsonContent = Files.readString(file)
            json.decodeFromString<MapData>(jsonContent)
        } catch (e: Exception) {
            logger.error("Failed to load map $mapName: ${e.message}", e)
            null
        }
    }
    
    /**
     * Save a map
     */
    fun saveMap(mapData: MapData): Boolean {
        val fileName = "${mapData.name}.json"
        val file = mapsDirectory.resolve(fileName)
        
        return try {
            val jsonContent = json.encodeToString(mapData)
            Files.writeString(file, jsonContent)
            logger.info("Map saved: ${mapData.name}")
            true
        } catch (e: Exception) {
            logger.error("Failed to save map ${mapData.name}: ${e.message}", e)
            false
        }
    }
    
    /**
     * List all available maps
     */
    fun listMaps(): List<String> {
        return try {
            if (!Files.exists(mapsDirectory)) {
                return emptyList()
            }
            
            Files.list(mapsDirectory)
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                .map { it.fileName.toString().removeSuffix(".json") }
                .sorted()
                .toList()
        } catch (e: Exception) {
            logger.error("Failed to list maps: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Delete a map
     */
    fun deleteMap(mapName: String): Boolean {
        val fileName = "$mapName.json"
        val file = mapsDirectory.resolve(fileName)
        
        return try {
            if (Files.exists(file)) {
                Files.delete(file)
                logger.info("Map deleted: $mapName")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to delete map $mapName: ${e.message}", e)
            false
        }
    }
    
    /**
     * Check if a map exists
     */
    fun mapExists(mapName: String): Boolean {
        val fileName = "$mapName.json"
        val file = mapsDirectory.resolve(fileName)
        return Files.exists(file)
    }
    
    /**
     * Get the maps directory path
     */
    fun getMapsDirectory(): Path {
        return mapsDirectory
    }
}

