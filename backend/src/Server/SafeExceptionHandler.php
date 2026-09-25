<?php declare(strict_types=1);

namespace Mmo\Server;

use Amp\Http\Server\ErrorHandler;
use Amp\Http\Server\ExceptionHandler;
use Amp\Http\Server\Request;
use Amp\Http\Server\Response;
use Psr\Log\LoggerInterface;

final readonly class SafeExceptionHandler implements ExceptionHandler
{
    public function __construct(
        private ErrorHandler $errorHandler,
        private LoggerInterface $logger,
    ) {
    }

    public function handleException(Request $request, \Throwable $exception): Response
    {
        // Never log the request URI: WebSocket query strings can contain one-time tickets.
        $this->logger->error('Unhandled HTTP server exception.', ['exceptionClass' => $exception::class]);

        return $this->errorHandler->handleError(500, request: $request);
    }
}
