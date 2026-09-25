<?php declare(strict_types=1);

namespace Mmo\Server;

use Amp\Http\HttpStatus;
use Amp\Http\Server\Request;
use Amp\Http\Server\RequestHandler;
use Amp\Http\Server\Response;
use Amp\Websocket\Server\Websocket;

final readonly class WsRoute implements RequestHandler
{
    public function __construct(private Websocket $websocket)
    {
    }

    public function handleRequest(Request $request): Response
    {
        if ($request->getMethod() !== 'GET' || $request->getUri()->getPath() !== '/ws') {
            return new Response(
                HttpStatus::NOT_FOUND,
                ['content-type' => 'application/json; charset=utf-8', 'cache-control' => 'no-store'],
                '{"error":{"code":"not_found","message":"Not found."}}',
            );
        }

        return $this->websocket->handleRequest($request);
    }
}
