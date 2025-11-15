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

All servers listen on their default ports (see the individual module readmes).  The Compose client will automatically discover and connect to these ports.

### Production Configuration

- Set `ktor.deployment.port` in each module’s `application.conf`.
- Use `application-prod.conf` with PostgreSQL credentials for a live database.
- Replace the `websocket.host` in the client by setting `ENV_WS_HOST`.

## Testing

```bash
./gradlew test        # Run all unit & integration tests
```

You can also run *module‑specific* tests: `./gradlew :account-server:test`.

## Documentation

All technical specs are in the `docs/` folder:

- `AccountServerSpec.md`
- `CharacterCreationServerSpec.md`
- `ZoneServerSpec.md`
- `GameClientSpec.md`

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
