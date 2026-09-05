import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { ApiError, api, configure, getToken, setToken } from "./api";

interface SessionResponse {
  token: string;
  user: { id: string; role: string; name: string | null };
}
interface Me {
  userId: string;
  role: string;
  name: string | null;
}

interface AuthValue {
  ready: boolean;
  me: Me | null;
  requestOtp: (phone: string) => Promise<string | null>;
  verifyOtp: (phone: string, code: string) => Promise<void>;
  signOut: () => void;
}

const Ctx = createContext<AuthValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [me, setMe] = useState<Me | null>(null);

  const signOut = useCallback(() => {
    setToken(null);
    setMe(null);
  }, []);

  useEffect(() => {
    configure({ onUnauthorized: signOut });
  }, [signOut]);

  useEffect(() => {
    (async () => {
      if (getToken()) {
        try {
          const m = await api.get<Me>("/me", { auth: true });
          if (m.role === "SUPER_ADMIN") setMe(m);
          else setToken(null);
        } catch {
          setToken(null);
        }
      }
      setReady(true);
    })();
  }, []);

  const requestOtp = useCallback(async (phone: string) => {
    const r = await api.post<{ devCode: string | null }>("/auth/otp/request", { phone }, { auth: false });
    return r.devCode;
  }, []);

  const verifyOtp = useCallback(async (phone: string, code: string) => {
    const s = await api.post<SessionResponse>("/auth/otp/verify", { phone, code }, { auth: false });
    if (s.user.role !== "SUPER_ADMIN") {
      throw new ApiError(403, "SP-403", "This console is for platform Super Admins only.");
    }
    setToken(s.token);
    const m = await api.get<Me>("/me");
    setMe(m);
  }, []);

  const value = useMemo<AuthValue>(
    () => ({ ready, me, requestOtp, verifyOtp, signOut }),
    [ready, me, requestOtp, verifyOtp, signOut]
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthValue {
  const v = useContext(Ctx);
  if (!v) throw new Error("useAuth outside AuthProvider");
  return v;
}
