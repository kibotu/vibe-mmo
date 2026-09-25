<?php declare(strict_types=1);

namespace Mmo\Protocol;

final class IntentParser
{
    private const MAX_SAFE_SEQUENCE = 9_007_199_254_740_991;

    public function __construct(private readonly int $maxMessageBytes = 4096)
    {
    }

    public function parse(string $json): Intent
    {
        if ($json === '' || strlen($json) > $this->maxMessageBytes) {
            throw new ProtocolException('message_too_large', 'The intent message is empty or too large.');
        }

        try {
            $decoded = json_decode($json, false, 4, JSON_THROW_ON_ERROR);
        } catch (\JsonException) {
            throw new ProtocolException('invalid_json', 'The intent must be valid JSON.');
        }
        if (!$decoded instanceof \stdClass) {
            throw new ProtocolException('invalid_message', 'The intent must be a JSON object.');
        }

        $type = $decoded->type ?? null;
        $sequence = $decoded->seq ?? null;
        $name = $decoded->intent ?? null;
        $payload = $decoded->payload ?? null;
        if ($type !== 'intent' || !is_int($sequence) || !is_string($name) || !$payload instanceof \stdClass) {
            throw new ProtocolException('invalid_message', 'The intent envelope is invalid.');
        }
        $envelopeKeys = array_keys(get_object_vars($decoded));
        sort($envelopeKeys);
        if ($envelopeKeys !== ['intent', 'payload', 'seq', 'type']) {
            throw new ProtocolException('invalid_message', 'The intent envelope contains unsupported fields.');
        }
        if ($sequence < 1 || $sequence > self::MAX_SAFE_SEQUENCE) {
            throw new ProtocolException('invalid_sequence', 'The intent sequence is out of range.');
        }
        if (!in_array($name, ['move', 'target', 'attack', 'pickup', 'use_item'], true)) {
            throw new ProtocolException('unknown_intent', 'The intent type is not supported.');
        }

        /** @var array<string, mixed> $payloadArray */
        $payloadArray = json_decode(json_encode($payload, JSON_THROW_ON_ERROR), true, 4, JSON_THROW_ON_ERROR);

        return new Intent($sequence, $name, $payloadArray);
    }
}
