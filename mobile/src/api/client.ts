import { API_BASE } from "./config";

export class ApiError extends Error {
  status: number;
  code: string;
  fieldErrors?: { field: string; message: string }[];
  constructor(status: number, code: string, message: string, fieldErrors?: any) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

let tokenGetter: () => string | null = () => null;
let onUnauthorized: () => void = () => {};
let deviceIdGetter: () => string | null = () => null;

export function configureClient(opts: {
  getToken: () => string | null;
  onUnauthorized: () => void;
  getDeviceId?: () => string | null;
}) {
  tokenGetter = opts.getToken;
  onUnauthorized = opts.onUnauthorized;
  if (opts.getDeviceId) deviceIdGetter = opts.getDeviceId;
}

interface RequestOpts {
  auth?: boolean;
  query?: Record<string, string | number | boolean | undefined | null>;
}

async function request<T>(method: string, path: string, body?: any, opts: RequestOpts = {}): Promise<T> {
  const { auth = true, query } = opts;
  let url = `${API_BASE}${path}`;
  if (query) {
    const qs = Object.entries(query)
      .filter(([, v]) => v !== undefined && v !== null && v !== "")
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
      .join("&");
    if (qs) url += `?${qs}`;
  }

  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (auth) {
    const t = tokenGetter();
    if (t) headers.Authorization = `Bearer ${t}`;
  }
  // MVP-10 (B): sent whenever a device id exists — including on /auth/otp/verify (unauthenticated),
  // which is where the id gets bound into a fresh token.
  const deviceId = deviceIdGetter();
  if (deviceId) headers["X-Device-Id"] = deviceId;

  let res: Response;
  try {
    res = await fetch(url, { method, headers, body: body != null ? JSON.stringify(body) : undefined });
  } catch (e: any) {
    throw new ApiError(0, "NETWORK", `Cannot reach the server. ${e?.message ?? ""}`.trim());
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!res.ok) {
    if (res.status === 401) onUnauthorized();
    const msg = data?.message || `Request failed (${res.status})`;
    throw new ApiError(res.status, data?.errorCode ?? "ERROR", msg, data?.fieldErrors);
  }
  return data as T;
}

export async function uploadFile<T>(
  path: string,
  file: { uri: string; name: string; type: string },
  fields?: Record<string, string>
): Promise<T> {
  const form = new FormData();
  if (fields) for (const [k, v] of Object.entries(fields)) form.append(k, v);
  // @ts-expect-error RN FormData file shape
  form.append("file", { uri: file.uri, name: file.name, type: file.type });
  const headers: Record<string, string> = {};
  const t = tokenGetter();
  if (t) headers.Authorization = `Bearer ${t}`;
  const deviceId = deviceIdGetter();
  if (deviceId) headers["X-Device-Id"] = deviceId;
  const res = await fetch(`${API_BASE}${path}`, { method: "POST", headers, body: form as any });
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) throw new ApiError(res.status, data?.errorCode ?? "ERROR", data?.message ?? "Upload failed");
  return data as T;
}

export const api = {
  get: <T>(path: string, opts?: RequestOpts) => request<T>("GET", path, undefined, opts),
  post: <T>(path: string, body?: any, opts?: RequestOpts) => request<T>("POST", path, body, opts),
  put: <T>(path: string, body?: any, opts?: RequestOpts) => request<T>("PUT", path, body, opts),
  del: <T>(path: string, opts?: RequestOpts) => request<T>("DELETE", path, undefined, opts),
};
