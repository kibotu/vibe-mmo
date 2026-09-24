import { describe, expect, it } from 'vitest';
import { Inventory } from './inventory';

describe('inventory mutations', () => {
  it('rejects invalid quantities without changing state', () => {
    const inventory = new Inventory();
    expect(inventory.add('apple', 1.5)).toBe(1.5);
    expect(inventory.count('apple')).toBe(0);
    expect(inventory.remove('apple', 1.5)).toBe(false);
  });

  it('removes only available items without partial consumption', () => {
    const inventory = new Inventory();
    expect(inventory.add('apple', 2)).toBe(0);
    expect(inventory.remove('apple')).toBe(true);
    expect(inventory.count('apple')).toBe(1);
    expect(inventory.remove('apple', 2)).toBe(false);
    expect(inventory.count('apple')).toBe(1);
  });

  it('leaves a full inventory overflow for the caller', () => {
    const inventory = new Inventory();
    expect(inventory.add('knife', inventory.capacity)).toBe(0);
    expect(inventory.add('apple', 1)).toBe(1);
    expect(inventory.getSlots().filter(Boolean)).toHaveLength(inventory.capacity);
  });
});
