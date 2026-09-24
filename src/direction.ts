const TAU = Math.PI * 2;

export const normalizeAngle = (angle: number): number => {
  const result = angle % TAU;
  return result < 0 ? result + TAU : result;
};

/**
 * Converts a world heading into the eight sprite views.
 * Sprite view zero faces south; world heading zero faces north.
 */
export const spriteDirection = (facing: number, cameraYawDegrees: number): number => {
  const relative = normalizeAngle(facing - cameraYawDegrees * Math.PI / 180);
  return (Math.round(relative / (Math.PI / 4)) + 4) % 8;
};
