'use strict';
// Builds the Express app (kept separate from server.js so it can be imported without connecting to a DB).
const express = require('express');
const cors = require('cors');
const mongoose = require('mongoose');

const app = express();
app.use(cors());
app.use(express.json({ limit: '200kb' }));

// Health check - used by Render and by the Android Settings screen ("Cloud status").
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    db: mongoose.connection.readyState === 1 ? 'connected' : 'disconnected',
    time: new Date().toISOString(),
  });
});
app.get('/', (req, res) => res.type('text').send('RoutinePulse API is running. See /health'));

app.use('/api/auth', require('./routes/auth'));
app.use('/api/habits', require('./routes/habits'));
app.use('/api/notes', require('./routes/notes'));
app.use('/api/quizzes', require('./routes/quizzes'));
app.use('/api/devices', require('./routes/devices'));
app.use('/api/internal', require('./routes/internal'));

// 404 for unknown routes
app.use((req, res) => res.status(404).json({ error: 'Route not found' }));

// Central error handler: log details server-side, return a generic message to the client.
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  if (err && err.type === 'entity.parse.failed') return res.status(400).json({ error: 'Invalid JSON body' });
  console.error('[error]', err);
  res.status(500).json({ error: 'Something went wrong on the server. Please try again.' });
});

module.exports = app;
