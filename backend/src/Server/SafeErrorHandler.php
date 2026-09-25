<?php declare(strict_types=1);

namespace Mmo\Server;

use Amp\Http\Server\ErrorHandler;
use Amp\Http\Server\Request;
use Amp\Http\Server\Response;

final class SafeErrorHandler implements ErrorHandler
{
    public function handleError(int $status, ?string $reason = null, ?Request $request = null): Response
    {
        $message = $status >= 500 ? 'Internal server error.' : 'Request rejected.';
        $body = json_encode([
            'error' => ['code' => 'http_' . $status, 'message' => $message],
        ], JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);

        return new Response($status, ['content-type' => 'application/json; charset=utf-8', 'cache-control' => 'no-store'], $body);
    }
}
