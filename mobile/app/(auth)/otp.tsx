import { useLocalSearchParams, useRouter } from "expo-router";
import React, { useState } from "react";
import { View } from "react-native";
import { auth } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Field } from "@/components/Field";
import { AppText, Screen } from "@/components/Themed";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

export default function Otp() {
  const { theme } = useTheme();
  const router = useRouter();
  const { phone, dev } = useLocalSearchParams<{ phone: string; dev?: string }>();
  const { signIn } = useSession();
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function verify() {
    setError(null);
    setBusy(true);
    try {
      const session = await auth.verifyOtp(String(phone), code.trim());
      await signIn(session);
      // Gate in _layout routes onward based on onboardingState / role.
      router.replace("/");
    } catch (e: any) {
      setError(e.message ?? "Invalid code");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen scroll={false}>
      <View style={{ flex: 1, justifyContent: "center", gap: theme.space(5) }}>
        <View style={{ gap: theme.space(2) }}>
          <AppText size="xl" weight="700">
            Enter your code
          </AppText>
          <AppText tone="muted">Sent to {phone}</AppText>
          {dev ? (
            <AppText size="xs" tone="primary">
              Dev code: {dev}
            </AppText>
          ) : null}
        </View>

        <Field
          label="6-digit code"
          placeholder="••••••"
          keyboardType="number-pad"
          maxLength={6}
          value={code}
          onChangeText={setCode}
          error={error}
          onSubmitEditing={verify}
          returnKeyType="go"
          style={{ fontSize: theme.font.xl, letterSpacing: 8, textAlign: "center" }}
        />

        <Button label="Verify" onPress={verify} loading={busy} disabled={code.trim().length < 4} />
        <Button label="Change number" variant="ghost" onPress={() => router.back()} />
      </View>
    </Screen>
  );
}
