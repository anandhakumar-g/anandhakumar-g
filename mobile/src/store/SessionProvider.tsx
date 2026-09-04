import AsyncStorage from "@react-native-async-storage/async-storage";
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { ApiError, configureClient } from "@/api/client";
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
  switchCommunity: (tenantId: string) => Promise<void>;
  leaveCommunity: (tenantId: string) => Promise<void>;
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

  const applyToken = useCallback((t: string | null) => {
    tokenRef.current = t;
    setToken(t);
  }, []);

  const clearSession = useCallback(() => {
    tokenRef.current = null;
    setToken(null);
    setUser(null);
    setMeData(null);
    setOnboardingState(null);
    setBrand({ defaultTheme: null, brandPrimaryColor: null });
    AsyncStorage.removeItem(TOKEN_KEY);
  }, [setBrand]);

  configureClient({
    getToken: () => tokenRef.current,
    onUnauthorized: clearSession,
  });

  const refreshMe = useCallback(
    async (opts?: { dropStaleToken?: boolean }) => {
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
      } catch (e) {
        // A stored token that no longer resolves (user/tenant gone after a data reset, expired,
        // revoked) comes back as a 4xx. Drop it so the app falls back to the login screen instead
        // of hanging. A 5xx or a network error is transient — keep the last known session.
        const stale = e instanceof ApiError && e.status >= 400 && e.status < 500;
        if (opts?.dropStaleToken && stale) clearSession();
      }
    },
    [setBrand, clearSession]
  );

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
    clearSession();
  }, [clearSession]);

  const switchCommunity = useCallback(async (tenantId: string) => {
    await signIn(await meApi.switchCommunity(tenantId));
  }, [signIn]);

  const leaveCommunity = useCallback(async (tenantId: string) => {
    await signIn(await meApi.leaveCommunity(tenantId));
  }, [signIn]);

  useEffect(() => {
    (async () => {
      const t = await AsyncStorage.getItem(TOKEN_KEY);
      if (t) {
        applyToken(t);
        await refreshMe({ dropStaleToken: true });
      }
      setReady(true);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Derive onboarding state from /me once we have it (covers app relaunch).
  useEffect(() => {
    if (!meData) return;
    if (!meData.profileCompleted) return setOnboardingState("NEEDS_PROFILE");
    // MVP-8: a profile-complete resident with no community is READY (a community-less
    // individual). They wait only while a join request is pending approval.
    if (
      meData.role === "RESIDENT" &&
      !meData.activeTenantId &&
      meData.memberships.some((m) => m.status === "PENDING_APPROVAL")
    ) {
      return setOnboardingState("PENDING_APPROVAL");
    }
    setOnboardingState("READY");
  }, [meData]);

  const value = useMemo<SessionValue>(
    () => ({ ready, token, user, me: meData, onboardingState, signIn, signOut, refreshMe,
             switchCommunity, leaveCommunity }),
    [ready, token, user, meData, onboardingState, signIn, signOut, refreshMe, switchCommunity, leaveCommunity]
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error("useSession must be used within SessionProvider");
  return ctx;
}
