/**
 * Browser-side multiplayer protocol and transport.
 *
 * This module deliberately knows nothing about the renderer.  It validates the
 * wire format, owns the small amount of input history needed for reconnects,
 * and exposes a reconnecting WebSocket.  The game can therefore consume the
 * same typed messages in a browser or in a unit test.
 */

export type IntentName = 'move' | 'target' | 'attack' | 'pickup' | 'use_item';

export interface MovePayload {
  x: number;
  z: number;
}

export interface TargetPayload {
  targetId: string;
}

export interface PickupPayload {
  itemId: string;
}

export interface UseItemPayload {
  itemId: string;
}

export type IntentPayload =
  | MovePayload
  | TargetPayload
  | PickupPayload
  | UseItemPayload
  | Record<string, never>;

export type IntentMessage =
  | { type: 'intent'; seq: number; intent: 'move'; payload: MovePayload }
  | { type: 'intent'; seq: number; intent: 'target'; payload: TargetPayload }
  | { type: 'intent'; seq: number; intent: 'attack'; payload: Record<string, never> }
  | { type: 'intent'; seq: number; intent: 'pickup'; payload: PickupPayload }
  | { type: 'intent'; seq: number; intent: 'use_item'; payload: UseItemPayload };

export interface SessionPlayer {
  id: string;
  name: string;
}

export interface SessionRoom {
  code: string;
  name: string;
}

export interface SessionWebSocket {
  url: string;
  ticket: string;
}

export interface SessionConfig {
  tickRate: number;
  snapshotRate: number;
  interpolationMs: number;
}

export interface SessionResponse {
  player: SessionPlayer;
  room: SessionRoom;
  seed: number;
  websocket: SessionWebSocket;
  config: SessionConfig;
}

export type NetworkActorKind = 'player' | 'poring';
export type NetworkActorState = 'idle' | 'walk' | 'attack' | 'hurt' | 'dead';

export interface NetworkPosition {
  x: number;
  y: number;
  z: number;
}

export interface NetworkActor {
  id: string;
  kind: NetworkActorKind;
  name: string;
  position: NetworkPosition;
  facing: number;
  state: NetworkActorState;
  hp: number;
  maxHp: number;
  targetId: string | null;
  nextAttackAt: number;
}

export interface NetworkItem {
  id: string;
  itemId: string;
  quantity: number;
  position: NetworkPosition;
  bobOffset: number;
}

export interface NetworkInventorySlot {
  itemId: string;
  quantity: number;
}

export interface NetworkSnapshot {
  type: 'snapshot';
  tick: number;
  serverTime: number;
  lastProcessedInput: number;
  actors: NetworkActor[];
  items: NetworkItem[];
  inventory: Array<NetworkInventorySlot | null>;
}

export interface NetworkEvent {
  id: string | number;
  kind: 'damage' | 'heal' | 'loot' | 'message' | 'death' | 'respawn';
  actorId?: string | null;
  amount?: number | null;
  text?: string | null;
  itemId?: string | null;
}

export interface NetworkEventMessage {
  type: 'event';
  event: NetworkEvent;
}

export interface NetworkErrorMessage {
  type: 'error';
  code: string;
  message: string;
}

export interface NetworkWelcome {
  type: 'welcome';
  connectionId: string;
  playerId: string;
  room: SessionRoom;
  seed: number;
  tickRate: number;
  snapshotRate: number;
  interpolationMs: number;
  lastProcessedInput: number;
  serverTime: number;
}

export type ServerMessage = NetworkWelcome | NetworkSnapshot | NetworkEventMessage | NetworkErrorMessage;

export type ConnectionState = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'disconnected' | 'error';

export interface ConnectionStatus {
  state: ConnectionState;
  message: string;
  attempt: number;
}

export class ProtocolError extends Error {
  public constructor(message: string) {
    super(message);
    this.name = 'ProtocolError';
  }
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const requiredString = (value: unknown, path: string): string => {
  if (typeof value !== 'string' || value.length === 0) {
    throw new ProtocolError(`${path} must be a non-empty string`);
  }
  return value;
};

const optionalString = (value: unknown, path: string): string | undefined => {
  if (value === undefined || value === null) return undefined;
  if (typeof value !== 'string') throw new ProtocolError(`${path} must be a string`);
  return value;
};

const eventId = (value: unknown, path: string): string | number => {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  throw new ProtocolError(`${path} must be a string or finite number`);
};

const finiteNumber = (value: unknown, path: string): number => {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new ProtocolError(`${path} must be a finite number`);
  }
  return value;
};

const nonNegativeNumber = (value: unknown, path: string): number => {
  const number = finiteNumber(value, path);
  if (number < 0) throw new ProtocolError(`${path} must not be negative`);
  return number;
};

const positiveNumber = (value: unknown, path: string): number => {
  const number = finiteNumber(value, path);
  if (number <= 0) throw new ProtocolError(`${path} must be greater than zero`);
  return number;
};

const positiveInteger = (value: unknown, path: string): number => {
  const number = positiveNumber(value, path);
  if (!Number.isInteger(number)) throw new ProtocolError(`${path} must be an integer`);
  return number;
};

const nonNegativeInteger = (value: unknown, path: string): number => {
  const number = nonNegativeNumber(value, path);
  if (!Number.isInteger(number)) throw new ProtocolError(`${path} must be an integer`);
  return number;
};

const position = (value: unknown, path: string): NetworkPosition => {
  if (!isRecord(value)) throw new ProtocolError(`${path} must be an object`);
  return {
    x: finiteNumber(value.x, `${path}.x`),
    y: finiteNumber(value.y, `${path}.y`),
    z: finiteNumber(value.z, `${path}.z`),
  };
};

const room = (value: unknown, path: string): SessionRoom => {
  if (!isRecord(value)) throw new ProtocolError(`${path} must be an object`);
  return {
    code: requiredString(value.code, `${path}.code`),
    name: requiredString(value.name, `${path}.name`),
  };
};

const actorKind = (value: unknown, path: string): NetworkActorKind => {
  if (value !== 'player' && value !== 'poring') {
    throw new ProtocolError(`${path} has an unsupported kind`);
  }
  return value;
};

const actorState = (value: unknown, path: string): NetworkActorState => {
  if (value !== 'idle' && value !== 'walk' && value !== 'attack' && value !== 'hurt' && value !== 'dead') {
    throw new ProtocolError(`${path} has an unsupported state`);
  }
  return value;
};

const parseActor = (value: unknown, path: string): NetworkActor => {
  if (!isRecord(value)) throw new ProtocolError(`${path} must be an object`);
  return {
    id: requiredString(value.id, `${path}.id`),
    kind: actorKind(value.kind, `${path}.kind`),
    name: requiredString(value.name, `${path}.name`),
    position: position(value.position, `${path}.position`),
    facing: finiteNumber(value.facing, `${path}.facing`),
    state: actorState(value.state, `${path}.state`),
    hp: nonNegativeNumber(value.hp, `${path}.hp`),
    maxHp: positiveNumber(value.maxHp, `${path}.maxHp`),
    targetId: value.targetId === null ? null : requiredString(value.targetId, `${path}.targetId`),
    nextAttackAt: finiteNumber(value.nextAttackAt, `${path}.nextAttackAt`),
  };
};

const parseItem = (value: unknown, path: string): NetworkItem => {
  if (!isRecord(value)) throw new ProtocolError(`${path} must be an object`);
  return {
    id: requiredString(value.id, `${path}.id`),
    itemId: requiredString(value.itemId, `${path}.itemId`),
    quantity: positiveInteger(value.quantity, `${path}.quantity`),
    position: position(value.position, `${path}.position`),
    bobOffset: finiteNumber(value.bobOffset, `${path}.bobOffset`),
  };
};

const parseInventorySlot = (value: unknown, path: string): NetworkInventorySlot | null => {
  if (value === null) return null;
  if (!isRecord(value)) throw new ProtocolError(`${path} must be an object or null`);
  return {
    itemId: requiredString(value.itemId, `${path}.itemId`),
    quantity: positiveInteger(value.quantity, `${path}.quantity`),
  };
};

export const parseSessionResponse = (value: unknown): SessionResponse => {
  if (!isRecord(value)) throw new ProtocolError('session response must be an object');
  if (!isRecord(value.player)) throw new ProtocolError('session.player must be an object');
  if (!isRecord(value.websocket)) throw new ProtocolError('session.websocket must be an object');
  if (!isRecord(value.config)) throw new ProtocolError('session.config must be an object');

  return {
    player: {
      id: requiredString(value.player.id, 'session.player.id'),
      name: requiredString(value.player.name, 'session.player.name'),
    },
    room: room(value.room, 'session.room'),
    seed: finiteNumber(value.seed, 'session.seed'),
    websocket: {
      url: requiredString(value.websocket.url, 'session.websocket.url'),
      ticket: requiredString(value.websocket.ticket, 'session.websocket.ticket'),
    },
    config: {
      tickRate: positiveNumber(value.config.tickRate, 'session.config.tickRate'),
      snapshotRate: positiveNumber(value.config.snapshotRate, 'session.config.snapshotRate'),
      interpolationMs: nonNegativeNumber(value.config.interpolationMs, 'session.config.interpolationMs'),
    },
  };
};

export const parseWelcome = (value: unknown): NetworkWelcome => {
  if (!isRecord(value)) throw new ProtocolError('welcome must be an object');
  return {
    type: 'welcome',
    connectionId: requiredString(value.connectionId, 'welcome.connectionId'),
    playerId: requiredString(value.playerId, 'welcome.playerId'),
    room: room(value.room, 'welcome.room'),
    seed: finiteNumber(value.seed, 'welcome.seed'),
    tickRate: positiveNumber(value.tickRate, 'welcome.tickRate'),
    snapshotRate: positiveNumber(value.snapshotRate, 'welcome.snapshotRate'),
    interpolationMs: nonNegativeNumber(value.interpolationMs, 'welcome.interpolationMs'),
    lastProcessedInput: nonNegativeInteger(value.lastProcessedInput, 'welcome.lastProcessedInput'),
    serverTime: finiteNumber(value.serverTime, 'welcome.serverTime'),
  };
};

export const parseSnapshot = (value: unknown): NetworkSnapshot => {
  if (!isRecord(value)) throw new ProtocolError('snapshot must be an object');
  if (!Array.isArray(value.actors)) throw new ProtocolError('snapshot.actors must be an array');
  if (!Array.isArray(value.items)) throw new ProtocolError('snapshot.items must be an array');
  if (!Array.isArray(value.inventory) || value.inventory.length !== 20) {
    throw new ProtocolError('snapshot.inventory must contain exactly 20 slots');
  }

  return {
    type: 'snapshot',
    tick: nonNegativeInteger(value.tick, 'snapshot.tick'),
    serverTime: finiteNumber(value.serverTime, 'snapshot.serverTime'),
    lastProcessedInput: nonNegativeInteger(value.lastProcessedInput, 'snapshot.lastProcessedInput'),
    actors: value.actors.map((actor, index) => parseActor(actor, `snapshot.actors[${index}]`)),
    items: value.items.map((item, index) => parseItem(item, `snapshot.items[${index}]`)),
    inventory: value.inventory.map((slot, index) => parseInventorySlot(slot, `snapshot.inventory[${index}]`)),
  };
};

const eventKind = (value: unknown, path: string): NetworkEvent['kind'] => {
  if (value !== 'damage' && value !== 'heal' && value !== 'loot' && value !== 'message' && value !== 'death' && value !== 'respawn') {
    throw new ProtocolError(`${path} has an unsupported event kind`);
  }
  return value;
};

export const parseEvent = (value: unknown): NetworkEventMessage => {
  if (!isRecord(value) || !isRecord(value.event)) {
    throw new ProtocolError('event message must contain an event object');
  }
  const source = value.event;
  return {
    type: 'event',
    event: {
      id: eventId(source.id, 'event.id'),
      kind: eventKind(source.kind, 'event.kind'),
      actorId: optionalString(source.actorId, 'event.actorId') ?? null,
      amount: source.amount === undefined || source.amount === null ? null : finiteNumber(source.amount, 'event.amount'),
      text: optionalString(source.text, 'event.text') ?? null,
      itemId: optionalString(source.itemId, 'event.itemId') ?? null,
    },
  };
};

export const parseError = (value: unknown): NetworkErrorMessage => {
  if (!isRecord(value)) throw new ProtocolError('error must be an object');
  return {
    type: 'error',
    code: requiredString(value.code, 'error.code'),
    message: requiredString(value.message, 'error.message'),
  };
};

const decodeJson = (value: unknown): unknown => {
  if (typeof value !== 'string') return value;
  try {
    return JSON.parse(value) as unknown;
  } catch {
    throw new ProtocolError('message is not valid JSON');
  }
};

export const parseServerMessage = (value: unknown): ServerMessage => {
  const decoded = decodeJson(value);
  if (!isRecord(decoded)) throw new ProtocolError('server message must be an object');
  switch (decoded.type) {
    case 'welcome':
      return parseWelcome(decoded);
    case 'snapshot':
      return parseSnapshot(decoded);
    case 'event':
      return parseEvent(decoded);
    case 'error':
      return parseError(decoded);
    default:
      throw new ProtocolError('server message has an unsupported type');
  }
};

const assertPayloadKeys = (value: Record<string, unknown>, allowed: string[], intent: IntentName): void => {
  const allowedSet = new Set(allowed);
  if (Object.keys(value).some((key) => !allowedSet.has(key))) {
    throw new ProtocolError(`${intent} payload contains unsupported fields`);
  }
};

const intentPayload = (intent: IntentName, value: unknown): IntentPayload => {
  if (intent === 'move') {
    if (!isRecord(value)) throw new ProtocolError('move payload must be an object');
    assertPayloadKeys(value, ['x', 'z'], intent);
    return { x: finiteNumber(value.x, 'move.x'), z: finiteNumber(value.z, 'move.z') };
  }
  if (intent === 'target') {
    if (!isRecord(value)) throw new ProtocolError('target payload must be an object');
    assertPayloadKeys(value, ['targetId'], intent);
    return { targetId: requiredString(value.targetId, 'target.targetId') };
  }
  if (intent === 'attack') {
    if (value !== undefined && (!isRecord(value) || Object.keys(value).length !== 0)) {
      throw new ProtocolError('attack payload must be an empty object');
    }
    return {};
  }
  if (intent === 'pickup') {
    if (!isRecord(value)) throw new ProtocolError('pickup payload must be an object');
    assertPayloadKeys(value, ['itemId'], intent);
    return { itemId: requiredString(value.itemId, 'pickup.itemId') };
  }
  if (!isRecord(value)) throw new ProtocolError('use_item payload must be an object');
  assertPayloadKeys(value, ['itemId'], intent);
  const itemId = requiredString(value.itemId, 'use_item.itemId');
  if (itemId !== 'apple') throw new ProtocolError('use_item only supports apples');
  return { itemId };
};

export const createIntent = (seq: number, intent: IntentName, payload: unknown): IntentMessage => {
  if (!Number.isInteger(seq) || seq < 0) throw new ProtocolError('intent sequence must be a non-negative integer');
  const parsedPayload = intentPayload(intent, payload);
  return { type: 'intent', seq, intent, payload: parsedPayload } as IntentMessage;
};

export const validateIntentMessage = (value: unknown): IntentMessage => {
  if (!isRecord(value)) throw new ProtocolError('intent must be an object');
  if (value.type !== 'intent') throw new ProtocolError('intent.type must be "intent"');
  if (value.intent !== 'move' && value.intent !== 'target' && value.intent !== 'attack' && value.intent !== 'pickup' && value.intent !== 'use_item') {
    throw new ProtocolError('intent has an unsupported type');
  }
  if (!isRecord(value.payload)) throw new ProtocolError('intent.payload must be an object');
  return createIntent(nonNegativeInteger(value.seq, 'intent.seq'), value.intent, value.payload);
};

/** A bounded, sequence-sorted input history used for reconnect replay. */
export class PendingInputBuffer {
  private readonly messages = new Map<number, IntentMessage>();

  public constructor(public readonly limit = 128) {
    if (!Number.isInteger(limit) || limit <= 0) throw new ProtocolError('pending input limit must be positive');
  }

  public add(message: IntentMessage): IntentMessage {
    const validated = validateIntentMessage(message);
    this.messages.set(validated.seq, validated);
    while (this.messages.size > this.limit) {
      const oldest = [...this.messages.keys()].sort((a, b) => a - b)[0];
      if (oldest === undefined) break;
      this.messages.delete(oldest);
    }
    return validated;
  }

  public acknowledge(lastProcessedInput: number): number {
    if (!Number.isInteger(lastProcessedInput) || lastProcessedInput < 0) {
      throw new ProtocolError('acknowledgement must be a non-negative integer');
    }
    let removed = 0;
    for (const sequence of [...this.messages.keys()].sort((a, b) => a - b)) {
      if (sequence > lastProcessedInput) break;
      this.messages.delete(sequence);
      removed += 1;
    }
    return removed;
  }

  public all(): IntentMessage[] {
    return [...this.messages.values()].sort((a, b) => a.seq - b.seq);
  }

  public clear(): void {
    this.messages.clear();
  }

  public get size(): number {
    return this.messages.size;
  }
}

export const fetchMultiplayerSession = async (
  endpoint = '/api/session.php',
  fetcher: typeof fetch = fetch,
): Promise<SessionResponse> => {
  const response = await fetcher(endpoint, {
    method: 'GET',
    credentials: 'same-origin',
    cache: 'no-store',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`Session request failed (${response.status})`);
  }
  let body: unknown;
  try {
    body = await response.json() as unknown;
  } catch {
    throw new ProtocolError('session response is not valid JSON');
  }
  return parseSessionResponse(body);
};

const browserBaseUrl = (): string => {
  if (typeof window !== 'undefined' && window.location.href) return window.location.href;
  return 'http://localhost/';
};

export const buildWebSocketUrl = (url: string, ticket: string): string => {
  const parsed = new URL(url, browserBaseUrl());
  if (parsed.protocol === 'http:') parsed.protocol = 'ws:';
  if (parsed.protocol === 'https:') parsed.protocol = 'wss:';
  if (parsed.protocol !== 'ws:' && parsed.protocol !== 'wss:') {
    throw new ProtocolError('websocket URL must use ws:// or wss://');
  }
  parsed.searchParams.set('ticket', ticket);
  return parsed.toString();
};

export interface WebSocketLike {
  readonly readyState: number;
  send(data: string): void;
  close(code?: number, reason?: string): void;
  onopen: ((event: unknown) => void) | null;
  onmessage: ((event: { data: unknown }) => void) | null;
  onerror: ((event: unknown) => void) | null;
  onclose: ((event: unknown) => void) | null;
}

export type WebSocketFactory = (url: string) => WebSocketLike;

export interface MultiplayerClientOptions {
  session: SessionResponse;
  refreshSession?: () => Promise<SessionResponse>;
  webSocketFactory?: WebSocketFactory;
  endpoint?: string;
  reconnectBaseMs?: number;
  reconnectMaxMs?: number;
  maxPendingInputs?: number;
  schedule?: (callback: () => void, delayMs: number) => unknown;
  cancel?: (handle: unknown) => void;
}

export interface MultiplayerClientHandlers {
  onStatus?: (status: ConnectionStatus) => void;
  onWelcome?: (message: NetworkWelcome) => void;
  onSnapshot?: (message: NetworkSnapshot) => void;
  onEvent?: (message: NetworkEventMessage) => void;
  onError?: (message: NetworkErrorMessage) => void;
}

const defaultWebSocketFactory: WebSocketFactory = (url) => new WebSocket(url) as unknown as WebSocketLike;

/** A deliberately small reconnecting transport. It has no renderer or DOM dependency. */
export class MultiplayerClient {
  private session: SessionResponse;
  private readonly refreshSession: () => Promise<SessionResponse>;
  private readonly webSocketFactory: WebSocketFactory;
  private readonly endpoint: string;
  private readonly reconnectBaseMs: number;
  private readonly reconnectMaxMs: number;
  private readonly pending: PendingInputBuffer;
  private readonly schedule: (callback: () => void, delayMs: number) => unknown;
  private readonly cancel: (handle: unknown) => void;
  private handlers: MultiplayerClientHandlers = {};
  private socket: WebSocketLike | null = null;
  private reconnectHandle: unknown = null;
  private reconnectAttempt = 0;
  private nextSequence = 1;
  private started = false;
  private stopped = false;
  private hasOpened = false;
  private lastStatus: ConnectionStatus = { state: 'idle', message: 'Not connected', attempt: 0 };

  public constructor(options: MultiplayerClientOptions) {
    this.session = parseSessionResponse(options.session);
    this.webSocketFactory = options.webSocketFactory ?? defaultWebSocketFactory;
    this.endpoint = options.endpoint ?? '/api/session.php';
    this.refreshSession = options.refreshSession ?? (() => fetchMultiplayerSession(this.endpoint));
    this.reconnectBaseMs = Math.max(50, options.reconnectBaseMs ?? 500);
    this.reconnectMaxMs = Math.max(this.reconnectBaseMs, options.reconnectMaxMs ?? 8_000);
    this.pending = new PendingInputBuffer(options.maxPendingInputs ?? 128);
    this.schedule = options.schedule ?? ((callback, delayMs) => setTimeout(callback, delayMs));
    this.cancel = options.cancel ?? ((handle) => clearTimeout(handle as ReturnType<typeof setTimeout>));
  }

  public get currentSession(): SessionResponse {
    return this.session;
  }

  public get connectionStatus(): ConnectionStatus {
    return this.lastStatus;
  }

  public get pendingInputs(): ReadonlyArray<IntentMessage> {
    return this.pending.all();
  }

  public get pendingInputCount(): number {
    return this.pending.size;
  }

  public setHandlers(handlers: MultiplayerClientHandlers): void {
    this.handlers = { ...handlers };
  }

  public start(): void {
    if (this.started) return;
    this.started = true;
    this.stopped = false;
    void this.openSocket(false);
  }

  public connect(): void {
    this.start();
  }

  public disconnect(): void {
    this.stopped = true;
    this.started = false;
    this.clearReconnect();
    const socket = this.socket;
    this.socket = null;
    if (socket && socket.readyState !== 3) {
      try {
        socket.close(1000, 'client disconnect');
      } catch {
        // A socket can already be closed between the state check and close.
      }
    }
    this.setStatus('disconnected', 'Disconnected', this.reconnectAttempt);
  }

  public close(): void {
    this.disconnect();
  }

  public clearPendingInputs(): void {
    this.pending.clear();
  }

  public acknowledge(lastProcessedInput: number): number {
    return this.pending.acknowledge(lastProcessedInput);
  }

  public sendIntent(intent: IntentName, payload: unknown): IntentMessage;
  public sendIntent(message: IntentMessage): IntentMessage;
  public sendIntent(intentOrMessage: IntentName | IntentMessage, payload?: unknown): IntentMessage {
    const message = typeof intentOrMessage === 'string'
      ? createIntent(this.nextSequence, intentOrMessage, payload)
      : validateIntentMessage(intentOrMessage);
    this.nextSequence = Math.max(this.nextSequence, message.seq + 1);
    this.pending.add(message);
    this.send(message);
    return message;
  }

  private setStatus(state: ConnectionState, message: string, attempt = this.reconnectAttempt): void {
    this.lastStatus = { state, message, attempt };
    this.handlers.onStatus?.(this.lastStatus);
  }

  private clearReconnect(): void {
    if (this.reconnectHandle !== null) {
      this.cancel(this.reconnectHandle);
      this.reconnectHandle = null;
    }
  }

  private send(message: IntentMessage): void {
    const socket = this.socket;
    if (!socket || socket.readyState !== 1) return;
    try {
      socket.send(JSON.stringify(message));
    } catch {
      this.setStatus('reconnecting', 'Connection lost while sending input', this.reconnectAttempt);
      try {
        socket.close();
      } catch {
        // The close event/reconnect path will handle an already-closed socket.
      }
    }
  }

  private replayPending(): void {
    for (const message of this.pending.all()) this.send(message);
  }

  private reconnectDelay(): number {
    const exponent = Math.max(0, this.reconnectAttempt - 1);
    return Math.min(this.reconnectMaxMs, this.reconnectBaseMs * (2 ** exponent));
  }

  private scheduleReconnect(): void {
    if (this.stopped || this.reconnectHandle !== null) return;
    const delay = this.reconnectDelay();
    this.setStatus('reconnecting', `Reconnecting in ${Math.ceil(delay / 1000)}s`, this.reconnectAttempt);
    this.reconnectHandle = this.schedule(() => {
      this.reconnectHandle = null;
      void this.openSocket(true);
    }, delay);
  }

  private async openSocket(refreshTicket: boolean): Promise<void> {
    if (this.stopped || this.socket) return;
    if (refreshTicket) {
      try {
        this.session = parseSessionResponse(await this.refreshSession());
      } catch {
        this.reconnectAttempt += 1;
        this.setStatus('reconnecting', 'Could not refresh the room ticket; retrying', this.reconnectAttempt);
        this.scheduleReconnect();
        return;
      }
    }
    if (this.stopped) return;

    this.setStatus(this.hasOpened ? 'reconnecting' : 'connecting', this.hasOpened ? 'Reconnecting…' : 'Connecting…', this.reconnectAttempt);
    let socket: WebSocketLike;
    try {
      const url = buildWebSocketUrl(this.session.websocket.url, this.session.websocket.ticket);
      socket = this.webSocketFactory(url);
      this.socket = socket;
    } catch {
      this.socket = null;
      this.reconnectAttempt += 1;
      this.setStatus('reconnecting', 'Could not open the room connection', this.reconnectAttempt);
      this.scheduleReconnect();
      return;
    }

    const waitForWelcome = this.hasOpened;
    socket.onopen = () => {
      if (this.socket !== socket || this.stopped) return;
      this.hasOpened = true;
      this.reconnectAttempt = 0;
      this.setStatus('connected', 'Connected to the room', 0);
      if (!waitForWelcome) this.replayPending();
    };
    socket.onmessage = (event) => {
      if (this.socket !== socket || this.stopped) return;
      try {
        const message = parseServerMessage(event.data);
        if (message.type === 'welcome') {
          this.nextSequence = Math.max(this.nextSequence, message.lastProcessedInput + 1);
          this.pending.acknowledge(message.lastProcessedInput);
          this.setStatus('connected', `Connected to ${message.room.name}`, 0);
          if (waitForWelcome) this.replayPending();
          this.handlers.onWelcome?.(message);
        } else if (message.type === 'snapshot') {
          this.nextSequence = Math.max(this.nextSequence, message.lastProcessedInput + 1);
          this.pending.acknowledge(message.lastProcessedInput);
          this.handlers.onSnapshot?.(message);
        } else if (message.type === 'event') {
          this.handlers.onEvent?.(message);
        } else {
          this.handlers.onError?.(message);
        }
      } catch (error) {
        const protocolError = error instanceof ProtocolError
          ? error
          : new ProtocolError(error instanceof Error ? error.message : 'Invalid server message');
        this.handlers.onError?.({ type: 'error', code: 'protocol', message: protocolError.message });
        this.setStatus('error', `Protocol error: ${protocolError.message}`, this.reconnectAttempt);
      }
    };
    socket.onerror = () => {
      if (this.socket !== socket || this.stopped) return;
      this.setStatus('reconnecting', 'The room connection reported an error', this.reconnectAttempt);
      try {
        socket.close();
      } catch {
        // The close handler will schedule the next attempt when available.
      }
    };
    socket.onclose = () => {
      if (this.socket !== socket) return;
      this.socket = null;
      if (this.stopped) return;
      this.reconnectAttempt += 1;
      this.scheduleReconnect();
    };
  }
}
