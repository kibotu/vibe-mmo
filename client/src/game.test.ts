import { describe, expect, it } from 'vitest';
import { findPath } from './pathfinding';
import { Random } from './random';
import { Inventory } from './inventory';
import { CLASSIC_2003_PROFILE, ROCamera } from './camera';
import type { GridAccess } from './types';

class TestGrid implements GridAccess {
  public readonly width = 8;
  public readonly height = 8;

  public isWalkable(x: number, z: number): boolean {
    return x >= 0 && z >= 0 && x < this.width && z < this.height && !(x === 3 && z === 3);
  }
}

describe('tracer bullet systems', () => {
  it('keeps the classic camera profile within its documented bounds', () => {
    const camera = new ROCamera(4 / 3);
    expect(CLASSIC_2003_PROFILE.fov).toBe(15);
    expect(CLASSIC_2003_PROFILE.defaultDistance).toBe(50);
    camera.adjustDistance(-1000);
    expect(camera.distance).toBe(CLASSIC_2003_PROFILE.defaultDistance);
    camera.adjustDistance(1000);
    camera.update(16, { x: 0, y: 0, z: 0 });
    expect(camera.distance).toBeLessThanOrEqual(CLASSIC_2003_PROFILE.maxDistance);
  });

  it('returns no path for an unreachable goal and no work for the current cell', () => {
    const grid = new TestGrid();
    expect(findPath(grid, { x: 2, z: 2 }, { x: 3, z: 3 })).toEqual([]);
    expect(findPath(grid, { x: 2, z: 2 }, { x: 2, z: 2 })).toEqual([]);
  });

  it('does not cut diagonal corners through blocked cells', () => {
    const path = findPath(new TestGrid(), { x: 2, z: 2 }, { x: 4, z: 4 });
    expect(path.length).toBeGreaterThan(0);
    expect(path.some((cell) => cell.x === 3 && cell.z === 3)).toBe(false);
  });

  it('stacks materials and leaves overflow available to the caller', () => {
    const inventory = new Inventory();
    expect(inventory.add('jellopy', 99)).toBe(0);
    expect(inventory.add('jellopy', 2)).toBe(0);
    expect(inventory.count('jellopy')).toBe(101);
  });

  it('uses deterministic random values for a fixed seed', () => {
    const first = new Random(1337);
    const second = new Random(1337);
    expect([first.next(), first.next(), first.next()]).toEqual([second.next(), second.next(), second.next()]);
  });
});
