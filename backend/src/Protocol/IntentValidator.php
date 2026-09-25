<?php declare(strict_types=1);

namespace Mmo\Protocol;

use Mmo\Game\ItemCatalog;
use Mmo\World\WorldGrid;

final class IntentValidator
{
    /**
     * @return array<string, int|string>
     */
    public function validate(Intent $intent, WorldGrid $grid): array
    {
        return match ($intent->name) {
            'move' => $this->validateMove($intent->payload, $grid),
            'target' => $this->validateTarget($intent->payload),
            'attack' => $this->validateAttack($intent->payload),
            'pickup' => $this->validatePickup($intent->payload),
            'use_item' => $this->validateUseItem($intent->payload),
            default => throw new ProtocolException('unknown_intent', 'The intent type is not supported.'),
        };
    }

    /**
     * @param array<string, mixed> $payload
     * @return array<string, int>
     */
    private function validateMove(array $payload, WorldGrid $grid): array
    {
        if (!$this->hasOnlyKeys($payload, ['x', 'z'])) {
            throw new ProtocolException('invalid_cell', 'Move requires one integer cell.');
        }
        $x = $payload['x'] ?? null;
        $z = $payload['z'] ?? null;
        if (!is_int($x) || !is_int($z) || $x < 0 || $z < 0 || $x >= $grid->width() || $z >= $grid->height()) {
            throw new ProtocolException('invalid_cell', 'The destination cell is outside the world.');
        }
        if (!$grid->isPassable($x, $z)) {
            throw new ProtocolException('blocked_cell', 'The destination cell cannot be reached.');
        }

        return ['x' => $x, 'z' => $z];
    }

    /**
     * @param array<string, mixed> $payload
     * @return array<string, string>
     */
    private function validateTarget(array $payload): array
    {
        $this->assertKeys($payload, ['targetId']);
        $targetId = $payload['targetId'] ?? null;
        if (!self::isActorId($targetId)) {
            throw new ProtocolException('invalid_target', 'The target identifier is invalid.');
        }

        return ['targetId' => $targetId];
    }

    /**
     * @param array<string, mixed> $payload
     * @return array<string, string>
     */
    private function validateAttack(array $payload): array
    {
        $this->assertKeys($payload, ['targetId'], true);
        $targetId = $payload['targetId'] ?? null;
        if ($targetId !== null && !self::isActorId($targetId)) {
            throw new ProtocolException('invalid_target', 'The target identifier is invalid.');
        }

        return $targetId === null ? [] : ['targetId' => $targetId];
    }

    /**
     * @param array<string, mixed> $payload
     * @return array<string, string>
     */
    private function validatePickup(array $payload): array
    {
        $this->assertKeys($payload, ['itemId']);
        $itemId = $payload['itemId'] ?? null;
        if (!is_string($itemId) || ItemCatalog::definition($itemId) === null) {
            throw new ProtocolException('invalid_item', 'The pickup item is invalid.');
        }

        return ['itemId' => $itemId];
    }

    /**
     * @param array<string, mixed> $payload
     * @return array<string, int|string>
     */
    private function validateUseItem(array $payload): array
    {
        $this->assertKeys($payload, ['itemId', 'slot'], true);
        $itemId = $payload['itemId'] ?? null;
        $slot = $payload['slot'] ?? null;
        if ($itemId !== 'apple') {
            throw new ProtocolException('item_not_usable', 'Only Apples can be used.');
        }
        if ($slot !== null && (!is_int($slot) || $slot < 0 || $slot >= 20)) {
            throw new ProtocolException('invalid_slot', 'The inventory slot is out of range.');
        }

        return $slot === null ? ['itemId' => $itemId] : ['itemId' => $itemId, 'slot' => $slot];
    }

    /** @param array<string, mixed> $payload */
    private function assertKeys(array $payload, array $allowed, bool $allOptional = false): void
    {
        $expected = $allOptional ? $allowed : [$allowed[0]];
        $unknown = array_diff(array_keys($payload), $allowed);
        if ($unknown !== []) {
            throw new ProtocolException('invalid_payload', 'The intent payload contains unsupported fields.');
        }
        foreach ($expected as $key) {
            if (!$allOptional && !array_key_exists($key, $payload)) {
                throw new ProtocolException('invalid_payload', 'The intent payload is missing a required field.');
            }
        }
    }

    /** @param array<mixed> $value */
    private function hasOnlyKeys(array $value, array $keys): bool
    {
        return count($value) === count($keys) && array_diff(array_keys($value), $keys) === [];
    }

    private static function isActorId(mixed $value): bool
    {
        if (!is_string($value)) {
            return false;
        }

        return preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/Di', $value) === 1
            || preg_match('/^poring-[0-9]{1,4}$/D', $value) === 1;
    }
}
