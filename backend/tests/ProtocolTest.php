<?php declare(strict_types=1);

namespace Mmo\Tests;

use Mmo\Protocol\InputSequencer;
use Mmo\Protocol\IntentParser;
use Mmo\Protocol\IntentValidator;
use Mmo\Protocol\ProtocolException;
use Mmo\World\WorldGenerator;
use PHPUnit\Framework\Attributes\CoversClass;
use PHPUnit\Framework\TestCase;

#[CoversClass(IntentParser::class)]
#[CoversClass(IntentValidator::class)]
#[CoversClass(InputSequencer::class)]
final class ProtocolTest extends TestCase
{
    public function testParsesOnlyTheIntentEnvelope(): void
    {
        $intent = (new IntentParser())->parse('{"type":"intent","seq":7,"intent":"move","payload":{"x":32,"z":32}}');

        self::assertSame(7, $intent->sequence);
        self::assertSame('move', $intent->name);
        self::assertSame(['x' => 32, 'z' => 32], $intent->payload);
    }

    public function testRejectsClientAuthoritativeFieldsAndOutOfBoundsCells(): void
    {
        $validator = new IntentValidator();
        $grid = WorldGenerator::generate(1337)->grid;
        $position = (new IntentParser())->parse('{"type":"intent","seq":1,"intent":"move","payload":{"position":{"x":1,"y":2,"z":3}}}');
        $outside = (new IntentParser())->parse('{"type":"intent","seq":2,"intent":"move","payload":{"x":64,"z":2}}');

        try {
            $validator->validate($position, $grid);
            self::fail('Position payload was accepted.');
        } catch (ProtocolException $exception) {
            self::assertSame('invalid_cell', $exception->errorCode);
        }

        $this->expectException(ProtocolException::class);
        $validator->validate($outside, $grid);
    }

    public function testSequenceMustIncreaseMonotonically(): void
    {
        $parser = new IntentParser();
        $sequencer = new InputSequencer();
        $sequencer->accept($parser->parse('{"type":"intent","seq":4,"intent":"pickup","payload":{"itemId":"apple"}}'));
        self::assertSame(4, $sequencer->lastProcessed());

        $this->expectException(ProtocolException::class);
        $sequencer->accept($parser->parse('{"type":"intent","seq":4,"intent":"pickup","payload":{"itemId":"apple"}}'));
    }

    public function testRejectsOversizedAndNonMonotonicInput(): void
    {
        $parser = new IntentParser(128);

        $this->expectException(ProtocolException::class);
        $parser->parse('{"type":"intent","seq":9007199254740992,"intent":"attack","payload":{}}');
    }
}
