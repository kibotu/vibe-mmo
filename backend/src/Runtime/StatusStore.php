<?php declare(strict_types=1);

namespace Mmo\Runtime;

final class StatusStore
{
    public function __construct(private readonly AtomicJsonFile $file)
    {
    }

    public function write(array $status): void
    {
        $this->file->write(StatusSanitizer::sanitize($status));
    }

    /** @return array<string, mixed>|null */
    public function read(): ?array
    {
        $status = $this->file->read();

        return $status === null ? null : StatusSanitizer::sanitize($status);
    }
}
