<?php declare(strict_types=1);

namespace Mmo\Database;

use Mmo\Config\Config;
use PDO;

final class Database
{
    private ?PDO $pdo = null;

    public function __construct(private readonly Config $config)
    {
    }

    public function pdo(): PDO
    {
        if ($this->pdo instanceof PDO) {
            return $this->pdo;
        }

        $host = $this->config->string('database.host');
        $port = $this->config->int('database.port');
        $database = $this->config->string('database.name');
        $dsn = sprintf('mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4', $host, $port, $database);

        $this->pdo = new PDO(
            $dsn,
            $this->config->string('database.username'),
            $this->config->string('database.password'),
            [
                PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                PDO::ATTR_EMULATE_PREPARES => false,
                PDO::ATTR_STRINGIFY_FETCHES => false,
                \Pdo\Mysql::ATTR_MULTI_STATEMENTS => false,
            ],
        );

        return $this->pdo;
    }

    public function ping(): void
    {
        $this->pdo()->query('SELECT 1')->fetchColumn();
    }
}
