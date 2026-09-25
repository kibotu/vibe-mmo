<?php declare(strict_types=1);

use Mmo\Http\Application;
use Mmo\Http\JsonResponse;
use Mmo\Http\SecurityHeaders;

require dirname(__DIR__, 2) . '/vendor/autoload.php';

try {
    $application = Application::boot();
    SecurityHeaders::apply();
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
        header('Allow: GET');
        JsonResponse::error('method_not_allowed', 'Method not allowed.', 405);
    }
    JsonResponse::send(['rooms' => $application->rooms->publicRooms()]);
} catch (Throwable) {
    JsonResponse::error('service_unavailable', 'Room status is temporarily unavailable.', 503);
}
