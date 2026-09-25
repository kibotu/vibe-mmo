# Authoritative multiplayer runtime

## Ownership boundaries

```text
lobby.php / MySQL
        │ short-lived, signed-in guest identity
        ▼
WebSocket ticket (single use, 30 seconds)
        │
        ▼
PHP game daemon ── owns active rooms in RAM
        │             20 Hz fixed simulation
        │             input sequencing
        │             movement, combat, loot, inventory
        ▼
10 Hz JSON snapshots
        │
        ├── own player: prediction + reconciliation
        └── other actors: buffered interpolation
```

A normal PHP request owns `lobby.php` and ends after rendering HTML. The game daemon is a separate CLI process kept alive by Supervisor or systemd. PHP-FPM cannot own persistent WebSockets, so nginx proxies `/ws` to the daemon's private port.

## Identities and reconnects

The browser never chooses a player ID. `lobby.php` creates or resumes a guest identity and stores a selector/validator session in an HttpOnly, SameSite cookie. Only the validator hash is stored in MySQL.

`api/session.php` exchanges that authenticated cookie for a random, single-use WebSocket ticket. Connecting consumes the ticket and binds the socket to the durable public player ID. Refreshing or briefly losing the network therefore creates a new socket for the same player.

When a socket closes:

1. The player remains in the room and is marked disconnected.
2. The room pauses simulation while it has no sockets.
3. Reconnection restores the retained in-memory player.
4. After the empty-room TTL, the room is removed and durable state is used if it is created again.
5. If the daemon stops, player state is flushed. After a restart, durable state is loaded and monsters/world state are rebuilt.

MySQL is not updated every tick. Position and durable state are persisted periodically and on graceful shutdown. Up to one persistence interval of volatile movement can be lost after a hard process or machine failure.

## Wire protocol

All messages are UTF-8 JSON text frames. Binary frames, malformed JSON, unknown message types, oversized frames, excessive input sequences, and stale sequence numbers are rejected.

### Client intent

```json
{
  "type": "intent",
  "seq": 184,
  "intent": "move",
  "payload": { "x": 32, "z": 30 }
}
```

Supported intents:

| Intent | Payload | Server validation |
|---|---|---|
| `move` | `{x, z}` | Integer cell, in bounds, passable, A* route exists |
| `target` | `{targetId}` | Actor exists in the same room and is alive |
| `attack` | `{}` | Current target exists, range and cooldown pass |
| `pickup` | `{itemId}` | Server-owned floor item exists within pickup range |
| `use_item` | `{itemId: "apple"}` | Player owns Apple and is below max HP |

Sequence numbers increase per connection. The welcome and snapshots expose the server's input watermark; clients start above that watermark, discard acknowledged intents, and replay unacknowledged movement locally after a reconnect or page reload.

### Welcome

```json
{
  "type": "welcome",
  "connectionId": "c_...",
  "playerId": "uuid",
  "room": { "code": "PAYON", "name": "Payon Forest" },
  "seed": 1337,
  "tickRate": 20,
  "snapshotRate": 10,
  "interpolationMs": 100,
  "lastProcessedInput": 183,
  "serverTime": 12345.678
}
```

### Snapshot

```json
{
  "type": "snapshot",
  "tick": 248,
  "serverTime": 12345.700,
  "lastProcessedInput": 184,
  "actors": [
    {
      "id": "uuid",
      "kind": "player",
      "name": "Aeryn",
      "position": { "x": 32.5, "y": 0.1, "z": 30.5 },
      "facing": 0,
      "state": "walk",
      "hp": 40,
      "maxHp": 40,
      "targetId": null,
      "nextAttackAt": 0
    }
  ],
  "items": [],
  "inventory": [null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null]
}
```

`serverTime` is a Unix timestamp in seconds; `interpolationMs` is expressed in milliseconds. The client must normalize those units before sampling the snapshot buffer.

The inventory is included only in the addressed player's snapshot. The server never accepts a client inventory, position, damage value, cooldown, or loot roll.

### Events and errors

Gameplay feedback uses unique event IDs so reconnects cannot reapply an old event:

```json
{
  "type": "event",
  "event": {
    "id": 991,
    "kind": "damage",
    "actorId": "poring-3",
    "amount": 11,
    "text": "You hit Poring 3 for 11.",
    "itemId": null
  }
}
```

Errors use `{ "type": "error", "code": "...", "message": "..." }`.

## Resource controls

The game server bounds the attack surface before simulation:

- maximum open sockets;
- maximum players per room;
- maximum message and frame size;
- per-connection bytes/frames per second;
- bounded intent queues and message parsing;
- fixed room capacity and empty-room TTL;
- periodic persistence rather than per-frame queries;
- container CPU, memory, and PID limits in local Compose;
- live memory, tick drift, connection, and traffic counters in `admin.php`.

Full-world JSON is deliberate at this scale. It is small for a lobby-sized game and much easier to debug than a premature binary delta protocol. Profiling comes before optimisation.

## Scaling boundary

A room has exactly one owning PHP process. Multiple game-server processes must not independently simulate the same room. Horizontal scaling would require room ownership plus a shared event bus or broker and a room handoff protocol; that is outside the current single-process design and should not be faked with a shared MySQL game loop.
