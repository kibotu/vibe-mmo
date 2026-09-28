import { describe, expect, it } from 'vitest';
import { SnapshotBuffer, calculateSnapshotRenderTime, interpolateAngle } from './interpolation';
import type { NetworkSnapshot } from './multiplayer';

const makeSnapshot = (tick: number, serverTime: number, x: number, facing: number): NetworkSnapshot => ({
  type: 'snapshot',
  tick,
  serverTime,
  lastProcessedInput: 0,
  actors: [{
    id: 'poring-1',
    kind: 'poring',
    name: 'Poring',
    position: { x, y: 0, z: 0 },
    facing,
    state: 'walk',
    hp: 50,
    maxHp: 50,
    targetId: null,
    nextAttackAt: 0,
  }],
  items: [],
  inventory: Array.from({ length: 20 }, () => null),
});

describe('snapshot interpolation', () => {
  it('takes the shortest path around the facing seam', () => {
    expect(interpolateAngle(3.1, -3.1, 0.5)).toBeCloseTo(Math.PI, 4);
  });

  it('buffers snapshots and samples between them', () => {
    const buffer = new SnapshotBuffer(4);
    buffer.add(makeSnapshot(1, 1_000, 0, 0));
    buffer.add(makeSnapshot(2, 1_100, 10, Math.PI / 2));
    const sample = buffer.sample(1_050);
    expect(sample.actors.get('poring-1')?.position.x).toBe(5);
    expect(sample.actors.get('poring-1')?.facing).toBeCloseTo(Math.PI / 4, 4);
  });

  it('converts server seconds and browser milliseconds into the same clock', () => {
    expect(calculateSnapshotRenderTime(1_000, 500, 550, 100)).toBeCloseTo(999.95, 6);
    expect(calculateSnapshotRenderTime(1_000, 0, 550, 100)).toBeCloseTo(999.9, 6);
  });

  it('does not extrapolate beyond the newest authoritative sample', () => {
    const buffer = new SnapshotBuffer();
    buffer.add(makeSnapshot(1, 1_000, 3, 0));
    expect(buffer.sample(9_999).actors.get('poring-1')?.position.x).toBe(3);
  });
});
