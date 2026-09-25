<?php declare(strict_types=1);

namespace Mmo\Database;

use Mmo\Config\Config;
use PDO;

final class Migrator
{
    public function __construct(
        private readonly Database $database,
        private readonly Config $config,
    ) {
    }

    /** @return list<string> */
    public function migrate(string $migrationsDirectory): array
    {
        $pdo = $this->database->pdo();
        $pdo->exec(<<<'SQL'
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version VARCHAR(128) NOT NULL PRIMARY KEY,
                checksum CHAR(64) NOT NULL,
                applied_at DATETIME(6) NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            SQL);

        $files = glob(rtrim($migrationsDirectory, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR . '*.sql') ?: [];
        sort($files, SORT_STRING);
        $statement = $pdo->prepare('SELECT checksum FROM schema_migrations WHERE version = :version');
        $record = $pdo->prepare('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES (:version, :checksum, UTC_TIMESTAMP(6))');
        $completed = [];

        foreach ($files as $file) {
            $version = basename($file, '.sql');
            $sql = file_get_contents($file);
            if ($sql === false) {
                throw new \RuntimeException('A migration file could not be read.');
            }
            $checksum = hash('sha256', $sql);
            $statement->execute(['version' => $version]);
            $existing = $statement->fetchColumn();
            if ($existing !== false) {
                if (!hash_equals((string) $existing, $checksum)) {
                    throw new \RuntimeException(sprintf('Migration "%s" has changed after it was applied.', $version));
                }
                continue;
            }

            foreach (self::splitStatements($sql) as $migrationSql) {
                $pdo->exec($migrationSql);
            }
            $record->execute(['version' => $version, 'checksum' => $checksum]);
            $completed[] = $version;
        }

        $this->ensureDefaultRoom($pdo);

        return $completed;
    }

    public function appliedVersions(string $migrationsDirectory): array
    {
        unset($migrationsDirectory);
        $statement = $this->database->pdo()->query('SELECT version FROM schema_migrations ORDER BY version');
        if ($statement === false) {
            return [];
        }

        return array_map('strval', $statement->fetchAll(PDO::FETCH_COLUMN));
    }

    private function ensureDefaultRoom(PDO $pdo): void
    {
        $statement = $pdo->prepare(
            'INSERT IGNORE INTO rooms (code, name, status, max_players, created_at, updated_at)
             VALUES (:code, :name, \'active\', :max_players, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))',
        );
        $statement->execute([
            'code' => $this->config->string('rooms.default_code', 'payon'),
            'name' => $this->config->string('rooms.default_name', 'Payon Forest'),
            'max_players' => $this->config->int('rooms.max_players', 100),
        ]);
    }

    /** @return list<string> */
    private static function splitStatements(string $sql): array
    {
        $lines = preg_split('/\R/', $sql) ?: [];
        $clean = [];
        foreach ($lines as $line) {
            if (preg_match('/^\s*--/', $line) === 1) {
                continue;
            }
            $clean[] = $line;
        }
        $statements = preg_split('/;\s*(?:\R|$)/', implode("\n", $clean)) ?: [];

        return array_values(array_filter(array_map('trim', $statements), static fn (string $statement): bool => $statement !== ''));
    }
}
