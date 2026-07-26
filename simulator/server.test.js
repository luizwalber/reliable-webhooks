const test = require('node:test');
const assert = require('node:assert/strict');
const { createApp } = require('./server');

function startServer() {
  const server = createApp().listen(0);
  const { port } = server.address();
  return { server, baseUrl: `http://localhost:${port}` };
}

test('success always returns 200', async () => {
  const { server, baseUrl } = startServer();
  try {
    const response = await fetch(`${baseUrl}/simulate/success`, { method: 'POST' });
    assert.equal(response.status, 200);
  } finally {
    server.close();
  }
});

test('error-500 always returns 500', async () => {
  const { server, baseUrl } = startServer();
  try {
    const response = await fetch(`${baseUrl}/simulate/error-500`, { method: 'POST' });
    assert.equal(response.status, 500);
  } finally {
    server.close();
  }
});

test('intermittent alternates success, failure, success, failure...', async () => {
  const { server, baseUrl } = startServer();
  try {
    const statuses = [];
    for (let i = 0; i < 4; i++) {
      const response = await fetch(`${baseUrl}/simulate/intermittent`, { method: 'POST' });
      statuses.push(response.status);
    }
    assert.deepEqual(statuses, [200, 500, 200, 500]);
  } finally {
    server.close();
  }
});
