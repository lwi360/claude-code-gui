/**
 * One short completion at the caret. No tools and no agent turn.
 */

import { loadClaudeSettings, setupApiKey } from '../config/api-config.js';
import { ensureAnthropicSdk, ensureBedrockSdk } from './claude/message-utils.js';
import { resolveModelFromSettings } from '../utils/model-utils.js';

const DEFAULT_HAIKU_MODEL = 'claude-haiku-4-5-20251001';
const API_TIMEOUT_MS = 20000;
const MAX_OUTPUT_TOKENS = 64;
const MAX_LINES = 4;
const MAX_LINE_LENGTH = 240;

function resolveFastModel() {
  const settings = loadClaudeSettings();
  return resolveModelFromSettings(DEFAULT_HAIKU_MODEL, settings?.env) || DEFAULT_HAIKU_MODEL;
}

async function createClient(config) {
  const headers = { 'x-app': 'cli', 'User-Agent': 'claude-cli' };
  if (config.authType === 'aws_bedrock') {
    const bedrockModule = await ensureBedrockSdk();
    const AnthropicBedrock = bedrockModule.AnthropicBedrock || bedrockModule.default || bedrockModule;
    return new AnthropicBedrock({ defaultHeaders: headers });
  }

  const anthropicModule = await ensureAnthropicSdk();
  const Anthropic = anthropicModule.default || anthropicModule.Anthropic || anthropicModule;
  if (config.authType === 'auth_token') {
    return new Anthropic({
      authToken: config.apiKey,
      apiKey: null,
      baseURL: config.baseUrl || undefined,
      defaultHeaders: headers
    });
  }
  return new Anthropic({
    apiKey: config.apiKey,
    baseURL: config.baseUrl || undefined,
    defaultHeaders: headers
  });
}

export function sanitizeSuggestion(prefix, raw) {
  if (!raw) {
    return '';
  }
  let text = String(raw).replace(/\r\n/g, '\n');
  const fenced = text.match(/^```[a-zA-Z0-9]*\n([\s\S]*?)\n```$/);
  if (fenced) {
    text = fenced[1];
  } else {
    text = text.replace(/^```[a-zA-Z0-9]*\n?/, '').replace(/\n?```$/, '');
  }
  text = text.replace(/<\/?CURSOR>/g, '');
  const lastLine = String(prefix || '').slice(String(prefix || '').lastIndexOf('\n') + 1);
  if (lastLine && text.startsWith(lastLine)) {
    text = text.slice(lastLine.length);
  }
  if (String(prefix || '').endsWith('\n')) {
    text = text.replace(/^\n+/, '');
  }
  const lines = text.split('\n').slice(0, MAX_LINES).map((line) => (
    line.length > MAX_LINE_LENGTH ? line.slice(0, MAX_LINE_LENGTH) : line
  ));
  text = lines.join('\n').replace(/\s+$/u, '');
  return text.trim() ? text : '';
}

function buildMessages(prefix) {
  const lastBreak = prefix.lastIndexOf('\n');
  const lastLine = lastBreak >= 0 ? prefix.slice(lastBreak + 1) : prefix;
  // Prefill the current line so the model continues the code instead of explaining it.
  if (!lastLine.trim()) {
    return [{ role: 'user', content: prefix }];
  }
  return [
    { role: 'user', content: prefix },
    { role: 'assistant', content: lastLine }
  ];
}

function stripLongOverlap(prefix, text) {
  const source = String(prefix || '');
  const max = Math.min(source.length, text.length);
  for (let size = max; size >= 8; size--) {
    if (text.startsWith(source.slice(source.length - size))) {
      return text.slice(size);
    }
  }
  return text;
}

function isProse(text) {
  const line = text.trim().split('\n')[0] || '';
  if (/^[\u4e00-\u9fff]/.test(line)) {
    return true;
  }
  return /^[A-Z][a-z]+ /.test(line) && !/[;{}()]/.test(line);
}

export function presentSuggestion(prefix, raw) {
  let text = String(raw || '').replace(/\r\n/g, '\n');
  const fenced = text.match(/```[a-zA-Z0-9]*\n([\s\S]*?)(?:```|$)/);
  if (fenced) {
    text = fenced[1];
  }
  text = stripLongOverlap(prefix, text);
  text = sanitizeSuggestion(prefix, text);
  if (!text || isProse(text)) {
    return '';
  }
  return text;
}

/**
 * Turns streamed model text into the suffix that is newly safe to show.
 * Already shown characters stay put; fences and a repeated current line are withheld.
 */
export function createStreamSanitizer(prefix) {
  let raw = '';
  let shown = '';

  function take(text) {
    if (!text.startsWith(shown)) {
      return '';
    }
    const extra = text.slice(shown.length);
    shown = text;
    return extra;
  }

  return {
    push(delta) {
      raw += delta || '';
      return take(sanitizeSuggestion(prefix, raw).replace(/`+$/u, ''));
    },
    finish() {
      return take(sanitizeSuggestion(prefix, raw));
    }
  };
}

function textDelta(event) {
  if (!event || event.type !== 'content_block_delta' || !event.delta) {
    return '';
  }
  return event.delta.type === 'text_delta' ? (event.delta.text || '') : '';
}

function fullMessageText(response) {
  return (response && response.content ? response.content : [])
    .filter((block) => block.type === 'text')
    .map((block) => block.text)
    .join('');
}

/**
 * Calls onDelta with each newly visible piece. Resolves when the model finishes or the signal aborts.
 * @param {{prefix?: string, suffix?: string, fileName?: string, language?: string}} request
 * @param {AbortSignal} signal
 * @param {(delta: string) => void} onDelta
 */
export async function predictStreaming(request, signal, onDelta) {
  const prefix = request?.prefix || '';
  if (!prefix.trim()) {
    return;
  }

  const config = setupApiKey();
  if (config.authType === 'cli_login') {
    return;
  }
  if (!config.apiKey && config.authType !== 'api_key_helper') {
    return;
  }

  const client = await createClient(config);
  const model = resolveFastModel();
  const controller = new AbortController();
  const timeoutHandle = setTimeout(() => controller.abort(), API_TIMEOUT_MS);
  const onAbort = () => controller.abort();
  if (signal) {
    if (signal.aborted) {
      controller.abort();
    } else {
      signal.addEventListener('abort', onAbort, { once: true });
    }
  }

  let raw = '';
  const body = {
    model,
    max_tokens: MAX_OUTPUT_TOKENS,
    messages: buildMessages(prefix)
  };

  try {
    let response;
    try {
      response = await client.messages.create({ ...body, stream: true }, { signal: controller.signal });
    } catch (error) {
      if (controller.signal.aborted) {
        return;
      }
      response = await client.messages.create(body, { signal: controller.signal });
    }

    if (response && typeof response[Symbol.asyncIterator] === 'function') {
      for await (const event of response) {
        if (controller.signal.aborted) {
          break;
        }
        raw += textDelta(event);
      }
    }

    const text = presentSuggestion(prefix, raw || fullMessageText(response));

    if (text) {
      onDelta(text);
    }
  } catch (error) {
    const text = presentSuggestion(prefix, raw);
    if (text) {
      onDelta(text);
    }
    if (controller.signal.aborted) {
      return;
    }
    throw error;
  } finally {
    clearTimeout(timeoutHandle);
    if (signal) {
      signal.removeEventListener('abort', onAbort);
    }
  }
}
