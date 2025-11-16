package net.kibotu.ro.dbviewer.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("net.kibotu.ro.dbviewer.database.DatabaseConnections")

object DatabaseConnections {
    lateinit var accountDatabase: Database
    lateinit var characterDatabase: Database
    
    fun init(config: ApplicationConfig) {
        // Connect to account database
        val accountConfig = config.config("ktor.databases.account")
        val accountDriver = accountConfig.property("driver").getString()
        val accountUrl = accountConfig.property("url").getString()
        val accountUser = accountConfig.property("user").getString()
        val accountPassword = accountConfig.property("password").getString()
        
        logger.info("Connecting to account database: $accountUrl")
        val accountHikariConfig = HikariConfig().apply {
            jdbcUrl = accountUrl
            driverClassName = accountDriver
            username = accountUser
            this.password = accountPassword
            maximumPoolSize = 5
            isAutoCommit = false
            validate()
        }
        
        try {
            val accountDataSource = HikariDataSource(accountHikariConfig)
            accountDatabase = Database.connect(accountDataSource)
            logger.info("Connected to account database successfully at: $accountUrl")
            
            // Verify we can query the database
            try {
                val originalDb = org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase
                try {
                    org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase = accountDatabase
                    transaction {
                        // Try to query table existence
                        try {
                            val count = Accounts.selectAll().count()
                            logger.info("Accounts table exists with $count records")
                        } catch (e: Exception) {
                            logger.warn("Accounts table does not exist or cannot be queried: ${e.message}")
                        }
                    }
                } finally {
                    org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase = originalDb
                }
            } catch (e: Exception) {
                logger.debug("Could not verify account table: ${e.message}")
            }
            
            // Try to create tables if they don't exist (using createMissingTablesAndColumns for safety)
            try {
                val originalDb = org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase
                try {
                    org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase = accountDatabase
                    transaction {
                        try {
                            SchemaUtils.createMissingTablesAndColumns(Accounts, Sessions)
                            logger.info("Ensured account database tables exist")
                        } catch (e: Exception) {
                            // If tables already exist, that's fine
                            if (e.message?.contains("already exists", ignoreCase = true) == true ||
                                e.message?.contains("duplicate", ignoreCase = true) == true) {
                                logger.debug("Account tables already exist")
                            } else {
                                logger.warn("Could not create account tables (they may already exist): ${e.message}")
                            }
                        }
                    }
                } finally {
                    org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase = originalDb
                }
            } catch (e: Exception) {
                logger.warn("Could not ensure account tables exist: ${e.message}. Tables may need to be created by account-server first.")
            }
        } catch (e: Exception) {
            logger.error("Failed to connect to account database: ${e.message}", e)
            logger.error("Make sure account-server is using a file-based database and the path matches")
            logger.warn("Database viewer will continue but account data will not be available")
            // Don't throw - allow server to start even if database connection fails
            // Create a dummy database connection to prevent null pointer exceptions
            accountDatabase = Database.connect("jdbc:h2:mem:dummy", driver = "org.h2.Driver")
        }
        
        // Connect to character database
        val characterConfig = config.config("ktor.databases.character")
        val characterDriver = characterConfig.property("driver").getString()
        val characterUrl = characterConfig.property("url").getString()
        val characterUser = characterConfig.property("user").getString()
        val characterPassword = characterConfig.property("password").getString()
        
        logger.info("Connecting to character database: $characterUrl")
        val characterHikariConfig = HikariConfig().apply {
            jdbcUrl = characterUrl
            driverClassName = characterDriver
            username = characterUser
            this.password = characterPassword
            maximumPoolSize = 5
            isAutoCommit = false
            validate()
        }
        
        try {
            val characterDataSource = HikariDataSource(characterHikariConfig)
            characterDatabase = Database.connect(characterDataSource)
            logger.info("Connected to character database successfully at: $characterUrl")
            
            // Verify we can query the database
            try {
                val originalDb = org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase
                try {
                    org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase = characterDatabase
                    transaction {
                        // Try to query table existence
                        try {
                            val count = Characters.selectAll().count()
                            logger.info("Characters table exists with $count records")
                        } catch (e: Exception) {
                            logger.warn("Characters table does not exist or cannot be queried: ${e.message}")
                        }
                    }
                } finally {
                    org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase = originalDb
                }
            } catch (e: Exception) {
                logger.debug("Could not verify character table: ${e.message}")
            }
            
            // Try to create tables if they don't exist (using createMissingTablesAndColumns for safety)
            try {
                val originalDb = org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase
                try {
                    org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase = characterDatabase
                    transaction {
                        try {
                            SchemaUtils.createMissingTablesAndColumns(Characters)
                            logger.info("Ensured character database tables exist")
                        } catch (e: Exception) {
                            // If tables already exist, that's fine
                            if (e.message?.contains("already exists", ignoreCase = true) == true ||
                                e.message?.contains("duplicate", ignoreCase = true) == true) {
                                logger.debug("Character tables already exist")
                            } else {
                                logger.warn("Could not create character tables (they may already exist): ${e.message}")
                            }
                        }
                    }
                } finally {
                    org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase = originalDb
                }
            } catch (e: Exception) {
                logger.warn("Could not ensure character tables exist: ${e.message}. Tables may need to be created by character-server first.")
            }
        } catch (e: Exception) {
            logger.error("Failed to connect to character database: ${e.message}", e)
            logger.error("Make sure character-server is using a file-based database and the path matches")
            logger.warn("Database viewer will continue but character data will not be available")
            // Don't throw - allow server to start even if database connection fails
            // Create a dummy database connection to prevent null pointer exceptions
            characterDatabase = Database.connect("jdbc:h2:mem:dummy2", driver = "org.h2.Driver")
        }
    }
}

