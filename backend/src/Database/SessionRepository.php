<?php declare(strict_types=1);

namespace Mmo\Database;

use DateTimeImmutable;
use DateTimeZone;
use PDO;
use Throwable;

final class SessionRepository
{
    public function __construct(private readonly Database $database)
    {
    }

    public function createGuest(string $name, string $roomCode, int $lifetimeSeconds): CreatedGuest
    {
        $selector = bin2hex(random_bytes(16));
        $validator = bin2hex(random_bytes(32));
        $now = new DateTimeImmutable('now', new DateTimeZone('UTC'));

        $pdo = $this->database->pdo();
        $pdo->beginTransaction();
        try {
            $player = $pdo->prepare(
                'INSERT INTO players (public_id, name, room_code, hp, x, y, z, inventory, last_processed_input, state, created_at, updated_at)
                 VALUES (:public_id, :name, :room_code, 40, 32.5, :y, 32.5, :inventory, 0, \'idle\', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))',
            );
            $player->execute([
                'public_id' => self::uuid4(),
                'name' => $name,
                'room_code' => $roomCode,
                'y' => 0.0,
                'inventory' => json_encode(array_fill(0, 20, null), JSON_THROW_ON_ERROR),
            ]);
            $playerId = (int) $pdo->lastInsertId();

            $session = $pdo->prepare(
                'INSERT INTO player_sessions (player_id, selector, validator_hash, expires_at, created_at, last_seen_at)
                 VALUES (:player_id, :selector, :validator_hash, :expires_at, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))',
            );
            $session->execute([
                'player_id' => $playerId,
                'selector' => $selector,
                'validator_hash' => hash('sha256', $validator),
                'expires_at' => $now->modify(sprintf('+%d seconds', $lifetimeSeconds))->format('Y-m-d H:i:s.u'),
            ]);
            $sessionId = (int) $pdo->lastInsertId();
            $pdo->commit();

            return new CreatedGuest(
                new GuestIdentity($sessionId, $playerId, $this->playerPublicId($pdo, $playerId), $name, $roomCode),
                $selector,
                $validator,
            );
        } catch (Throwable $exception) {
            $pdo->rollBack();
            throw $exception;
        }
    }

    public function authenticate(string $selector, string $validator): ?GuestIdentity
    {
        if (preg_match('/^[0-9a-f]{32}$/D', $selector) !== 1 || preg_match('/^[0-9a-f]{64}$/D', $validator) !== 1) {
            return null;
        }

        $statement = $this->database->pdo()->prepare(
            'SELECT s.id AS session_id, s.expires_at, p.id, p.public_id, p.name, p.room_code
             FROM player_sessions s
             INNER JOIN players p ON p.id = s.player_id
             WHERE s.selector = :selector AND s.validator_hash = :validator_hash
             LIMIT 1',
        );
        $statement->execute([
            'selector' => $selector,
            'validator_hash' => hash('sha256', $validator),
        ]);
        $row = $statement->fetch();
        if (!is_array($row) || !$this->isFuture($row['expires_at'])) {
            return null;
        }

        $touch = $this->database->pdo()->prepare('UPDATE player_sessions SET last_seen_at = UTC_TIMESTAMP(6) WHERE id = :id');
        $touch->execute(['id' => (int) $row['session_id']]);

        return new GuestIdentity(
            (int) $row['session_id'],
            (int) $row['id'],
            (string) $row['public_id'],
            (string) $row['name'],
            $row['room_code'] !== null ? (string) $row['room_code'] : null,
        );
    }

    public function issueTicket(GuestIdentity $identity, int $lifetimeSeconds = 30): string
    {
        $ticket = bin2hex(random_bytes(32));
        $expiresAt = (new DateTimeImmutable('now', new DateTimeZone('UTC')))
            ->modify(sprintf('+%d seconds', $lifetimeSeconds))
            ->format('Y-m-d H:i:s.u');
        $statement = $this->database->pdo()->prepare(
            'UPDATE player_sessions
             SET websocket_ticket_hash = :ticket_hash,
                 websocket_ticket_expires_at = :expires_at,
                 websocket_ticket_consumed_at = NULL,
                 last_seen_at = UTC_TIMESTAMP(6)
             WHERE id = :id AND player_id = :player_id AND expires_at > UTC_TIMESTAMP(6)',
        );
        $statement->execute([
            'ticket_hash' => hash('sha256', $ticket),
            'expires_at' => $expiresAt,
            'id' => $identity->sessionId,
            'player_id' => $identity->databaseId,
        ]);
        if ($statement->rowCount() !== 1) {
            throw new \RuntimeException('The guest session is no longer active.');
        }

        return $ticket;
    }

    public function consumeTicket(string $ticket): ?TicketIdentity
    {
        if (preg_match('/^[0-9a-f]{64}$/D', $ticket) !== 1) {
            return null;
        }

        $pdo = $this->database->pdo();
        $pdo->beginTransaction();
        try {
            $statement = $pdo->prepare(
                'SELECT s.id AS session_id, p.*, r.code AS joined_room_code, r.name AS joined_room_name,
                        r.max_players AS joined_room_max_players
                 FROM player_sessions s
                 INNER JOIN players p ON p.id = s.player_id
                 INNER JOIN rooms r ON r.code = p.room_code
                 WHERE s.websocket_ticket_hash = :ticket_hash
                   AND s.websocket_ticket_consumed_at IS NULL
                   AND s.websocket_ticket_expires_at > UTC_TIMESTAMP(6)
                   AND s.expires_at > UTC_TIMESTAMP(6)
                   AND r.status IN (\'active\', \'paused\')
                 LIMIT 1
                 FOR UPDATE',
            );
            $statement->execute(['ticket_hash' => hash('sha256', $ticket)]);
            $row = $statement->fetch();
            if (!is_array($row)) {
                $pdo->rollBack();

                return null;
            }

            $consume = $pdo->prepare(
                'UPDATE player_sessions
                 SET websocket_ticket_consumed_at = UTC_TIMESTAMP(6), last_seen_at = UTC_TIMESTAMP(6)
                 WHERE id = :id AND websocket_ticket_consumed_at IS NULL',
            );
            $consume->execute(['id' => (int) $row['session_id']]);
            if ($consume->rowCount() !== 1) {
                $pdo->rollBack();

                return null;
            }
            $pdo->commit();

            return new TicketIdentity(
                PlayerRecord::fromRow($row, (string) $row['inventory']),
                (string) $row['joined_room_code'],
                (string) $row['joined_room_name'],
                (int) $row['joined_room_max_players'],
            );
        } catch (Throwable $exception) {
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
            throw $exception;
        }
    }

    public function updateRoom(int $playerId, string $roomCode): void
    {
        $statement = $this->database->pdo()->prepare(
            'UPDATE players SET room_code = :room_code, updated_at = UTC_TIMESTAMP(6) WHERE id = :id',
        );
        $statement->execute(['room_code' => $roomCode, 'id' => $playerId]);
    }

    private function playerPublicId(PDO $pdo, int $playerId): string
    {
        $statement = $pdo->prepare('SELECT public_id FROM players WHERE id = :id');
        $statement->execute(['id' => $playerId]);
        $publicId = $statement->fetchColumn();
        if (!is_string($publicId)) {
            throw new \RuntimeException('The guest player could not be loaded.');
        }

        return $publicId;
    }

    private function isFuture(mixed $value): bool
    {
        if (!is_string($value)) {
            return false;
        }
        $expires = DateTimeImmutable::createFromFormat('Y-m-d H:i:s.u', $value, new DateTimeZone('UTC'));

        return $expires !== false && $expires > new DateTimeImmutable('now', new DateTimeZone('UTC'));
    }

    private static function uuid4(): string
    {
        $bytes = random_bytes(16);
        $bytes[6] = chr((ord($bytes[6]) & 0x0F) | 0x40);
        $bytes[8] = chr((ord($bytes[8]) & 0x3F) | 0x80);
        $hex = bin2hex($bytes);

        return substr($hex, 0, 8) . '-' . substr($hex, 8, 4) . '-' . substr($hex, 12, 4) . '-'
            . substr($hex, 16, 4) . '-' . substr($hex, 20, 12);
    }
}
