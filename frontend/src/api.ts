import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform, TurboModuleRegistry } from 'react-native';

const BACKEND_PORT = '9000';

function inferredApiUrl(): string {
  if (Platform.OS === 'web') {
    const hostname = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
    return `http://${hostname}:${BACKEND_PORT}`;
  }

  // During Expo Go development, the JS bundle URL reveals the computer's LAN
  // address. A phone's localhost is the phone itself, so reuse that host for API calls.
  const sourceCode = TurboModuleRegistry.get('SourceCode') as {
    getConstants?: () => { scriptURL?: string };
    scriptURL?: string;
  } | null;
  const scriptUrl = sourceCode?.getConstants?.().scriptURL || sourceCode?.scriptURL;
  if (scriptUrl) {
    try {
      const host = new URL(scriptUrl).hostname;
      if (host && host !== 'localhost' && host !== '127.0.0.1') {
        return `http://${host}:${BACKEND_PORT}`;
      }
    } catch {
      // Fall through to the Android emulator or local development default.
    }
  }

  if (Platform.OS === 'android') return `http://10.0.2.2:${BACKEND_PORT}`;
  return `http://localhost:${BACKEND_PORT}`;
}

const configuredApiUrl = process.env.EXPO_PUBLIC_API_URL?.trim();
const localhostOverride = configuredApiUrl && /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/i.test(configuredApiUrl);
export const API_URL = (localhostOverride ? inferredApiUrl() : configuredApiUrl || inferredApiUrl()).replace(/\/$/, '');

const DEVICE_KEY = 'civitas_device_fingerprint';
const TOKEN_KEY = 'civitas_token';
const MEMBER_KEY = 'civitas_member';
const LANGUAGE_KEY = 'civitas_language';

let fingerprint: string | null = null;

async function deviceFingerprint(): Promise<string> {
  if (fingerprint) return fingerprint;
  try {
    const stored = await AsyncStorage.getItem(DEVICE_KEY);
    if (stored) {
      fingerprint = stored;
      return stored;
    }
    const generated = `${Platform.OS}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
    await AsyncStorage.setItem(DEVICE_KEY, generated);
    fingerprint = generated;
    return generated;
  } catch {
    fingerprint = `${Platform.OS}-ephemeral`;
    return fingerprint;
  }
}

export class ApiError extends Error {
  status: number;
  constructor(message: string, status = 0) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  token?: string | null;
  headers?: Record<string, string>;
  timeoutMs?: number;
};

function friendlyNetworkError(error: unknown): Error {
  if (error instanceof ApiError) return error;
  if (error instanceof Error && error.name === 'AbortError') {
    return new ApiError(`The server at ${API_URL} did not respond in time. Check that the backend is running.`);
  }
  return new ApiError(`Cannot reach the CIVITAS backend at ${API_URL}. Check the server address and Wi-Fi, then try again.`);
}

async function parse<T>(response: Response): Promise<T> {
  const raw = await response.text();
  let data: any = {};
  if (raw) {
    try {
      data = JSON.parse(raw);
    } catch {
      data = { message: raw };
    }
  }
  if (!response.ok) {
    const message =
      data.message ||
      data.detail ||
      data.error ||
      (response.status === 401
        ? 'Your session expired. Sign in again.'
        : response.status === 403
          ? 'The backend denied this action for your account.'
          : `Request failed (${response.status}).`);
    throw new ApiError(String(message), response.status);
  }
  return data as T;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, token, headers = {}, timeoutMs = 20000 } = options;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(`${API_URL}${path}`, {
      method,
      signal: controller.signal,
      headers: {
        Accept: 'application/json',
        'X-Device-Fingerprint': await deviceFingerprint(),
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    });
    return await parse<T>(response);
  } catch (error) {
    throw friendlyNetworkError(error);
  } finally {
    clearTimeout(timeout);
  }
}

/* ------------------------------------------------------------------ */
/* Session storage                                                     */
/* ------------------------------------------------------------------ */

export const storage = {
  async token(): Promise<string | null> {
    return AsyncStorage.getItem(TOKEN_KEY);
  },
  async member() {
    const raw = await AsyncStorage.getItem(MEMBER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  },
  async saveSession(token: string, member: unknown) {
    await AsyncStorage.multiSet([
      [TOKEN_KEY, token],
      [MEMBER_KEY, JSON.stringify(member)],
    ]);
  },
  async clearSession() {
    await AsyncStorage.multiRemove([TOKEN_KEY, MEMBER_KEY]);
  },
  async language(): Promise<string | null> {
    return AsyncStorage.getItem(LANGUAGE_KEY);
  },
  async saveLanguage(language: string) {
    await AsyncStorage.setItem(LANGUAGE_KEY, language);
  },
};

/* ------------------------------------------------------------------ */
/* Authenticated file downloads                                        */
/* ------------------------------------------------------------------ */

const BASE64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

function encodeBase64(bytes: Uint8Array): string {
  let output = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i];
    const b1 = i + 1 < bytes.length ? bytes[i + 1] : 0;
    const b2 = i + 2 < bytes.length ? bytes[i + 2] : 0;
    output += BASE64[b0 >> 2];
    output += BASE64[((b0 & 3) << 4) | (b1 >> 4)];
    output += i + 1 < bytes.length ? BASE64[((b1 & 15) << 2) | (b2 >> 6)] : '=';
    output += i + 2 < bytes.length ? BASE64[b2 & 63] : '=';
  }
  return output;
}

export interface DownloadResult {
  filename: string;
  uri: string;
  shared: boolean;
  sha256?: string | null;
  signature?: string | null;
}

/**
 * Downloads a protected file with the bearer token and hands it to the OS share
 * sheet. On web it falls back to a normal browser download.
 */
export async function downloadFile(
  path: string,
  filename: string,
  token?: string | null,
  options: { method?: 'GET' | 'POST'; body?: unknown } = {},
): Promise<DownloadResult> {
  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method: options.method || 'GET',
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        'X-Device-Fingerprint': await deviceFingerprint(),
        ...(options.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {}),
    });
  } catch {
    throw new ApiError(`Cannot reach the CIVITAS backend at ${API_URL}.`);
  }
  if (!response.ok) {
    const raw = await response.text();
    let message = `Download failed (${response.status}).`;
    try {
      const parsed = JSON.parse(raw);
      message = parsed.message || message;
    } catch {
      // Keep the status message.
    }
    throw new ApiError(message, response.status);
  }
  const buffer = new Uint8Array(await response.arrayBuffer());
  const contentType = response.headers.get('Content-Type') || 'application/octet-stream';
  const sha256 = response.headers.get('X-CIVITAS-SHA256');
  const signature = response.headers.get('X-CIVITAS-SIGNATURE');

  if (Platform.OS === 'web') {
    const anyGlobal = globalThis as any;
    const blob = new anyGlobal.Blob([buffer], { type: contentType });
    const url = anyGlobal.URL.createObjectURL(blob);
    const anchor = anyGlobal.document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    anyGlobal.URL.revokeObjectURL(url);
    return { filename, uri: url, shared: false, sha256, signature };
  }

  const FileSystem = require('expo-file-system/legacy');
  const Sharing = require('expo-sharing');
  const uri = `${FileSystem.cacheDirectory}${filename}`;
  await FileSystem.writeAsStringAsync(uri, encodeBase64(buffer), {
    encoding: FileSystem.EncodingType.Base64,
  });
  let shared = false;
  if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(uri, {
      mimeType: contentType,
      dialogTitle: filename,
      UTI: contentType,
    });
    shared = true;
  }
  return { filename, uri, shared, sha256, signature };
}

export function downloadPath(path: string, filename: string): string {
  return `${API_URL}${path}`;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
