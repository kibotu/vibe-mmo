<?php declare(strict_types=1);

namespace Mmo\Database;

use Mmo\Config\Config;
use PDO;

final class Database
{
    /** @var list<string> Unprefixed table names owned by this application. */
    public const TABLES = ['schema_migrations', 'rooms', 'players', 'player_sessions'];

    private ?PDO $pdo = null;

    public function __construct(private readonly Config $config)
    {
    }

    /**
     * Resolves an unprefixed table name to its configured, prefixed identifier.
     *
     * Passing an unknown name is a programming error rather than a missing
     * table, so it throws instead of building an identifier that would only
     * fail later as a MySQL error.
     */
    public function table(string $name): string
    {
        if (!in_array($name, self::TABLES, true)) {
            throw new \InvalidArgumentException(sprintf('Unknown table "%s".', $name));
        }

        return $this->config->string('database.prefix') . $name;
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
