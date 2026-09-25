<?php declare(strict_types=1);

namespace Mmo\Game;

final class Inventory
{
    public const CAPACITY = 20;

    /** @var list<array{itemId: string, quantity: int}|null> */
    private array $slots;

    public function __construct()
    {
        $this->slots = array_fill(0, self::CAPACITY, null);
    }

    public function add(string $itemId, int $quantity): int
    {
        $definition = ItemCatalog::definition($itemId);
        if ($definition === null || $quantity <= 0) {
            return $quantity;
        }

        $remaining = $quantity;
        if ($definition['stackable']) {
            foreach ($this->slots as $index => $stack) {
                if ($stack === null || $stack['itemId'] !== $itemId || $stack['quantity'] >= $definition['maxStack']) {
                    continue;
                }
                $accepted = min($remaining, $definition['maxStack'] - $stack['quantity']);
                $this->slots[$index]['quantity'] += $accepted;
                $remaining -= $accepted;
                if ($remaining === 0) {
                    return 0;
                }
            }
        }

        for ($index = 0; $index < self::CAPACITY && $remaining > 0; ++$index) {
            if ($this->slots[$index] !== null) {
                continue;
            }
            $accepted = $definition['stackable'] ? min($remaining, $definition['maxStack']) : 1;
            $this->slots[$index] = ['itemId' => $itemId, 'quantity' => $accepted];
            $remaining -= $accepted;
        }

        return $remaining;
    }

    public function remove(string $itemId, int $quantity = 1): bool
    {
        if ($quantity <= 0 || $this->count($itemId) < $quantity) {
            return false;
        }

        $remaining = $quantity;
        for ($index = 0; $index < self::CAPACITY && $remaining > 0; ++$index) {
            $stack = $this->slots[$index];
            if ($stack === null || $stack['itemId'] !== $itemId) {
                continue;
            }
            $removed = min($remaining, $stack['quantity']);
            $this->slots[$index]['quantity'] -= $removed;
            $remaining -= $removed;
            if ($stack['quantity'] === 0) {
                $this->slots[$index] = null;
            }
        }

        return $remaining === 0;
    }

    public function count(string $itemId): int
    {
        $total = 0;
        foreach ($this->slots as $stack) {
            if ($stack !== null && $stack['itemId'] === $itemId) {
                $total += $stack['quantity'];
            }
        }

        return $total;
    }

    /** @return list<array{itemId: string, quantity: int}|null> */
    public function slots(): array
    {
        return $this->slots;
    }

    /**
     * @param list<array{itemId: string, quantity: int}|null> $slots
     */
    public static function fromSlots(array $slots): self
    {
        $inventory = new self();
        foreach (array_slice($slots, 0, self::CAPACITY) as $index => $stack) {
            if (!is_array($stack) || !isset($stack['itemId'], $stack['quantity'])) {
                continue;
            }
            $itemId = $stack['itemId'];
            $quantity = $stack['quantity'];
            if (!is_string($itemId) || !is_int($quantity) || $quantity <= 0 || ItemCatalog::definition($itemId) === null) {
                continue;
            }
            $definition = ItemCatalog::definition($itemId);
            $inventory->slots[$index] = [
                'itemId' => $itemId,
                'quantity' => $definition['stackable'] ? min($quantity, $definition['maxStack']) : 1,
            ];
        }

        return $inventory;
    }
}
