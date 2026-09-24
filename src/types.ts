import type * as THREE from 'three';
import type { SpriteActor } from './sprites';

export interface Vec3 {
  x: number;
  y: number;
  z: number;
}

export interface GridCell {
  x: number;
  z: number;
}

export type ActorKind = 'player' | 'poring';
export type ActorState = 'idle' | 'walk' | 'attack' | 'hurt' | 'dead';

export interface Actor {
  id: string;
  kind: ActorKind;
  position: Vec3;
  facing: number;
  state: ActorState;
  hp: number;
  maxHp: number;
  attack: number;
  defense: number;
  path: GridCell[];
  pathGoal?: GridCell;
  targetId?: string;
  nextAttackAt: number;
  attackStartedAt?: number;
  attackImpactAt?: number;
  hurtUntil?: number;
  nextThinkAt: number;
  nextPathRefreshAt: number;
  spawnCell: GridCell;
  deathAt?: number;
  respawnAt?: number;
  lootRolled: boolean;
  animationOffset: number;
  visual: SpriteActor;
}

export interface ItemDefinition {
  id: string;
  name: string;
  color: string;
  glyph: string;
  stackable: boolean;
  maxStack: number;
}

export interface ItemStack {
  itemId: string;
  quantity: number;
}

export interface FloorItem {
  id: string;
  itemId: string;
  quantity: number;
  position: Vec3;
  object: THREE.Sprite;
  bobOffset: number;
}

export interface CameraProfile {
  fov: number;
  near: number;
  far: number;
  minDistance: number;
  defaultDistance: number;
  maxDistance: number;
  wheelStep: number;
  defaultElevation: number;
  minElevation: number;
  maxElevation: number;
  defaultYaw: number;
}

export interface GridAccess {
  width: number;
  height: number;
  isWalkable(x: number, z: number): boolean;
}

export interface ScreenPoint {
  x: number;
  y: number;
  visible: boolean;
}
