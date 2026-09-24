import { existsSync } from 'node:fs';
import { chromium } from 'playwright-core';

const chromePath = process.env.CHROME_PATH ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
if (!existsSync(chromePath)) {
  throw new Error(`Chrome executable not found: ${chromePath}. Set CHROME_PATH to a local Chrome binary.`);
}

const browser = await chromium.launch({
  headless: true,
  executablePath: chromePath,
  args: ['--disable-gpu'],
});
const page = await browser.newPage({ viewport: { width: 800, height: 600 } });
const pageErrors = [];
page.on('pageerror', (error) => pageErrors.push(error.message));

await page.goto('http://127.0.0.1:5173/?seed=1337', { waitUntil: 'networkidle' });
await page.waitForFunction(() => window.__RO_READY__ === true, null, { timeout: 10_000 });

const initial = await page.evaluate(() => {
  const game = window.__RO_GAME__;
  const canvas = document.querySelector('canvas');
  return {
    ready: window.__RO_READY__,
    hasCanvas: Boolean(canvas),
    audioElements: document.querySelectorAll('audio').length,
    cameraDistance: game.camera.distance,
    cameraYaw: game.camera.yaw,
  };
});
if (!initial.ready || !initial.hasCanvas || initial.audioElements !== 0) {
  throw new Error(`Invalid initial state: ${JSON.stringify(initial)}`);
}

const findProjectedCell = (kind) => page.evaluate((cellKind) => {
  const game = window.__RO_GAME__;
  const rect = document.querySelector('canvas').getBoundingClientRect();
  const candidates = [];
  for (let z = 1; z < game.world.grid.height - 1; z += 1) {
    for (let x = 1; x < game.world.grid.width - 1; x += 1) {
      const walkable = game.world.grid.isWalkable(x, z);
      const passable = game.world.grid.isPassable(x, z);
      const matches = cellKind === 'valid'
        ? passable
        : !walkable && !game.world.grid.isWater(x, z) && !game.world.grid.isBridge(x, z);
      if (!matches) continue;
      const center = game.world.grid.cellCenter({ x, z });
      const point = game.camera.project(center, 800, 600);
      if (!point.visible || point.x < 50 || point.x > 750 || point.y < 90 || point.y > 500) continue;
      const clearOfActors = [...game.actors.values()].every((actor) => {
        const actorPoint = game.camera.project(actor.position, 800, 600);
        return Math.hypot(actorPoint.x - point.x, actorPoint.y - point.y) > 38;
      });
      if (!clearOfActors) continue;
      candidates.push({ x, z, point, distance: Math.hypot(center.x - game.player.position.x, center.z - game.player.position.z) });
    }
  }
  candidates.sort((a, b) => cellKind === 'valid' ? a.distance - b.distance : b.distance - a.distance);
  const candidate = candidates.find(({ x, z }) => cellKind !== 'valid' || Math.hypot(x + 0.5 - game.player.position.x, z + 0.5 - game.player.position.z) > 2);
  return candidate ? {
    x: rect.left + candidate.point.x / 800 * rect.width,
    y: rect.top + candidate.point.y / 600 * rect.height,
  } : null;
}, kind);

const initialPosition = await page.evaluate(() => ({ ...window.__RO_GAME__.player.position }));
const walkTarget = await findProjectedCell('valid');
if (!walkTarget) throw new Error('Could not find a visible walkable cell');
await page.mouse.click(walkTarget.x, walkTarget.y);
await page.waitForFunction((origin) => {
  const game = window.__RO_GAME__;
  return game.player.path.length === 0 && Math.hypot(
    game.player.position.x - origin.x,
    game.player.position.z - origin.z,
  ) > 0.5;
}, initialPosition, { timeout: 5_000 });

const blockedTarget = await findProjectedCell('blocked');
if (!blockedTarget) throw new Error('Could not find a visible blocked cell');
const beforeBlockedClick = await page.evaluate(() => ({ ...window.__RO_GAME__.player.position }));
await page.mouse.click(blockedTarget.x, blockedTarget.y);
await page.waitForTimeout(200);
const afterBlockedClick = await page.evaluate(() => ({ ...window.__RO_GAME__.player.position }));
if (Math.hypot(afterBlockedClick.x - beforeBlockedClick.x, afterBlockedClick.z - beforeBlockedClick.z) > 0.01) {
  throw new Error(`Blocked cell moved the player: ${JSON.stringify({ beforeBlockedClick, afterBlockedClick })}`);
}

await page.evaluate(() => {
  const game = window.__RO_GAME__;
  game.player.hp = 10;
  if (game.inventory.add('apple', 1) !== 0) throw new Error('Could not seed the healing test');
});
await page.keyboard.press('h');
await page.waitForFunction(() => window.__RO_GAME__.player.hp === 25, null, { timeout: 1_000 });

await page.mouse.move(400, 300);
await page.mouse.down({ button: 'right' });
await page.mouse.move(470, 300, { steps: 5 });
await page.mouse.up({ button: 'right' });
await page.waitForTimeout(500);
const rotated = await page.evaluate(() => ({
  yaw: window.__RO_GAME__.camera.yaw,
  distance: window.__RO_GAME__.camera.distance,
}));
if (Math.abs(rotated.yaw - initial.cameraYaw) < 5) {
  throw new Error(`Camera rotation did not register: ${JSON.stringify(rotated)}`);
}
const elevationBefore = await page.evaluate(() => window.__RO_GAME__.camera.elevation);
await page.keyboard.down('Shift');
await page.mouse.move(400, 300);
await page.mouse.down({ button: 'right' });
await page.mouse.move(400, 360, { steps: 5 });
await page.mouse.up({ button: 'right' });
await page.keyboard.up('Shift');
await page.waitForTimeout(500);
const tilted = await page.evaluate(() => window.__RO_GAME__.camera.elevation);
if (tilted <= elevationBefore) {
  throw new Error(`Camera tilt did not register: ${JSON.stringify({ elevationBefore, tilted })}`);
}
const distanceBeforeControl = await page.evaluate(() => window.__RO_GAME__.camera.distance);
await page.keyboard.down('Control');
await page.mouse.move(400, 300);
await page.mouse.down({ button: 'right' });
await page.mouse.move(400, 340, { steps: 5 });
await page.mouse.up({ button: 'right' });
await page.keyboard.up('Control');
await page.waitForTimeout(500);
const controlZoomed = await page.evaluate(() => window.__RO_GAME__.camera.distance);
if (controlZoomed <= distanceBeforeControl) {
  throw new Error(`Control zoom did not register: ${JSON.stringify({ distanceBeforeControl, controlZoomed })}`);
}
await page.mouse.wheel(0, 120);
await page.waitForTimeout(500);
const zoomed = await page.evaluate(() => window.__RO_GAME__.camera.distance);
if (zoomed <= rotated.distance) {
  throw new Error(`Camera zoom did not register: ${JSON.stringify({ rotated, zoomed })}`);
}
await page.mouse.click(400, 300, { button: 'right', clickCount: 2 });
await page.waitForTimeout(700);
const directionReset = await page.evaluate(() => ({
  yaw: window.__RO_GAME__.camera.yaw,
  distance: window.__RO_GAME__.camera.distance,
}));
if (Math.abs(directionReset.yaw) > 2 || Math.abs(directionReset.distance - zoomed) > 1) {
  throw new Error(`Direction reset did not register: ${JSON.stringify(directionReset)}`);
}
await page.keyboard.down('Shift');
await page.mouse.click(400, 300, { button: 'right', clickCount: 2 });
await page.keyboard.up('Shift');
await page.waitForTimeout(700);
const fullReset = await page.evaluate(() => ({
  yaw: window.__RO_GAME__.camera.yaw,
  distance: window.__RO_GAME__.camera.distance,
}));
if (Math.abs(fullReset.yaw) > 2 || Math.abs(fullReset.distance - 50) > 1) {
  throw new Error(`Full camera reset did not register: ${JSON.stringify(fullReset)}`);
}

const poringClick = await page.evaluate(() => {
  const game = window.__RO_GAME__;
  const rect = document.querySelector('canvas').getBoundingClientRect();
  const candidates = [...game.actors.values()].filter((actor) => actor.kind === 'poring');
  const candidate = candidates
    .map((actor) => ({ actor, point: game.camera.project(actor.position, 800, 600) }))
    .find(({ point }) => point.visible && point.x > 30 && point.x < 770 && point.y > 70 && point.y < 520) ?? candidates[0];
  return {
    x: rect.left + candidate.point.x / 800 * rect.width,
    y: rect.top + candidate.point.y / 600 * rect.height,
  };
});
await page.mouse.click(poringClick.x, poringClick.y);
await page.waitForFunction(() => window.__RO_GAME__.floorItems.length > 0, null, { timeout: 30_000 });

const itemClick = await page.evaluate(() => {
  const game = window.__RO_GAME__;
  const rect = document.querySelector('canvas').getBoundingClientRect();
  const item = game.floorItems[0];
  const point = game.camera.project({
    x: item.object.position.x,
    y: item.object.position.y,
    z: item.object.position.z,
  }, 800, 600);
  return {
    x: rect.left + point.x / 800 * rect.width,
    y: rect.top + point.y / 600 * rect.height,
  };
});
await page.mouse.click(itemClick.x, itemClick.y);
await page.keyboard.press('i');
await page.waitForTimeout(100);
const openedWithShortcut = await page.evaluate(() => !document.querySelector('#inventory-window').classList.contains('is-hidden'));
await page.keyboard.press('Escape');
await page.waitForTimeout(100);
const closedWithEscape = await page.evaluate(() => document.querySelector('#inventory-window').classList.contains('is-hidden'));
await page.keyboard.down('Alt');
await page.keyboard.press('e');
await page.keyboard.up('Alt');
await page.waitForTimeout(100);
const reopenedWithAltE = await page.evaluate(() => !document.querySelector('#inventory-window').classList.contains('is-hidden'));
if (!openedWithShortcut || !closedWithEscape || !reopenedWithAltE) {
  throw new Error(`Inventory shortcut regression: ${JSON.stringify({ openedWithShortcut, closedWithEscape, reopenedWithAltE })}`);
}

const final = await page.evaluate(() => ({
  floorItems: window.__RO_GAME__.floorItems.length,
  inventory: window.__RO_GAME__.inventory.getSlots().filter(Boolean),
  inventoryOpen: !document.querySelector('#inventory-window').classList.contains('is-hidden'),
  audioElements: document.querySelectorAll('audio').length,
}));

if (pageErrors.length > 0) {
  throw new Error(`Page errors: ${pageErrors.join('; ')}`);
}
if (final.floorItems !== 0 || final.inventory.length === 0 || !final.inventoryOpen || final.audioElements !== 0) {
  throw new Error(`Invalid final state: ${JSON.stringify(final)}`);
}

console.log(JSON.stringify({ initial, final }, null, 2));
await browser.close();
