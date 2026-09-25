<?php declare(strict_types=1);

namespace Mmo\Runtime;

final class StatusSanitizer
{
    /**
     * @param array<string, mixed> $status
     * @return array<string, mixed>
     */
    public static function sanitize(array $status): array
    {
        $connections = [];
        foreach (self::rows($status['connections'] ?? []) as $row) {
            $connections[] = [
                'connectionId' => self::identifier($row['connectionId'] ?? ''),
                'playerId' => self::identifier($row['playerId'] ?? ''),
                'playerName' => self::text($row['playerName'] ?? '', 24),
                'forwardedIp' => self::ip($row['forwardedIp'] ?? ''),
                'room' => self::text($row['room'] ?? '', 32),
                'connected' => ($row['connected'] ?? false) === true,
                'connectedAt' => self::number($row['connectedAt'] ?? 0.0),
                'lastSeen' => self::number($row['lastSeen'] ?? 0.0),
                'inboundMessages' => self::integer($row['inboundMessages'] ?? 0),
                'inboundBytes' => self::integer($row['inboundBytes'] ?? 0),
                'outboundBytes' => self::integer($row['outboundBytes'] ?? 0),
            ];
        }

        $rooms = [];
        foreach (self::rows($status['rooms'] ?? []) as $row) {
            $rooms[] = [
                'code' => self::text($row['code'] ?? '', 32),
                'name' => self::text($row['name'] ?? '', 64),
                'state' => self::enum($row['state'] ?? 'paused', ['running', 'paused']),
                'connectedPlayers' => self::integer($row['connectedPlayers'] ?? 0),
                'retainedPlayers' => self::integer($row['retainedPlayers'] ?? 0),
                'maxPlayers' => self::integer($row['maxPlayers'] ?? 0),
                'actors' => self::integer($row['actors'] ?? 0),
                'items' => self::integer($row['items'] ?? 0),
                'emptySeconds' => $row['emptySeconds'] === null ? null : self::number($row['emptySeconds']),
                'tick' => self::integer($row['tick'] ?? 0),
            ];
        }

        $state = self::enum($status['state'] ?? 'stopped', ['starting', 'running', 'paused', 'stopping', 'stopped']);

        return [
            'schemaVersion' => 1,
            'updatedAt' => gmdate('c'),
            'pid' => self::integer($status['pid'] ?? 0),
            'state' => $state,
            'startedAt' => self::number($status['startedAt'] ?? 0.0),
            'uptime' => self::number($status['uptime'] ?? 0.0),
            'memory' => self::sanitizeMemory(is_array($status['memory'] ?? null) ? $status['memory'] : []),
            'limits' => self::sanitizeLimits(is_array($status['limits'] ?? null) ? $status['limits'] : []),
            'tick' => self::sanitizeTick(is_array($status['tick'] ?? null) ? $status['tick'] : []),
            'network' => self::sanitizeNetwork(is_array($status['network'] ?? null) ? $status['network'] : []),
            'totals' => self::sanitizeTotals(is_array($status['totals'] ?? null) ? $status['totals'] : []),
            'rooms' => $rooms,
            'connections' => $connections,
        ];
    }

    /** @return list<array<string, mixed>> */
    private static function rows(mixed $value): array
    {
        return is_array($value) && array_is_list($value) ? array_values(array_filter($value, 'is_array')) : [];
    }

    /** @param array<string, mixed> $row */
    private static function sanitizeMemory(array $row): array
    {
        return [
            'currentBytes' => self::integer($row['currentBytes'] ?? 0),
            'peakBytes' => self::integer($row['peakBytes'] ?? 0),
        ];
    }

    /** @param array<string, mixed> $row */
    private static function sanitizeLimits(array $row): array
    {
        return [
            'connections' => self::integer($row['connections'] ?? 0),
            'connectionsPerRoom' => self::integer($row['connectionsPerRoom'] ?? 0),
            'messageBytes' => self::integer($row['messageBytes'] ?? 0),
            'bytesPerSecond' => self::integer($row['bytesPerSecond'] ?? 0),
            'framesPerSecond' => self::integer($row['framesPerSecond'] ?? 0),
            'rooms' => self::integer($row['rooms'] ?? 0),
        ];
    }

    /** @param array<string, mixed> $row */
    private static function sanitizeTick(array $row): array
    {
        return [
            'count' => self::integer($row['count'] ?? 0),
            'rate' => self::integer($row['rate'] ?? 20),
            'lastAt' => self::number($row['lastAt'] ?? 0.0),
            'driftMs' => self::number($row['driftMs'] ?? 0.0),
            'totalDriftMs' => self::number($row['totalDriftMs'] ?? 0.0),
            'maxDriftMs' => self::number($row['maxDriftMs'] ?? 0.0),
        ];
    }

    /** @param array<string, mixed> $row */
    private static function sanitizeNetwork(array $row): array
    {
        return [
            'inboundMessages' => self::integer($row['inboundMessages'] ?? 0),
            'inboundBytes' => self::integer($row['inboundBytes'] ?? 0),
            'outboundBytes' => self::integer($row['outboundBytes'] ?? 0),
        ];
    }

    /** @param array<string, mixed> $row */
    private static function sanitizeTotals(array $row): array
    {
        return [
            'connectionsAccepted' => self::integer($row['connectionsAccepted'] ?? 0),
            'connectionsRejected' => self::integer($row['connectionsRejected'] ?? 0),
            'connectionsActive' => self::integer($row['connectionsActive'] ?? 0),
            'messagesRejected' => self::integer($row['messagesRejected'] ?? 0),
            'messagesIn' => self::integer($row['messagesIn'] ?? 0),
            'bytesIn' => self::integer($row['bytesIn'] ?? 0),
            'bytesOut' => self::integer($row['bytesOut'] ?? 0),
            'persistenceWrites' => self::integer($row['persistenceWrites'] ?? 0),
        ];
    }

    private static function identifier(mixed $value): string
    {
        $value = is_string($value) ? strtolower($value) : '';

        return preg_match('/^[0-9a-f-]{36}$/D', $value) === 1 ? $value : '';
    }

    private static function text(mixed $value, int $maxLength): string
    {
        if (!is_string($value)) {
            return '';
        }
        $value = preg_replace('/[\x00-\x1F\x7F]/u', '', $value) ?? '';

        return substr($value, 0, $maxLength);
    }

    private static function ip(mixed $value): string
    {
        return is_string($value) && filter_var($value, FILTER_VALIDATE_IP) !== false ? $value : '';
    }

    private static function enum(mixed $value, array $allowed): string
    {
        return is_string($value) && in_array($value, $allowed, true) ? $value : $allowed[0];
    }

    private static function integer(mixed $value): int
    {
        return is_int($value) || is_float($value) || (is_string($value) && is_numeric($value))
            ? max(0, (int) $value)
            : 0;
    }

    private static function number(mixed $value): float
    {
        if (!is_int($value) && !is_float($value) && !(is_string($value) && is_numeric($value))) {
            return 0.0;
        }
        $number = (float) $value;

        return is_finite($number) ? $number : 0.0;
    }
}
