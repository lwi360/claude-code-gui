/**
 * stdin: {"prompt":"..."}
 * stdout: one JSON line {"ok":true,"message":"..."} or {"ok":false,"code":"...","error":"..."}
 */
import { generateCommitMessage } from './services/commit-message-service.js';

function writeResult(payload) {
  process.stdout.write(JSON.stringify(payload) + '\n');
}

let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => {
  input += chunk;
});
process.stdin.on('end', async () => {
  try {
    const parsed = JSON.parse(input || '{}');
    const message = await generateCommitMessage(parsed.prompt || '');
    writeResult({ ok: true, message });
  } catch (error) {
    writeResult({
      ok: false,
      code: error.code || 'error',
      error: error.message || String(error)
    });
  }
});
