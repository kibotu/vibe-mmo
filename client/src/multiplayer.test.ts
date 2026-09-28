import { describe, expect, it } from 'vitest';
import {
  buildWebSocketUrl,
  createIntent,
  parseServerMessage,
  parseSessionResponse,
  MultiplayerClient,
  PendingInputBuffer,
  ProtocolError,
} from './multiplayer';

const session = {
  player: { id: 'player-1', name: 'Aeryn' },
  room: { code: 'forest', name: 'Payon Forest' },
  seed: 1337,
  websocket: { url: 'ws://example.test/ws', ticket: 'ticket-1' },
  config: { tickRate: 20, snapshotRate: 10, interpolationMs: 100 },
};

const snapshot = {
  type: 'snapshot',
  tick: 4,
  serverTime: 1_000,
  lastProcessedInput: 2,
  actors: [{
    id: 'player-1',
    kind: 'player',
    name: 'Aeryn',
    position: { x: 1, y: 0, z: 2 },
    facing: 0,
    state: 'idle',
    hp: 40,
    maxHp: 40,
    targetId: null,
    nextAttackAt: 0,
  }],
  items: [],
  inventory: Array.from({ length: 20 }, () => null),
};

describe('multiplayer protocol', () => {
  it('validates a session and preserves the room configuration', () => {
    expect(parseSessionResponse(session)).toEqual(session);
    expect(() => parseSessionResponse({ ...session, config: { ...session.config, interpolationMs: -1 } }))
      .toThrow(ProtocolError);
  });

  it('validates the welcome sequence watermark used for reconnects', () => {
    const welcome = {
      type: 'welcome',
      connectionId: 'connection-1',
      playerId: 'player-1',
      room: { code: 'forest', name: 'Payon Forest' },
      seed: 1337,
      tickRate: 20,
      snapshotRate: 10,
      interpolationMs: 100,
      lastProcessedInput: 7,
      serverTime: 1_000,
    };
    expect(parseServerMessage(welcome)).toEqual(welcome);
    expect(() => parseServerMessage({ ...welcome, lastProcessedInput: -1 })).toThrow(ProtocolError);
  });

  it('starts new inputs above the welcome watermark after a reload', () => {
    const sent: string[] = [];
    const socket = {
      readyState: 1,
      send(data: string) { sent.push(data); },
      close() {},
      onopen: null as ((event: unknown) => void) | null,
      onmessage: null as ((event: { data: unknown }) => void) | null,
      onerror: null as ((event: unknown) => void) | null,
      onclose: null as ((event: unknown) => void) | null,
    };
    const client = new MultiplayerClient({ session, webSocketFactory: () => socket });
    client.start();
    socket.onopen?.({});
    socket.onmessage?.({
      data: JSON.stringify({
        type: 'welcome',
        connectionId: 'connection-1',
        playerId: 'player-1',
        room: { code: 'forest', name: 'Payon Forest' },
        seed: 1337,
        tickRate: 20,
        snapshotRate: 10,
        interpolationMs: 100,
        lastProcessedInput: 7,
        serverTime: 1_000,
      }),
    });
    client.sendIntent('move', { x: 4, z: 9 });
    expect(JSON.parse(sent[0])).toMatchObject({ seq: 8, intent: 'move' });
    client.disconnect();
  });

  it('validates snapshots and events, including the fixed inventory size', () => {
    expect(parseServerMessage(JSON.stringify(snapshot))).toEqual(snapshot);
    expect(parseServerMessage({ type: 'event', event: { id: 7, kind: 'damage', actorId: 'poring-1', amount: 3 } })).toEqual({
      type: 'event',
      event: { id: 7, kind: 'damage', actorId: 'poring-1', amount: 3, text: null, itemId: null },
    });
    expect(() => parseServerMessage({ ...snapshot, inventory: [] })).toThrow(ProtocolError);
  });

  it('constructs typed intents and rejects malformed payloads', () => {
    expect(createIntent(7, 'move', { x: 4, z: 9 })).toEqual({
      type: 'intent',
      seq: 7,
      intent: 'move',
      payload: { x: 4, z: 9 },
    });
    expect(() => createIntent(7, 'attack', { unexpected: true })).toThrow(ProtocolError);
  });
});

describe('pending input history', () => {
  it('acknowledges in sequence and replays only unprocessed input', () => {
    const pending = new PendingInputBuffer(3);
    pending.add(createIntent(1, 'move', { x: 1, z: 1 }));
    pending.add(createIntent(2, 'target', { targetId: 'poring-1' }));
    pending.add(createIntent(3, 'attack', {}));
    expect(pending.acknowledge(2)).toBe(2);
    expect(pending.all().map((message) => message.seq)).toEqual([3]);
  });

  it('drops the oldest entries when its bound is reached', () => {
    const pending = new PendingInputBuffer(2);
    pending.add(createIntent(1, 'move', { x: 1, z: 1 }));
    pending.add(createIntent(2, 'move', { x: 2, z: 2 }));
    pending.add(createIntent(3, 'move', { x: 3, z: 3 }));
    expect(pending.all().map((message) => message.seq)).toEqual([2, 3]);
  });
});

describe('websocket URL construction', () => {
  it('converts HTTP URLs and replaces stale ticket parameters', () => {
    expect(buildWebSocketUrl('http://example.test/ws?ticket=old', 'new')).toBe('ws://example.test/ws?ticket=new');
    expect(buildWebSocketUrl('wss://example.test/ws', 'a b')).toBe('wss://example.test/ws?ticket=a+b');
  });
});
