import AsyncStorage from "@react-native-async-storage/async-storage";
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useColorScheme } from "react-native";
import { THEMES, ThemeName, ThemeTokens } from "./tokens";

const STORAGE_KEY = "sp.theme";

interface ThemeContextValue {
  theme: ThemeTokens;
  themeName: ThemeName;
  isExplicit: boolean;
  brandPrimary: string | null;
  setTheme: (name: ThemeName | null) => void;
  setBrand: (opts: { defaultTheme?: string | null; brandPrimaryColor?: string | null }) => void;
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

function coerce(name: string | null | undefined): ThemeName | null {
  if (name && name in THEMES) return name as ThemeName;
  return null;
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const system = useColorScheme();
  const [explicit, setExplicit] = useState<ThemeName | null>(null);
  const [tenantDefault, setTenantDefault] = useState<ThemeName | null>(null);
  const [brandPrimary, setBrandPrimary] = useState<string | null>(null);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY)
      .then((v) => setExplicit(coerce(v)))
      .finally(() => setHydrated(true));
  }, []);

  const setTheme = useCallback((name: ThemeName | null) => {
    setExplicit(name);
    if (name) AsyncStorage.setItem(STORAGE_KEY, name);
    else AsyncStorage.removeItem(STORAGE_KEY);
  }, []);

  const setBrand = useCallback(
    (opts: { defaultTheme?: string | null; brandPrimaryColor?: string | null }) => {
      setTenantDefault(coerce(opts.defaultTheme));
      setBrandPrimary(opts.brandPrimaryColor ?? null);
    },
    []
  );

  const themeName: ThemeName = explicit ?? tenantDefault ?? (system === "dark" ? "dark" : "light");

  const theme = useMemo(() => {
    const base = THEMES[themeName];
    // Personal theme choice wins; only apply tenant brand colour when the user has NOT
    // explicitly picked a theme (blueprint 4.15 — brand default vs personal preference).
    if (brandPrimary && !explicit) {
      return { ...base, color: { ...base.color, primary: brandPrimary, accent: brandPrimary } };
    }
    return base;
  }, [themeName, brandPrimary, explicit]);

  const value: ThemeContextValue = {
    theme,
    themeName,
    isExplicit: explicit != null,
    brandPrimary,
    setTheme,
    setBrand,
  };

  if (!hydrated) return null;
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useTheme must be used within ThemeProvider");
  return ctx;
}
