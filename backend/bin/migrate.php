#!/usr/bin/env php
<?php declare(strict_types=1);

use Mmo\Database\Migrator;
use Mmo\Http\Application;

require dirname(__DIR__) . '/vendor/autoload.php';

try {
    $application = Application::boot();
    $migrations = (new Migrator($application->database, $application->config))
        ->migrate(dirname(__DIR__) . '/database/migrations');
    if ($migrations === []) {
        fwrite(STDOUT, "Database is already up to date.\n");
    } else {
        foreach ($migrations as $migration) {
            fwrite(STDOUT, 'Applied ' . $migration . "\n");
        }
    }
} catch (Throwable $exception) {
    fwrite(STDERR, 'Migration failed: ' . $exception->getMessage() . "\n");
    exit(1);
}
