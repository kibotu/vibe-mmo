import './style.css';
import { cameraProfileFor } from './camera';
import { Game } from './game';

const canvas = document.getElementById('game-canvas');
if (!(canvas instanceof HTMLCanvasElement)) {
  throw new Error('The game canvas is missing');
}

const params = new URLSearchParams(window.location.search);
const seedParam = params.get('seed');
const seed = seedParam ? Number.parseInt(seedParam, 10) || 1337 : 1337;
const game = new Game(canvas, seed, cameraProfileFor(params.get('profile')));
(window as Window & { __RO_GAME__?: Game }).__RO_GAME__ = game;
game.start();
