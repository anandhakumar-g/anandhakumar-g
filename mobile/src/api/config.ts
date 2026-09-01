import Constants from "expo-constants";
import { Platform } from "react-native";

/**
 * Base URL for the Single Point API.
 * - web / iOS simulator / same machine: localhost:18080
 * - Android emulator: 10.0.2.2 maps to the host
 * - real device: set EXPO_PUBLIC_API_URL to your machine's LAN IP, e.g. http://192.168.1.20:18080
 */
function resolveBaseUrl(): string {
  const fromEnv = process.env.EXPO_PUBLIC_API_URL;
  if (fromEnv) return fromEnv.replace(/\/+$/, "");

  const host = Constants.expoConfig?.hostUri?.split(":")[0];
  if (Platform.OS === "android" && (!host || host === "localhost" || host === "127.0.0.1")) {
    return "http://10.0.2.2:18080";
  }
  if (host && Platform.OS !== "web") return `http://${host}:18080`;
  return "http://localhost:18080";
}

export const API_BASE = `${resolveBaseUrl()}/api/v1`;
