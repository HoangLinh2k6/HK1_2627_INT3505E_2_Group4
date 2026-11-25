import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { once } from 'node:events';
import { createServer } from 'vite';
import configureVite from '../apps/web/vite.config.js';

test('development proxy preserves the v1 prefix for API and documentation requests', async (t) => {
  const backend = http.createServer((request, response) => {
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify({ method: request.method, path: request.url }));
  });
  backend.listen(0, '127.0.0.1');
  await once(backend, 'listening');
  t.after(() => new Promise((resolve) => backend.close(resolve)));

  const configuration = configureVite({ mode: 'test' });
  const vite = await createServer({
    ...configuration,
    configFile: false,
    appType: 'custom',
    server: {
      middlewareMode: true,
      hmr: false,
      watch: null,
      proxy: {
        '/api/v1': {
          ...configuration.server.proxy['/api/v1'],
          target: `http://127.0.0.1:${backend.address().port}`,
        },
      },
    },
  });
  t.after(() => vite.close());
  const frontend = http.createServer(vite.middlewares);
  frontend.listen(0, '127.0.0.1');
  await once(frontend, 'listening');
  t.after(() => new Promise((resolve) => frontend.close(resolve)));
  const origin = `http://127.0.0.1:${frontend.address().port}`;

  const login = await fetch(`${origin}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'proxy@example.test', password: 'Example123' }),
  });
  assert.equal(login.status, 200);
  assert.deepEqual(await login.json(), { method: 'POST', path: '/api/v1/auth/login' });

  const specification = await fetch(`${origin}/api/v1/openapi.yaml`);
  assert.equal(specification.status, 200);
  assert.deepEqual(await specification.json(), { method: 'GET', path: '/api/v1/openapi.yaml' });
});
