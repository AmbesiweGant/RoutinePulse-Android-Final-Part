'use strict';
const mongoose = require('mongoose');

const isId = (id) => mongoose.isValidObjectId(id);
const escapeRegex = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const notFound = (res, what) => res.status(404).json({ error: `${what} not found` });

module.exports = { isId, escapeRegex, notFound };
