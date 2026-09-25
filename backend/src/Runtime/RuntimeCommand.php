<?php declare(strict_types=1);

namespace Mmo\Runtime;

use Mmo\Support\Uuid;

final class RuntimeCommand
{
    public function __construct(private readonly AtomicJsonFile $file)
    {
    }

    public function write(string $action, ?string $connectionId = null): string
    {
        if (!in_array($action, ['pause', 'resume', 'disconnect'], true)) {
            throw new \InvalidArgumentException('Unsupported runtime command.');
        }
        if ($connectionId !== null && preg_match('/^[0-9a-f-]{36}$/Di', $connectionId) !== 1) {
            throw new \InvalidArgumentException('Invalid connection identifier.');
        }
        $id = Uuid::v4();
        $this->file->write([
            'id' => $id,
            'action' => $action,
            'connectionId' => $connectionId,
            'requestedAt' => microtime(true),
        ]);

        return $id;
    }
}
