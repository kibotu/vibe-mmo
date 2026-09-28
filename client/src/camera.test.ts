import { describe, expect, it } from 'vitest';
import { cameraProfileFor, CLASSIC_2003_PROFILE, PRE_RENEWAL_2008_PROFILE, ROCamera } from './camera';

describe('classic camera controller', () => {
  it('places the camera using the documented spherical model', () => {
    const camera = new ROCamera(4 / 3);
    const target = { x: 10, y: 2, z: -4 };
    camera.update(0, target);
    const elevation = CLASSIC_2003_PROFILE.defaultElevation * Math.PI / 180;
    const horizontal = CLASSIC_2003_PROFILE.defaultDistance * Math.cos(elevation);
    expect(camera.camera.position.x).toBeCloseTo(target.x, 3);
    expect(camera.camera.position.y).toBeCloseTo(target.y + 0.55 + CLASSIC_2003_PROFILE.defaultDistance * Math.sin(elevation), 3);
    expect(camera.camera.position.z).toBeCloseTo(target.z - horizontal, 3);
  });

  it('resets direction and distance independently', () => {
    const camera = new ROCamera(4 / 3);
    camera.adjustYaw(90);
    camera.adjustElevation(-20);
    camera.adjustDistance(30);
    camera.update(16, { x: 0, y: 0, z: 0 });
    camera.reset(false);
    camera.update(10_000, { x: 0, y: 0, z: 0 });
    expect(Math.abs(camera.yaw)).toBeLessThan(0.001);
    expect(Math.abs(camera.elevation - CLASSIC_2003_PROFILE.defaultElevation)).toBeLessThan(0.001);
    expect(camera.distance).toBeGreaterThan(CLASSIC_2003_PROFILE.defaultDistance);

    camera.reset(true);
    camera.update(10_000, { x: 0, y: 0, z: 0 });
    expect(camera.distance).toBeCloseTo(CLASSIC_2003_PROFILE.defaultDistance, 3);
  });

  it('selects the named comparison profile without changing the default', () => {
    expect(cameraProfileFor(null)).toBe(CLASSIC_2003_PROFILE);
    expect(cameraProfileFor('preRenewal2008')).toBe(PRE_RENEWAL_2008_PROFILE);
    expect(cameraProfileFor('unknown')).toBe(CLASSIC_2003_PROFILE);
  });

  it('clamps the minimum and maximum zoom bounds', () => {
    const camera = new ROCamera(4 / 3);
    camera.adjustDistance(-1000);
    camera.update(10_000, { x: 0, y: 0, z: 0 });
    expect(camera.distance).toBeCloseTo(CLASSIC_2003_PROFILE.minDistance, 3);
    camera.adjustDistance(1000);
    camera.update(10_000, { x: 0, y: 0, z: 0 });
    expect(camera.distance).toBeCloseTo(CLASSIC_2003_PROFILE.maxDistance, 3);
  });
});
