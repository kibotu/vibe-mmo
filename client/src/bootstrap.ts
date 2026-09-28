import { cameraProfileFor } from './camera';
import { Game } from './game';
import { fetchMultiplayerSession, MultiplayerClient } from './multiplayer';

const SESSION_ENDPOINT = '/api/session.php';

const readyWindow = window as Window & {
  __RO_GAME__?: Game;
  __RO_MULTIPLAYER__?: MultiplayerClient;
  __RO_READY__?: boolean;
  __RO_SESSION__?: unknown;
};

const getCanvas = (): HTMLCanvasElement => {
  const element = document.getElementById('game-canvas');
  if (!(element instanceof HTMLCanvasElement)) {
    throw new Error('The game canvas is missing');
  }

  return element;
};

const getProfile = () => cameraProfileFor(new URLSearchParams(window.location.search).get('profile'));

export const isLobbyPath = (): boolean => {
  const path = window.location.pathname.replace(/\/+$/, '');

  return path === '/lobby' || path === '/lobby.php';
};

export const showBootError = (message: string): void => {
  const panel = document.getElementById('connection-panel');
  const status = document.getElementById('connection-status');
  const detail = document.getElementById('connection-message');
  panel?.classList.remove('is-hidden');
  panel?.classList.add('is-error');
  if (status) status.textContent = 'ERROR';
  if (detail) detail.textContent = message;
};

export const redirectLobbyToMultiplayer = (): void => {
  if (isLobbyPath()) {
    window.location.replace('/game/');
  }
};

export const startOffline = (): void => {
  const params = new URLSearchParams(window.location.search);
  const seedParam = params.get('seed');
  const seed = seedParam ? Number.parseInt(seedParam, 10) || 1337 : 1337;
  const game = new Game(getCanvas(), seed, getProfile());
  readyWindow.__RO_GAME__ = game;
  game.start();
};

export const startMultiplayer = async (): Promise<void> => {
  const panel = document.getElementById('connection-panel');
  const status = document.getElementById('connection-status');
  const detail = document.getElementById('connection-message');
  panel?.classList.remove('is-hidden');
  panel?.classList.add('is-connecting');
  if (status) status.textContent = 'CONNECTING';
  if (detail) detail.textContent = 'Creating a room session…';

  // The session request intentionally happens before Game construction. A
  // failed session must never silently fall back to the offline simulation.
  const session = await fetchMultiplayerSession(SESSION_ENDPOINT);
  readyWindow.__RO_SESSION__ = session;

  const game = new Game(getCanvas(), session.seed, getProfile(), {
    multiplayer: true,
    playerId: session.player.id,
    playerName: session.player.name,
    roomName: session.room.name,
    interpolationMs: session.config.interpolationMs,
  });
  const client = new MultiplayerClient({
    session,
    refreshSession: () => fetchMultiplayerSession(SESSION_ENDPOINT),
  });
  readyWindow.__RO_GAME__ = game;
  readyWindow.__RO_MULTIPLAYER__ = client;
  window.addEventListener('pagehide', () => client.disconnect(), { once: true });
  game.attachMultiplayer(client);
  game.start();
};
