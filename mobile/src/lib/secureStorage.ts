import AsyncStorage from "@react-native-async-storage/async-storage";
import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";

/**
 * expo-secure-store is native-only. This project's dev loop is Expo **web** (`npm run web`),
 * where SecureStore is unavailable — so fall back to AsyncStorage (localStorage-backed on
 * web). Every call is also try/caught so a SecureStore failure degrades instead of crashing.
 */
const useSecure = Platform.OS !== "web";

export async function secureGet(key: string): Promise<string | null> {
  try {
    return useSecure ? await SecureStore.getItemAsync(key) : await AsyncStorage.getItem(key);
  } catch {
    try {
      return await AsyncStorage.getItem(key);
    } catch {
      return null;
    }
  }
}

export async function secureSet(key: string, value: string): Promise<void> {
  try {
    if (useSecure) await SecureStore.setItemAsync(key, value);
    else await AsyncStorage.setItem(key, value);
  } catch {
    try {
      await AsyncStorage.setItem(key, value);
    } catch {
      /* ignore — a per-viewer convenience, not critical state */
    }
  }
}

export async function secureDelete(key: string): Promise<void> {
  try {
    if (useSecure) await SecureStore.deleteItemAsync(key);
    else await AsyncStorage.removeItem(key);
  } catch {
    try {
      await AsyncStorage.removeItem(key);
    } catch {
      /* ignore */
    }
  }
}
