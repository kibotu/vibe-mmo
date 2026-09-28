export const PLAYER_ATTACK = 12;
export const ATTACK_RANGE = 1.5;
export const ATTACK_INTERVAL = 0.8;
export const ATTACK_IMPACT_DELAY = 0.18;

export const calculateDamage = (attack: number, defense: number, variance: number): number =>
  Math.max(1, Math.round((attack - defense * 0.5) * variance));
