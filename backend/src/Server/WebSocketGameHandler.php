<?php declare(strict_types=1);

namespace Mmo\Server;

use Amp\Http\Server\Middleware\Forwarded;
use Amp\Http\Server\Request;
use Amp\Http\Server\Response;
use Amp\Socket\InternetAddress;
use Amp\Websocket\Server\WebsocketClientHandler;
use Amp\Websocket\WebsocketClient;
use Amp\Websocket\WebsocketCloseCode;
use Amp\Websocket\WebsocketClosedException;
use Mmo\Config\Config;
use Mmo\Database\SessionRepository;
use Mmo\Protocol\IntentParser;
use Mmo\Protocol\ProtocolException;
use Psr\Log\LoggerInterface;

final readonly class WebSocketGameHandler implements WebsocketClientHandler
{
    public function __construct(
        private Config $config,
        private SessionRepository $sessions,
        private RoomManager $rooms,
        private LoggerInterface $logger,
    ) {
    }

    public function handleClient(WebsocketClient $client, Request $request, Response $response): void
    {
        $ticket = $request->getUri()->getQuery();
        parse_str($ticket, $query);
        $plaintextTicket = is_string($query['ticket'] ?? null) ? $query['ticket'] : '';
        $identity = $this->sessions->consumeTicket($plaintextTicket);
        if ($identity === null) {
            $this->sendError($client, 'invalid_ticket', 'The WebSocket ticket is invalid or expired.');
            $client->close(WebsocketCloseCode::POLICY_VIOLATION, 'Invalid ticket');

            return;
        }
        unset($plaintextTicket, $query, $ticket);

        try {
            $connection = $this->rooms->connect($identity, $client, $this->clientIp($request), microtime(true));
        } catch (ProtocolException $exception) {
            $this->sendError($client, $exception->errorCode, $exception->getMessage());
            $client->close(WebsocketCloseCode::TRY_AGAIN_LATER, 'Connection unavailable');

            return;
        } catch (\Throwable $exception) {
            $this->logger->error('WebSocket connection setup failed.', ['exceptionClass' => $exception::class]);
            $this->sendError($client, 'internal_error', 'The connection could not be established.');
            $client->close(WebsocketCloseCode::UNEXPECTED_SERVER_ERROR, 'Internal server error');

            return;
        }

        $parser = new IntentParser($this->config->int('server.max_message_bytes', 4096));
        try {
            while (($message = $client->receive()) !== null) {
                $payload = $message->buffer(null, $this->config->int('server.max_message_bytes', 4096) + 1);
                $connection->markInbound(strlen($payload));
                if (!$message->isText()) {
                    $connection->send([
                        'type' => 'error',
                        'code' => 'invalid_message',
                        'message' => 'Only JSON text messages are accepted.',
                    ]);
                    continue;
                }

                try {
                    $intent = $parser->parse($payload);
                    $this->rooms->handleIntent($connection, $intent);
                } catch (ProtocolException $exception) {
                    $connection->send([
                        'type' => 'error',
                        'code' => $exception->errorCode,
                        'message' => $exception->getMessage(),
                    ]);
                }
            }
        } catch (WebsocketClosedException) {
            // A normal peer disconnect is completed by the finally block.
        } catch (\Throwable $exception) {
            $this->logger->warning('WebSocket message handling failed.', ['exceptionClass' => $exception::class]);
        } finally {
            $this->rooms->disconnect($connection->id, microtime(true));
        }
    }

    private function clientIp(Request $request): string
    {
        $forwarded = $request->getAttribute(Forwarded::class);
        if ($forwarded instanceof Forwarded) {
            return $forwarded->getFor()->getAddress();
        }
        $remote = $request->getClient()->getRemoteAddress();

        return $remote instanceof InternetAddress ? $remote->getAddress() : '0.0.0.0';
    }

    private function sendError(WebsocketClient $client, string $code, string $message): void
    {
        try {
            $client->sendText(json_encode([
                'type' => 'error',
                'code' => $code,
                'message' => $message,
            ], JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES));
        } catch (WebsocketClosedException) {
            // The peer may disappear before receiving the policy error.
        }
    }
}
