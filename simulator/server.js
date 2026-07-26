const express = require('express');

const TIMEOUT_DELAY_MS = 10_000; // longer than the backend's default DELIVERY_HTTP_TIMEOUT_MS (3000ms)

/** Fresh state per app instance — no database, no persistence; restart the container to reset (docs/adr/0010-simulated-consumers). */
function createApp() {
  const app = express();
  let intermittentCallCount = 0;

  app.get('/health', (req, res) => res.sendStatus(200));

  app.post('/simulate/success', (req, res) => res.sendStatus(200));

  app.post('/simulate/error-500', (req, res) => res.sendStatus(500));

  app.post('/simulate/timeout', (req, res) => {
    setTimeout(() => res.sendStatus(200), TIMEOUT_DELAY_MS);
  });

  app.post('/simulate/intermittent', (req, res) => {
    intermittentCallCount += 1;
    res.sendStatus(intermittentCallCount % 2 === 0 ? 500 : 200);
  });

  return app;
}

if (require.main === module) {
  const port = process.env.PORT || 4000;
  createApp().listen(port, () => {
    console.log(`Simulator listening on port ${port}`);
  });
}

module.exports = { createApp };
