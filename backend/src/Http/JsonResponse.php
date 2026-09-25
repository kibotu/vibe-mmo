<?php declare(strict_types=1);

namespace Mmo\Http;

final class JsonResponse
{
    /** @param array<string, mixed> $data */
    public static function send(array $data, int $status = 200): never
    {
        SecurityHeaders::apply();
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode($data, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);

        exit;
    }

    public static function error(string $code, string $message, int $status): never
    {
        self::send(['error' => ['code' => $code, 'message' => $message]], $status);
    }
}
