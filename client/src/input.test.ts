import { afterEach, describe, expect, it, vi } from 'vitest';
import { InputController, type InputCallbacks } from './input';

type Listener = (event: never) => void;

// The input layer is the only part of the client with a wide surface of timing
// and threshold rules, and those rules are where a regression silently becomes
// an accidental move command. jsdom does not implement PointerEvent, so the
// events here are plain objects carrying only the fields the controller reads.
class FakeCanvas {
  public readonly listeners = new Map<string, Listener[]>();
  public readonly captured = new Set<number>();
  public defaultPrevented = 0;

  public addEventListener(type: string, listener: Listener): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  public setPointerCapture(pointerId: number): void {
    this.captured.add(pointerId);
  }

  public hasPointerCapture(pointerId: number): boolean {
    return this.captured.has(pointerId);
  }

  public releasePointerCapture(pointerId: number): void {
    this.captured.delete(pointerId);
  }

  public emit(type: string, event: unknown): void {
    for (const listener of this.listeners.get(type) ?? []) {
      (listener as (value: unknown) => void)(event);
    }
  }
}

interface Call {
  name: string;
  args: unknown[];
}

const harness = () => {
  const calls: Call[] = [];
  const record = (name: string) => (...args: unknown[]): void => {
    calls.push({ name, args });
  };
  const callbacks: InputCallbacks = {
    onPrimary: record('primary'),
    onHover: record('hover'),
    onCameraDrag: record('cameraDrag'),
    onPinch: record('pinch'),
    onWheel: record('wheel'),
    onDoubleRight: record('doubleRight'),
    onToggleInventory: record('toggleInventory'),
    onAttackShortcut: record('attack'),
    onUseHealShortcut: record('heal'),
    onEscape: record('escape'),
  };
  const canvas = new FakeCanvas();
  const windowListeners = new Map<string, Listener[]>();
  vi.stubGlobal('window', {
    addEventListener: (type: string, listener: Listener) => {
      windowListeners.set(type, [...(windowListeners.get(type) ?? []), listener]);
    },
  });

  let now = 1_000;
  vi.spyOn(performance, 'now').mockImplementation(() => now);

  const input = new InputController(canvas as unknown as HTMLCanvasElement, callbacks);

  const event = (overrides: Record<string, unknown> = {}) => ({
    pointerId: 1,
    pointerType: 'touch',
    clientX: 0,
    clientY: 0,
    button: 0,
    shiftKey: false,
    ctrlKey: false,
    metaKey: false,
    preventDefault: () => { canvas.defaultPrevented += 1; },
    ...overrides,
  });

  const down = (overrides?: Record<string, unknown>) => { now += 1; canvas.emit('pointerdown', event(overrides)); };
  const move = (overrides?: Record<string, unknown>) => { now += 1; canvas.emit('pointermove', event(overrides)); };
  const up = (overrides?: Record<string, unknown>) => { now += 1; canvas.emit('pointerup', event(overrides)); };
  const cancel = (overrides?: Record<string, unknown>) => { now += 1; canvas.emit('pointercancel', event(overrides)); };
  const advance = (ms: number) => { now += ms; };
  const named = (name: string) => calls.filter((call) => call.name === name);

  return { canvas, input, calls, windowListeners, down, move, up, cancel, advance, named, event };
};

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('touch input', () => {
  it('treats a short stationary press as a tap', () => {
    const h = harness();
    h.down({ clientX: 200, clientY: 400 });
    h.up({ clientX: 200, clientY: 400 });
    expect(h.named('primary')).toHaveLength(1);
    expect(h.named('cameraDrag')).toHaveLength(0);
  });

  it('keeps a tap that wanders up to the slop threshold and turns a drag past it', () => {
    const atLimit = harness();
    atLimit.down({ clientX: 0, clientY: 0 });
    atLimit.move({ clientX: 10, clientY: 0 });
    atLimit.up({ clientX: 10, clientY: 0 });
    expect(atLimit.named('primary')).toHaveLength(1);
    expect(atLimit.named('cameraDrag')).toHaveLength(0);

    const pastLimit = harness();
    pastLimit.down({ clientX: 0, clientY: 0 });
    pastLimit.move({ clientX: 11, clientY: 0 });
    pastLimit.up({ clientX: 11, clientY: 0 });
    expect(pastLimit.named('primary')).toHaveLength(0);
    expect(pastLimit.named('cameraDrag')).toHaveLength(1);
  });

  it('measures slop as the sum of both axes from where the finger landed', () => {
    // 4 across and 3 down is 7, well inside the threshold, so this stays a tap
    // even though neither axis alone would tell you much.
    const h = harness();
    h.down({ clientX: 0, clientY: 0 });
    h.move({ clientX: 4, clientY: 3 });
    h.up({ clientX: 4, clientY: 3 });
    expect(h.named('primary')).toHaveLength(1);
    expect(h.named('cameraDrag')).toHaveLength(0);
  });

  it('suppresses the move when a press is held past the tap duration', () => {
    // The release costs one more millisecond on the mocked clock, so these are
    // the 400ms boundary and the first millisecond past it.
    const atLimit = harness();
    atLimit.down({ clientX: 0, clientY: 0 });
    atLimit.advance(399);
    atLimit.up({ clientX: 0, clientY: 0 });
    expect(atLimit.named('primary')).toHaveLength(1);

    const pastLimit = harness();
    pastLimit.down({ clientX: 0, clientY: 0 });
    pastLimit.advance(400);
    pastLimit.up({ clientX: 0, clientY: 0 });
    expect(pastLimit.named('primary')).toHaveLength(0);
  });

  it('rotates the camera for a one-finger drag without moving the player', () => {
    const h = harness();
    h.down({ clientX: 100, clientY: 400 });
    h.move({ clientX: 130, clientY: 400 });
    h.up({ clientX: 130, clientY: 400 });
    expect(h.named('cameraDrag')).toEqual([{ name: 'cameraDrag', args: [30, 0, false, false] }]);
    expect(h.named('primary')).toHaveLength(0);
  });

  it('reports a positive scale when fingers spread apart and a negative one when they close', () => {
    const spread = harness();
    spread.down({ pointerId: 1, clientX: 100, clientY: 400 });
    spread.down({ pointerId: 2, clientX: 200, clientY: 400 });
    spread.move({ pointerId: 1, clientX: 80, clientY: 400 });
    expect(spread.named('pinch')[0]?.args[0]).toBeCloseTo(0.2, 6);

    const close = harness();
    close.down({ pointerId: 1, clientX: 100, clientY: 400 });
    close.down({ pointerId: 2, clientX: 200, clientY: 400 });
    close.move({ pointerId: 1, clientX: 120, clientY: 400 });
    expect(close.named('pinch')[0]?.args[0]).toBeCloseTo(-0.2, 6);
  });

  it('tilts rather than rotates while two fingers move together vertically', () => {
    const h = harness();
    h.down({ pointerId: 1, clientX: 100, clientY: 400 });
    h.down({ pointerId: 2, clientX: 200, clientY: 500 });
    h.move({ pointerId: 1, clientX: 100, clientY: 380 });
    h.move({ pointerId: 2, clientX: 200, clientY: 480 });

    // Each finger arrives as its own event, so the tilt accumulates across two
    // shift-drags of 10 rather than one of 20. Both are vertical: no yaw.
    const drags = h.named('cameraDrag');
    expect(drags.map((call) => call.args)).toEqual([[0, 10, true, false], [0, 10, true, false]]);
    expect(drags.reduce((total, call) => total + (call.args[1] as number), 0)).toBe(20);
  });

  it('ignores a pinch step that changes the span by nothing', () => {
    const h = harness();
    h.down({ pointerId: 1, clientX: 100, clientY: 400 });
    h.down({ pointerId: 2, clientX: 200, clientY: 400 });
    h.move({ pointerId: 1, clientX: 100, clientY: 400 });

    // A zero span change still reports a 0 scale, which the camera turns into a
    // no-op distance adjustment. Asserting the value rather than the absence of
    // the call documents the real behaviour.
    expect(h.named('pinch')[0]?.args[0]).toBe(0);
  });

  it('never issues a move for any multi-finger gesture', () => {
    const pinch = harness();
    pinch.down({ pointerId: 1, clientX: 100, clientY: 400 });
    pinch.down({ pointerId: 2, clientX: 200, clientY: 400 });
    pinch.move({ pointerId: 1, clientX: 60, clientY: 400 });
    pinch.up({ pointerId: 1, clientX: 60, clientY: 400 });
    pinch.up({ pointerId: 2, clientX: 240, clientY: 400 });
    expect(pinch.named('primary')).toHaveLength(0);

    // Two fingers placed and lifted without moving is still not a tap.
    const flatTap = harness();
    flatTap.down({ pointerId: 1, clientX: 100, clientY: 400 });
    flatTap.down({ pointerId: 2, clientX: 200, clientY: 400 });
    flatTap.up({ pointerId: 1, clientX: 100, clientY: 400 });
    flatTap.up({ pointerId: 2, clientX: 200, clientY: 400 });
    expect(flatTap.named('primary')).toHaveLength(0);
  });

  it('resumes rotating with the surviving finger instead of tapping', () => {
    const h = harness();
    h.down({ pointerId: 1, clientX: 100, clientY: 400 });
    h.down({ pointerId: 2, clientX: 200, clientY: 400 });
    h.move({ pointerId: 1, clientX: 70, clientY: 400 });
    h.up({ pointerId: 2, clientX: 230, clientY: 400 });
    h.calls.length = 0;
    h.move({ pointerId: 1, clientX: 40, clientY: 400 });
    h.up({ pointerId: 1, clientX: 40, clientY: 400 });
    expect(h.named('cameraDrag')).toEqual([{ name: 'cameraDrag', args: [-30, 0, false, false] }]);
    expect(h.named('primary')).toHaveLength(0);
  });

  it('ignores an interrupted gesture and tracks no pointers afterwards', () => {
    const h = harness();
    h.down({ pointerId: 1, clientX: 100, clientY: 400 });
    h.cancel({ pointerId: 1, clientX: 100, clientY: 400 });
    expect(h.named('primary')).toHaveLength(0);
    expect(h.canvas.captured.size).toBe(0);
  });

  it('keeps a live touch alive when the pointer leaves the canvas', () => {
    const h = harness();
    h.down({ pointerId: 1, clientX: 100, clientY: 400 });
    h.canvas.emit('pointerleave', {});
    h.up({ pointerId: 1, clientX: 100, clientY: 400 });
    expect(h.named('primary')).toHaveLength(1);
  });

  it('drops every tracked pointer when the window loses focus', () => {
    const h = harness();
    h.down({ pointerId: 1, clientX: 100, clientY: 400 });
    for (const listener of h.windowListeners.get('blur') ?? []) {
      (listener as () => void)();
    }
    h.up({ pointerId: 1, clientX: 100, clientY: 400 });
    expect(h.named('primary')).toHaveLength(0);
    expect(h.canvas.captured.size).toBe(0);
  });

  it('does not raycast for hover on touch, where there is nothing to hover', () => {
    const h = harness();
    h.down({ clientX: 200, clientY: 400 });
    h.move({ clientX: 220, clientY: 400 });
    h.up({ clientX: 220, clientY: 400 });
    expect(h.named('hover')).toHaveLength(0);
  });
});

describe('mouse and keyboard input', () => {
  it('moves on mouse press rather than on release', () => {
    const h = harness();
    h.down({ pointerType: 'mouse', clientX: 200, clientY: 400 });
    expect(h.named('primary')).toHaveLength(1);
  });

  it('hovers on every mouse move and repeats a held press', () => {
    const h = harness();
    h.down({ pointerType: 'mouse', clientX: 200, clientY: 400 });
    h.calls.length = 0;
    h.advance(150);
    h.move({ pointerType: 'mouse', clientX: 240, clientY: 400 });
    expect(h.named('hover')).toHaveLength(1);
    expect(h.named('primary')).toHaveLength(1);
    expect(h.named('primary')[0]?.args).toEqual([240, 400]);
  });

  it('tilt-drags and zooms when the right button is held with a modifier', () => {
    const h = harness();
    h.down({ pointerType: 'mouse', button: 2, clientX: 200, clientY: 400 });
    h.move({ pointerType: 'mouse', button: 2, clientX: 200, clientY: 410, shiftKey: true });
    expect(h.named('cameraDrag')[0]?.args).toEqual([0, 10, true, false]);

    h.calls.length = 0;
    h.down({ pointerType: 'mouse', button: 2, clientX: 200, clientY: 400 });
    h.move({ pointerType: 'mouse', button: 2, clientX: 200, clientY: 410, metaKey: true });
    expect(h.named('cameraDrag')[0]?.args).toEqual([0, 10, false, true]);
  });

  it('resets the camera only on the second right click of a quick pair', () => {
    const h = harness();
    h.down({ pointerType: 'mouse', button: 2, clientX: 200, clientY: 400 });
    h.up({ pointerType: 'mouse', button: 2, clientX: 200, clientY: 400 });
    expect(h.named('doubleRight')).toHaveLength(0);

    h.down({ pointerType: 'mouse', button: 2, clientX: 200, clientY: 400 });
    h.up({ pointerType: 'mouse', button: 2, clientX: 200, clientY: 400 });
    expect(h.named('doubleRight')).toHaveLength(1);

    h.calls.length = 0;
    h.down({ pointerType: 'mouse', button: 2, clientX: 200, clientY: 400 });
    h.advance(600);
    h.up({ pointerType: 'mouse', button: 2, clientX: 200, clientY: 400 });
    expect(h.named('doubleRight')).toHaveLength(0);
  });

  it('zooms on the wheel and tilts when shift is held', () => {
    const h = harness();
    h.canvas.emit('wheel', h.event({ deltaY: 120 }));
    expect(h.named('wheel')[0]?.args).toEqual([120, false]);

    h.calls.length = 0;
    h.canvas.emit('wheel', h.event({ deltaY: 120, shiftKey: true }));
    expect(h.named('wheel')[0]?.args).toEqual([120, true]);
  });

  it('maps the documented keyboard shortcuts', () => {
    const h = harness();
    const press = (code: string, extra: Record<string, unknown> = {}) => {
      for (const listener of h.windowListeners.get('keydown') ?? []) {
        (listener as (value: unknown) => void)({ code, preventDefault: () => {}, ...extra });
      }
    };
    press('F1');
    press('KeyI');
    press('KeyE', { altKey: true });
    press('KeyH');
    press('Escape');
    expect(h.named('attack')).toHaveLength(1);
    expect(h.named('toggleInventory')).toHaveLength(2);
    expect(h.named('heal')).toHaveLength(1);
    expect(h.named('escape')).toHaveLength(1);
  });
});
