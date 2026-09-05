import React, { useEffect, useState } from "react";
import { View } from "react-native";
import { Button } from "@/components/Button";
import { AppText, Screen } from "@/components/Themed";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

/** MVP-10 (B): shown instead of the app when biometric unlock is on. A failed attempt just
 * offers "Try again" — it never signs the user out. */
export function BiometricLockScreen() {
  const { theme } = useTheme();
  const { unlock } = useSession();
  const [failed, setFailed] = useState(false);

  async function attempt() {
    setFailed(false);
    const ok = await unlock();
    if (!ok) setFailed(true);
  }

  useEffect(() => {
    attempt();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <Screen scroll={false}>
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center", gap: theme.space(4) }}>
        <AppText size="xl" weight="700">
          🔒 Single Point is locked
        </AppText>
        <AppText tone="muted" style={{ textAlign: "center" }}>
          {failed ? "That didn't work — try again." : "Unlock with Face ID / fingerprint to continue."}
        </AppText>
        <Button label="Unlock" onPress={attempt} fullWidth={false} />
      </View>
    </Screen>
  );
}
