import { describe, expect, it, vi } from 'vitest';
import { PollingTransport } from './polling';

/**
 * The transport's sleep is injected, so the tests drive the loop with a manual
 * clock instead of real timers. That keeps every case deterministic and stops a
 * polling loop from outliving the test that started it.
 */
const createScheduler = () => {
  let pending: Array<() => void> = [];
  let elapsed = 0;

  return {
    sleep: (ms: number) => new Promise<void>((resolve) => {
      pending.push(() => { elapsed += ms; resolve(); });
    }),
    /** Lets the transport take `steps` iterations, draining timers and microtasks. */
    async run(steps = 3) {
      for (let i = 0; i < steps; i += 1) {
        const due = pending;
        pending = [];
        for (const resolve of due) resolve();
        for (let t = 0; t < 8; t += 1) await Promise.resolve();
      }
    },
    get elapsed() { return elapsed; },
    get pendingTimers() { return pending.length; },
  };
};

type Step = { ok?: boolean; status?: number; body?: unknown; throws?: boolean };

const snapshot = (tick: number, extra: Record<string, unknown> = {}) => ({
  type: 'snapshot',
  tick,
  simulated: true,
  actors: [],
  items: [],
  ...extra,
});

const harness = (steps: Step[], holdMs = 10) => {
  const bodies: string[] = [];
  const requests: Array<Record<string, unknown>> = [];
  let index = 0;

  const fetchImpl = vi.fn(async (_input: unknown, init?: { body?: string }) => {
    const body = init?.body ?? '{}';
    bodies.push(body);
    requests.push(JSON.parse(body) as Record<string, unknown>);
    const step = steps[Math.min(index, steps.length - 1)] ?? { body: snapshot(index) };
    index += 1;
    if (step.throws) throw new Error('network down');
    const status = step.status ?? 200;
    return {
      ok: status >= 200 && status < 300,
      status,
      json: async () => step.body ?? snapshot(index),
    } as unknown as Response;
  });

  const scheduler = createScheduler();
  const transport = new PollingTransport({
    endpoint: '/api/poll.php',
    holdMs,
    fetchImpl: fetchImpl as unknown as typeof fetch,
    sleep: scheduler.sleep,
  });

  return { transport, bodies, requests, scheduler, calls: () => index };
};

const intentsSent = (requests: Array<Record<string, unknown>>) =>
  requests.filter((r) => (r.intents as unknown[]).length > 0);

describe('long-poll transport', () => {
  it('converts a framed intent into a structured poll entry', async () => {
    const { transport, requests, scheduler } = harness([{ body: snapshot(1) }]);
    transport.start();
    await scheduler.run(2);

    transport.send(JSON.stringify({ type: 'intent', seq: 7, intent: 'move', payload: { x: 36, z: 34 } }));
    await scheduler.run(2);

    expect(intentsSent(requests)[0].intents).toEqual([
      { seq: 7, intent: 'move', payload: { x: 36, z: 34 } },
    ]);
    transport.close();
  });

  it('never forwards a frame that is not a well-formed intent', async () => {
    const { transport, requests, scheduler } = harness([{ body: snapshot(1) }]);
    transport.start();
    await scheduler.run(2);
    requests.length = 0;

    transport.send(JSON.stringify({ type: 'welcome', room: 'payon' }));
    transport.send('not json');
    transport.send(JSON.stringify({ type: 'intent', seq: 0, intent: 'move', payload: {} }));
    transport.send(JSON.stringify({ type: 'intent', seq: 'x', intent: 'move', payload: {} }));
    await scheduler.run(3);

    for (const request of requests) expect(request.intents).toEqual([]);
    transport.close();
  });

  it('opens once, on the first successful response', async () => {
    const { transport, scheduler } = harness([{ body: snapshot(1) }]);
    const onopen = vi.fn();
    transport.onopen = onopen;
    transport.start();
    await scheduler.run(4);

    expect(onopen).toHaveBeenCalledTimes(1);
    transport.close();
  });

  it('delivers each snapshot as a message the client can parse', async () => {
    const { transport, scheduler } = harness([{ body: snapshot(1) }, { body: snapshot(2) }]);
    const onmessage = vi.fn();
    transport.onmessage = onmessage;
    transport.start();
    await scheduler.run(4);

    const ticks = onmessage.mock.calls.map((call) => JSON.parse(call[0].data as string).tick);
    expect(ticks[0]).toBe(1);
    expect(ticks).toContain(2);
    transport.close();
  });

  it('reports a failing status as an error and keeps polling', async () => {
    const { transport, scheduler, calls } = harness([{ status: 503 }, { body: snapshot(9) }]);
    const onerror = vi.fn();
    transport.onerror = onerror;
    transport.start();
    await scheduler.run(4);

    expect(onerror).toHaveBeenCalledWith({ status: 503 });
    expect(calls()).toBeGreaterThan(1);
    transport.close();
  });

  it('returns intents to the queue when a request fails, so a move is never silently lost', async () => {
    // The intent is queued before polling starts so the very first request
    // carries it, and that request is the one that fails.
    const { transport, requests, scheduler } = harness([
      { throws: true },
      { body: snapshot(2) },
    ]);
    transport.send(JSON.stringify({ type: 'intent', seq: 11, intent: 'move', payload: { x: 30, z: 30 } }));
    transport.start();
    await scheduler.run(5);

    const sent = intentsSent(requests);
    expect(sent.length).toBeGreaterThanOrEqual(2);
    expect(sent.every((request) => (request.intents as Array<{ seq: number }>)[0].seq === 11)).toBe(true);

    // The retry must be the very next request out. Re-queueing after the backoff
    // sleep instead of before it would still eventually retry, but it would burn
    // an extra empty poll first.
    const indices = requests
      .map((request, index) => ((request.intents as unknown[]).length > 0 ? index : -1))
      .filter((index) => index >= 0);
    expect(indices[1]).toBe(indices[0]! + 1);
    transport.close();
  });

  it('retries intents answered by a response older than one already delivered', async () => {
    const { transport, requests, scheduler } = harness([
      { body: snapshot(10) },
      { body: snapshot(4) },
    ]);
    transport.start();
    await scheduler.run(2);

    transport.send(JSON.stringify({ type: 'intent', seq: 21, intent: 'attack', payload: {} }));
    await scheduler.run(4);

    const sent = intentsSent(requests);
    expect(sent.length).toBeGreaterThanOrEqual(2);
    expect(sent.every((request) => (request.intents as Array<{ seq: number }>)[0].seq === 21)).toBe(true);
    transport.close();
  });

  it('sends the hold the caller configured, in seconds', async () => {
    const { transport, requests, scheduler } = harness([{ body: snapshot(1) }], 250);
    transport.start();
    await scheduler.run(2);

    expect(requests[0].hold).toBeCloseTo(0.25, 5);
    transport.close();
  });

  it('yields between polls instead of spinning when the server is fast', async () => {
    const { transport, scheduler } = harness([{ body: snapshot(1) }]);
    transport.start();
    await scheduler.run(6);

    // Time must have advanced for each iteration, which is what stops a fast
    // server from turning the poll loop into a busy loop.
    expect(scheduler.elapsed).toBeGreaterThan(0);
    transport.close();
  });

  it('stops polling and reports close exactly once', async () => {
    const { transport, scheduler, calls } = harness([{ body: snapshot(1) }]);
    const onclose = vi.fn();
    transport.onclose = onclose;
    transport.start();
    await scheduler.run(3);

    const before = calls();
    transport.close();
    transport.close();
    await scheduler.run(2);

    expect(calls()).toBe(before);
    expect(onclose).toHaveBeenCalledTimes(1);
    expect(transport.readyState).toBe(3);
  });

  it('neither opens nor closes when the caller stops before the first response', async () => {
    const { transport, scheduler } = harness([{ body: snapshot(1) }]);
    const onopen = vi.fn();
    const onclose = vi.fn();
    transport.onopen = onopen;
    transport.onclose = onclose;
    transport.start();
    transport.close();
    await scheduler.run(3);

    expect(onopen).not.toHaveBeenCalled();
    expect(onclose).not.toHaveBeenCalled();
    expect(transport.readyState).toBe(3);
  });

  it('ignores send() after close', async () => {
    const { transport, requests, scheduler } = harness([{ body: snapshot(1) }]);
    transport.start();
    await scheduler.run(2);
    transport.close();
    requests.length = 0;

    transport.send(JSON.stringify({ type: 'intent', seq: 5, intent: 'move', payload: {} }));
    await scheduler.run(2);

    expect(requests).toEqual([]);
  });
});
