export interface InputCallbacks {
  onPrimary(clientX: number, clientY: number): void;
  onHover(clientX: number, clientY: number): void;
  onCameraDrag(deltaX: number, deltaY: number, shift: boolean, control: boolean): void;
  onWheel(deltaY: number, shift: boolean): void;
  onDoubleRight(shift: boolean): void;
  onToggleInventory(): void;
  onAttackShortcut(): void;
  onUseHealShortcut(): void;
  onEscape(): void;
}

export class InputController {
  private leftDown = false;
  private rightDown = false;
  private rightMoved = false;
  private lastX = 0;
  private lastY = 0;
  private lastPrimaryRepeat = 0;
  private lastRightClickAt = -Infinity;

  public constructor(private readonly canvas: HTMLCanvasElement, private readonly callbacks: InputCallbacks) {
    canvas.addEventListener('pointerdown', this.onPointerDown);
    canvas.addEventListener('pointermove', this.onPointerMove);
    canvas.addEventListener('pointerup', this.onPointerUp);
    canvas.addEventListener('pointercancel', this.onPointerCancel);
    canvas.addEventListener('pointerleave', this.onPointerLeave);
    canvas.addEventListener('wheel', this.onWheel, { passive: false });
    canvas.addEventListener('contextmenu', this.onContextMenu);
    window.addEventListener('keydown', this.onKeyDown);
    window.addEventListener('blur', this.onBlur);
  }

  private onPointerDown = (event: PointerEvent): void => {
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

  private onPointerUp = (event: PointerEvent): void => {
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
    if (event.button === 0) this.leftDown = false;
    if (event.button === 2) {
      this.rightDown = false;
      this.rightMoved = false;
    }
  };

  private onPointerLeave = (): void => {
    this.leftDown = false;
    this.rightDown = false;
  };

  private onBlur = (): void => {
    this.leftDown = false;
    this.rightDown = false;
    this.rightMoved = false;
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
