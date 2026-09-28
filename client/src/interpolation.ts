import type { NetworkActor, NetworkSnapshot } from './multiplayer';

export interface InterpolatedActor extends NetworkActor {
  position: NetworkActor['position'];
}

export interface InterpolatedWorld {
  tick: number;
  serverTime: number;
  actors: Map<string, InterpolatedActor>;
}

const clamp01 = (value: number): number => Math.max(0, Math.min(1, value));

export const lerp = (a: number, b: number, amount: number): number => a + (b - a) * amount;

/** Convert the browser millisecond clock and server second clock into one render timestamp. */
export const calculateSnapshotRenderTime = (
  serverTime: number,
  receivedAtMs: number,
  nowMs: number,
  interpolationMs: number,
): number => {
  const elapsedMs = receivedAtMs > 0 ? Math.max(0, nowMs - receivedAtMs) : 0;
  return serverTime + elapsedMs / 1000 - Math.max(0, interpolationMs) / 1000;
};

/** Interpolate radians by the shortest path, including the -π/π seam. */
export const interpolateAngle = (a: number, b: number, amount: number): number => {
  const delta = Math.atan2(Math.sin(b - a), Math.cos(b - a));
  return a + delta * clamp01(amount);
};

export const interpolatePosition = (
  a: NetworkActor['position'],
  b: NetworkActor['position'],
  amount: number,
): NetworkActor['position'] => ({
  x: lerp(a.x, b.x, amount),
  y: lerp(a.y, b.y, amount),
  z: lerp(a.z, b.z, amount),
});

const cloneActor = (actor: NetworkActor): InterpolatedActor => ({
  ...actor,
  position: { ...actor.position },
});

const interpolateActor = (before: NetworkActor, after: NetworkActor, amount: number): InterpolatedActor => ({
  ...after,
  position: interpolatePosition(before.position, after.position, amount),
  facing: interpolateAngle(before.facing, after.facing, amount),
});

/**
 * A small server-time snapshot buffer.  It intentionally interpolates between
 * received snapshots instead of extrapolating: a late packet must never make
 * a remote actor run ahead of the authoritative simulation.
 */
export class SnapshotBuffer {
  private readonly snapshots: NetworkSnapshot[] = [];

  public constructor(public readonly capacity = 32) {
    if (!Number.isInteger(capacity) || capacity <= 0) {
      throw new Error('Snapshot buffer capacity must be positive');
    }
  }

  public add(snapshot: NetworkSnapshot): void {
    const existingIndex = this.snapshots.findIndex((candidate) => candidate.tick === snapshot.tick);
    if (existingIndex >= 0) {
      this.snapshots[existingIndex] = snapshot;
      return;
    }

    // WebSocket delivery is ordered, but accepting a late packet makes the
    // buffer deterministic when a test or proxy delivers one out of order.
    let insertionIndex = this.snapshots.length;
    while (insertionIndex > 0 && this.snapshots[insertionIndex - 1].tick > snapshot.tick) {
      insertionIndex -= 1;
    }
    this.snapshots.splice(insertionIndex, 0, snapshot);
    while (this.snapshots.length > this.capacity) this.snapshots.shift();
  }

  public push(snapshot: NetworkSnapshot): void {
    this.add(snapshot);
  }

  public get latest(): NetworkSnapshot | undefined {
    return this.snapshots[this.snapshots.length - 1];
  }

  public get size(): number {
    return this.snapshots.length;
  }

  public clear(): void {
    this.snapshots.length = 0;
  }

  public sample(renderTime: number): InterpolatedWorld {
    const first = this.snapshots[0];
    if (!first) return { tick: 0, serverTime: renderTime, actors: new Map() };

    const last = this.snapshots[this.snapshots.length - 1];
    if (renderTime <= first.serverTime) {
      return {
        tick: first.tick,
        serverTime: first.serverTime,
        actors: new Map(first.actors.map((actor) => [actor.id, cloneActor(actor)])),
      };
    }
    if (renderTime >= last.serverTime) {
      return {
        tick: last.tick,
        serverTime: last.serverTime,
        actors: new Map(last.actors.map((actor) => [actor.id, cloneActor(actor)])),
      };
    }

    let before = first;
    let after = last;
    for (let index = 0; index < this.snapshots.length - 1; index += 1) {
      const candidate = this.snapshots[index];
      const next = this.snapshots[index + 1];
      if (candidate.serverTime <= renderTime && renderTime <= next.serverTime) {
        before = candidate;
        after = next;
        break;
      }
    }

    const span = after.serverTime - before.serverTime;
    const amount = span <= 0 ? 1 : clamp01((renderTime - before.serverTime) / span);
    const actors = new Map<string, InterpolatedActor>();
    const ids = new Set([...before.actors.map((actor) => actor.id), ...after.actors.map((actor) => actor.id)]);
    for (const id of ids) {
      const beforeActor = before.actors.find((actor) => actor.id === id);
      const afterActor = after.actors.find((actor) => actor.id === id);
      if (beforeActor && afterActor) {
        actors.set(id, interpolateActor(beforeActor, afterActor, amount));
      } else if (afterActor) {
        actors.set(id, cloneActor(afterActor));
      } else if (beforeActor) {
        actors.set(id, cloneActor(beforeActor));
      }
    }

    return {
      tick: amount < 1 ? before.tick : after.tick,
      serverTime: lerp(before.serverTime, after.serverTime, amount),
      actors,
    };
  }
}

/** A renderer-independent ground movement step used only for local prediction. */
export const advancePredictedMove = (
  position: NetworkActor['position'],
  target: { x: number; z: number },
  speed: number,
  deltaSeconds: number,
): NetworkActor['position'] => {
  const dx = target.x - position.x;
  const dz = target.z - position.z;
  const distance = Math.hypot(dx, dz);
  if (distance <= 0.0001 || speed <= 0 || deltaSeconds <= 0) return { ...position };

  const step = Math.min(distance, speed * deltaSeconds);
  const ratio = step / distance;
  return {
    ...position,
    x: position.x + dx * ratio,
    z: position.z + dz * ratio,
  };
};
