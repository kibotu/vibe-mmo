# General Project Readme

# Ragnarok Online MMO Clone (Prototype)

This repository contains a minimal, **Kotlin‑Multiplatform** prototype of a Ragnarok‑style MMO.  The project is split into four “server” modules and one **Compose Desktop** client.  All components target the JVM/desktop and are written in Kotlin 1.9 with Gradle (Groovy DSL).

## Repository Structure

```
├─ account-server/      # User account & auth
├─ character-server/    # Character data
├─ zone-server/         # World state & movement
├─ game-client/         # Compose Desktop UI
├─ docs/                # Technical specifications (you just added!)
└─ shared/              # Common multiplatform objects
```

## Quickstart

### Prerequisites

- **Java 17+** (JDK)
- **Gradle** (wrapper included)

### Windows

The easiest way to get started is using the provided scripts:

1. **Start all servers:**
   ```powershell
   # Double-click start-servers.bat or run:
   .\start-servers.bat
   ```
   This will start all three servers (Account Server on port 28080, Character Server on port 28081, Zone Server on port 28082) in separate windows.
   
   **Important:** 
   - **Account Server** and **Character Server** allow only **ONE instance** per machine
   - **Zone Server** allows **multiple instances** (use `start-zone-server.bat [PORT]` for additional instances)
   - The script will prevent starting duplicate account/character servers

2. **Start the client:**
   ```powershell
   # Double-click start-client.bat or run:
   .\start-client.bat
   ```
   **Note:** Multiple client instances are allowed. You can run multiple clients simultaneously.

3. **Start additional zone servers:**
   ```powershell
   # Start a zone server on a custom port (e.g., 28084)
   .\start-zone-server.bat 28084
   ```

4. **Start Database Viewer (optional):**
   ```powershell
   # Double-click start-db-viewer.bat or run:
   .\start-db-viewer.bat
   ```
   This will start the Database Viewer Server on port 28083, providing a web interface to view all server databases.
   **Note:** For the database viewer to work, you need to configure file-based databases (see Database Viewer section below).

5. **Stop all servers:**
   - Press `Ctrl+C` in the main server window, or
   - Run `.\stop-servers.bat` to manually stop all servers

**Note:** All scripts automatically clean up processes when closed or interrupted, so you don't need to worry about orphaned processes.

### macOS/Linux

The easiest way to get started is using the provided scripts:

1. **Start all servers:**
   ```bash
   ./start-servers.sh
   ```
   This will start all three servers (Account Server on port 28080, Character Server on port 28081, Zone Server on port 28082) in the background.
   
   **Important:** 
   - **Account Server** and **Character Server** allow only **ONE instance** per machine
   - **Zone Server** allows **multiple instances** (use `./start-zone-server.sh [PORT]` for additional instances)
   - The script will prevent starting duplicate account/character servers

2. **Start the client:**
   ```bash
   ./start-client.sh
   ```
   **Note:** Multiple client instances are allowed. You can run multiple clients simultaneously.

3. **Start additional zone servers:**
   ```bash
   # Start a zone server on a custom port (e.g., 28084)
   ./start-zone-server.sh 28084
   ```

4. **Stop all servers:**
   - Press `Ctrl+C` in the main server window, or
   - Run `./stop-servers.sh` to manually stop all servers

**Note:** All scripts automatically clean up processes when closed or interrupted, so you don't need to worry about orphaned processes.

## Build Tools

- **Gradle** (wrapper) – all modules share `settings.gradle.kts` and a common `build.gradle.kts`.
- **Ktor** – asynchronous HTTP server/client.
- **Exposed** – type‑safe SQL DSL (H2 for dev, PostgreSQL for prod).
- **Serde** – `kotlinx.serialization` for JSON.
- **Compose for Desktop** – UI.
- **ktlint** + **detekt** – linting.

## Installation

```bash
git clone <repo-url>
cd ragnarok
./gradlew clean build
```

The wrapper pulls the correct Gradle version.

## Running the Project

### Using Scripts (Windows - Recommended)

See the [Quickstart](#quickstart) section above for the easiest way to run everything.

### Manual Start (All Platforms)

Start each server in its own terminal (or backgrounded tasks), then launch the client.

```bash
# Terminal 1
./gradlew :account-server:run
# Terminal 2
./gradlew :character-server:run
# Terminal 3
./gradlew :zone-server:run
# Terminal 4 (client)
./gradlew :game-client:run
```

All servers listen on their default ports:
- **Account Server**: Port 28080 (single instance only)
- **Character Server**: Port 28081 (single instance only)
- **Zone Server**: Port 28082 (multiple instances allowed, use `PORT=28084` env var for additional instances)
- **Database Viewer Server**: Port 28083 (optional, for viewing databases)

**Note:** Account and Character servers enforce single-instance restrictions. Zone servers and clients can run multiple instances.

The Compose client will automatically discover and connect to these ports.

### Production Configuration

- Set `ktor.deployment.port` in each module’s `application.conf`.
- Use `application-prod.conf` with PostgreSQL credentials for a live database.
- Replace the `websocket.host` in the client by setting `ENV_WS_HOST`.

## Testing

```bash
./gradlew test        # Run all unit & integration tests
```

You can also run *module‑specific* tests: `./gradlew :account-server:test`.

## Troubleshooting

### Build Issues

#### "Unable to delete directory" / File Locked Error (Windows)

If you encounter errors like:
```
Unable to delete directory 'shared\build'
Failed to delete some children. This might happen because a process has files open
Could not delete 'D:\...\shared\build\kotlin\compileKotlinJvm\cacheable\caches-jvm'
```

**Note:** The start scripts (`start-servers.ps1` and `start-servers.bat`) now automatically handle this by:
- Stopping all Gradle daemons before starting servers
- Pre-building the shared module once to avoid concurrent builds
- Using `--no-daemon` flag to prevent file locking conflicts
- Adding proper delays between server starts

**If you still encounter this issue:**

1. **Stop Gradle daemons manually:**
   ```bash
   ./gradlew --stop
   ```

2. **Close IDEs/editors:** Close Cursor, IntelliJ IDEA, or any IDE that has the project folder open.

3. **Close File Explorer:** If Windows Explorer has the `build` folder open, close it.

4. **Manual cleanup (PowerShell):**
   ```powershell
   Remove-Item -Path "shared\build" -Recurse -Force
   ```

5. **Use the stop script:** Run `.\stop-servers.bat` to properly stop all servers and Gradle daemons.

#### Port Already in Use

If a server fails to start with a port conflict error:

```bash
# Windows - Find process using port (e.g., 28080)
netstat -ano | findstr :28080

# Kill the process (replace PID with actual process ID)
taskkill /PID <PID> /F

# Linux/Mac - Find and kill process
lsof -ti:28080 | xargs kill -9
```

#### Gradle Daemon Issues

If builds hang or behave unexpectedly:

```bash
# Stop all daemons
./gradlew --stop

# Check daemon status
./gradlew --status

# Run without daemon (one-time)
./gradlew --no-daemon build
```

#### Build Cache Corruption

If you encounter strange build errors:

```bash
# Clear Gradle cache
./gradlew clean --refresh-dependencies

# Or manually delete cache (Windows)
Remove-Item -Path "$env:USERPROFILE\.gradle\caches" -Recurse -Force

# Linux/Mac
rm -rf ~/.gradle/caches
```

### Runtime Issues

#### Server Won't Start

1. **Check logs:** Look for error messages in the console output.
2. **Verify ports:** Ensure ports aren't already in use (see above).
3. **Check configuration:** Verify `application.conf` files are correct.
4. **Database connection:** If using PostgreSQL, ensure the database is running and credentials are correct.

#### Client Can't Connect to Servers

1. **Verify servers are running:** Check all three servers are started.
2. **Check ports:** Ensure servers are listening on expected ports (defaults in `application.conf`).
3. **Firewall:** Ensure firewall isn't blocking local connections.
4. **WebSocket issues:** Check `ENV_WS_HOST` environment variable if using custom host.

### Development Issues

#### Kotlin Multiplatform Build Errors

If you see errors related to multiplatform targets:

```bash
# Clean and rebuild
./gradlew clean
./gradlew build --refresh-dependencies
```

#### Dependency Resolution Issues

```bash
# Refresh dependencies
./gradlew --refresh-dependencies build

# Clear dependency cache
./gradlew clean --refresh-dependencies
```

## Database Viewer

The **Database Viewer Server** provides a web-based interface to view and inspect the databases from all servers.

### Starting the Database Viewer

```powershell
.\start-db-viewer.bat
```

Then open your browser to: **http://localhost:28083**

### Configuration

**Important:** The Database Viewer needs to connect to the same databases as the servers. For in-memory databases, this won't work across different processes. You need to configure file-based databases:

1. **Update Account Server** (`account-server/src/main/resources/application.conf`):
   ```hocon
   database {
       driver = "org.h2.Driver"
       url = "jdbc:h2:file:./data/accountdb;AUTO_SERVER=TRUE;MODE=PostgreSQL"
       user = "sa"
       password = ""
   }
   ```

2. **Update Character Server** (`character-server/src/main/resources/application.conf`):
   ```hocon
   database {
       driver = "org.h2.Driver"
       url = "jdbc:h2:file:./data/characterdb;AUTO_SERVER=TRUE;MODE=PostgreSQL"
       user = "sa"
       password = ""
   }
   ```

3. **Update Database Viewer** (`db-viewer-server/src/main/resources/application.conf`):
   ```hocon
   databases {
       account {
           driver = "org.h2.Driver"
           url = "jdbc:h2:file:./data/accountdb;AUTO_SERVER=TRUE;MODE=PostgreSQL"
           user = "sa"
           password = ""
       }
       character {
           driver = "org.h2.Driver"
           url = "jdbc:h2:file:./data/characterdb;AUTO_SERVER=TRUE;MODE=PostgreSQL"
           user = "sa"
           password = ""
       }
   }
   ```

The Database Viewer provides:
- Real-time statistics (accounts, sessions, characters)
- View all accounts with creation dates
- View all active sessions
- View all characters
- Zone server status

## Documentation

All technical specs are in the `docs/` folder:

- `AccountServerSpec.md`
- `CharacterCreationServerSpec.md`
- `ZoneServerSpec.md`
- `GameClientSpec.md`
- `DATABASE_DEBUGGING.md` - Guide for debugging database issues

These documents detail the data models, endpoints, auth flow, and wiring instructions.

## Contributing

1. Fork the repo.
2. Create a feature branch.
3. Submit a pull request.

The project uses **ktlint** and **detekt**. Run `./gradlew detektAll ktlintCheck` before committing.

## Roadmap (Short‑Term)

| Feature | Status |
|---------|--------|
| User registration & login | ✅ |
| Character creation & selection | ✅ |
| 2‑D zone world & movement | ✅ |
| Client UI (login, character manager, world) | ✅ |
| Persistent world state | 🔜 |
| Multiplayer sync | 🔜 |

Happy hacking! 🎮
