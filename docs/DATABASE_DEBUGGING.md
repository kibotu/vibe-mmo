# Database Debugging Guide

This guide explains how to debug database issues in the Ragnarok Online MMO Clone project.

## Overview

The project uses:
- **H2 Database** (in-memory) for development
- **PostgreSQL** for production
- **Exposed ORM** for database operations
- **HikariCP** for connection pooling

## Debugging Features Enabled

### 1. SQL Query Logging

SQL queries are automatically logged to the console when executed. You'll see:
- All SQL statements executed by Exposed
- Query parameters
- Execution times

**Example output:**
```
SQL: CREATE TABLE IF NOT EXISTS accounts (id UUID PRIMARY KEY, email VARCHAR(255) NOT NULL, ...)
```

### 2. Database Connection Logging

The `DatabaseFactory` classes now log:
- Database initialization
- Connection parameters (driver, URL, user)
- Connection success/failure
- Table creation status

**Log levels:**
- `INFO`: Connection status, table creation
- `DEBUG`: Detailed connection parameters, test queries

### 3. HikariCP Connection Pool Logging

Connection pool events are logged at DEBUG level:
- Connection acquisition/release
- Pool statistics
- Connection validation

## Viewing Logs

### Console Output

When you start a server, you'll see database-related logs in the console:

```
2024-01-01 12:00:00.000 [main] INFO  net.kibotu.ro.account.database.DatabaseFactory - Initializing database connection...
2024-01-01 12:00:00.001 [main] DEBUG net.kibotu.ro.account.database.DatabaseFactory - Database driver: org.h2.Driver
2024-01-01 12:00:00.002 [main] DEBUG net.kibotu.ro.account.database.DatabaseFactory - Database URL: jdbc:h2:mem:accountdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
2024-01-01 12:00:00.003 [main] INFO  net.kibotu.ro.account.database.DatabaseFactory - Database connection established successfully
2024-01-01 12:00:00.004 [main] INFO  net.kibotu.ro.account.database.DatabaseFactory - Creating database tables...
SQL: CREATE TABLE IF NOT EXISTS accounts ...
2024-01-01 12:00:00.005 [main] INFO  net.kibotu.ro.account.database.DatabaseFactory - Database tables created successfully
```

### Adjusting Log Levels

Edit the `logback.xml` files to change log verbosity:

**account-server/src/main/resources/logback.xml** or **character-server/src/main/resources/logback.xml**

```xml
<!-- For more verbose SQL logging, change DEBUG to TRACE -->
<logger name="org.jetbrains.exposed.sql" level="TRACE"/>

<!-- For less verbose logging, change DEBUG to INFO -->
<logger name="net.kibotu.ro.account.database" level="INFO"/>
```

## Inspecting Database Contents

### Option 1: Using H2 Console (File-based Database)

**Note:** H2 Console only works with file-based databases, not in-memory databases.

1. **Switch to file-based database** in `application.conf`:

   **Account Server:**
   ```hocon
   database {
       driver = "org.h2.Driver"
       url = "jdbc:h2:file:./data/accountdb;AUTO_SERVER=TRUE;MODE=PostgreSQL"
       user = "sa"
       password = ""
   }
   ```

   **Character Server:**
   ```hocon
   database {
       driver = "org.h2.Driver"
       url = "jdbc:h2:file:./data/characterdb;AUTO_SERVER=TRUE;MODE=PostgreSQL"
       user = "sa"
       password = ""
   }
   ```

2. **Start the server** - the database file will be created in `./data/` directory

3. **Access H2 Console:**
   - Account Server: http://localhost:28080/h2-console
   - Character Server: http://localhost:28081/h2-console

4. **Login credentials:**
   - JDBC URL: `jdbc:h2:file:./data/accountdb` (or `characterdb`)
   - User: `sa`
   - Password: (leave empty)

### Option 2: Using SQL Queries in Code

Add temporary debugging code to inspect database contents:

```kotlin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

// In your service or route handler
transaction {
    val accounts = Accounts.selectAll().toList()
    logger.debug("Accounts in database: ${accounts.size}")
    accounts.forEach { account ->
        logger.debug("Account: ${account[Accounts.email]}")
    }
}
```

### Option 3: Using Database Inspection Endpoints (Recommended)

You can add debug endpoints to your servers for inspecting database state:

**Example for Account Server:**

```kotlin
// In accountRoutes.kt
get("/debug/accounts") {
    val accounts = transaction {
        Accounts.selectAll().map {
            mapOf(
                "id" to it[Accounts.id].toString(),
                "email" to it[Accounts.email],
                "createdAt" to it[Accounts.createdAt]
            )
        }
    }
    call.respond(accounts)
}
```

## Common Debugging Scenarios

### 1. Database Connection Fails

**Symptoms:**
- Server fails to start
- Error: "Failed to initialize database"

**Debugging steps:**
1. Check logs for connection parameters
2. Verify database URL is correct
3. For PostgreSQL: Ensure database is running and credentials are correct
4. Check network connectivity if using remote database

**Example log output:**
```
ERROR net.kibotu.ro.account.database.DatabaseFactory - Failed to initialize database
java.sql.SQLException: Connection refused
```

### 2. Tables Not Created

**Symptoms:**
- Queries fail with "table not found" errors

**Debugging steps:**
1. Check logs for table creation messages
2. Verify `SchemaUtils.create()` is called
3. Check for errors during table creation

**Enable verbose logging:**
```xml
<logger name="org.jetbrains.exposed.sql" level="TRACE"/>
```

### 3. Query Performance Issues

**Symptoms:**
- Slow response times
- High database load

**Debugging steps:**
1. Enable SQL query logging (already enabled)
2. Check query execution times in logs
3. Review HikariCP pool statistics
4. Consider adding database indexes

**Enable connection pool logging:**
```xml
<logger name="com.zaxxer.hikari" level="DEBUG"/>
```

### 4. Data Not Persisting

**Symptoms:**
- Data disappears after server restart
- Queries return empty results

**Debugging steps:**
1. **For in-memory databases:** This is expected! In-memory databases don't persist data.
2. **Solution:** Switch to file-based H2 database (see Option 1 above)
3. Check transaction commits - ensure transactions are properly committed

**Verify transaction handling:**
```kotlin
transaction {
    // Your database operations
    // Transaction auto-commits on success
}
```

### 5. Connection Pool Exhaustion

**Symptoms:**
- Errors: "Connection is not available"
- Timeouts waiting for connections

**Debugging steps:**
1. Check HikariCP logs for pool statistics
2. Review `maximumPoolSize` setting (default: 10)
3. Ensure connections are properly closed
4. Check for connection leaks

**Monitor pool status:**
```kotlin
val pool = (Database.connect(dataSource) as HikariDataSource).hikariPoolMXBean
logger.info("Active connections: ${pool.activeConnections}")
logger.info("Idle connections: ${pool.idleConnections}")
logger.info("Total connections: ${pool.totalConnections}")
```

## Advanced Debugging

### Enable Full SQL Trace

To see every SQL statement with parameters:

1. Update `logback.xml`:
```xml
<logger name="org.jetbrains.exposed.sql" level="TRACE"/>
```

2. Or programmatically in code:
```kotlin
transaction {
    addLogger(StdOutSqlLogger) // Already enabled by default
    // Your queries here
}
```

### Monitor Connection Pool Metrics

Add this to your `DatabaseFactory.init()`:

```kotlin
val dataSource = HikariDataSource(hikariConfig)
val pool = dataSource.hikariPoolMXBean

// Log pool metrics periodically (add to a scheduled task)
logger.info("Pool stats - Active: ${pool.activeConnections}, Idle: ${pool.idleConnections}, Total: ${pool.totalConnections}")
```

### Debug Specific Queries

Wrap problematic queries with additional logging:

```kotlin
transaction {
    addLogger(StdOutSqlLogger)
    logger.debug("Executing query: SELECT * FROM accounts WHERE email = ?")
    val result = Accounts.select { Accounts.email eq "test@example.com" }.firstOrNull()
    logger.debug("Query result: ${result?.get(Accounts.email)}")
}
```

## Production Debugging

For production PostgreSQL databases:

1. **Use PostgreSQL logs:**
   - Enable `log_statement = 'all'` in `postgresql.conf`
   - Check PostgreSQL logs for slow queries

2. **Use connection monitoring:**
   - Monitor HikariCP metrics
   - Use PostgreSQL's `pg_stat_activity` view

3. **Reduce logging verbosity:**
   - Set log levels to INFO or WARN in production
   - Avoid logging sensitive data (passwords, tokens)

## Quick Reference

### Log Files Location
- Logs are output to console (stdout)
- No log files are created by default

### Database File Locations (File-based H2)
- Account Server: `./data/accountdb.mv.db`
- Character Server: `./data/characterdb.mv.db`

### Useful Loggers
- `net.kibotu.ro.account.database` - Account server database operations
- `net.kibotu.ro.character.database` - Character server database operations
- `org.jetbrains.exposed.sql` - SQL query logging
- `com.zaxxer.hikari` - Connection pool logging
- `org.h2` - H2 database engine logging

### Environment Variables
- `PORT` - Server port (default: 28080 for account, 28081 for character)
- `HOST` - Server host (default: 0.0.0.0)

## Tips

1. **Start with DEBUG level** to see all database operations
2. **Use file-based H2** for persistent debugging sessions
3. **Add temporary debug endpoints** for quick database inspection
4. **Monitor connection pool** if experiencing performance issues
5. **Check transaction boundaries** if data isn't persisting

## Troubleshooting

### Logs Not Appearing

- Verify `logback.xml` is in `src/main/resources/`
- Check log level settings
- Ensure SLF4J is properly configured

### SQL Queries Not Logged

- Verify `StdOutSqlLogger` is added in transactions
- Check Exposed logger level in `logback.xml`
- Ensure queries are executed within transactions

### H2 Console Not Accessible

- H2 console only works with file-based databases
- Ensure `AUTO_SERVER=TRUE` is in the JDBC URL
- Check that the server is running

---

For more information, see:
- [Exposed Documentation](https://github.com/JetBrains/Exposed)
- [HikariCP Documentation](https://github.com/brettwooldridge/HikariCP)
- [H2 Database Documentation](https://www.h2database.com/html/main.html)

