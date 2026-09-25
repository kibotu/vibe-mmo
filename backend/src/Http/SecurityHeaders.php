<?php declare(strict_types=1);

namespace Mmo\Http;

final class SecurityHeaders
{
    public static function apply(bool $html = false): void
    {
        header('X-Content-Type-Options: nosniff');
        header('X-Frame-Options: DENY');
        header('Referrer-Policy: no-referrer');
        header('Permissions-Policy: camera=(), microphone=(), geolocation=()');
        header('Cross-Origin-Opener-Policy: same-origin');
        header(
            "Content-Security-Policy: default-src 'self'; base-uri 'none'; frame-ancestors 'none'; "
            . "form-action 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
            . "script-src 'self' 'unsafe-inline'; connect-src 'self' ws: wss:",
        );
        header('Cache-Control: no-store, max-age=0');
        header('Pragma: no-cache');
        if ($html) {
            header('Content-Type: text/html; charset=utf-8');
        }
    }
}
