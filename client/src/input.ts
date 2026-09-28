export interface InputCallbacks {
  onPrimary(clientX: number, clientY: number): void;
  onHover(clientX: number, clientY: number): void;
  onCameraDrag(deltaX: number, deltaY: number, shift: boolean, control: boolean): void;
  // Positive when the fingers spread apart, which zooms the camera in.
  onPinch(scale: number): void;
  onWheel(deltaY: number, shift: boolean): void;
  onDoubleRight(shift: boolean): void;
  onToggleInventory(): void;
  onAttackShortcut(): void;
  onUseHealShortcut(): void;
  onEscape(): void;
}

interface TouchState {
  startX: number;
  startY: number;
  lastX: number;
  lastY: number;
  moved: boolean;
  startedAt: number;
}

// A touch only becomes a camera drag once it travels this far, so a slightly
// shaky tap still moves or attacks instead of nudging the view.
const TAP_SLOP = 10;
// Holding a finger without moving is not a tap. Without this, a long press to
// look around would fire a move on release.
const TAP_DURATION = 400;

export class InputController {
  private leftDown = false;
  private rightDown = false;
  private rightMoved = false;
  private lastX = 0;
  private lastY = 0;
  private lastPrimaryRepeat = 0;
  private lastRightClickAt = -Infinity;
  private readonly touches = new Map<number, TouchState>();
  private pinchSpan = 0;
  private pinchMidY = 0;

  public constructor(private readonly canvas: HTMLCanvasElement, private readonly callbacks: InputCallbacks) {
    canvas.addEventListener('pointerdown', this.onPointerDown);
    canvas.addEventListener('pointermove', this.onPointerMove);
    canvas.addEventListener('pointerup', this.onPointerUp);
    canvas.addEventListener('pointercancel', this.onPointerCancel);
    canvas.addEventListener('pointerleave', this.onPointerLeave);
    canvas.addEventListener('wheel', this.onWheel, { passive: false });
    canvas.addEventListener('contextmenu', this.onContextMenu);
    // Safari still raises these for pinch-zoom even where touch-action is none.
    canvas.addEventListener('gesturestart', this.preventDefault);
    canvas.addEventListener('gesturechange', this.preventDefault);
    window.addEventListener('keydown', this.onKeyDown);
    window.addEventListener('blur', this.onBlur);
  }

  private preventDefault = (event: Event): void => {
    event.preventDefault();
  };

  private pinchGeometry(): { span: number; midY: number } {
    const [first, second] = [...this.touches.values()];
    if (!first || !second) return { span: 0, midY: 0 };
    return {
      span: Math.hypot(second.lastX - first.lastX, second.lastY - first.lastY),
      midY: (first.lastY + second.lastY) / 2,
    };
  }

  private onPointerDown = (event: PointerEvent): void => {
    // Touch reports button 0, so it has to be handled before the mouse buttons
    // or every tap would be claimed by the mouse path.
    if (event.pointerType === 'touch') {
      event.preventDefault();
      this.touches.set(event.pointerId, {
        startX: event.clientX,
        startY: event.clientY,
        lastX: event.clientX,
        lastY: event.clientY,
        moved: false,
        startedAt: performance.now(),
      });
      this.canvas.setPointerCapture(event.pointerId);
      if (this.touches.size >= 2) {
        const { span, midY } = this.pinchGeometry();
        this.pinchSpan = span;
        this.pinchMidY = midY;
        // A second finger makes this a camera gesture, never a tap, even if both
        // fingers are lifted again without ever having moved.
        for (const touch of this.touches.values()) touch.moved = true;
      }
      return;
    }

    if (event.button === 0) {
      event.preventDefault();
      this.leftDown = true;
      this.lastPrimaryRepeat = performance.now();
      this.callbacks.onPrimary(event.clientX, event.clientY);
      this.canvas.setPointerCapture(event.pointerId);
      return;
    }

    if (event.button === 2) {
      const now = performance.now();
      if (!this.rightMoved && now - this.lastRightClickAt < 500) {
        this.callbacks.onDoubleRight(event.shiftKey);
        this.lastRightClickAt = -Infinity;
      } else {
        this.lastRightClickAt = now;
      }
      this.rightDown = true;
      this.rightMoved = false;
      this.lastX = event.clientX;
      this.lastY = event.clientY;
      this.canvas.setPointerCapture(event.pointerId);
      event.preventDefault();
    }
  };

  private onPointerMove = (event: PointerEvent): void => {
    if (event.pointerType === 'touch') {
      const state = this.touches.get(event.pointerId);
      if (!state) return;
      const deltaX = event.clientX - state.lastX;
      const deltaY = event.clientY - state.lastY;
      state.lastX = event.clientX;
      state.lastY = event.clientY;

      if (this.touches.size >= 2) {
        const { span, midY } = this.pinchGeometry();
        if (span > 0 && this.pinchSpan > 0) this.callbacks.onPinch(span / this.pinchSpan - 1);
        if (this.pinchSpan > 0) this.callbacks.onCameraDrag(0, this.pinchMidY - midY, true, false);
        this.pinchSpan = span;
        this.pinchMidY = midY;
        return;
      }

      if (Math.abs(event.clientX - state.startX) + Math.abs(event.clientY - state.startY) > TAP_SLOP) {
        state.moved = true;
      }
      if (state.moved) this.callbacks.onCameraDrag(deltaX, deltaY, false, false);
      return;
    }

    this.callbacks.onHover(event.clientX, event.clientY);
    if (this.rightDown) {
      const deltaX = event.clientX - this.lastX;
      const deltaY = event.clientY - this.lastY;
      if (Math.abs(deltaX) + Math.abs(deltaY) > 2) {
        this.rightMoved = true;
      }
      this.callbacks.onCameraDrag(deltaX, deltaY, event.shiftKey, event.ctrlKey || event.metaKey);
      this.lastX = event.clientX;
      this.lastY = event.clientY;
    }

    if (this.leftDown && performance.now() - this.lastPrimaryRepeat >= 100) {
      this.lastPrimaryRepeat = performance.now();
      this.callbacks.onPrimary(event.clientX, event.clientY);
    }
  };

  private endTouch(event: PointerEvent, allowTap: boolean): void {
    const state = this.touches.get(event.pointerId);
    this.touches.delete(event.pointerId);
    if (this.touches.size < 2) {
      this.pinchSpan = 0;
      this.pinchMidY = 0;
    }
    if (allowTap && state && !state.moved && performance.now() - state.startedAt <= TAP_DURATION) {
      this.callbacks.onPrimary(event.clientX, event.clientY);
    }
    if (this.canvas.hasPointerCapture(event.pointerId)) {
      this.canvas.releasePointerCapture(event.pointerId);
    }
  }

  private onPointerUp = (event: PointerEvent): void => {
    if (event.pointerType === 'touch') {
      this.endTouch(event, true);
      return;
    }

    if (event.button === 0) {
      this.leftDown = false;
    }
    if (event.button === 2) {
      this.rightDown = false;
      this.rightMoved = false;
    }
    if (this.canvas.hasPointerCapture(event.pointerId)) {
      this.canvas.releasePointerCapture(event.pointerId);
    }
  };

  private onPointerCancel = (event: PointerEvent): void => {
    if (event.pointerType === 'touch') {
      // The gesture was interrupted, so it must not resolve into a tap.
      this.endTouch(event, false);
      return;
    }
    if (event.button === 0) this.leftDown = false;
    if (event.button === 2) {
      this.rightDown = false;
      this.rightMoved = false;
    }
  };

  private onPointerLeave = (): void => {
    if (this.touches.size > 0) return;
    this.leftDown = false;
    this.rightDown = false;
  };

  private onBlur = (): void => {
    this.leftDown = false;
    this.rightDown = false;
    this.rightMoved = false;
    this.touches.clear();
    this.pinchSpan = 0;
    this.pinchMidY = 0;
  };

  private onWheel = (event: WheelEvent): void => {
    event.preventDefault();
    this.callbacks.onWheel(event.deltaY, event.shiftKey);
  };

  private onContextMenu = (event: MouseEvent): void => {
    event.preventDefault();
  };

  private onKeyDown = (event: KeyboardEvent): void => {
    if (event.code === 'F1') {
      event.preventDefault();
      this.callbacks.onAttackShortcut();
      return;
    }
    if (event.code === 'KeyI' || (event.code === 'KeyE' && event.altKey)) {
      event.preventDefault();
      this.callbacks.onToggleInventory();
      return;
    }
    if (event.code === 'KeyH') {
      event.preventDefault();
      this.callbacks.onUseHealShortcut();
      return;
    }
    if (event.code === 'Escape') {
      this.callbacks.onEscape();
    }
  };
}
