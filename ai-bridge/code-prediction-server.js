import './code-prediction-log.js';
import { writeSync } from 'node:fs';
import readline from 'node:readline';
import { predictStreaming } from './services/code-prediction-service.js';

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
let current = null;

function write(payload) {
  writeSync(1, JSON.stringify(payload) + '\n');
}

rl.on('line', (line) => {
  const trimmed = line.trim();
  if (!trimmed) {
    return;
  }
  let request;
  try {
    request = JSON.parse(trimmed);
  } catch {
    return;
  }
  if (!request.id) {
    return;
  }
  if (current) {
    current.abort.abort();
    current = null;
  }
  if (request.cancel) {
    write({ id: request.id, done: true, ok: true });
    return;
  }

  const abort = new AbortController();
  const ticket = { id: request.id, abort, chars: 0 };
  const started = Date.now();
  current = ticket;
  predictStreaming(request, abort.signal, (delta) => {
    if (current !== ticket || !delta) {
      return;
    }
    ticket.chars += delta.length;
    write({ id: request.id, delta });
  }).catch((error) => {
    if (current === ticket && !abort.signal.aborted) {
      console.error('[code-prediction]', error && error.message ? error.message : error);
    }
  }).finally(() => {
    if (current === ticket) {
      console.error(`[code-prediction] done ${Date.now() - started}ms chars=${ticket.chars}`);
    }
    if (current !== ticket) {
      return;
    }
    current = null;
    write({ id: request.id, done: true, ok: true });
  });
});
