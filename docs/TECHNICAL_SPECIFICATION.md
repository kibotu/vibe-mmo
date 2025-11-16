# Technical Specification
## Ragnarok Online MMO Clone

**Version:** 1.0  
**Last Updated:** 2024  
**Status:** In Development

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture Overview](#architecture-overview)
3. [Technology Stack](#technology-stack)
4. [Account Server](#account-server)
5. [Character Server](#character-server)
6. [Zone Server](#zone-server)
7. [Game Client](#game-client)
8. [Shared Module](#shared-module)
9. [Communication Protocols](#communication-protocols)
10. [Data Models](#data-models)
11. [Security Considerations](#security-considerations)
12. [Deployment Architecture](#deployment-architecture)

---

## Project Overview

This project is a **Ragnarok Online MMO clone** built using **Kotlin Multiplatform** technology. The goal is to create a fully functional 2D MMORPG with a distributed server architecture and a desktop game client.

### Core Objectives

- **Account Management**: Secure user registration, authentication, and session management
- **Character Management**: Character creation, selection, and data persistence
- **World Simulation**: 2D zone-based world with maps, NPCs, monsters, and player interactions
- **Real-time Multiplayer**: Synchronized player movement and interactions across zone instances
- **Desktop Client**: Native desktop application using Jetpack Compose Multiplatform

### Target Platform

- **Client**: Desktop only (Windows, macOS, Linux) - JVM target
- **Servers**: JVM-based servers
- **No browser/web support** (at this time)

---

## Architecture Overview

The system follows a **microservices architecture** with clear separation of concerns:

```
┌─────────────────┐
│  Game Client    │  (Compose Desktop)
│  (Desktop JVM)  │
└────────┬────────┘
         │
         │ HTTP/WebSocket
         │
    ┌────┴────────────────────────────────────┐
    │                                          │
┌───▼──────────┐  ┌──────────────┐  ┌─────────▼──────┐
│   Account    │  │  Character   │  │   Zone Server  │
│   Server     │  │   Server     │  │   (Instances)  │
│              │  │              │  │                │
│ - Auth       │  │ - Creation   │  │ - Maps         │
│ - Sessions   │  │ - Selection  │  │ - Movement     │
│ - Email/PWD  │  │ - Data       │  │ - NPCs         │
└──────────────┘  └──────────────┘  │ - Monsters     │
                                    │ - Collision    │
                                    └────────────────┘
```

### Service Responsibilities

1. **Account Server**: Handles all authentication, user accounts, and session tokens
2. **Character Server**: Manages character data, creation, and selection
3. **Zone Server**: Handles game world state, player movement, NPCs, monsters, and collisions
4. **Game Client**: Provides the user interface and game rendering

### Communication Flow

```
User Registration/Login Flow:
Client → Account Server → (Session Token) → Client

Character Selection Flow:
Client → Character Server (with Session Token) → Character List → Client

Game World Entry Flow:
Client → Zone Server (with Character ID + Session Token) → Zone Instance → WebSocket Connection
```

---

## Technology Stack

### Core Technologies

- **Language**: Kotlin 1.9+
- **Build System**: Gradle (Groovy DSL)
- **Multiplatform**: Kotlin Multiplatform (KMP)
- **Target Platforms**: JVM (Desktop + Server)

### Server Technologies

- **HTTP Framework**: Ktor
- **Database**: 
  - Development: H2 (in-memory/file)
  - Production: PostgreSQL
- **ORM/Query Builder**: Exposed (type-safe SQL DSL)
- **Serialization**: kotlinx.serialization (JSON)
- **WebSocket**: Ktor WebSocket support

### Client Technologies

- **UI Framework**: Jetpack Compose Multiplatform
- **Target**: Desktop (JVM)
- **Networking**: Ktor Client (HTTP + WebSocket)
- **Serialization**: kotlinx.serialization

### Development Tools

- **Linting**: ktlint
- **Static Analysis**: detekt
- **Testing**: Kotlin Test, JUnit

---

## Account Server

### Purpose

The Account Server is responsible for:
- User registration (email + password)
- User authentication (login)
- Session token generation and validation
- Password hashing and security

### Endpoints

#### POST `/api/account/register`
Register a new user account.

**Request:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response (Success):**
```json
{
  "success": true,
  "message": "Account created successfully",
  "userId": "uuid-here"
}
```

**Response (Error):**
```json
{
  "success": false,
  "error": "Email already exists"
}
```

#### POST `/api/account/login`
Authenticate a user and create a session.

**Request:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response (Success):**
```json
{
  "success": true,
  "sessionToken": "jwt-token-here",
  "userId": "uuid-here",
  "expiresAt": "2024-12-31T23:59:59Z"
}
```

**Response (Error):**
```json
{
  "success": false,
  "error": "Invalid credentials"
}
```

#### POST `/api/account/validate`
Validate a session token (used by other servers).

**Request:**
```json
{
  "sessionToken": "jwt-token-here"
}
```

**Response:**
```json
{
  "valid": true,
  "userId": "uuid-here",
  "expiresAt": "2024-12-31T23:59:59Z"
}
```

### Database Schema

```sql
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_login TIMESTAMP
);

CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    token VARCHAR(512) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT true
);
```

### Security

- Passwords are hashed using **bcrypt** or **Argon2**
- Session tokens are **JWT** tokens with expiration
- Email validation (format checking)
- Rate limiting on login/register endpoints

### Default Port

- **Development**: `28080`
- **Production**: Configurable via `application.conf`

---

## Character Server

### Purpose

The Character Server manages:
- Character creation (name only, for now)
- Character listing for a user
- Character selection
- Character data persistence

### Endpoints

#### GET `/api/character/list`
Get all characters for the authenticated user.

**Headers:**
```
Authorization: Bearer <session-token>
```

**Response:**
```json
{
  "success": true,
  "characters": [
    {
      "id": "uuid-here",
      "name": "PlayerOne",
      "createdAt": "2024-01-01T00:00:00Z"
    }
  ]
}
```

#### POST `/api/character/create`
Create a new character for the authenticated user.

**Headers:**
```
Authorization: Bearer <session-token>
```

**Request:**
```json
{
  "name": "PlayerOne"
}
```

**Response (Success):**
```json
{
  "success": true,
  "character": {
    "id": "uuid-here",
    "name": "PlayerOne",
    "createdAt": "2024-01-01T00:00:00Z"
  }
}
```

**Response (Error):**
```json
{
  "success": false,
  "error": "Character name already taken" // or "Invalid name format"
}
```

#### GET `/api/character/{characterId}`
Get character details by ID.

**Headers:**
```
Authorization: Bearer <session-token>
```

**Response:**
```json
{
  "success": true,
  "character": {
    "id": "uuid-here",
    "name": "PlayerOne",
    "accountId": "account-uuid",
    "createdAt": "2024-01-01T00:00:00Z"
  }
}
```

### Database Schema

```sql
CREATE TABLE characters (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    name VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

### Validation Rules

- Character name: 3-20 characters, alphanumeric + spaces
- One character per account (initially, can be extended)
- Name uniqueness across all characters

### Default Port

- **Development**: `28081`
- **Production**: Configurable via `application.conf`

---

## Zone Server

### Purpose

The Zone Server handles:
- 2D map management and rendering data
- Player movement and position synchronization
- Collision detection (bounding boxes)
- NPCs (Non-Player Characters)
- Monsters and AI
- Real-time multiplayer synchronization via WebSocket

### Architecture

Each **Zone Server** can host multiple **zone instances**. Each instance represents a separate 2D map/area.

```
Zone Server
├── Zone Instance 1 (Map: "prontera")
│   ├── Players (connected via WebSocket)
│   ├── NPCs
│   ├── Monsters
│   └── Collision Map
├── Zone Instance 2 (Map: "geffen")
│   └── ...
└── Zone Instance N
```

### HTTP Endpoints

#### POST `/api/zone/join`
Request to join a zone instance with a character.

**Headers:**
```
Authorization: Bearer <session-token>
```

**Request:**
```json
{
  "characterId": "uuid-here",
  "zoneName": "prontera"
}
```

**Response:**
```json
{
  "success": true,
  "zoneInstanceId": "instance-uuid",
  "websocketUrl": "ws://localhost:28082/ws/zone/{instanceId}",
  "spawnPosition": {
    "x": 150,
    "y": 150
  }
}
```

### WebSocket Protocol

**Connection URL:** `ws://host:port/ws/zone/{zoneInstanceId}`

**Headers:**
```
Authorization: Bearer <session-token>
Character-Id: <character-id>
```

#### Client → Server Messages

**Player Movement:**
```json
{
  "type": "move",
  "direction": "up", // "up", "down", "left", "right"
  "position": {
    "x": 150,
    "y": 151
  }
}
```

**Chat Message:**
```json
{
  "type": "chat",
  "message": "Hello, world!"
}
```

#### Server → Client Messages

**Zone State Update:**
```json
{
  "type": "zone_state",
  "players": [
    {
      "characterId": "uuid",
      "name": "PlayerOne",
      "position": { "x": 150, "y": 150 },
      "direction": "down"
    }
  ],
  "npcs": [
    {
      "id": "npc-1",
      "name": "Merchant",
      "position": { "x": 200, "y": 200 },
      "type": "merchant"
    }
  ],
  "monsters": [
    {
      "id": "monster-1",
      "type": "poring",
      "position": { "x": 300, "y": 300 },
      "health": 100
    }
  ]
}
```

**Player Joined:**
```json
{
  "type": "player_joined",
  "characterId": "uuid",
  "name": "PlayerTwo",
  "position": { "x": 160, "y": 160 }
}
```

**Player Left:**
```json
{
  "type": "player_left",
  "characterId": "uuid"
}
```

**Movement Update:**
```json
{
  "type": "movement",
  "characterId": "uuid",
  "position": { "x": 151, "y": 151 },
  "direction": "up"
}
```

### Map Data Structure

**Map Definition (JSON):**
```json
{
  "name": "prontera",
  "width": 1000,
  "height": 1000,
  "collisionMap": "base64-encoded-bitmap",
  "spawnPoints": [
    { "x": 150, "y": 150 }
  ],
  "npcs": [
    {
      "id": "npc-1",
      "name": "Merchant",
      "position": { "x": 200, "y": 200 },
      "type": "merchant"
    }
  ]
}
```

### Collision Detection

- **Bounding Box System**: Each entity (player, NPC, monster) has a bounding box
- **Tile-based Collision**: Map has a collision layer (bitmap or tile data)
- **Movement Validation**: Server validates all movement before broadcasting

### NPCs

- Static NPCs defined in map data
- Can have interactions (dialogue, shops, quests) - future feature
- Position is fixed unless scripted

### Monsters

- Spawned dynamically based on map configuration
- Basic AI (patrol, chase, attack) - future feature
- Health, position, and state synchronized to all players

### Default Port

- **Development**: `28082`
- **Production**: Configurable via `application.conf`

---

## Game Client

### Purpose

The Game Client provides:
- User registration and login UI
- Character selection and creation UI
- 2D game world rendering
- Real-time multiplayer synchronization
- Input handling (keyboard, mouse)

### Technology

- **UI Framework**: Jetpack Compose Multiplatform
- **Target**: Desktop (JVM)
- **Rendering**: Canvas-based 2D rendering (Skia via Compose)
- **Networking**: Ktor Client (HTTP + WebSocket)

### Application Flow

```
1. Launch Application
   ↓
2. Login/Register Screen
   ├─→ Register new account
   └─→ Login with credentials
   ↓
3. Character Selection Screen
   ├─→ Select existing character
   └─→ Create new character
   ↓
4. Zone Selection / Auto-join
   ↓
5. Game World Screen
   ├─→ Render 2D map
   ├─→ Render players, NPCs, monsters
   ├─→ Handle input (movement, chat)
   └─→ WebSocket communication
```

### UI Screens

#### 1. Login/Register Screen
- Email input field
- Password input field
- "Login" button
- "Register" button
- Error message display

#### 2. Character Selection Screen
- List of existing characters
- "Create New Character" button
- "Select Character" button
- Character creation dialog (name input)

#### 3. Game World Screen
- 2D map rendering (tiles/sprites)
- Player sprite (controlled by user)
- Other players' sprites
- NPC sprites
- Monster sprites
- Chat window (future)
- UI overlay (health, stats - future)

### Input Handling

- **Arrow Keys / WASD**: Player movement
- **Mouse Click**: Interaction (future)
- **Enter**: Chat input (future)
- **ESC**: Menu (future)

### Rendering

- **Coordinate System**: 2D top-down or isometric (to be decided)
- **Camera**: Follows player character
- **Sprites**: Loaded from resources (future: sprite sheets)
- **Map Rendering**: Tile-based or bitmap-based

### Network Client

- **HTTP Client**: For account/character server communication
- **WebSocket Client**: For real-time zone server communication
- **Session Management**: Stores session token, handles reconnection

---

## Shared Module

### Purpose

The `shared` module contains common code used across all modules:
- Data models (DTOs)
- Serialization definitions
- Common utilities
- Constants

### Common Data Models

```kotlin
// Account models
data class RegisterRequest(val email: String, val password: String)
data class LoginRequest(val email: String, val password: String)
data class SessionResponse(val sessionToken: String, val userId: String, val expiresAt: String)

// Character models
data class Character(val id: String, val name: String, val accountId: String, val createdAt: String)
data class CreateCharacterRequest(val name: String)

// Zone models
data class Position(val x: Int, val y: Int)
data class Player(val characterId: String, val name: String, val position: Position, val direction: String)
data class NPC(val id: String, val name: String, val position: Position, val type: String)
data class Monster(val id: String, val type: String, val position: Position, val health: Int)

// WebSocket messages
sealed class ZoneMessage
data class MoveMessage(val direction: String, val position: Position) : ZoneMessage()
data class ZoneStateMessage(val players: List<Player>, val npcs: List<NPC>, val monsters: List<Monster>) : ZoneMessage()
```

---

## Communication Protocols

### HTTP REST API

All servers expose REST APIs using JSON:

- **Content-Type**: `application/json`
- **Authentication**: Bearer token in `Authorization` header
- **Error Responses**: Standardized error format

```json
{
  "success": false,
  "error": "Error message here",
  "code": "ERROR_CODE"
}
```

### WebSocket Protocol

- **Protocol**: WebSocket (ws:// or wss://)
- **Message Format**: JSON
- **Heartbeat**: Ping/pong for connection keepalive
- **Reconnection**: Client should handle disconnections gracefully

### Inter-Service Communication

- **Account Server** → **Character Server**: Session validation (HTTP)
- **Account Server** → **Zone Server**: Session validation (HTTP)
- **Character Server** → **Zone Server**: Character data validation (HTTP)

---

## Data Models

### Account
- `id`: UUID
- `email`: String (unique)
- `passwordHash`: String
- `createdAt`: Timestamp
- `lastLogin`: Timestamp (nullable)

### Session
- `id`: UUID
- `accountId`: UUID (foreign key)
- `token`: String (JWT)
- `createdAt`: Timestamp
- `expiresAt`: Timestamp
- `isActive`: Boolean

### Character
- `id`: UUID
- `accountId`: UUID (foreign key)
- `name`: String (unique, 3-20 chars)
- `createdAt`: Timestamp

### Zone Instance
- `id`: UUID
- `zoneName`: String
- `mapData`: JSON/Blob
- `players`: List<Player>
- `npcs`: List<NPC>
- `monsters`: List<Monster>

---

## Security Considerations

### Authentication & Authorization

- **Password Hashing**: Use bcrypt or Argon2 (never store plaintext)
- **Session Tokens**: JWT with expiration (default: 24 hours)
- **Token Validation**: All protected endpoints validate session tokens
- **HTTPS/WSS**: Use in production (TLS encryption)

### Input Validation

- **Email**: Format validation, length limits
- **Password**: Minimum length (8+), complexity requirements
- **Character Name**: Alphanumeric + spaces, length limits, uniqueness
- **Position Data**: Bounds checking, rate limiting

### Rate Limiting

- **Login Attempts**: Limit failed login attempts per IP
- **Registration**: Limit registrations per IP
- **Movement**: Validate movement rate (prevent speed hacking)

### SQL Injection Prevention

- Use **Exposed** ORM (parameterized queries)
- Never use string concatenation for SQL

### XSS Prevention

- Sanitize all user input
- Escape output in client rendering

---

## Deployment Architecture

### Development Setup

```
Local Machine:
├── Account Server (localhost:28080)
├── Character Server (localhost:28081)
├── Zone Server (localhost:28082)
└── Game Client (Desktop App)
```

### Production Setup

```
Load Balancer / Reverse Proxy (nginx)
├── Account Server Cluster (port 28080)
├── Character Server Cluster (port 28081)
├── Zone Server Cluster (port 28082)
│   ├── Zone Instance 1
│   ├── Zone Instance 2
│   └── Zone Instance N
└── PostgreSQL Database
```

### Configuration

Each server uses `application.conf` (HOCON format):

```hocon
ktor {
    deployment {
        port = 28080
        host = "0.0.0.0"
    }
    application {
        modules = [ net.kibotu.ro.ApplicationKt.module ]
    }
}

database {
    url = "jdbc:postgresql://localhost:5432/ragnarok"
    user = "ragnarok_user"
    password = "secure_password"
    driver = "org.postgresql.Driver"
}

jwt {
    secret = "your-secret-key-here"
    issuer = "ragnarok-account-server"
    audience = "ragnarok-clients"
    expirationHours = 24
}
```

### Environment Variables

- `DATABASE_URL`: PostgreSQL connection string
- `JWT_SECRET`: Secret key for JWT signing
- `SERVER_PORT`: Override default port
- `ENV`: `development` or `production`

---

## Future Enhancements

### Phase 2 Features

- [ ] Character stats (level, HP, MP, STR, AGI, etc.)
- [ ] Inventory system
- [ ] Item system
- [ ] Combat system
- [ ] Monster AI and spawning
- [ ] Quest system
- [ ] Chat system (global, local, party)
- [ ] Party/Group system
- [ ] Guild system

### Phase 3 Features

- [ ] Multiple zones/maps
- [ ] Zone transitions
- [ ] Save/load game state
- [ ] Admin tools
- [ ] Analytics and monitoring
- [ ] Performance optimization

---

## Development Guidelines

### Code Style

- Follow Kotlin coding conventions
- Use `ktlint` for formatting
- Use `detekt` for static analysis

### Testing

- Unit tests for business logic
- Integration tests for API endpoints
- End-to-end tests for critical flows

### Version Control

- Feature branches
- Pull requests for code review
- Commit messages follow conventional commits

---

## Appendix

### Default Ports

| Service | Port |
|---------|------|
| Account Server | 28080 |
| Character Server | 28081 |
| Zone Server | 28082 |

### API Base URLs

- Account Server: `http://localhost:28080`
- Character Server: `http://localhost:28081`
- Zone Server: `http://localhost:28082`

### WebSocket URLs

- Zone Server: `ws://localhost:28082/ws/zone/{instanceId}`

---

**Document Version:** 1.0  
**Last Updated:** 2024  
**Maintainer:** Development Team

