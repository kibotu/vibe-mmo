<?php

declare(strict_types=1);

use Mmo\Config\Config;
use Symfony\Component\Yaml\Yaml;

require dirname(__DIR__) . '/vendor/autoload.php';

$config = Config::fromFile(dirname(__DIR__) . '/secrets.yml');
$output = '/run/mmo-config';
$values = [
    'root_password' => $config->string('database.root_password'),
    'database' => $config->string('database.name'),
    'user' => $config->string('database.username'),
    'password' => $config->string('database.password'),
];

if (!is_dir($output) && !mkdir($output, 0700, true) && !is_dir($output)) {
    throw new RuntimeException("Unable to create {$output}");
}

foreach ($values as $name => $value) {
    if ($value === '') {
        throw new RuntimeException("Database configuration {$name} must not be empty");
    }

    $path = $output . '/' . $name;
    $temporary = $path . '.tmp';
    if (file_put_contents($temporary, $value . "\n", LOCK_EX) === false) {
        throw new RuntimeException("Unable to write {$temporary}");
    }
    chmod($temporary, 0400);
    if (!rename($temporary, $path)) {
        throw new RuntimeException("Unable to publish {$path}");
    }
}

fwrite(STDOUT, Yaml::dump(['configured' => array_keys($values)], 1, 2));
