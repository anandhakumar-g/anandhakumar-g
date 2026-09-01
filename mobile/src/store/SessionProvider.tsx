import AsyncStorage from "@react-native-async-storage/async-storage";
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { configureClient } from "@/api/client";
import { me as meApi } from "@/api/endpoints";
import { MeResponse, OnboardingState, SessionResponse, UserSummary } from "@/api/types";
import { useTheme } from "@/theme/ThemeProvider";

const TOKEN_KEY = "sp.token";

interface SessionValue {
  ready: boolean;
  token: string | null;
  user: UserSummary | null;
  me: MeResponse | null;
  onboardingState: OnboardingState | null;
  signIn: (s: SessionResponse) => Promise<void>;
  signOut: () => Promise<void>;
  refreshMe: () => Promise<void>;
}

const SessionContext = createContext<SessionValue | undefined>(undefined);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const { setBrand } = useTheme();
  const [ready, setReady] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<UserSummary | null>(null);
  const [meData, setMeData] = useState<MeResponse | null>(null);
  const [onboardingState, setOnboardingState] = useState<OnboardingState | null>(null);
  const tokenRef = useRef<string | null>(null);

  configureClient({
    getToken: () => tokenRef.current,
    onUnauthorized: () => {
      tokenRef.current = null;
      setToken(null);
      setUser(null);
      setMeData(null);
      AsyncStorage.removeItem(TOKEN_KEY);
    },
  });

  const applyToken = useCallback((t: string | null) => {
    tokenRef.current = t;
    setToken(t);
  }, []);

  const refreshMe = useCallback(async () => {
    if (!tokenRef.current) return;
    try {
      const m = await meApi.get();
      setMeData(m);
      setUser((u) =>
        u
          ? { ...u, name: m.name, role: m.role, preferredTheme: m.preferredTheme, activeTenantId: m.activeTenantId }
          : {
              id: m.userId, role: m.role, name: m.name, phoneMasked: m.phoneMasked,
              profileCompleted: m.profileCompleted, preferredTheme: m.preferredTheme,
              activeTenantId: m.activeTenantId,
            }
      );
      setBrand({
        defaultTheme: m.activeTenantBranding?.defaultTheme,
        brandPrimaryColor: m.activeTenantBranding?.brandPrimaryColor,
      });
    } catch {
      /* ignore — keep last known */
    }
  }, [setBrand]);

  const signIn = useCallback(
    async (s: SessionResponse) => {
      applyToken(s.token);
      setUser(s.user);
      setOnboardingState(s.onboardingState);
      await AsyncStorage.setItem(TOKEN_KEY, s.token);
      await refreshMe();
    },
    [applyToken, refreshMe]
  );

  const signOut = useCallback(async () => {
    applyToken(null);
    setUser(null);
    setMeData(null);
    setOnboardingState(null);
    setBrand({ defaultTheme: null, brandPrimaryColor: null });
    await AsyncStorage.removeItem(TOKEN_KEY);
  }, [applyToken, setBrand]);

  useEffect(() => {
    (async () => {
      const t = await AsyncStorage.getItem(TOKEN_KEY);
      if (t) {
        applyToken(t);
        await refreshMe();
      }
      setReady(true);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Derive onboarding state from /me once we have it (covers app relaunch).
  useEffect(() => {
    if (!meData) return;
    if (!meData.profileCompleted) return setOnboardingState("NEEDS_PROFILE");
    if (meData.role === "RESIDENT" && !meData.activeTenantId) {
      const pending = meData.memberships.some((m) => m.status === "PENDING_APPROVAL");
      return setOnboardingState(pending ? "PENDING_APPROVAL" : "NEEDS_COMMUNITY");
    }
    setOnboardingState("READY");
  }, [meData]);

  const value = useMemo<SessionValue>(
    () => ({ ready, token, user, me: meData, onboardingState, signIn, signOut, refreshMe }),
    [ready, token, user, meData, onboardingState, signIn, signOut, refreshMe]
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error("useSession must be used within SessionProvider");
  return ctx;
}
