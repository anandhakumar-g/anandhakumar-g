import * as SecureStore from "expo-secure-store";

const DEVICE_ID_KEY = "sp.deviceId";
let cached: string | null = null;

/**
 * A per-install id, not a secret — it only binds the session JWT to the device that requested
 * it (MVP-10 (B)); the JWT signature is the real security boundary. Persisted so it survives
 * app restarts; a fresh install gets a new one (a "new device" for binding purposes).
 */
function generate(): string {
  const rand = () => Math.random().toString(36).slice(2);
  return `${Date.now().toString(36)}-${rand()}-${rand()}`;
}

/** Resolves (and persists) this install's device id. Call once at bootstrap. */
export async function ensureDeviceId(): Promise<string> {
  if (cached) return cached;
  let id = await SecureStore.getItemAsync(DEVICE_ID_KEY);
  if (!id) {
    id = generate();
    await SecureStore.setItemAsync(DEVICE_ID_KEY, id);
  }
  cached = id;
  return id;
}

/** Synchronous accessor for the API client — null until ensureDeviceId() has resolved once. */
export function getCachedDeviceId(): string | null {
  return cached;
}
