package net.kibotu.ro.character.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("net.kibotu.ro.character.database.DatabaseFactory")

object DatabaseFactory {
    fun init(config: ApplicationConfig) {
        val dbConfig = config.config("ktor.database")
        val driver = dbConfig.property("driver").getString()
        val url = dbConfig.property("url").getString()
        val user = dbConfig.property("user").getString()
        val password = dbConfig.property("password").getString()
        
        logger.info("Initializing database connection...")
        logger.debug("Database driver: $driver")
        logger.debug("Database URL: $url")
        logger.debug("Database user: $user")
        
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = url
            driverClassName = driver
            username = user
            this.password = password
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            
            // Enable connection pool logging
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            
            validate()
        }
        
        try {
            val dataSource = HikariDataSource(hikariConfig)
            Database.connect(dataSource)
            logger.info("Database connection established successfully")
            
            // Test connection
            transaction {
                logger.debug("Testing database connection...")
            }
            logger.info("Database connection test passed")
            
            // Create tables
            transaction {
                // Enable SQL query logging for debugging
                addLogger(StdOutSqlLogger)
                logger.info("Creating database tables...")
                SchemaUtils.create(Characters)
                logger.info("Database tables created successfully")
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize database", e)
            throw e
        }
    }
}

