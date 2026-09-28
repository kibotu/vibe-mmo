import { describe, expect, it } from 'vitest';
import { WorldGrid } from './world';
import { Random } from './random';

describe('world grid', () => {
  it('keeps the bridge walkable while rejecting open water', () => {
    const grid = new WorldGrid(64, 64, new Random(1337));
    let bridge: { x: number; z: number } | undefined;
    let openWater: { x: number; z: number } | undefined;

    for (let z = 1; z < grid.height - 1 && (!bridge || !openWater); z += 1) {
      for (let x = 1; x < grid.width - 1; x += 1) {
        if (!bridge && grid.isBridge(x, z) && grid.isWater(x, z)) {
          bridge = { x, z };
        }
        if (!openWater && grid.isWater(x, z) && !grid.isBridge(x, z)) {
          openWater = { x, z };
        }
      }
    }

    expect(bridge).toBeDefined();
    expect(openWater).toBeDefined();
    expect(grid.isWalkable(bridge!.x, bridge!.z)).toBe(true);
    expect(grid.isPassable(bridge!.x, bridge!.z)).toBe(true);
    expect(grid.isPassable(openWater!.x, openWater!.z)).toBe(false);
    expect(grid.heightAt(bridge!.x + 0.5, bridge!.z + 0.5)).toBeCloseTo(0.16);
  });
});
