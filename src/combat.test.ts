import { describe, expect, it } from 'vitest';
import { calculateDamage } from './combat';

describe('combat damage', () => {
  it('applies the injected variance without dropping below one damage', () => {
    expect(calculateDamage(12, 0, 1)).toBe(12);
    expect(calculateDamage(12, 0, 0.9)).toBe(11);
    expect(calculateDamage(12, 0, 1.1)).toBe(13);
    expect(calculateDamage(2, 20, 1)).toBe(1);
  });
});
