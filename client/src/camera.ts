import * as THREE from 'three';
import type { CameraProfile, ScreenPoint, Vec3 } from './types';

export const CLASSIC_2003_PROFILE: CameraProfile = {
  fov: 15,
  near: 2,
  far: 300,
  minDistance: 45,
  defaultDistance: 50,
  maxDistance: 80,
  wheelStep: 5,
  defaultElevation: 60,
  minElevation: 15,
  maxElevation: 75,
  defaultYaw: 0,
};

export const PRE_RENEWAL_2008_PROFILE: CameraProfile = {
  fov: 15,
  near: 2,
  far: 300,
  minDistance: 32.5,
  defaultDistance: 62.5,
  maxDistance: 82.5,
  wheelStep: 5,
  defaultElevation: 50,
  minElevation: 15,
  maxElevation: 75,
  defaultYaw: 0,
};

export const cameraProfileFor = (name: string | null): CameraProfile =>
  name === 'preRenewal2008' ? PRE_RENEWAL_2008_PROFILE : CLASSIC_2003_PROFILE;

const lerp = (a: number, b: number, t: number): number => a + (b - a) * t;
const lerpAngle = (a: number, b: number, t: number): number => {
  let delta = (b - a) % 360;
  if (delta > 180) delta -= 360;
  if (delta < -180) delta += 360;
  return a + delta * t;
};

export class ROCamera {
  public readonly camera: THREE.PerspectiveCamera;
  public readonly profile: CameraProfile;

  private currentTarget = new THREE.Vector3();
  private desiredTarget = new THREE.Vector3();
  private currentDistance: number;
  private desiredDistance: number;
  private currentElevation: number;
  private desiredElevation: number;
  private currentYaw: number;
  private desiredYaw: number;
  private initialized = false;

  public constructor(aspect: number, profile: CameraProfile = CLASSIC_2003_PROFILE) {
    this.profile = profile;
    this.camera = new THREE.PerspectiveCamera(profile.fov, aspect, profile.near, profile.far);
    this.currentDistance = profile.defaultDistance;
    this.desiredDistance = profile.defaultDistance;
    this.currentElevation = profile.defaultElevation;
    this.desiredElevation = profile.defaultElevation;
    this.currentYaw = profile.defaultYaw;
    this.desiredYaw = profile.defaultYaw;
  }

  public get distance(): number {
    return this.currentDistance;
  }

  public get yaw(): number {
    return this.currentYaw;
  }

  public get elevation(): number {
    return this.currentElevation;
  }

  public setAspect(aspect: number): void {
    this.camera.aspect = aspect;
    this.camera.updateProjectionMatrix();
  }

  public adjustYaw(deltaDegrees: number): void {
    this.desiredYaw = (this.desiredYaw + deltaDegrees) % 360;
  }

  public adjustElevation(deltaDegrees: number): void {
    this.desiredElevation = THREE.MathUtils.clamp(
      this.desiredElevation + deltaDegrees,
      this.profile.minElevation,
      this.profile.maxElevation,
    );
  }

  public adjustDistance(delta: number): void {
    this.desiredDistance = THREE.MathUtils.clamp(
      this.desiredDistance + delta,
      this.profile.minDistance,
      this.profile.maxDistance,
    );
  }

  public zoomByWheel(deltaY: number): void {
    this.adjustDistance(Math.sign(deltaY) * this.profile.wheelStep);
  }

  public reset(includeDistance: boolean): void {
    this.desiredYaw = this.profile.defaultYaw;
    this.desiredElevation = this.profile.defaultElevation;
    if (includeDistance) {
      this.desiredDistance = this.profile.defaultDistance;
    }
  }

  public update(deltaMs: number, playerPosition: Vec3): void {
    const followAlpha = Math.min(Math.max(deltaMs, 0) * 0.006, 1);
    const viewAlpha = Math.min(followAlpha * 2, 1);
    this.desiredTarget.set(playerPosition.x, playerPosition.y + 0.55, playerPosition.z);

    if (!this.initialized) {
      this.currentTarget.copy(this.desiredTarget);
      this.currentDistance = this.desiredDistance;
      this.currentElevation = this.desiredElevation;
      this.currentYaw = this.desiredYaw;
      this.initialized = true;
    } else {
      this.currentTarget.lerp(this.desiredTarget, followAlpha);
      this.currentDistance = lerp(this.currentDistance, this.desiredDistance, viewAlpha);
      this.currentElevation = lerp(this.currentElevation, this.desiredElevation, viewAlpha);
      this.currentYaw = lerpAngle(this.currentYaw, this.desiredYaw, viewAlpha);
    }

    const yaw = THREE.MathUtils.degToRad(this.currentYaw);
    const elevation = THREE.MathUtils.degToRad(this.currentElevation);
    const horizontal = this.currentDistance * Math.cos(elevation);
    this.camera.position.set(
      this.currentTarget.x + Math.sin(yaw) * horizontal,
      this.currentTarget.y + Math.sin(elevation) * this.currentDistance,
      this.currentTarget.z - Math.cos(yaw) * horizontal,
    );
    this.camera.lookAt(this.currentTarget);
  }

  public project(worldPosition: Vec3, width: number, height: number): ScreenPoint {
    const point = new THREE.Vector3(worldPosition.x, worldPosition.y, worldPosition.z);
    point.project(this.camera);
    return {
      x: (point.x + 1) * 0.5 * width,
      y: (1 - point.y) * 0.5 * height,
      visible: point.z >= -1 && point.z <= 1,
    };
  }
}
