/**
 * One-shot commit message generation.
 * Calls the messages API directly so it does not start a Claude Code agent turn.
 */

import { loadClaudeSettings, setupApiKey } from '../config/api-config.js';
import { ensureAnthropicSdk, ensureBedrockSdk } from './claude/message-utils.js';
import { resolveModelFromSettings } from '../utils/model-utils.js';

const DEFAULT_HAIKU_MODEL = 'claude-haiku-4-5-20251001';
const API_TIMEOUT_MS = 20000;
const MAX_OUTPUT_TOKENS = 512;

const SYSTEM_PROMPT = `你是 Git 提交说明生成器。根据给出的代码变更，只输出一条 Conventional Commits 提交说明。

规则：
- 语言使用中文
- 格式：type(scope): 描述。必要时空一行再写简短正文
- type 只能是 feat、fix、docs、style、refactor、perf、test、chore、ci、build、revert
- 主题不超过 72 个字符，使用祈使语气
- 只根据给出的变更写，不要编造未出现的文件或行为
- 小变更用单行。不要解释，不要署名，不要 emoji
- 必须用 <commit> 和 </commit> 包裹，标签外不要有任何内容`;

function fail(code, message) {
  const error = new Error(message);
  error.code = code;
  return error;
}

function resolveHaikuModel() {
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

/**
 * @param {string} prompt diff and any extra user instructions
 * @returns {Promise<string>} raw model text
 */
export async function generateCommitMessage(prompt) {
  if (!prompt || !String(prompt).trim()) {
    throw fail('empty_prompt', 'No diff to summarize');
  }

  const config = setupApiKey();
  if (config.authType === 'cli_login') {
    throw fail('cli_login', 'CLI login cannot use the direct commit API');
  }
  if (!config.apiKey && config.authType !== 'api_key_helper') {
    throw fail('no_api_key', 'No API key available');
  }

  const client = await createClient(config);
  const model = resolveHaikuModel();
  const controller = new AbortController();
  const timeoutHandle = setTimeout(() => controller.abort(), API_TIMEOUT_MS);

  let response;
  try {
    response = await client.messages.create({
      model,
      max_tokens: MAX_OUTPUT_TOKENS,
      thinking: { type: 'disabled' },
      system: SYSTEM_PROMPT,
      messages: [{ role: 'user', content: String(prompt) }]
    }, { signal: controller.signal });
  } catch (error) {
    if (controller.signal.aborted) {
      throw fail('timeout', 'Commit message request timed out');
    }
    throw error;
  } finally {
    clearTimeout(timeoutHandle);
  }

  const text = (response.content || [])
    .filter((block) => block.type === 'text')
    .map((block) => block.text)
    .join('')
    .trim();
  if (!text) {
    throw fail('empty_response', 'Model returned an empty commit message');
  }
  return text;
}
