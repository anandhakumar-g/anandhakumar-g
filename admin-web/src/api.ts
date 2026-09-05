const BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:18080/api/v1";
const TOKEN_KEY = "sp.console.token";

export class ApiError extends Error {
  status: number;
  code: string;
  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}
export function setToken(t: string | null) {
  try {
    if (t) localStorage.setItem(TOKEN_KEY, t);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* ignore */
  }
}

let onUnauthorized: () => void = () => {};
export function configure(opts: { onUnauthorized: () => void }) {
  onUnauthorized = opts.onUnauthorized;
}

interface Opts {
  auth?: boolean;
  query?: Record<string, string | number | boolean | undefined | null>;
}

async function request<T>(method: string, path: string, body?: unknown, opts: Opts = {}): Promise<T> {
  const { auth = true, query } = opts;
  let url = `${BASE}${path}`;
  if (query) {
    const qs = Object.entries(query)
      .filter(([, v]) => v !== undefined && v !== null && v !== "")
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
      .join("&");
    if (qs) url += `?${qs}`;
  }
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (auth) {
    const t = getToken();
    if (t) headers.Authorization = `Bearer ${t}`;
  }
  let res: Response;
  try {
    res = await fetch(url, { method, headers, body: body != null ? JSON.stringify(body) : undefined });
  } catch (e) {
    throw new ApiError(0, "NETWORK", `Cannot reach the API at ${BASE}. ${(e as Error)?.message ?? ""}`.trim());
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) {
    if (res.status === 401) onUnauthorized();
    throw new ApiError(res.status, data?.errorCode ?? "ERROR", data?.message ?? `Request failed (${res.status})`);
  }
  return data as T;
}

export const api = {
  get: <T,>(path: string, opts?: Opts) => request<T>("GET", path, undefined, opts),
  post: <T,>(path: string, body?: unknown, opts?: Opts) => request<T>("POST", path, body, opts),
  put: <T,>(path: string, body?: unknown, opts?: Opts) => request<T>("PUT", path, body, opts),
  del: <T,>(path: string, opts?: Opts) => request<T>("DELETE", path, undefined, opts),
};
