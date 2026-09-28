import { defineConfig, loadEnv } from 'vite';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

const normalizeBase = (value: string | undefined, fallback: string): string => {
  const configured = value?.trim() || fallback;
  if (configured === './' || configured === '.') return './';
  if (configured.startsWith('.') || configured.startsWith('/')) {
    return configured.endsWith('/') ? configured : `${configured}/`;
  }

  return `/${configured.replace(/^\/+/, '').replace(/\/+$/, '')}/`;
};

export const createClientConfig = (clientDirectory: string, fallbackBase: string) => {
  const root = resolve(repositoryRoot, clientDirectory);

  return defineConfig(({ mode }) => {
    const fileEnvironment = loadEnv(mode, root, '');
    const configuredBase = process.env.BASE_PATH
      ?? process.env.VITE_BASE_PATH
      ?? fileEnvironment.BASE_PATH
      ?? fileEnvironment.VITE_BASE_PATH;

    return {
      root,
      base: normalizeBase(configuredBase, fallbackBase),
      publicDir: resolve(repositoryRoot, 'client/public'),
      build: {
        outDir: resolve(root, 'dist'),
        emptyOutDir: true,
      },
      server: {
        fs: {
          strict: true,
          allow: [repositoryRoot],
        },
      },
    };
  });
};
