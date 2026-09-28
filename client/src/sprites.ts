import * as THREE from 'three';
import type { ActorKind, ActorState } from './types';

export type SpriteAction = 'idle' | 'walk' | 'attack' | 'hurt' | 'dead';

const directionCount = 8;
const actionFrameCount: Record<SpriteAction, number> = {
  idle: 1,
  walk: 4,
  attack: 3,
  hurt: 1,
  dead: 1,
};

const normalizeDirection = (direction: number): number => ((direction % directionCount) + directionCount) % directionCount;

const createTexture = (width: number, height: number, draw: (ctx: CanvasRenderingContext2D) => void): THREE.CanvasTexture => {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('Canvas 2D context is unavailable');
  }
  ctx.imageSmoothingEnabled = false;
  draw(ctx);
  const texture = new THREE.CanvasTexture(canvas);
  texture.magFilter = THREE.NearestFilter;
  texture.minFilter = THREE.NearestFilter;
  texture.generateMipmaps = false;
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.needsUpdate = true;
  return texture;
};

const rect = (ctx: CanvasRenderingContext2D, color: string, x: number, y: number, width: number, height: number): void => {
  ctx.fillStyle = color;
  ctx.fillRect(x, y, width, height);
};

const drawPlayerFrame = (
  ctx: CanvasRenderingContext2D,
  direction: number,
  action: SpriteAction,
  frame: number,
): void => {
  const dir = normalizeDirection(direction);
  const walkOffset = action === 'walk' ? [0, 1, 0, -1][frame % 4] : 0;
  const attackFrame = action === 'attack' ? frame % 3 : 0;
  const hurt = action === 'hurt';
  const dead = action === 'dead';
  const back = dir >= 3 && dir <= 5;
  const side = dir === 1 || dir === 2 || dir === 6 || dir === 7;

  ctx.save();
  if (dead) {
    ctx.translate(24, 43);
    ctx.rotate(-Math.PI / 2);
    ctx.translate(-24, -43);
  }

  const outline = hurt ? '#5b2530' : '#202a2d';
  const skin = '#e7b487';
  const hair = '#5a3b2d';
  const shirt = hurt ? '#c66c69' : '#4c83a5';
  const shirtLight = hurt ? '#e29b88' : '#79b4c4';
  const pants = '#384a62';
  const boot = '#272a2d';
  const metal = '#d5d5bd';
  const wood = '#805333';

  // Head and hair.
  rect(ctx, outline, 15, 7, 18, 16);
  rect(ctx, skin, 17, 9, 14, 12);
  rect(ctx, hair, 14, 5, 20, 7);
  rect(ctx, hair, 13, 9, 5, 9);
  if (!back) {
    rect(ctx, '#2a2a2b', 20, 14, 2, 2);
    rect(ctx, '#2a2a2b', 28, 14, 2, 2);
    if (dir === 0 || dir === 1 || dir === 7) {
      rect(ctx, skin, 29, 17, 3, 2);
    }
  }

  // Torso and arms.
  rect(ctx, outline, 12, 22, 25, 22);
  rect(ctx, shirt, 14, 24, 21, 18);
  rect(ctx, shirtLight, 16, 25, 7, 15);
  if (!back) {
    rect(ctx, skin, 8, 25 + walkOffset, 6, 13);
    rect(ctx, skin, 35, 25 - walkOffset, 6, 13);
  } else {
    rect(ctx, shirt, 9, 25 + walkOffset, 6, 13);
    rect(ctx, shirt, 34, 25 - walkOffset, 6, 13);
  }

  // Legs and boots.
  rect(ctx, outline, 14, 42, 9, 14 + walkOffset);
  rect(ctx, outline, 27, 42, 9, 14 - walkOffset);
  rect(ctx, pants, 16, 43, 5, 11 + walkOffset);
  rect(ctx, pants, 28, 43, 5, 11 - walkOffset);
  rect(ctx, boot, 14, 53 + walkOffset, 11, 5);
  rect(ctx, boot, 27, 53 - walkOffset, 11, 5);

  // A small sword/club silhouette. The direction changes its side.
  const weaponX = side ? (dir === 1 || dir === 2 ? 39 : 4) : 37;
  const weaponY = attackFrame === 0 ? 26 : attackFrame === 1 ? 19 : 13;
  rect(ctx, outline, weaponX, weaponY, 4, 25);
  rect(ctx, metal, weaponX + 1, weaponY + 1, 2, 21);
  rect(ctx, wood, weaponX - 2, weaponY + 20, 8, 3);
  if (attackFrame > 0 && !dead) {
    rect(ctx, '#f4df9a', weaponX - 3, weaponY - 2, 3, 4);
  }

  if (hurt) {
    rect(ctx, '#ffd0bd', 16, 30, 4, 3);
    rect(ctx, '#ffd0bd', 29, 36, 4, 3);
  }
  ctx.restore();
};

const drawPoringFrame = (
  ctx: CanvasRenderingContext2D,
  direction: number,
  action: SpriteAction,
  frame: number,
): void => {
  const dir = normalizeDirection(direction);
  const hop = action === 'walk' ? [0, -2, 0, 1][frame % 4] : 0;
  const squash = action === 'walk' && frame % 2 === 1 ? 1 : 0;
  const hurt = action === 'hurt';
  const dead = action === 'dead';
  const body = hurt ? '#d87883' : '#ef6e83';
  const light = hurt ? '#f0a09a' : '#ffb0a6';
  const dark = '#8e3d62';

  const centerX = 20;
  const centerY = 21 + hop;
  const radiusX = 14 - squash;
  const radiusY = dead ? 7 : 12 + squash;
  for (let y = 0; y < 18; y += 1) {
    for (let x = 0; x < 20; x += 1) {
      const px = x * 2;
      const py = y * 2;
      const nx = (px + 1 - centerX) / radiusX;
      const ny = (py + 1 - centerY) / radiusY;
      const distance = nx * nx + ny * ny;
      if (distance > 1) {
        continue;
      }
      const edge = distance > 0.66;
      const highlight = !edge && px < centerX - 2 && py < centerY - 2;
      rect(ctx, edge ? dark : highlight ? light : body, px, py, 2, 2);
    }
  }

  if (!dead) {
    const eyeShift = dir === 1 || dir === 2 ? 1 : dir === 6 || dir === 7 ? -1 : 0;
    rect(ctx, '#2c2534', 14 + eyeShift, 19, 3, 4);
    rect(ctx, '#2c2534', 24 + eyeShift, 19, 3, 4);
    rect(ctx, '#fff0c2', 14 + eyeShift, 19, 1, 1);
    rect(ctx, '#fff0c2', 24 + eyeShift, 19, 1, 1);
    rect(ctx, dark, 18, 27, 7, 2);
    if (dir === 0 || dir === 7 || dir === 1) {
      rect(ctx, '#fff0c2', 11, 12, 5, 3);
    }
  } else {
    rect(ctx, dark, 13, 21, 6, 2);
    rect(ctx, dark, 24, 21, 6, 2);
    rect(ctx, dark, 18, 28, 8, 2);
  }

  if (hurt) {
    rect(ctx, '#ffe3a2', 9, 15, 3, 3);
    rect(ctx, '#ffe3a2', 31, 24, 3, 3);
  }
};

export class SpriteTextureLibrary {
  private readonly actorTextures = new Map<string, THREE.CanvasTexture>();
  private readonly itemTextures = new Map<string, THREE.CanvasTexture>();

  public getActor(kind: ActorKind, action: SpriteAction, direction: number, frame: number): THREE.CanvasTexture {
    const normalizedDirection = normalizeDirection(direction);
    const normalizedFrame = ((frame % actionFrameCount[action]) + actionFrameCount[action]) % actionFrameCount[action];
    const id = `${kind}:${action}:${normalizedDirection}:${normalizedFrame}`;
    const cached = this.actorTextures.get(id);
    if (cached) {
      return cached;
    }

    const texture = createTexture(kind === 'player' ? 48 : 40, kind === 'player' ? 64 : 40, (ctx) => {
      if (kind === 'player') {
        drawPlayerFrame(ctx, normalizedDirection, action, normalizedFrame);
      } else {
        drawPoringFrame(ctx, normalizedDirection, action, normalizedFrame);
      }
    });
    this.actorTextures.set(id, texture);
    return texture;
  }

  public getItem(itemId: string, color: string, glyph: string): THREE.CanvasTexture {
    const cached = this.itemTextures.get(itemId);
    if (cached) {
      return cached;
    }
    const texture = createTexture(24, 24, (ctx) => {
      rect(ctx, '#30251f', 3, 3, 18, 18);
      rect(ctx, color, 5, 5, 14, 14);
      rect(ctx, '#fff0b2', 7, 6, 4, 3);
      ctx.fillStyle = '#3a271d';
      ctx.font = 'bold 11px monospace';
      ctx.textAlign = 'center';
      ctx.fillText(glyph.slice(0, 1), 12, 18);
    });
    this.itemTextures.set(itemId, texture);
    return texture;
  }
}

export class SpriteActor {
  public readonly root = new THREE.Group();
  public readonly sprite: THREE.Sprite;
  public readonly shadow: THREE.Mesh;

  private readonly material: THREE.SpriteMaterial;
  private readonly kind: ActorKind;
  private lastTextureKey = '';

  public constructor(kind: ActorKind, private readonly textures: SpriteTextureLibrary) {
    this.kind = kind;
    this.material = new THREE.SpriteMaterial({
      map: textures.getActor(kind, 'idle', 0, 0),
      transparent: true,
      alphaTest: 0.5,
      depthTest: true,
      depthWrite: false,
      sizeAttenuation: true,
    });
    this.sprite = new THREE.Sprite(this.material);
    this.sprite.center.set(0.5, 0);
    this.sprite.renderOrder = 10;

    const shadowMaterial = new THREE.MeshBasicMaterial({
      color: 0x172319,
      transparent: true,
      opacity: 0.34,
      depthWrite: false,
    });
    const shadowGeometry = new THREE.CircleGeometry(kind === 'player' ? 0.48 : 0.4, 12);
    shadowGeometry.rotateX(-Math.PI / 2);
    this.shadow = new THREE.Mesh(shadowGeometry, shadowMaterial);
    this.shadow.position.y = 0.025;
    this.shadow.renderOrder = 2;

    const scale = kind === 'player' ? new THREE.Vector3(1.8, 2.4, 1) : new THREE.Vector3(1.25, 1.12, 1);
    this.sprite.scale.copy(scale);
    this.root.add(this.shadow, this.sprite);
    this.root.name = `${kind}-sprite-root`;
  }

  public setFrame(action: SpriteAction, direction: number, frame: number, tint = 0xffffff, opacity = 1): void {
    const textureKey = `${action}:${normalizeDirection(direction)}:${frame}`;
    if (textureKey !== this.lastTextureKey) {
      this.material.map = this.textures.getActor(this.kind, action, direction, frame);
      this.material.needsUpdate = true;
      this.lastTextureKey = textureKey;
    }
    this.material.color.setHex(tint);
    this.material.opacity = opacity;
  }

  public setPosition(x: number, y: number, z: number): void {
    this.root.position.set(x, y, z);
  }

  public setVisible(visible: boolean): void {
    this.root.visible = visible;
  }

  public setShadowScale(scale: number): void {
    this.shadow.scale.set(scale, 1, scale);
  }

  public dispose(): void {
    this.root.removeFromParent();
    this.material.dispose();
    this.shadow.geometry.dispose();
    (this.shadow.material as THREE.Material).dispose();
  }
}

export const stateToSpriteAction = (state: ActorState): SpriteAction => state;
