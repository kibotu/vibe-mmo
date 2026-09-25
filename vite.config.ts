import { defineConfig, loadEnv } from 'vite';

const normalizeBase = (value: string | undefined): string => {
  const configured = value?.trim() || './';
  if (configured === './' || configured === '.') return './';
  if (configured.startsWith('.') || configured.startsWith('/')) {
    return configured.endsWith('/') ? configured : `${configured}/`;
  }
  return `/${configured.replace(/^\/+/, '').replace(/\/+$/, '')}/`;
};

export default defineConfig(({ mode }) => {
  const fileEnvironment = loadEnv(mode, process.cwd(), '');
  const configuredBase = process.env.BASE_PATH
    ?? process.env.VITE_BASE_PATH
    ?? fileEnvironment.BASE_PATH
    ?? fileEnvironment.VITE_BASE_PATH;
  return {
    // `./` keeps the GitHub Pages artifact relocatable.  Set BASE_PATH (or
    // VITE_BASE_PATH) to `/game/` for the backend-served build.
    base: normalizeBase(configuredBase),
    server: {
      fs: {
        strict: true,
        allow: ['.'],
      },
    },
  };
});
