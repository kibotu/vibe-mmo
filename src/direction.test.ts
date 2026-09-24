import { describe, expect, it } from 'vitest';
import { spriteDirection } from './direction';

describe('sprite direction selection', () => {
  it('maps north to the back view and south to the front view', () => {
    expect(spriteDirection(0, 0)).toBe(4);
    expect(spriteDirection(Math.PI, 0)).toBe(0);
  });

  it('keeps all eight headings stable while the camera rotates', () => {
    const headings = Array.from({ length: 8 }, (_, index) => index * Math.PI / 4);
    expect(headings.map((heading) => spriteDirection(heading, 0))).toEqual([4, 5, 6, 7, 0, 1, 2, 3]);
    expect(spriteDirection(0, 90)).toBe(2);
  });
});
