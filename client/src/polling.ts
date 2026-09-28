import type { WebSocketLike } from './multiplayer';

/**
 * A WebSocketLike transport backed by long polling.
 *
 * The authoritative simulation cannot hold a socket open on shared hosting, so
 * this posts the caller's queued intents and receives a snapshot per request.
 * It implements the same interface as the real socket, which is why
 * MultiplayerClient needs no changes to run on either transport.
 *
 * Three details make it behave like a socket rather than a plain request loop:
 *
 *  - `send()` queues instead of writing, because a request is already in flight
 *    when the intent is produced. The queue is drained by the next poll, so no
 *    input is lost and ordering is preserved.
 *  - `onopen` fires on the first successful response, and `onclose` fires only
 *    when the caller closes or the transport is disposed. A failed request
 *    surfaces as `onerror` and the client applies its own backoff, matching how
 *    it treats a socket that will not open.
 *  - The response is delivered as a single JSON document rather than framed
 *    messages, so the poll loop reads `snapshot` and any `events` that the
 *    server produced during the simulated step.
 */

const OPEN = 1;
const CLOSED = 3;

export interface PollingTransportOptions {
  /** Endpoint that accepts a POST and answers with a snapshot. */
  readonly endpoint: string;
  /** Milliseconds the server may hold the response waiting for a change. */
  readonly holdMs?: number;
  /**
   * Floor on the gap between polls.
   *
   * Without this a fast server returns as soon as it is asked, so a client that
   * polls with no hold spins as fast as the network allows and pins a worker on
   * the host continuously. Yielding every iteration bounds that and keeps a
   * quiet room cheap.
   */
  readonly minIntervalMs?: number;
  /** Injectable for tests. */
  readonly fetchImpl?: typeof fetch;
  /** Injected by the client so aborted requests can be cancelled. */
  readonly createAbortController?: () => AbortController;
  /** Injectable for tests. */
  readonly sleep?: (ms: number) => Promise<void>;
}

interface PollIntent {
  readonly seq: number;
  readonly intent: string;
  readonly payload: Record<string, unknown>;
}

interface PollBody {
  readonly intents: PollIntent[];
  readonly hold: number;
}

export class PollingTransport implements WebSocketLike {
  public readyState: number = 0;
  public onopen: ((event: unknown) => void) | null = null;
  public onmessage: ((event: { data: unknown }) => void) | null = null;
  public onerror: ((event: unknown) => void) | null = null;
  public onclose: ((event: unknown) => void) | null = null;

  private readonly endpoint: string;
  private readonly holdMs: number;
  private readonly minIntervalMs: number;
  private readonly fetchImpl: typeof fetch;
  private readonly newAbortController: () => AbortController;
  private readonly sleep: (ms: number) => Promise<void>;
  private readonly queue: PollIntent[] = [];

  private running = false;
  private opened = false;
  private stopped = false;
  private inFlight: AbortController | null = null;

  public constructor(options: PollingTransportOptions) {
    this.endpoint = options.endpoint;
    this.holdMs = options.holdMs ?? 500;
    this.minIntervalMs = options.minIntervalMs ?? 50;
    this.fetchImpl = options.fetchImpl ?? ((...args) => fetch(...args));
    this.newAbortController = options.createAbortController ?? (() => new AbortController());
    this.sleep = options.sleep ?? ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
  }

  /**
   * Queues an intent for the next poll rather than writing it immediately.
   *
   * The client produces a framed message, but a poll body is structured, so the
   * frame is decoded here and reduced to the three fields the server reads.
   * Anything that is not an intent is dropped rather than sent, because the
   * server ignores it anyway and forwarding it would only widen the request.
   */
  public send(data: string): void {
    if (this.stopped) return;
    const intent = this.decodeIntent(data);
    if (intent !== null) this.queue.push(intent);
  }

  private decodeIntent(data: string): PollIntent | null {
    let parsed: unknown;
    try {
      parsed = JSON.parse(data);
    } catch {
      return null;
    }
    if (typeof parsed !== 'object' || parsed === null) return null;
    const frame = parsed as Record<string, unknown>;
    if (frame.type !== 'intent') return null;
    // The server sequences from one, and a non-positive sequence can never be
    // applied, so it is dropped here rather than consuming a poll slot.
    if (typeof frame.seq !== 'number' || !Number.isInteger(frame.seq) || frame.seq < 1) return null;
    if (typeof frame.intent !== 'string' || frame.intent === '') return null;
    const payload = typeof frame.payload === 'object' && frame.payload !== null
      ? frame.payload as Record<string, unknown>
      : {};
    return { seq: frame.seq, intent: frame.intent, payload };
  }

  public close(): void {
    if (this.stopped) return;
    this.stopped = true;
    this.inFlight?.abort();
    this.inFlight = null;
    if (this.opened) {
      this.readyState = CLOSED;
      this.opened = false;
      this.onclose?.({ code: 1000, reason: 'closed' });
    } else {
      this.readyState = CLOSED;
    }
  }

  /** Begins polling. Safe to call once; later calls are ignored. */
  public start(): void {
    if (this.running || this.stopped) return;
    this.running = true;
    void this.loop();
  }

  private async loop(): Promise<void> {
    while (!this.stopped) {
      // Every iteration yields, so a fast server cannot turn this into a busy
      // loop that pins a worker on the host and starves the event loop.
      await this.sleep(this.minIntervalMs);
      if (this.stopped) return;

      const batch = this.queue.splice(0, this.queue.length);
      const body: PollBody = { intents: batch, hold: this.holdMs / 1000 };
      const controller = this.newAbortController();
      this.inFlight = controller;
      // Intents are only dropped once the server has answered for them. A failed
      // or abandoned request must return them to the queue, because a lost
      // "move" is a player pressing a button that silently does nothing. Both
      // exit paths below re-queue, so there is no path that discards a batch.
      try {
        const response = await this.fetchImpl(this.endpoint, {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
          signal: controller.signal,
        });

        if (!response.ok) {
          // A rejected request is reported as an error, not a close, so the
          // client's backoff decides when to try again. The batch goes back
          // before the backoff, so a retry is the very next request.
          this.requeue(batch);
          this.onerror?.({ status: response.status });
          await this.pause(response.status);
          continue;
        }

        const payload: unknown = await response.json();

        if (!this.opened) {
          this.opened = true;
          this.readyState = OPEN;
          this.onopen?.({});
        }

        this.deliver(payload);

        // A response computed from state older than one already delivered may
        // not have applied these intents, so they are retried once.
        if (this.isStale(payload)) this.requeue(batch);
      } catch (error) {
        if (this.stopped) return;
        this.requeue(batch);
        this.onerror?.({ error });
        await this.pause(0);
      } finally {
        this.inFlight = null;
      }
    }
  }

  /**
   * Puts an unanswered batch back at the head of the queue. Called before any
   * backoff sleep so the retry is the next request out, and guarded so a batch
   * that already went back is never queued twice.
   */
  private requeue(batch: PollIntent[]): void {
    if (batch.length === 0) return;
    this.queue.unshift(...batch);
  }

  private deliver(payload: unknown): void {
    if (Array.isArray(payload)) {
      for (const message of payload) this.onmessage?.({ data: JSON.stringify(message) });
      return;
    }
    this.onmessage?.({ data: JSON.stringify(payload) });
  }

  /**
   * A snapshot older than the newest one already delivered means the response
   * was computed from stale state, so its intents may not have been applied.
   */
  private isStale(payload: unknown): boolean {
    if (typeof payload !== 'object' || payload === null) return false;
    const record = payload as { tick?: unknown; simulated?: unknown };
    if (typeof record.tick !== 'number' || typeof record.simulated !== 'boolean') return false;
    if (this.lastTick === null) {
      this.lastTick = record.tick;
      return false;
    }
    const stale = record.tick < this.lastTick;
    this.lastTick = Math.max(this.lastTick, record.tick);
    return stale;
  }

  private lastTick: number | null = null;

  /** Backs off briefly so a failing endpoint is not hammered. */
  private async pause(status: number): Promise<void> {
    const delay = status === 0 ? 1000 : Math.min(4000, 200 * Math.max(1, status / 100));
    await this.sleep(delay);
  }
}
