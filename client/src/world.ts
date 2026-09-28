import * as THREE from 'three';
import { Random } from './random';
import type { GridAccess, GridCell, Vec3 } from './types';

const STREAM_Z = 44;
const BRIDGE_X = 32;

const key = (x: number, z: number): string => `${x},${z}`;
const clamp = (value: number, min: number, max: number): number => Math.max(min, Math.min(max, value));

export class WorldGrid implements GridAccess {
  public readonly width: number;
  public readonly height: number;
  private readonly heights: Float32Array;
  private readonly walkable: Uint8Array;
  private readonly water: Uint8Array;
  private readonly paths: Uint8Array;
  private readonly bridges: Uint8Array;

  public constructor(width: number, height: number, random: Random) {
    this.width = width;
    this.height = height;
    this.heights = new Float32Array(width * height);
    this.walkable = new Uint8Array(width * height);
    this.water = new Uint8Array(width * height);
    this.paths = new Uint8Array(width * height);
    this.bridges = new Uint8Array(width * height);
    this.generate(random);
  }

  private index(x: number, z: number): number {
    return z * this.width + x;
  }

  private generate(random: Random): void {
    for (let z = 0; z < this.height; z += 1) {
      for (let x = 0; x < this.width; x += 1) {
        const i = this.index(x, z);
        const rolling = Math.sin(x * 0.19) * 0.16 + Math.cos(z * 0.23) * 0.12;
        const small = Math.sin((x + z) * 0.41) * 0.035;
        const terrainHeight = rolling + small + random.range(-0.025, 0.025);

        const streamCenter = STREAM_Z + Math.sin(x * 0.16) * 0.7;
        const isWater = Math.abs(z - streamCenter) < 1.15;
        const isBridge = x >= BRIDGE_X - 2 && x <= BRIDGE_X + 2 && z >= STREAM_Z - 2 && z <= STREAM_Z + 2;
        const isPath = (Math.abs(x - 32) < 1.25 && z <= STREAM_Z + 1) ||
          (Math.abs(z - 32) < 1.25 && x >= 25);

        this.water[i] = isWater ? 1 : 0;
        this.paths[i] = isPath ? 1 : 0;
        this.bridges[i] = isBridge ? 1 : 0;
        this.heights[i] = isBridge ? 0.16 : isWater ? -0.42 : terrainHeight;
        this.walkable[i] = x > 0 && z > 0 && x < this.width - 1 && z < this.height - 1 && (!isWater || isBridge) ? 1 : 0;
      }
    }
  }

  public isWalkable(x: number, z: number): boolean {
    if (x < 0 || z < 0 || x >= this.width || z >= this.height) {
      return false;
    }
    return this.walkable[this.index(x, z)] === 1;
  }

  public isPassable(x: number, z: number): boolean {
    return this.isWalkable(x, z) && (!this.isWater(x, z) || this.isBridge(x, z));
  }

  public setWalkable(x: number, z: number, value: boolean): void {
    if (x < 0 || z < 0 || x >= this.width || z >= this.height) {
      return;
    }
    this.walkable[this.index(x, z)] = value ? 1 : 0;
  }

  public isWater(x: number, z: number): boolean {
    return this.inside(x, z) && this.water[this.index(x, z)] === 1;
  }

  public isPath(x: number, z: number): boolean {
    return this.inside(x, z) && this.paths[this.index(x, z)] === 1;
  }

  public isBridge(x: number, z: number): boolean {
    return this.inside(x, z) && this.bridges[this.index(x, z)] === 1;
  }

  public isReserved(x: number, z: number): boolean {
    if (Math.abs(x - 32) <= 4 && Math.abs(z - 32) <= 4) {
      return true;
    }
    return this.isPath(x, z) || this.isBridge(x, z) || this.isWater(x, z);
  }

  public cellAt(x: number, z: number): GridCell {
    return {
      x: clamp(Math.floor(x), 0, this.width - 1),
      z: clamp(Math.floor(z), 0, this.height - 1),
    };
  }

  public cellCenter(cell: GridCell): Vec3 {
    return {
      x: cell.x + 0.5,
      y: this.heightAt(cell.x + 0.5, cell.z + 0.5),
      z: cell.z + 0.5,
    };
  }

  public heightAt(x: number, z: number): number {
    const gx = clamp(x, 0, this.width - 1.000001);
    const gz = clamp(z, 0, this.height - 1.000001);
    const x0 = Math.floor(gx);
    const z0 = Math.floor(gz);
    const x1 = Math.min(x0 + 1, this.width - 1);
    const z1 = Math.min(z0 + 1, this.height - 1);
    const tx = gx - x0;
    const tz = gz - z0;
    const h00 = this.heights[this.index(x0, z0)];
    const h10 = this.heights[this.index(x1, z0)];
    const h01 = this.heights[this.index(x0, z1)];
    const h11 = this.heights[this.index(x1, z1)];
    const a = h00 + (h10 - h00) * tx;
    const b = h01 + (h11 - h01) * tx;
    return a + (b - a) * tz;
  }

  public cornerHeight(x: number, z: number): number {
    return this.heights[this.index(clamp(x, 0, this.width - 1), clamp(z, 0, this.height - 1))];
  }

  private inside(x: number, z: number): boolean {
    return x >= 0 && z >= 0 && x < this.width && z < this.height;
  }
}

interface TreeInstance {
  x: number;
  z: number;
  height: number;
  scale: number;
  rotation: number;
}

export class World {
  public readonly grid: WorldGrid;
  public readonly terrain: THREE.Mesh;
  public readonly cursor: THREE.Mesh;
  public readonly water: THREE.Mesh;
  public readonly props = new THREE.Group();

  public constructor(scene: THREE.Scene, random: Random) {
    this.grid = new WorldGrid(64, 64, random);
    this.terrain = this.createTerrain();
    this.cursor = this.createCursor();
    this.water = this.createWater();
    scene.add(this.terrain, this.water, this.cursor, this.props);
    this.createBridge();
  }

  public findSpawnCells(count: number, random: Random): GridCell[] {
    const candidates: GridCell[] = [];
    for (let z = 18; z <= 44; z += 1) {
      for (let x = 18; x <= 46; x += 1) {
        const distance = Math.hypot(x - 32, z - 32);
        if (distance < 4 || distance > 14 || !this.grid.isWalkable(x, z) || this.grid.isWater(x, z)) {
          continue;
        }
        candidates.push({ x, z });
      }
    }

    for (let i = candidates.length - 1; i > 0; i -= 1) {
      const j = random.int(0, i);
      [candidates[i], candidates[j]] = [candidates[j], candidates[i]];
    }

    return candidates.slice(0, count);
  }

  public buildProps(random: Random, reserved: Set<string>): void {
    const trees: TreeInstance[] = [];
    const rocks: Array<{ x: number; z: number; scale: number; rotation: number }> = [];
    const mushrooms: Array<{ x: number; z: number; scale: number }> = [];

    for (let attempt = 0; attempt < 1400 && trees.length < 78; attempt += 1) {
      const x = random.int(3, this.grid.width - 4);
      const z = random.int(3, this.grid.height - 4);
      if (!this.grid.isWalkable(x, z) || this.grid.isReserved(x, z) || reserved.has(key(x, z))) {
        continue;
      }
      if (trees.some((tree) => Math.hypot(tree.x - x, tree.z - z) < 2.1)) {
        continue;
      }

      const tree: TreeInstance = {
        x,
        z,
        height: random.range(0.9, 1.25),
        scale: random.range(0.85, 1.2),
        rotation: random.range(0, Math.PI * 2),
      };
      trees.push(tree);
      this.grid.setWalkable(x, z, false);
      reserved.add(key(x, z));
    }

    for (let attempt = 0; attempt < 500 && rocks.length < 26; attempt += 1) {
      const x = random.int(3, this.grid.width - 4);
      const z = random.int(3, this.grid.height - 4);
      if (!this.grid.isWalkable(x, z) || this.grid.isReserved(x, z) || reserved.has(key(x, z))) {
        continue;
      }
      rocks.push({ x, z, scale: random.range(0.35, 0.75), rotation: random.range(0, Math.PI * 2) });
      if (rocks[rocks.length - 1].scale > 0.5) {
        this.grid.setWalkable(x, z, false);
      }
      reserved.add(key(x, z));
    }

    for (let attempt = 0; attempt < 600 && mushrooms.length < 34; attempt += 1) {
      const x = random.int(3, this.grid.width - 4);
      const z = random.int(3, this.grid.height - 4);
      if (!this.grid.isWalkable(x, z) || this.grid.isReserved(x, z) || reserved.has(key(x, z))) {
        continue;
      }
      mushrooms.push({ x, z, scale: random.range(0.7, 1.1) });
    }

    this.addTrees(trees);
    this.addRocks(rocks);
    this.addMushrooms(mushrooms);
    this.addFlowers(random);
  }

  public update(time: number): void {
    this.water.position.y = -0.16 + Math.sin(time * 0.8) * 0.018;
  }

  public setCursor(cell: GridCell | null, valid: boolean): void {
    this.cursor.visible = cell !== null;
    if (!cell) {
      return;
    }
    const center = this.grid.cellCenter(cell);
    this.cursor.position.set(center.x, center.y + 0.035, center.z);
    const material = this.cursor.material as THREE.MeshBasicMaterial;
    material.color.set(valid ? 0x9fe08a : 0xd46d58);
  }

  public cellFromPoint(point: THREE.Vector3): GridCell {
    return this.grid.cellAt(point.x, point.z);
  }

  private createTerrain(): THREE.Mesh {
    const positions: number[] = [];
    const colors: number[] = [];
    const indices: number[] = [];
    const color = new THREE.Color();

    for (let z = 0; z < this.grid.height; z += 1) {
      for (let x = 0; x < this.grid.width; x += 1) {
        const base = positions.length / 3;
        const corners = [
          [x, z], [x + 1, z], [x + 1, z + 1], [x, z + 1],
        ];
        for (const [cornerX, cornerZ] of corners) {
          positions.push(cornerX, this.grid.cornerHeight(cornerX, cornerZ), cornerZ);
          if (this.grid.isWater(x, z)) {
            color.set(0x466f68);
          } else if (this.grid.isBridge(x, z)) {
            color.set(0x8c633d);
          } else if (this.grid.isPath(x, z)) {
            color.set(0x9b8850);
          } else {
            const variation = ((x * 13 + z * 7) % 5) * 0.012;
            color.set(0x4f874d).offsetHSL(0, 0, variation - 0.02);
          }
          colors.push(color.r, color.g, color.b);
        }
        indices.push(base, base + 2, base + 1, base, base + 3, base + 2);
      }
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
    geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));
    geometry.setIndex(indices);
    geometry.computeVertexNormals();

    const material = new THREE.MeshLambertMaterial({
      vertexColors: true,
      flatShading: true,
    });
    const terrain = new THREE.Mesh(geometry, material);
    terrain.name = 'payon-terrain';
    terrain.userData.pickableGround = true;
    return terrain;
  }

  private createCursor(): THREE.Mesh {
    const geometry = new THREE.PlaneGeometry(0.92, 0.92);
    geometry.rotateX(-Math.PI / 2);
    const material = new THREE.MeshBasicMaterial({
      color: 0x9fe08a,
      transparent: true,
      opacity: 0.52,
      depthWrite: false,
      side: THREE.DoubleSide,
    });
    const cursor = new THREE.Mesh(geometry, material);
    cursor.visible = false;
    cursor.renderOrder = 20;
    cursor.name = 'ground-cursor';
    return cursor;
  }

  private createWater(): THREE.Mesh {
    const geometry = new THREE.PlaneGeometry(this.grid.width, 2.55);
    geometry.rotateX(-Math.PI / 2);
    const material = new THREE.MeshLambertMaterial({
      color: 0x4d9ab4,
      transparent: true,
      opacity: 0.72,
      depthWrite: false,
    });
    const water = new THREE.Mesh(geometry, material);
    water.position.set(this.grid.width / 2, -0.16, STREAM_Z);
    water.renderOrder = 1;
    water.name = 'stream';
    return water;
  }

  private createBridge(): void {
    const wood = new THREE.MeshLambertMaterial({ color: 0x805333, flatShading: true });
    const darkWood = new THREE.MeshLambertMaterial({ color: 0x513b2b, flatShading: true });
    const bridge = new THREE.Group();
    bridge.name = 'wooden-bridge';

    for (let z = STREAM_Z - 2; z <= STREAM_Z + 2; z += 1) {
      const plank = new THREE.Mesh(new THREE.BoxGeometry(2.8, 0.16, 0.42), wood);
      plank.position.set(BRIDGE_X + 0.5, 0.12, z + 0.5);
      bridge.add(plank);
    }
    for (const x of [BRIDGE_X - 0.65, BRIDGE_X + 1.65]) {
      const rail = new THREE.Mesh(new THREE.BoxGeometry(0.13, 0.8, 5.4), darkWood);
      rail.position.set(x, 0.55, STREAM_Z + 0.5);
      bridge.add(rail);
    }
    for (const z of [STREAM_Z - 2, STREAM_Z + 3]) {
      for (const x of [BRIDGE_X - 0.65, BRIDGE_X + 1.65]) {
        const post = new THREE.Mesh(new THREE.BoxGeometry(0.2, 1.1, 0.2), darkWood);
        post.position.set(x, 0.55, z);
        bridge.add(post);
      }
    }
    this.props.add(bridge);
  }

  private addTrees(trees: TreeInstance[]): void {
    if (trees.length === 0) {
      return;
    }

    const trunkGeometry = new THREE.CylinderGeometry(0.16, 0.24, 2.1, 6);
    const trunkMaterial = new THREE.MeshLambertMaterial({ color: 0x68452e, flatShading: true });
    const foliageGeometry = new THREE.ConeGeometry(1.05, 2.1, 7);
    const foliageMaterial = new THREE.MeshLambertMaterial({ color: 0x2f6b3c, flatShading: true });
    const highlightGeometry = new THREE.ConeGeometry(0.78, 1.55, 7);
    const highlightMaterial = new THREE.MeshLambertMaterial({ color: 0x46894a, flatShading: true });
    const trunks = new THREE.InstancedMesh(trunkGeometry, trunkMaterial, trees.length);
    const foliage = new THREE.InstancedMesh(foliageGeometry, foliageMaterial, trees.length);
    const highlights = new THREE.InstancedMesh(highlightGeometry, highlightMaterial, trees.length);
    const dummy = new THREE.Object3D();

    trees.forEach((tree, index) => {
      const y = this.grid.heightAt(tree.x + 0.5, tree.z + 0.5);
      dummy.position.set(tree.x + 0.5, y + 1.05 * tree.scale, tree.z + 0.5);
      dummy.rotation.set(0, tree.rotation, 0);
      dummy.scale.setScalar(tree.scale);
      dummy.updateMatrix();
      trunks.setMatrixAt(index, dummy.matrix);

      dummy.position.y = y + 2.2 * tree.scale;
      dummy.scale.set(tree.scale * 1.05, tree.scale, tree.scale * 1.05);
      dummy.updateMatrix();
      foliage.setMatrixAt(index, dummy.matrix);

      dummy.position.y = y + 2.75 * tree.scale;
      dummy.scale.set(tree.scale * 0.8, tree.scale * 0.8, tree.scale * 0.8);
      dummy.updateMatrix();
      highlights.setMatrixAt(index, dummy.matrix);
    });

    trunks.instanceMatrix.needsUpdate = true;
    foliage.instanceMatrix.needsUpdate = true;
    highlights.instanceMatrix.needsUpdate = true;
    this.props.add(trunks, foliage, highlights);
  }

  private addRocks(rocks: Array<{ x: number; z: number; scale: number; rotation: number }>): void {
    if (rocks.length === 0) {
      return;
    }
    const geometry = new THREE.DodecahedronGeometry(0.55, 0);
    const material = new THREE.MeshLambertMaterial({ color: 0x7b806d, flatShading: true });
    const mesh = new THREE.InstancedMesh(geometry, material, rocks.length);
    const dummy = new THREE.Object3D();
    rocks.forEach((rock, index) => {
      const y = this.grid.heightAt(rock.x + 0.5, rock.z + 0.5);
      dummy.position.set(rock.x + 0.5, y + rock.scale * 0.35, rock.z + 0.5);
      dummy.rotation.set(rock.rotation * 0.2, rock.rotation, rock.rotation * 0.13);
      dummy.scale.set(rock.scale, rock.scale * 0.72, rock.scale);
      dummy.updateMatrix();
      mesh.setMatrixAt(index, dummy.matrix);
    });
    mesh.instanceMatrix.needsUpdate = true;
    this.props.add(mesh);
  }

  private addMushrooms(mushrooms: Array<{ x: number; z: number; scale: number }>): void {
    if (mushrooms.length === 0) {
      return;
    }
    const stemGeometry = new THREE.CylinderGeometry(0.06, 0.08, 0.3, 5);
    const capGeometry = new THREE.SphereGeometry(0.18, 6, 4, 0, Math.PI * 2, 0, Math.PI / 2);
    const stemMaterial = new THREE.MeshLambertMaterial({ color: 0xd9c48b, flatShading: true });
    const capMaterial = new THREE.MeshLambertMaterial({ color: 0xb54c43, flatShading: true });
    const stems = new THREE.InstancedMesh(stemGeometry, stemMaterial, mushrooms.length);
    const caps = new THREE.InstancedMesh(capGeometry, capMaterial, mushrooms.length);
    const dummy = new THREE.Object3D();
    mushrooms.forEach((mushroom, index) => {
      const y = this.grid.heightAt(mushroom.x + 0.5, mushroom.z + 0.5);
      dummy.position.set(mushroom.x + 0.5, y + 0.15 * mushroom.scale, mushroom.z + 0.5);
      dummy.scale.setScalar(mushroom.scale);
      dummy.rotation.set(0, 0, 0);
      dummy.updateMatrix();
      stems.setMatrixAt(index, dummy.matrix);
      dummy.position.y = y + 0.3 * mushroom.scale;
      dummy.updateMatrix();
      caps.setMatrixAt(index, dummy.matrix);
    });
    stems.instanceMatrix.needsUpdate = true;
    caps.instanceMatrix.needsUpdate = true;
    this.props.add(stems, caps);
  }

  private addFlowers(random: Random): void {
    const positions: number[] = [];
    for (let i = 0; i < 150; i += 1) {
      const x = random.range(2, this.grid.width - 2);
      const z = random.range(2, this.grid.height - 2);
      if (!this.grid.isWalkable(Math.floor(x), Math.floor(z)) || this.grid.isReserved(Math.floor(x), Math.floor(z))) {
        continue;
      }
      positions.push(x, this.grid.heightAt(x, z) + 0.06, z);
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
    const material = new THREE.PointsMaterial({ color: 0xe8c96b, size: 0.09, sizeAttenuation: true });
    this.props.add(new THREE.Points(geometry, material));
  }
}
