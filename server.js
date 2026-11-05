'use strict';
// Entry point: loads config, connects to MongoDB Atlas, then starts Express.
require('dotenv').config();
const mongoose = require('mongoose');
const app = require('./src/app');

const { MONGODB_URI, JWT_SECRET } = process.env;
const PORT = process.env.PORT || 3000;

if (!MONGODB_URI || !JWT_SECRET) {
  console.error('[startup] MONGODB_URI and JWT_SECRET must be set (see .env.example).');
  process.exit(1);
}

mongoose
  .connect(MONGODB_URI)
  .then(() => {
    console.log('[startup] Connected to MongoDB');
    app.listen(PORT, () => console.log(`[startup] RoutinePulse API listening on port ${PORT}`));

    // Optional in-process scheduler. On Render's free tier the instance sleeps when idle, so for dependable
    // reminders also call POST /api/internal/run-reminders every minute from an external cron service.
    if (process.env.ENABLE_SCHEDULER === 'true') {
      const { runReminders } = require('./src/services/reminderRunner');
      setInterval(() => runReminders().catch((e) => console.error('[scheduler]', e.message)), 60 * 1000);
      console.log('[startup] In-process reminder scheduler enabled');
    }
  })
  .catch((err) => {
    console.error('[startup] MongoDB connection failed:', err.message);
    process.exit(1);
  });
