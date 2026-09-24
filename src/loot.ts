import type { Random } from './random';

export interface LootEntry {
  itemId: string | null;
  weight: number;
}

export const PORING_LOOT_TABLE: readonly LootEntry[] = [
  { itemId: 'jellopy', weight: 0.65 },
  { itemId: 'empty_bottle', weight: 0.15 },
  { itemId: 'apple', weight: 0.10 },
  { itemId: 'sticky_mucus', weight: 0.04 },
  { itemId: 'knife', weight: 0.01 },
  { itemId: null, weight: 0.05 },
];

export const selectWeighted = <T>(entries: readonly { value: T; weight: number }[], value: number): T | null => {
  const totalWeight = entries.reduce((sum, entry) => sum + entry.weight, 0);
  if (totalWeight <= 0) return null;

  const target = Math.min(Math.max(value, 0), totalWeight);
  let cursor = 0;
  for (const entry of entries) {
    cursor += entry.weight;
    if (target < cursor) return entry.value;
  }
  return entries[entries.length - 1]?.value ?? null;
};

export const rollLoot = (random: Random, table: readonly LootEntry[] = PORING_LOOT_TABLE): string | null =>
  selectWeighted(
    table.map((entry) => ({ value: entry.itemId, weight: entry.weight })),
    random.next() * table.reduce((sum, entry) => sum + entry.weight, 0),
  );
