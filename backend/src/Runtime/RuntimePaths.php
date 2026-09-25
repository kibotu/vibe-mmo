<?php declare(strict_types=1);

namespace Mmo\Runtime;

use Mmo\Config\Config;
use Mmo\Support\BackendPaths;

final readonly class RuntimePaths
{
    public string $directory;
    public string $statusFile;
    public string $commandFile;

    public function __construct(Config $config)
    {
        $root = BackendPaths::root();
        $configured = $config->string('runtime.directory', $root . DIRECTORY_SEPARATOR . 'runtime');
        $this->directory = str_starts_with($configured, DIRECTORY_SEPARATOR)
            ? rtrim($configured, DIRECTORY_SEPARATOR)
            : $root . DIRECTORY_SEPARATOR . ltrim($configured, DIRECTORY_SEPARATOR);
        $public = $root . DIRECTORY_SEPARATOR . 'public' . DIRECTORY_SEPARATOR;
        if (str_starts_with($this->directory . DIRECTORY_SEPARATOR, $public)) {
            throw new \RuntimeException('The runtime directory must be outside backend/public.');
        }
        $this->statusFile = $this->directory . DIRECTORY_SEPARATOR . 'server-status.json';
        $this->commandFile = $this->directory . DIRECTORY_SEPARATOR . 'server-command.json';
    }
}
