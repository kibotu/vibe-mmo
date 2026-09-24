import type { ItemDefinition, ItemStack } from './types';

export const ITEM_DEFINITIONS: Record<string, ItemDefinition> = {
  jellopy: {
    id: 'jellopy',
    name: 'Jellopy',
    color: '#d6c27b',
    glyph: 'J',
    stackable: true,
    maxStack: 99,
  },
  empty_bottle: {
    id: 'empty_bottle',
    name: 'Empty Bottle',
    color: '#7eaaa0',
    glyph: 'B',
    stackable: true,
    maxStack: 99,
  },
  apple: {
    id: 'apple',
    name: 'Apple',
    color: '#c85a48',
    glyph: 'A',
    stackable: true,
    maxStack: 99,
  },
  sticky_mucus: {
    id: 'sticky_mucus',
    name: 'Sticky Mucus',
    color: '#82a85c',
    glyph: 'M',
    stackable: true,
    maxStack: 99,
  },
  knife: {
    id: 'knife',
    name: 'Knife [4]',
    color: '#a4abb0',
    glyph: 'K',
    stackable: false,
    maxStack: 1,
  },
};

export class Inventory {
  public readonly capacity = 20;
  private readonly slots: Array<ItemStack | null>;

  public constructor() {
    this.slots = Array<ItemStack | null>(this.capacity).fill(null);
  }

  public add(itemId: string, quantity: number): number {
    const definition = ITEM_DEFINITIONS[itemId];
    if (!definition || !Number.isInteger(quantity) || quantity <= 0) {
      return quantity;
    }

    let remaining = quantity;
    if (definition.stackable) {
      for (const stack of this.slots) {
        if (!stack || stack.itemId !== itemId || stack.quantity >= definition.maxStack) {
          continue;
        }
        const accepted = Math.min(remaining, definition.maxStack - stack.quantity);
        stack.quantity += accepted;
        remaining -= accepted;
        if (remaining === 0) {
          return 0;
        }
      }
    }

    for (let index = 0; index < this.slots.length && remaining > 0; index += 1) {
      if (this.slots[index]) {
        continue;
      }
      const accepted = definition.stackable ? Math.min(remaining, definition.maxStack) : 1;
      this.slots[index] = { itemId, quantity: accepted };
      remaining -= accepted;
      if (!definition.stackable && remaining > 0) {
        continue;
      }
    }

    return remaining;
  }

  public remove(itemId: string, quantity = 1): boolean {
    if (!Number.isInteger(quantity) || quantity <= 0 || this.count(itemId) < quantity) {
      return false;
    }

    let remaining = quantity;
    for (let index = 0; index < this.slots.length && remaining > 0; index += 1) {
      const stack = this.slots[index];
      if (!stack || stack.itemId !== itemId) {
        continue;
      }
      const removed = Math.min(remaining, stack.quantity);
      stack.quantity -= removed;
      remaining -= removed;
      if (stack.quantity === 0) {
        this.slots[index] = null;
      }
    }
    return remaining === 0;
  }

  public getSlots(): ReadonlyArray<ItemStack | null> {
    return this.slots;
  }

  public count(itemId: string): number {
    return this.slots.reduce((total, stack) => {
      return total + (stack?.itemId === itemId ? stack.quantity : 0);
    }, 0);
  }
}
