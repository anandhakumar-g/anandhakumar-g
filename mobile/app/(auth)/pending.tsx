import { useRouter } from "expo-router";
import React, { useState } from "react";
import { View } from "react-native";
import { Button } from "@/components/Button";
import { AppText, Screen } from "@/components/Themed";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

export default function Pending() {
  const { theme } = useTheme();
  const router = useRouter();
  const { refreshMe, signOut } = useSession();
  const [busy, setBusy] = useState(false);

  async function check() {
    setBusy(true);
    await refreshMe();
    setBusy(false);
    router.replace("/");
  }

  return (
    <Screen scroll={false}>
      <View style={{ flex: 1, justifyContent: "center", gap: theme.space(4), alignItems: "center" }}>
        <AppText size="xl" weight="700">
          Waiting for approval
        </AppText>
        <AppText tone="muted" style={{ textAlign: "center", maxWidth: 320 }}>
          Your request to join has been sent to the community admin. You'll get a notification once
          it's approved.
        </AppText>
        <Button label="Check again" onPress={check} loading={busy} fullWidth={false} />
        <Button label="Sign out" variant="ghost" onPress={signOut} fullWidth={false} />
      </View>
    </Screen>
  );
}
