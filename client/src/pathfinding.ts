import type { GridAccess, GridCell } from './types';

interface SearchNode {
  cell: GridCell;
  g: number;
  f: number;
}

const key = (x: number, z: number): string => `${x},${z}`;

const heuristic = (ax: number, az: number, bx: number, bz: number): number => {
  const dx = Math.abs(ax - bx);
  const dz = Math.abs(az - bz);
  return Math.max(dx, dz) + (Math.SQRT2 - 1) * Math.min(dx, dz);
};

const sameCell = (a: GridCell, b: GridCell): boolean => a.x === b.x && a.z === b.z;

export const findPath = (grid: GridAccess, start: GridCell, goal: GridCell): GridCell[] => {
  if (!grid.isWalkable(goal.x, goal.z) || !grid.isWalkable(start.x, start.z)) {
    return [];
  }

  if (sameCell(start, goal)) {
    return [];
  }

  const open: SearchNode[] = [{ cell: start, g: 0, f: heuristic(start.x, start.z, goal.x, goal.z) }];
  const cameFrom = new Map<string, GridCell>();
  const gScore = new Map<string, number>([[key(start.x, start.z), 0]]);
  const directions = [
    [-1, -1], [0, -1], [1, -1],
    [-1, 0], [1, 0],
    [-1, 1], [0, 1], [1, 1],
  ];

  while (open.length > 0) {
    open.sort((a, b) => a.f - b.f || a.cell.z - b.cell.z || a.cell.x - b.cell.x);
    const current = open.shift();
    if (!current) {
      break;
    }

    if (sameCell(current.cell, goal)) {
      const result: GridCell[] = [];
      let cursor = goal;
      while (!sameCell(cursor, start)) {
        result.push(cursor);
        const previous = cameFrom.get(key(cursor.x, cursor.z));
        if (!previous) {
          return [];
        }
        cursor = previous;
      }
      result.reverse();
      return result;
    }

    for (const [dx, dz] of directions) {
      const nx = current.cell.x + dx;
      const nz = current.cell.z + dz;
      if (!grid.isWalkable(nx, nz)) {
        continue;
      }

      if (dx !== 0 && dz !== 0) {
        if (!grid.isWalkable(current.cell.x + dx, current.cell.z) ||
            !grid.isWalkable(current.cell.x, current.cell.z + dz)) {
          continue;
        }
      }

      const step = dx !== 0 && dz !== 0 ? Math.SQRT2 : 1;
      const tentativeG = current.g + step;
      const nodeKey = key(nx, nz);
      if (tentativeG >= (gScore.get(nodeKey) ?? Number.POSITIVE_INFINITY)) {
        continue;
      }

      cameFrom.set(nodeKey, current.cell);
      gScore.set(nodeKey, tentativeG);
      open.push({
        cell: { x: nx, z: nz },
        g: tentativeG,
        f: tentativeG + heuristic(nx, nz, goal.x, goal.z),
      });
    }
  }

  return [];
};

export const cellDistance = (a: GridCell, b: GridCell): number =>
  Math.hypot(a.x - b.x, a.z - b.z);
