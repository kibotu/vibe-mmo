CREATE TABLE IF NOT EXISTS rooms (
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    max_players SMALLINT UNSIGNED NOT NULL DEFAULT 100,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (code),
    CONSTRAINT chk_rooms_status CHECK (status IN ('active', 'paused', 'inactive'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS players (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(24) NOT NULL,
    room_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    hp SMALLINT UNSIGNED NOT NULL DEFAULT 40,
    x DOUBLE NOT NULL DEFAULT 32.5,
    y DOUBLE NOT NULL DEFAULT 0,
    z DOUBLE NOT NULL DEFAULT 32.5,
    inventory JSON NOT NULL,
    last_processed_input BIGINT UNSIGNED NOT NULL DEFAULT 0,
    state VARCHAR(16) NOT NULL DEFAULT 'idle',
    target_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    next_attack_at DOUBLE NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_players_public_id (public_id),
    KEY idx_players_room (room_code),
    CONSTRAINT fk_players_room FOREIGN KEY (room_code) REFERENCES rooms (code) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_players_state CHECK (state IN ('idle', 'walk', 'attack', 'hurt', 'dead'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_id BIGINT UNSIGNED NOT NULL,
    selector CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    validator_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    websocket_ticket_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    websocket_ticket_expires_at DATETIME(6) NULL,
    websocket_ticket_consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_player_sessions_player (player_id),
    UNIQUE KEY uq_player_sessions_selector (selector),
    KEY idx_player_sessions_ticket (websocket_ticket_hash),
    KEY idx_player_sessions_expiry (expires_at),
    CONSTRAINT fk_player_sessions_player FOREIGN KEY (player_id) REFERENCES players (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
