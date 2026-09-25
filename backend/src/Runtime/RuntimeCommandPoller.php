<?php declare(strict_types=1);

namespace Mmo\Runtime;

final class RuntimeCommandPoller
{
    private ?string $lastId = null;

    public function __construct(private readonly AtomicJsonFile $file)
    {
    }

    /** @return array{id: string, action: string, connectionId: string|null}|null */
    public function poll(): ?array
    {
        $claimedPath = $this->file->path() . '.polling-' . getmypid();
        if (!@rename($this->file->path(), $claimedPath)) {
            return null;
        }
        $data = (new AtomicJsonFile($claimedPath))->read(4096);
        @unlink($claimedPath);
        if ($data === null) {
            return null;
        }

        $id = $data['id'] ?? null;
        $action = $data['action'] ?? null;
        $connectionId = $data['connectionId'] ?? null;
        if (!is_string($id)
            || preg_match('/^[0-9a-f-]{36}$/Di', $id) !== 1
            || $id === $this->lastId
            || !is_string($action)
            || !in_array($action, ['pause', 'resume', 'disconnect'], true)
            || ($connectionId !== null && (!is_string($connectionId) || preg_match('/^[0-9a-f-]{36}$/Di', $connectionId) !== 1))
        ) {
            return null;
        }
        $this->lastId = $id;

        return ['id' => $id, 'action' => $action, 'connectionId' => $connectionId];
    }
}
