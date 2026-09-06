import { Platform } from "react-native";
import { API_BASE } from "@/api/config";

/**
 * CSV export downloads only work on Expo web — native needs `expo-file-system` + a share
 * sheet (documented follow-up). Screens hide the export row when this is false.
 */
export const canDownloadCsv = Platform.OS === "web";

/**
 * Fetch a `text/csv` attachment with the session bearer token and hand it to the browser
 * as a download. `<a download>` can't set an Authorization header, so: fetch → blob →
 * object URL → synthetic click.
 */
export async function downloadCsv(
  path: string,
  token: string | null,
  query?: Record<string, string | number | undefined | null>,
): Promise<void> {
  let url = `${API_BASE}${path}`;
  if (query) {
    const qs = Object.entries(query)
      .filter(([, v]) => v !== undefined && v !== null && v !== "")
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
      .join("&");
    if (qs) url += `?${qs}`;
  }
  const res = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!res.ok) {
    let msg = `Download failed (${res.status})`;
    try {
      msg = JSON.parse(await res.text())?.message ?? msg;
    } catch {
      /* not JSON — keep the generic message */
    }
    throw new Error(msg);
  }
  const blob = await res.blob();
  const cd = res.headers.get("Content-Disposition") ?? "";
  const name = /filename="?([^"]+)"?/.exec(cd)?.[1] ?? path.split("/").pop() ?? "export.csv";
  const objUrl = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = objUrl;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(objUrl), 1000);
}
