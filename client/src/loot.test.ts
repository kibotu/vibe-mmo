import { describe, expect, it } from 'vitest';
import { PORING_LOOT_TABLE, rollLoot, selectWeighted } from './loot';
import { Random } from './random';

describe('loot selection', () => {
  it('keeps weighted boundaries deterministic', () => {
    const entries = [
      { value: 'first', weight: 0.25 },
      { value: 'second', weight: 0.75 },
    ];
    expect(selectWeighted(entries, 0)).toBe('first');
    expect(selectWeighted(entries, 0.2499)).toBe('first');
    expect(selectWeighted(entries, 0.25)).toBe('second');
    expect(selectWeighted(entries, 1)).toBe('second');
  });

  it('returns the same result for the same seeded random stream', () => {
    const first = rollLoot(new Random(1337));
    const second = rollLoot(new Random(1337));
    expect(first).toBe(second);
    expect(first === null || PORING_LOOT_TABLE.some((entry) => entry.itemId === first)).toBe(true);
  });

  it('keeps the Poring table normalized', () => {
    const total = PORING_LOOT_TABLE.reduce((sum, entry) => sum + entry.weight, 0);
    expect(total).toBeCloseTo(1);
  });
});
