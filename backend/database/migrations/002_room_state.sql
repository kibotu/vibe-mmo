-- World state for the long-polling runtime.
--
-- The WebSocket daemon holds room state in memory for the life of the process.
-- Shared hosting has no long-lived process, so a polled room hydrates from this
-- table at the start of a request and writes back at the end. One row per room.
--
-- Only non-player actors and floor items are stored. Players already live in
-- {prefix}players and are joined in at request time, which keeps the existing
-- persistence and inventory handling authoritative in one place.

CREATE TABLE IF NOT EXISTS {prefix}room_state (
    room_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    -- Poring actors and floor items as JSON documents. Shape is defined by
    -- Mmo\Server\RoomStateCodec, which is the only reader and writer.
    state JSON NOT NULL,
    -- Monotonic simulation tick, so a client can tell a fresh snapshot from a
    -- replayed one and so respawn timers survive between requests.
    tick BIGINT UNSIGNED NOT NULL DEFAULT 0,
    -- Wall-clock of the last simulated instant, used to compute the delta for
    -- the next request. Without it a room would fast-forward on a long pause.
    simulated_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (room_code),
    CONSTRAINT {prefix}fk_room_state_room FOREIGN KEY (room_code) REFERENCES {prefix}rooms (code) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
