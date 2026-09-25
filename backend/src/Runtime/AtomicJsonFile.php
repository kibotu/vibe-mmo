<?php declare(strict_types=1);

namespace Mmo\Runtime;

final class AtomicJsonFile
{
    public function __construct(private readonly string $path)
    {
    }

    public function path(): string
    {
        return $this->path;
    }

    public function write(array $data): void
    {
        $directory = dirname($this->path);
        if (!is_dir($directory) && !mkdir($directory, 0770, true) && !is_dir($directory)) {
            throw new \RuntimeException('Runtime directory creation failed.');
        }
        $json = json_encode($data, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT) . "\n";
        $temporary = @tempnam($directory, '.write-');
        if ($temporary === false) {
            throw new \RuntimeException('Runtime temporary file creation failed.');
        }
        try {
            if (file_put_contents($temporary, $json, LOCK_EX) === false) {
                throw new \RuntimeException('Runtime file write failed.');
            }
            @chmod($temporary, 0660);
            if (!rename($temporary, $this->path)) {
                throw new \RuntimeException('Runtime file replacement failed.');
            }
        } finally {
            if (is_file($temporary)) {
                @unlink($temporary);
            }
        }
    }

    /** @return array<string, mixed>|null */
    public function read(int $maxBytes = 262_144): ?array
    {
        $size = @filesize($this->path);
        if ($size === false || $size < 1 || $size > $maxBytes || !is_readable($this->path)) {
            return null;
        }
        $contents = @file_get_contents($this->path, false, null, 0, $maxBytes);
        if ($contents === false) {
            return null;
        }
        try {
            $decoded = json_decode($contents, true, 16, JSON_THROW_ON_ERROR);
        } catch (\JsonException) {
            return null;
        }

        return is_array($decoded) && array_is_list($decoded) ? null : $decoded;
    }
}
