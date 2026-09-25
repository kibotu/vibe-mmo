<?php declare(strict_types=1);

use Mmo\Http\Application;
use Mmo\Http\JsonResponse;

require dirname(__DIR__, 2) . '/vendor/autoload.php';

try {
    $application = Application::boot();
    $application->database->ping();
    JsonResponse::send([
        'status' => 'ok',
        'checks' => ['application' => 'ok', 'database' => 'ok'],
        'time' => gmdate('c'),
    ]);
} catch (Throwable) {
    JsonResponse::error('service_unavailable', 'Service checks failed.', 503);
}
