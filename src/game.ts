import * as THREE from 'three';
import { CLASSIC_2003_PROFILE, ROCamera } from './camera';
import { calculateDamage, ATTACK_IMPACT_DELAY, ATTACK_INTERVAL, ATTACK_RANGE, PLAYER_ATTACK } from './combat';
import { spriteDirection } from './direction';
import { InputController } from './input';
import { Inventory, ITEM_DEFINITIONS } from './inventory';
import { rollLoot } from './loot';
import { findPath, cellDistance } from './pathfinding';
import { Random } from './random';
import { SpriteActor, SpriteTextureLibrary } from './sprites';
import { World } from './world';
import type { Actor, FloorItem, GridCell, Vec3 } from './types';

const VIEWPORT_WIDTH = 800;
const VIEWPORT_HEIGHT = 600;
const PLAYER_SPEED = 4;
const PORING_SPEED = 1.15;
const PORING_RESPAWN_DELAY = 5;
const DAMAGE_DURATION = 0.9;
const APPLE_HEAL_AMOUNT = 15;

interface MessageEntry {
  text: string;
  expiresAt: number;
}

interface DamageEffect {
  element: HTMLDivElement;
  position: Vec3;
  startedAt: number;
}

interface UiRefs {
  playerHpLabel: HTMLElement;
  playerHpFill: HTMLElement;
  targetPanel: HTMLElement;
  targetName: HTMLElement;
  targetHpLabel: HTMLElement;
  targetHpFill: HTMLElement;
  attackSlot: HTMLButtonElement;
  healSlot: HTMLButtonElement;
  inventoryToggle: HTMLButtonElement;
  inventoryWindow: HTMLElement;
  inventoryClose: HTMLButtonElement;
  inventoryGrid: HTMLElement;
  messageLog: HTMLElement;
  seedLabel: HTMLElement;
  overlay: HTMLElement;
  helpCard: HTMLElement;
  helpDismiss: HTMLButtonElement;
}

const required = <T extends HTMLElement>(id: string): T => {
  const element = document.getElementById(id);
  if (!element) {
    throw new Error(`Missing required element: #${id}`);
  }
  return element as T;
};

const key = (x: number, z: number): string => `${x},${z}`;
const distance2D = (a: Vec3, b: Vec3): number => Math.hypot(a.x - b.x, a.z - b.z);

export class Game {
  private readonly canvas: HTMLCanvasElement;
  private readonly scene = new THREE.Scene();
  private readonly renderer: THREE.WebGLRenderer;
  private readonly camera: ROCamera;
  private readonly world: World;
  private readonly random: Random;
  private readonly spriteTextures = new SpriteTextureLibrary();
  private readonly actors = new Map<string, Actor>();
  private readonly floorItems: FloorItem[] = [];
  private readonly inventory = new Inventory();
  private readonly messages: MessageEntry[] = [];
  private readonly damageEffects: DamageEffect[] = [];
  private readonly targetMarker: THREE.Mesh;
  private readonly raycaster = new THREE.Raycaster();
  private readonly pointer = new THREE.Vector2();
  private readonly ui: UiRefs;
  private readonly input: InputController;
  private readonly player: Actor;
  private readonly inventorySlots: HTMLElement[] = [];
  private readonly inventorySlotContents: Array<{ icon: HTMLElement; quantity: HTMLElement }> = [];

  private time = 0;
  private lastFrame = performance.now();
  private nextFloorItemId = 1;
  private lastMessageSignature = '';
  private lastInventorySignature = '';
  private hoveredCell: GridCell | null = null;

  public constructor(canvas: HTMLCanvasElement, seed = 1337, profile = CLASSIC_2003_PROFILE) {
    this.canvas = canvas;
    this.camera = new ROCamera(VIEWPORT_WIDTH / VIEWPORT_HEIGHT, profile);
    this.random = new Random(seed);
    this.renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: false,
      alpha: false,
      powerPreference: 'high-performance',
    });
    this.renderer.setPixelRatio(1);
    this.renderer.setSize(VIEWPORT_WIDTH, VIEWPORT_HEIGHT, false);
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.NoToneMapping;
    this.renderer.setClearColor(0x719b70, 1);

    this.scene.background = new THREE.Color(0x719b70);
    this.scene.fog = new THREE.Fog(0x719b70, 90, 220);
    this.scene.add(new THREE.HemisphereLight(0xe5efc8, 0x263525, 1.35));
    const sun = new THREE.DirectionalLight(0xffe0a2, 1.25);
    sun.position.set(-30, 48, 22);
    this.scene.add(sun);

    this.world = new World(this.scene, this.random);
    this.targetMarker = this.createTargetMarker();
    this.scene.add(this.targetMarker);
    const spawnCells = this.world.findSpawnCells(16, this.random);
    const reserved = new Set<string>();
    reserved.add(key(32, 32));
    for (const cell of spawnCells) reserved.add(key(cell.x, cell.z));
    this.world.buildProps(this.random, reserved);

    const playerCell: GridCell = { x: 32, z: 32 };
    this.player = this.createActor('player', 'player-0', playerCell, 40, PLAYER_ATTACK, 0);
    this.actors.set(this.player.id, this.player);
    spawnCells.forEach((cell, index) => {
      const poring = this.createActor('poring', `poring-${index}`, cell, 50, 6, 0);
      poring.nextThinkAt = this.time + this.random.range(0.5, 3.5);
      this.actors.set(poring.id, poring);
    });

    this.ui = {
      playerHpLabel: required('player-hp-label'),
      playerHpFill: required('player-hp-fill'),
      targetPanel: required('target-panel'),
      targetName: required('target-name'),
      targetHpLabel: required('target-hp-label'),
      targetHpFill: required('target-hp-fill'),
      attackSlot: required('attack-slot'),
      healSlot: required('heal-slot'),
      inventoryToggle: required('inventory-toggle'),
      inventoryWindow: required('inventory-window'),
      inventoryClose: required('inventory-close'),
      inventoryGrid: required('inventory-grid'),
      messageLog: required('message-log'),
      seedLabel: required('seed-label'),
      overlay: required('world-overlay'),
      helpCard: required('help-card'),
      helpDismiss: required('help-dismiss'),
    };
    this.buildInventorySlots();
    this.bindUi();
    this.ui.seedLabel.textContent = `seed ${seed} · F1 attack · H apple · I inventory`;
    this.input = new InputController(canvas, {
      onPrimary: (x, y) => this.handlePrimary(x, y),
      onHover: (x, y) => this.handleHover(x, y),
      onCameraDrag: (dx, dy, shift, control) => this.handleCameraDrag(dx, dy, shift, control),
      onWheel: (delta, shift) => this.handleWheel(delta, shift),
      onDoubleRight: (shift) => this.camera.reset(shift),
      onToggleInventory: () => this.toggleInventory(),
      onAttackShortcut: () => this.useAttackShortcut(),
      onUseHealShortcut: () => this.useHealingItem('apple'),
      onEscape: () => this.closeInventory(),
    });

    this.addMessage('Welcome to Payon Forest. Left-click a Poring to begin.');
    this.addMessage('The forest is quiet. No audio, as requested.');
    this.updateInventoryUi(true);
    this.updateHud();
    (window as Window & { __RO_READY__?: boolean }).__RO_READY__ = true;
  }

  public start(): void {
    this.lastFrame = performance.now();
    requestAnimationFrame(this.frame);
  }

  private readonly frame = (timestamp: number): void => {
    const deltaSeconds = Math.min((timestamp - this.lastFrame) / 1000, 0.05);
    this.lastFrame = timestamp;
    this.update(deltaSeconds);
    this.renderer.render(this.scene, this.camera.camera);
    requestAnimationFrame(this.frame);
  };

  private update(deltaSeconds: number): void {
    this.time += deltaSeconds;
    this.updatePlayer(deltaSeconds);
    this.updatePorings(deltaSeconds);
    this.updateFloorItems();
    this.camera.update(deltaSeconds * 1000, this.player.position);
    this.updateVisuals();
    this.world.update(this.time);
    this.updateDamageEffects();
    this.updateHud();
  }

  private createTargetMarker(): THREE.Mesh {
    const geometry = new THREE.RingGeometry(0.46, 0.57, 24);
    geometry.rotateX(-Math.PI / 2);
    const material = new THREE.MeshBasicMaterial({
      color: 0xf1d27b,
      transparent: true,
      opacity: 0.82,
      depthWrite: false,
      side: THREE.DoubleSide,
    });
    const marker = new THREE.Mesh(geometry, material);
    marker.visible = false;
    marker.renderOrder = 11;
    marker.name = 'target-marker';
    return marker;
  }

  private createActor(kind: Actor['kind'], id: string, cell: GridCell, hp: number, attack: number, defense: number): Actor {
    const position = this.world.grid.cellCenter(cell);
    const visual = new SpriteActor(kind, this.spriteTextures);
    visual.setPosition(position.x, position.y, position.z);
    visual.sprite.userData = { kind: 'actor', id };
    this.scene.add(visual.root);
    const actor: Actor = {
      id,
      kind,
      position,
      facing: kind === 'player' ? Math.PI : 0,
      state: 'idle',
      hp,
      maxHp: hp,
      attack,
      defense,
      path: [],
      targetId: undefined,
      nextAttackAt: 0,
      nextThinkAt: this.time + this.random.range(1, 4),
      nextPathRefreshAt: 0,
      spawnCell: { ...cell },
      lootRolled: false,
      animationOffset: this.random.range(0, 10),
      visual,
    };
    return actor;
  }

  private updatePlayer(deltaSeconds: number): void {
    const player = this.player;
    if (player.hurtUntil !== undefined && this.time >= player.hurtUntil) {
      player.hurtUntil = undefined;
      if (player.state === 'hurt') player.state = 'idle';
    }

    const target = this.targetActor();
    if (target && target.state === 'dead') {
      player.targetId = undefined;
    }

    if (target) {
      const distance = distance2D(player.position, target.position);
      if (distance <= ATTACK_RANGE) {
        player.path = [];
        player.pathGoal = undefined;
        player.facing = Math.atan2(target.position.x - player.position.x, target.position.z - player.position.z);
        if (player.state !== 'attack' && this.time >= player.nextAttackAt) {
          this.beginAttack(player);
        } else if (player.state !== 'attack' && player.state !== 'hurt') {
          player.state = 'idle';
        }
      } else {
        if (this.time >= player.nextPathRefreshAt || !player.pathGoal) {
          this.pathPlayerToTarget(target);
        }
        this.moveActor(player, deltaSeconds, PLAYER_SPEED);
      }
    } else {
      this.moveActor(player, deltaSeconds, PLAYER_SPEED);
    }

    if (player.attackImpactAt !== undefined && this.time >= player.attackImpactAt) {
      player.attackImpactAt = undefined;
      this.resolvePlayerAttack();
    }
  }

  private updatePorings(deltaSeconds: number): void {
    for (const actor of this.actors.values()) {
      if (actor.kind !== 'poring') continue;

      if (actor.state === 'dead') {
        if (this.time >= (actor.respawnAt ?? Number.POSITIVE_INFINITY)) {
          this.respawnPoring(actor);
        }
        continue;
      }

      if (actor.hurtUntil !== undefined) {
        if (this.time < actor.hurtUntil) {
          actor.state = 'hurt';
          continue;
        }
        actor.hurtUntil = undefined;
        actor.state = 'idle';
      }

      if (actor.path.length === 0 && this.time >= actor.nextThinkAt) {
        this.choosePoringWander(actor);
      }
      this.moveActor(actor, deltaSeconds, PORING_SPEED);
    }
  }

  private beginAttack(actor: Actor): void {
    actor.state = 'attack';
    actor.attackStartedAt = this.time;
    actor.attackImpactAt = this.time + ATTACK_IMPACT_DELAY;
    actor.nextAttackAt = this.time + ATTACK_INTERVAL;
  }

  private resolvePlayerAttack(): void {
    const target = this.targetActor();
    if (!target || target.state === 'dead' || distance2D(this.player.position, target.position) > ATTACK_RANGE + 0.25) {
      this.player.state = 'idle';
      return;
    }

    const variance = this.random.range(0.9, 1.1);
    const damage = calculateDamage(this.player.attack, target.defense, variance);
    target.hp = Math.max(0, target.hp - damage);
    target.hurtUntil = this.time + 0.2;
    target.state = 'hurt';
    target.nextThinkAt = Math.max(target.nextThinkAt, this.time + 0.5);
    this.showDamage(target, damage);
    this.addMessage(`You hit ${target.id.replace('poring-', 'Poring ')} for ${damage}.`);

    if (target.hp <= 0) {
      this.killPoring(target);
    }
    this.player.state = 'idle';
  }

  private killPoring(poring: Actor): void {
    if (poring.state === 'dead') return;
    poring.hp = 0;
    poring.state = 'dead';
    poring.deathAt = this.time;
    poring.respawnAt = this.time + PORING_RESPAWN_DELAY;
    poring.path = [];
    poring.pathGoal = undefined;
    poring.targetId = undefined;
    poring.lootRolled = true;
    if (this.player.targetId === poring.id) {
      this.player.targetId = undefined;
    }
    this.addMessage('The Poring pops. A small item remains.');

    const itemId = rollLoot(this.random);
    if (itemId) {
      this.spawnFloorItem(itemId, poring.position);
      this.addMessage(`${ITEM_DEFINITIONS[itemId].name} was dropped to the ground.`);
    } else {
      this.addMessage('The Poring left nothing behind.');
    }
  }

  private respawnPoring(poring: Actor): void {
    const spawn = this.world.grid.cellCenter(poring.spawnCell);
    poring.position.x = spawn.x;
    poring.position.y = spawn.y;
    poring.position.z = spawn.z;
    poring.hp = poring.maxHp;
    poring.state = 'idle';
    poring.deathAt = undefined;
    poring.respawnAt = undefined;
    poring.lootRolled = false;
    poring.path = [];
    poring.pathGoal = undefined;
    poring.visual.setVisible(true);
    poring.nextThinkAt = this.time + this.random.range(0.5, 2.5);
  }

  private choosePoringWander(poring: Actor): void {
    const origin = this.world.grid.cellAt(poring.position.x, poring.position.z);
    const destination: GridCell = { x: origin.x, z: origin.z };
    for (let attempt = 0; attempt < 8; attempt += 1) {
      const candidate = {
        x: origin.x + this.random.int(-5, 5),
        z: origin.z + this.random.int(-5, 5),
      };
      if (this.world.grid.isWalkable(candidate.x, candidate.z)) {
        destination.x = candidate.x;
        destination.z = candidate.z;
        break;
      }
    }
    this.setPath(poring, destination);
    poring.nextThinkAt = this.time + this.random.range(1.4, 4.2);
  }

  private pathPlayerToTarget(target: Actor): void {
    const targetCell = this.world.grid.cellAt(target.position.x, target.position.z);
    const playerCell = this.world.grid.cellAt(this.player.position.x, this.player.position.z);
    const candidates: GridCell[] = [];
    for (let dz = -1; dz <= 1; dz += 1) {
      for (let dx = -1; dx <= 1; dx += 1) {
        const candidate = { x: targetCell.x + dx, z: targetCell.z + dz };
        if (this.world.grid.isWalkable(candidate.x, candidate.z) && cellDistance(candidate, targetCell) <= ATTACK_RANGE) {
          candidates.push(candidate);
        }
      }
    }
    candidates.sort((a, b) => cellDistance(a, playerCell) - cellDistance(b, playerCell));
    for (const candidate of candidates) {
      const path = findPath(this.world.grid, playerCell, candidate);
      if (path.length > 0 || (candidate.x === playerCell.x && candidate.z === playerCell.z)) {
        this.setPath(this.player, candidate);
        return;
      }
    }
  }

  private setPath(actor: Actor, goal: GridCell): void {
    const start = this.world.grid.cellAt(actor.position.x, actor.position.z);
    const path = findPath(this.world.grid, start, goal);
    actor.path = path;
    actor.pathGoal = { ...goal };
    actor.nextPathRefreshAt = this.time + 0.35;
  }

  private moveActor(actor: Actor, deltaSeconds: number, speed: number): void {
    if (actor.path.length === 0) {
      return;
    }
    const next = actor.path[0];
    const destination = this.world.grid.cellCenter(next);
    const dx = destination.x - actor.position.x;
    const dz = destination.z - actor.position.z;
    const distance = Math.hypot(dx, dz);
    if (distance <= 0.0001) {
      actor.path.shift();
      if (actor.path.length === 0) actor.state = 'idle';
      return;
    }

    actor.facing = Math.atan2(dx, dz);
    actor.state = 'walk';
    const step = speed * deltaSeconds;
    if (step >= distance) {
      actor.position.x = destination.x;
      actor.position.y = destination.y;
      actor.position.z = destination.z;
      actor.path.shift();
    } else {
      const ratio = step / distance;
      actor.position.x += dx * ratio;
      actor.position.z += dz * ratio;
      actor.position.y += (destination.y - actor.position.y) * Math.min(1, ratio * 4);
    }
    if (actor.path.length === 0) actor.state = 'idle';
  }

  private targetActor(): Actor | undefined {
    return this.player.targetId ? this.actors.get(this.player.targetId) : undefined;
  }

  private updateVisuals(): void {
    for (const actor of this.actors.values()) {
      let action = actor.state;
      let frame = 0;
      if (actor.state === 'walk') {
        frame = Math.floor((this.time * 7 + actor.animationOffset) % 4);
      } else if (actor.state === 'attack') {
        frame = Math.min(2, Math.floor((this.time - (actor.attackStartedAt ?? this.time)) / 0.12));
      }

      const direction = spriteDirection(actor.facing, this.camera.yaw);
      const tint = actor.state === 'hurt' ? 0xffb0b0 : actor.state === 'dead' ? 0xd88c9a : 0xffffff;
      actor.visual.setFrame(action, direction, frame, tint, actor.state === 'dead' ? 0.92 : 1);

      const hop = actor.kind === 'poring' && actor.state === 'walk' ? Math.abs(Math.sin(this.time * 8 + actor.animationOffset)) * 0.13 : 0;
      actor.visual.setPosition(actor.position.x, actor.position.y + hop, actor.position.z);
      if (actor.state === 'dead' && actor.deathAt !== undefined && this.time - actor.deathAt > 0.75) {
        actor.visual.setVisible(false);
      } else {
        actor.visual.setVisible(true);
      }
    }

    const target = this.targetActor();
    this.targetMarker.visible = Boolean(target && target.state !== 'dead');
    if (target && target.state !== 'dead') {
      const pulse = 1 + Math.sin(this.time * 5) * 0.08;
      this.targetMarker.position.set(target.position.x, target.position.y + 0.045, target.position.z);
      this.targetMarker.scale.set(pulse, 1, pulse);
    }
  }

  private updateFloorItems(): void {
    for (const item of this.floorItems) {
      item.object.position.y = item.position.y + 0.2 + Math.sin(this.time * 2.4 + item.bobOffset) * 0.07;
    }
  }

  private spawnFloorItem(itemId: string, position: Vec3): void {
    const definition = ITEM_DEFINITIONS[itemId];
    const material = new THREE.SpriteMaterial({
      map: this.spriteTextures.getItem(itemId, definition.color, definition.glyph),
      transparent: true,
      alphaTest: 0.5,
      depthWrite: false,
      sizeAttenuation: true,
    });
    const object = new THREE.Sprite(material);
    object.center.set(0.5, 0);
    object.scale.set(0.72, 0.72, 1);
    object.position.set(position.x, this.world.grid.heightAt(position.x, position.z) + 0.2, position.z);
    object.renderOrder = 12;
    object.userData = { kind: 'floor-item', id: `floor-${this.nextFloorItemId}` };
    this.scene.add(object);
    this.floorItems.push({
      id: `floor-${this.nextFloorItemId}`,
      itemId,
      quantity: 1,
      position: { x: position.x, y: this.world.grid.heightAt(position.x, position.z), z: position.z },
      object,
      bobOffset: this.random.range(0, Math.PI * 2),
    });
    this.nextFloorItemId += 1;
  }

  private pickupItem(item: FloorItem): void {
    const remaining = this.inventory.add(item.itemId, item.quantity);
    if (remaining === item.quantity) {
      this.addMessage(`Inventory full: ${ITEM_DEFINITIONS[item.itemId].name} stays on the ground.`);
      this.updateInventoryUi(true);
      return;
    }

    item.quantity = remaining;
    this.addMessage(`Picked up ${ITEM_DEFINITIONS[item.itemId].name}.`);
    if (remaining === 0) {
      this.scene.remove(item.object);
      const index = this.floorItems.indexOf(item);
      if (index >= 0) this.floorItems.splice(index, 1);
    }
    this.updateInventoryUi(true);
  }

  private handlePrimary(clientX: number, clientY: number): void {
    if (!this.setPointerFromClient(clientX, clientY)) return;
    this.scene.updateMatrixWorld(true);
    this.camera.camera.updateMatrixWorld();

    const screenTarget = this.pickScreenTarget(clientX, clientY);
    if (screenTarget) {
      if (screenTarget.kind === 'floor-item') {
        const item = this.floorItems.find((candidate) => candidate.id === screenTarget.id);
        if (item) this.pickupItem(item);
        return;
      }
      const actor = this.actors.get(screenTarget.id);
      if (actor && actor.id !== this.player.id && actor.state !== 'dead') {
        this.player.targetId = actor.id;
        this.player.path = [];
        this.player.pathGoal = undefined;
        this.player.nextPathRefreshAt = 0;
        this.addMessage(`Targeting ${actor.id.replace('poring-', 'Poring ')}.`);
      }
      return;
    }

    const objects = [
      ...Array.from(this.actors.values(), (actor) => actor.visual.sprite),
      ...this.floorItems.map((item) => item.object),
    ];
    const objectHits = this.raycaster.intersectObjects(objects, false);
    const groundHits = this.raycaster.intersectObject(this.world.terrain, false);
    const objectHit = objectHits[0];
    const groundHit = groundHits[0];

    if (objectHit && (!groundHit || objectHit.distance <= groundHit.distance + 0.4)) {
      const data = objectHit.object.userData as { kind?: string; id?: string };
      if (data.kind === 'floor-item' && data.id) {
        const item = this.floorItems.find((candidate) => candidate.id === data.id);
        if (item) this.pickupItem(item);
        return;
      }
      if (data.kind === 'actor' && data.id && data.id !== this.player.id) {
        const actor = this.actors.get(data.id);
        if (actor && actor.state !== 'dead') {
          this.player.targetId = actor.id;
          this.player.path = [];
          this.player.pathGoal = undefined;
          this.player.nextPathRefreshAt = 0;
          this.addMessage(`Targeting ${actor.id.replace('poring-', 'Poring ')}.`);
        }
        return;
      }
    }

    if (!groundHit) return;
    const cell = this.world.cellFromPoint(groundHit.point);
    const valid = this.world.grid.isPassable(cell.x, cell.z);
    this.world.setCursor(cell, valid);
    if (!valid) {
      this.addMessage('That cell cannot be reached.');
      return;
    }
    this.player.targetId = undefined;
    this.setPath(this.player, cell);
  }

  private pickScreenTarget(clientX: number, clientY: number): { kind: 'actor' | 'floor-item'; id: string } | null {
    const rect = this.canvas.getBoundingClientRect();
    const pointerX = (clientX - rect.left) / rect.width * VIEWPORT_WIDTH;
    const pointerY = (clientY - rect.top) / rect.height * VIEWPORT_HEIGHT;
    let best: { kind: 'actor' | 'floor-item'; id: string; distance: number } | null = null;

    for (const actor of this.actors.values()) {
      if (actor.id === this.player.id || actor.state === 'dead' || !actor.visual.root.visible) continue;
      const bodyHeight = actor.kind === 'player' ? 1.25 : 0.62;
      const point = this.camera.project({
        x: actor.position.x,
        y: actor.position.y + bodyHeight,
        z: actor.position.z,
      }, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
      const radius = actor.kind === 'player' ? 30 : 25;
      const distance = Math.hypot(pointerX - point.x, pointerY - point.y);
      if (point.visible && distance <= radius && (!best || distance < best.distance)) {
        best = { kind: 'actor', id: actor.id, distance };
      }
    }

    for (const item of this.floorItems) {
      const point = this.camera.project({
        x: item.object.position.x,
        y: item.object.position.y + 0.34,
        z: item.object.position.z,
      }, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
      const distance = Math.hypot(pointerX - point.x, pointerY - point.y);
      if (point.visible && distance <= 20 && (!best || distance < best.distance)) {
        best = { kind: 'floor-item', id: item.id, distance };
      }
    }

    return best ? { kind: best.kind, id: best.id } : null;
  }

  private handleHover(clientX: number, clientY: number): void {
    if (!this.setPointerFromClient(clientX, clientY)) return;
    const hits = this.raycaster.intersectObject(this.world.terrain, false);
    if (hits.length === 0) {
      this.hoveredCell = null;
      this.world.setCursor(null, false);
      return;
    }
    const cell = this.world.cellFromPoint(hits[0].point);
    this.hoveredCell = cell;
    const valid = this.world.grid.isPassable(cell.x, cell.z);
    this.world.setCursor(cell, valid);
  }

  private setPointerFromClient(clientX: number, clientY: number): boolean {
    const rect = this.canvas.getBoundingClientRect();
    this.pointer.x = ((clientX - rect.left) / rect.width) * 2 - 1;
    this.pointer.y = -((clientY - rect.top) / rect.height) * 2 + 1;
    this.raycaster.setFromCamera(this.pointer, this.camera.camera);
    return rect.width > 0 && rect.height > 0;
  }

  private handleCameraDrag(deltaX: number, deltaY: number, shift: boolean, control: boolean): void {
    if (shift) {
      this.camera.adjustElevation((deltaY / VIEWPORT_HEIGHT) * 300);
    } else if (control) {
      this.camera.adjustDistance((deltaY / VIEWPORT_HEIGHT) * 30);
    } else {
      this.camera.adjustYaw(-(deltaX / VIEWPORT_WIDTH) * 720);
    }
  }

  private handleWheel(deltaY: number, shift: boolean): void {
    if (shift) {
      this.camera.adjustElevation(Math.sign(deltaY) * 5);
    } else {
      this.camera.zoomByWheel(deltaY);
    }
  }

  private useAttackShortcut(): void {
    const target = this.targetActor();
    if (!target || target.state === 'dead') {
      this.addMessage('Select a living Poring first.');
      return;
    }
    if (distance2D(this.player.position, target.position) > ATTACK_RANGE) {
      this.addMessage('Move closer to attack.');
      return;
    }
    if (this.time >= this.player.nextAttackAt) {
      this.beginAttack(this.player);
    }
  }

  private useInventorySlot(index: number): void {
    const stack = this.inventory.getSlots()[index];
    if (!stack) return;
    if (stack.itemId !== 'apple') {
      this.addMessage(`${ITEM_DEFINITIONS[stack.itemId]?.name ?? stack.itemId} is not usable yet.`);
      return;
    }
    this.useHealingItem(stack.itemId);
  }

  private useHealingItem(itemId: string): void {
    if (itemId !== 'apple') {
      this.addMessage(`${ITEM_DEFINITIONS[itemId]?.name ?? itemId} is not usable yet.`);
      return;
    }
    if (this.player.hp >= this.player.maxHp) {
      this.addMessage('You are already at full health.');
      return;
    }
    if (!this.inventory.remove(itemId, 1)) {
      this.addMessage('You have no Apple.');
      return;
    }

    const healed = Math.min(APPLE_HEAL_AMOUNT, this.player.maxHp - this.player.hp);
    this.player.hp += healed;
    this.showFloatingNumber(this.player, `+${healed}`, 'heal-number');
    this.addMessage(`You ate an Apple and recovered ${healed} HP.`);
    this.updateInventoryUi(true);
  }

  private toggleInventory(): void {
    this.ui.inventoryWindow.classList.toggle('is-hidden');
    this.ui.inventoryToggle.classList.toggle('active', !this.ui.inventoryWindow.classList.contains('is-hidden'));
    this.updateInventoryUi(true);
  }

  private closeInventory(): void {
    this.ui.inventoryWindow.classList.add('is-hidden');
    this.ui.inventoryToggle.classList.remove('active');
  }

  private bindUi(): void {
    this.ui.inventoryToggle.addEventListener('click', () => this.toggleInventory());
    this.ui.inventoryClose.addEventListener('click', () => this.closeInventory());
    this.ui.attackSlot.addEventListener('click', () => this.useAttackShortcut());
    this.ui.healSlot.addEventListener('click', () => this.useHealingItem('apple'));
    this.ui.helpDismiss.addEventListener('click', () => this.ui.helpCard.classList.add('is-dismissed'));
  }

  private buildInventorySlots(): void {
    for (let index = 0; index < this.inventory.capacity; index += 1) {
      const slot = document.createElement('div');
      slot.className = 'inventory-slot';
      slot.tabIndex = 0;
      slot.setAttribute('role', 'button');
      const icon = document.createElement('span');
      icon.className = 'inventory-icon';
      const quantity = document.createElement('span');
      quantity.className = 'inventory-quantity';
      slot.append(icon, quantity);
      slot.addEventListener('click', () => this.useInventorySlot(index));
      slot.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          this.useInventorySlot(index);
        }
      });
      this.ui.inventoryGrid.append(slot);
      this.inventorySlots.push(slot);
      this.inventorySlotContents.push({ icon, quantity });
    }
  }

  private updateInventoryUi(force = false): void {
    const slots = this.inventory.getSlots();
    const signature = slots.map((stack) => stack ? `${stack.itemId}:${stack.quantity}` : '-').join('|');
    if (!force && signature === this.lastInventorySignature) return;
    this.lastInventorySignature = signature;
    slots.forEach((stack, index) => {
      const slot = this.inventorySlots[index];
      const content = this.inventorySlotContents[index];
      if (!stack) {
        slot.classList.remove('has-item');
        content.icon.textContent = '';
        content.quantity.textContent = '';
        slot.title = '';
        return;
      }
      const definition = ITEM_DEFINITIONS[stack.itemId];
      slot.classList.add('has-item');
      content.icon.textContent = definition.glyph;
      content.quantity.textContent = String(stack.quantity);
      slot.title = `${definition.name} ×${stack.quantity}`;
    });
  }

  private showDamage(actor: Actor, amount: number): void {
    this.showFloatingNumber(actor, String(amount), 'damage-number');
  }

  private showFloatingNumber(actor: Actor, text: string, className: string): void {
    const element = document.createElement('div');
    element.className = className;
    element.textContent = text;
    this.ui.overlay.append(element);
    this.damageEffects.push({
      element,
      position: { x: actor.position.x, y: actor.position.y + (actor.kind === 'player' ? 2.6 : 1.5), z: actor.position.z },
      startedAt: this.time,
    });
  }

  private updateDamageEffects(): void {
    for (let index = this.damageEffects.length - 1; index >= 0; index -= 1) {
      const effect = this.damageEffects[index];
      const age = this.time - effect.startedAt;
      if (age >= DAMAGE_DURATION) {
        effect.element.remove();
        this.damageEffects.splice(index, 1);
        continue;
      }
      const point = this.camera.project(effect.position, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
      effect.element.style.left = `${(point.x / VIEWPORT_WIDTH) * 100}%`;
      effect.element.style.top = `${(point.y / VIEWPORT_HEIGHT) * 100 - age * 2}%`;
      effect.element.style.opacity = String(Math.max(0, 1 - age / DAMAGE_DURATION));
      effect.element.style.display = point.visible ? 'block' : 'none';
    }
  }

  private addMessage(text: string): void {
    this.messages.push({ text, expiresAt: this.time + 6 });
    if (this.messages.length > 5) this.messages.shift();
  }

  private updateHud(): void {
    this.ui.playerHpLabel.textContent = `${this.player.hp} / ${this.player.maxHp}`;
    this.ui.playerHpFill.style.width = `${Math.max(0, this.player.hp / this.player.maxHp * 100)}%`;
    const canHeal = this.player.hp < this.player.maxHp && this.inventory.count('apple') > 0;
    this.ui.healSlot.classList.toggle('active', canHeal);
    this.ui.healSlot.setAttribute('aria-disabled', String(!canHeal));

    const target = this.targetActor();
    if (target && target.state !== 'dead') {
      this.ui.targetPanel.classList.remove('is-hidden');
      this.ui.targetName.textContent = target.id.replace('poring-', 'Poring ');
      this.ui.targetHpLabel.textContent = `${target.hp} / ${target.maxHp}`;
      this.ui.targetHpFill.style.width = `${Math.max(0, target.hp / target.maxHp * 100)}%`;
      this.ui.attackSlot.classList.toggle('active', this.time >= this.player.nextAttackAt);
    } else {
      this.ui.targetPanel.classList.add('is-hidden');
      this.ui.attackSlot.classList.remove('active');
    }

    const activeMessages = this.messages.filter((message) => message.expiresAt > this.time);
    const signature = activeMessages.map((message) => message.text).join('|');
    if (signature !== this.lastMessageSignature) {
      this.lastMessageSignature = signature;
      this.ui.messageLog.replaceChildren(...activeMessages.map((message) => {
        const line = document.createElement('div');
        line.className = 'message-line';
        line.textContent = message.text;
        return line;
      }));
    }
  }
}
